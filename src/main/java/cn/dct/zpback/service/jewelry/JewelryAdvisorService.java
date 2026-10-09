package cn.dct.zpback.service.jewelry;

import cn.dct.zpback.entity.Chart;
import cn.dct.zpback.exception.JewelryValidationException;
import cn.dct.zpback.service.jewelry.JewelryChartAdapter.AdaptedChart;
import cn.dct.zpback.service.jewelry.JewelryEligibilityGate.Evidence;
import cn.dct.zpback.service.jewelry.JewelryEligibilityGate.GateResult;
import cn.dct.zpback.service.jewelry.JewelryEligibilityGate.GatedCandidate;
import cn.dct.zpback.service.jewelry.JewelryEligibilityGate.Modality;
import cn.dct.zpback.service.jewelry.JewelryQuestionnaireScorer.PlanetScore;
import cn.dct.zpback.service.jewelry.JewelryQuestionnaireScorer.ScoreResult;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * 行星珠宝建议｜主服务
 *
 * 全链路（01_最终开发总说明 §5）：
 * ChartAdapter → Scorer → GoalCandidates → EligibilityGate → Modality
 * → Ranker → FrozenResult → CatalogTagQuery → LiveJoin → Renderer
 *
 * 核心判断全部是确定性后端服务。LLM 只在结果锁定后负责自然语言表达，
 * 且当前 V1 直接使用固定文案（Fixed Copy Renderer），不依赖 LLM 可用性。
 *
 * 幂等：同一 assessmentId + 同一 answers + 同一 chartSnapshot，
 * 重复 resolve 必须得到完全相同的排序（GC18 / 16.非功能要求）。
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class JewelryAdvisorService {

    public static final String MODE_SELF = "SELF_ASSESSMENT";
    public static final String MODE_GIFT = "GIFT_BLESSING";

    /** 结果有效期 90 天（§11）：仅用于提醒重新评估，不自动失效 */
    public static final int VALID_DAYS = 90;

    /** 安全与诚信统一免责（§15） */
    public static final String DISCLAIMER =
            "饰品用于日常仪式、象征性支持与自我提醒，不替代医疗、心理、法律、财务或现实行动。";

    private final JewelryPolicies policies;
    private final JewelryChartAdapter chartAdapter;
    private final JewelryQuestionnaireScorer scorer;
    private final JewelryEligibilityGate gate;
    private final JewelryCatalogService catalog;
    private final JewelryWaitlistService waitlist;
    private final cn.dct.zpback.service.ChartService chartService;

    private final ObjectMapper mapper = new ObjectMapper();

    // ==================================================================
    // 对外：下发表单
    // ==================================================================

    /**
     * 下发问卷（前端不得硬编码题库，§03_1）
     */
    public Map<String, Object> buildQuestionnaire(String mode) {
        requireMode(mode);
        Map<String, Object> out = new LinkedHashMap<>();
        out.put("questionnaire_version", policies.QUESTIONNAIRE_VERSION);
        out.put("assessment_mode", mode);

        List<Map<String, Object>> questions = new ArrayList<>();
        for (JsonNode q : policies.questions(mode)) {
            Map<String, Object> qm = new LinkedHashMap<>();
            qm.put("question_id", q.path("id").asText());
            qm.put("type", q.path("type").asText());
            qm.put("prompt", q.path("prompt").asText());
            qm.put("required", policies.requiredQuestionIds(mode).contains(q.path("id").asText()));
            qm.put("copy_only", policies.isCopyOnly(mode, q.path("id").asText()));
            qm.put("max_select", policies.maxSelect(mode, q.path("id").asText()));
            qm.put("options", optionsOf(q));
            // 分支触发关系由 branch_questions[].trigger_option_ids 声明，不挂在本题的选项上
            questions.add(qm);
        }
        out.put("questions", questions);

        Map<String, Map<String, Object>> branchMap = new LinkedHashMap<>();
        for (JsonNode b : policies.branchQuestions(mode)) {
            Map<String, Object> bm = new LinkedHashMap<>();
            bm.put("question_id", b.path("id").asText());
            bm.put("type", b.path("type").asText());
            bm.put("prompt", b.path("prompt").asText());
            bm.put("required", false);
            bm.put("copy_only", false);
            bm.put("max_select", 1);
            bm.put("options", optionsOf(b));
            branchMap.put(b.path("id").asText(), bm);
        }
        out.put("branch_questions", branchMap);
        out.put("required_question_ids", policies.requiredQuestionIds(mode));
        out.put("max_goal_select", policies.maxGoalSelect(mode));
        return out;
    }

    private List<Map<String, Object>> optionsOf(JsonNode question) {
        List<Map<String, Object>> out = new ArrayList<>();
        for (JsonNode o : question.path("options")) {
            Map<String, Object> om = new LinkedHashMap<>();
            om.put("option_id", o.path("id").asText());
            om.put("label", o.path("label").asText(o.path("text").asText()));
            om.put("negative", o.path("planet_weights").isObject()
                    && negative(o.path("planet_weights")));
            JsonNode branch = o.path("branch_question_id");
            if (!branch.isMissingNode() && !branch.isNull()) om.put("branch_question_id", branch.asText());
            out.add(om);
        }
        return out;
    }

    private boolean negative(JsonNode weights) {
        boolean any = false;
        for (var it = weights.fields(); it.hasNext(); ) {
            if (it.next().getValue().asInt(0) < 0) any = true;
        }
        return any;
    }

    // ==================================================================
    // 对外：校验答案
    // ==================================================================

    /**
     * Resolve 前完整性校验（§03_7）
     *
     * @return 空 map 表示通过；否则为 error_code -> message
     */
    public Map<String, String> validateAnswers(String mode, Map<String, List<String>> answers) {
        Map<String, String> errors = new LinkedHashMap<>();
        requireMode(mode);

        for (String required : policies.requiredQuestionIds(mode)) {
            List<String> picked = answers.get(required);
            if (picked == null || picked.isEmpty()) {
                errors.put("QUESTION_UNANSWERED", required);
            }
        }

        Set<String> validOptions = new LinkedHashSet<>(policies.allOptionIds(mode));
        Set<String> branchIds = new LinkedHashSet<>();
        for (JsonNode b : policies.branchQuestions(mode)) {
            branchIds.add(b.path("id").asText());
        }

        for (Map.Entry<String, List<String>> e : answers.entrySet()) {
            String qid = e.getKey();
            boolean isBranch = branchIds.contains(qid);
            boolean known = policies.findQuestion(mode, qid) != null || isBranch;
            if (!known) {
                errors.put("UNKNOWN_QUESTION", qid);
                continue;
            }
            List<String> picked = e.getValue();
            if (picked == null || picked.isEmpty()) continue;
            if (!policies.isCopyOnly(mode, qid) && picked.size() > policies.maxSelect(mode, qid)) {
                errors.put("MAX_SELECT_EXCEEDED", qid);
            }
            for (String opt : picked) {
                if (!validOptions.contains(opt)) {
                    errors.put("UNKNOWN_OPTION", qid + ":" + opt);
                }
            }
        }
        return errors;
    }

    /** 前端按已选目标重新计算应激活的分支题（取消目标时同步移除其分支答案） */
    public List<String> resolveActiveBranches(String mode, List<String> goalOptionIds) {
        List<String> branches = new ArrayList<>();
        for (String opt : goalOptionIds) {
            for (String b : policies.branchOfOption(mode, opt)) {
                if (!branches.contains(b)) branches.add(b);
            }
            if (branches.size() >= 2) break;   // 最多 2 道分支题
        }
        return branches;
    }

    // ==================================================================
    // 主入口：Resolve
    // ==================================================================

    /**
     * 按 chart_id 解析：出生资料由后端从 chart 表读取，前端不接触完整出生信息。
     *
     * 这是前端实际走的主路径（P1 选盘只拿到脱敏数据，
     * 提交时只回传 chart_id）。同时满足 01 §16「API 日志不得记录不必要的完整出生资料」。
     */
    public Map<String, Object> resolveByChartId(String mode,
                                                Map<String, List<String>> answers,
                                                String chartId,
                                                String subjectName,
                                                String userId,
                                                String assessmentId) {
        Chart chart = chartService.getChartById(chartId);
        if (chart == null) {
            throw new JewelryValidationException(Map.of("PJA_CHART_NOT_FOUND", String.valueOf(chartId)));
        }
        Map<String, Object> birth = birthParamsOf(chart);
        String snapshotId = "pjs_" + chartId + "_" + (assessmentId == null ? "adhoc" : assessmentId);
        Map<String, String> chartContext = new LinkedHashMap<>();
        chartContext.put("chart_id", chart.getChartId());
        chartContext.put("chart_snapshot_id", snapshotId);
        chartContext.put("subject_name", chart.getName() == null ? subjectName : chart.getName());
        chartContext.put("birth_time", chart.getBirthTime());
        return resolve(mode, answers, birth, chartContext, userId, assessmentId);
    }

    /** Chart → VedicAstroService 标准出生参数（复用现有排盘入口，不新建第二套） */
    private Map<String, Object> birthParamsOf(Chart chart) {
        Map<String, Object> birth = new LinkedHashMap<>();
        birth.put("name", chart.getName());
        birth.put("gender", chart.getGender());
        birth.put("birthDate", chart.getBirthDate());
        birth.put("birthTime", chart.getBirthTime());
        birth.put("birthPlace", chart.getBirthPlace());
        birth.put("latitude", chart.getLatitude());
        birth.put("longitude", chart.getLongitude());
        birth.put("timezone", chart.getTimezone());
        return birth;
    }

    /**
     * @param mode            SELF_ASSESSMENT / GIFT_BLESSING
     * @param answers         question_id -> option_ids（保持用户选择顺序）
     * @param birthParams     出生参数（复用 VedicAstroService，不新建第二套排盘）
     * @param chartContext    chart_id / snapshot_id / subject_name / birth_time
     */
    public Map<String, Object> resolve(String mode,
                                       Map<String, List<String>> answers,
                                       Map<String, Object> birthParams,
                                       Map<String, String> chartContext) {
        return resolve(mode, answers, birthParams, chartContext, null, null);
    }

    /**
     * 完整 Resolve：锁定推荐方向后再查实时商城（§5 总体架构最后两步）
     *
     * @param userId       用于回显补货订阅状态；可空
     * @param assessmentId 写入订阅来源，便于运营归因
     */
    public Map<String, Object> resolve(String mode,
                                       Map<String, List<String>> answers,
                                       Map<String, Object> birthParams,
                                       Map<String, String> chartContext,
                                       String userId,
                                       String assessmentId) {
        requireMode(mode);
        Map<String, String> errors = validateAnswers(mode, answers);
        if (!errors.isEmpty()) {
            throw new JewelryValidationException(errors);
        }

        String chartId = str(chartContext, "chart_id");
        String snapshotId = str(chartContext, "chart_snapshot_id");
        String subjectName = str(chartContext, "subject_name");
        String birthTime = chartContext == null ? null : chartContext.get("birth_time");

        // 1) Chart Adapter
        AdaptedChart chart = chartAdapter.adapt(birthParams, chartId, snapshotId, subjectName, birthTime);

        // 2) Questionnaire Scorer
        ScoreResult scored = scorer.score(mode, answers);

        // 过度表达题里带负权重的选项（用于饱和冲突判定，GC16）
        List<String> overExpressionOptions = new ArrayList<>();
        for (var en : scored.scores.entrySet()) {
            en.getValue().contributions.forEach((optId, w) -> {
                if (w < 0) overExpressionOptions.add(optId);
            });
        }

        // 3~5) Gate → Modality → Rank（此处之后推荐方向已锁定）
        GateResult gated = gate.evaluate(mode, chart, scored.candidates, overExpressionOptions);

        Map<String, Object> frozen = freeze(mode, chart, scored, gated, answers, assessmentId);

        // 6) 之后才查商城：目录无法影响上面的排序（§5 架构顺序）
        attachCatalog(mode, frozen, userId, assessmentId);
        return frozen;
    }

    /**
     * 冻结结果 + 实时商城匹配
     *
     * 关键：此处<b>只读</b> recommendations 里的 planet/modality，
     * 绝不因为库存或价格回写 rank（GC10 / GC14）。
     */
    @SuppressWarnings("unchecked")
    private void attachCatalog(String mode, Map<String, Object> frozen, String userId, String assessmentId) {
        List<Map<String, Object>> recommendations =
                (List<Map<String, Object>>) frozen.get("recommendations");
        List<Map<String, Object>> matches = new ArrayList<>();

        for (Map<String, Object> rec : recommendations) {
            String rank = String.valueOf(rec.get("rank"));
            String planet = String.valueOf(rec.get("planet_code"));
            String modality = String.valueOf(rec.get("modality"));
            List<String> allowed = allowedRemedyTypes(modality);
            Map<String, Object> match = catalog.match(planet, allowed, rank, userId);
            // catalog.match 已回填期待人数与本用户订阅状态（真实去重订阅，06 §6），
            // 这里只补 user_waitlist_status 映射，不再重复查询
            decorateUserStatus(match, userId);
            match.put("cta_payload", catalog.ctaForMode(rank, String.valueOf(match.get("match_state")), mode));
            matches.add(match);
            rec.put("catalog_query_tags", Map.of(
                    "planet_code", planet,
                    "allowed_remedy_types", allowed,
                    "modality", modality,
                    "rank", rank));
            rec.put("product_match_state", match.get("match_state"));
        }

        frozen.put("catalog_matches", matches);
        frozen.put("sticky_cta_payload", buildStickyCta(mode, matches, frozen));
    }

    /** Modality → 允许的补救类型（§6 Modality 与商品池） */
    private List<String> allowedRemedyTypes(String modality) {
        return switch (modality) {
            case "GEMSTONE_ELIGIBLE" -> List.of("GEMSTONE", "YANTRA");
            case "YANTRA_PREFERRED", "YANTRA_ONLY", "BLESSING_THEME_ONLY" -> List.of("YANTRA");
            default -> List.of();
        };
    }

    /**
     * 汇总「我登记了哪些缺货 SKU」：sku_id -> true。
     *
     * 期待人数与 user_subscribed 已由 catalog.match 回填，这里只做映射，
     * 避免同一份订阅被查两遍。
     */
    @SuppressWarnings("unchecked")
    private void decorateUserStatus(Map<String, Object> match, String userId) {
        Map<String, Boolean> mine = new LinkedHashMap<>();
        for (Map<String, Object> p : (List<Map<String, Object>>) match.getOrDefault("products", List.of())) {
            for (Map<String, Object> s : (List<Map<String, Object>>) p.getOrDefault("skus", List.of())) {
                if (Boolean.TRUE.equals(s.get("user_subscribed"))) {
                    mine.put(String.valueOf(s.get("sku_id")), Boolean.TRUE);
                }
            }
        }
        match.put("user_waitlist_status", mine);
    }

    /**
     * 固定底部 CTA（02_双模式页面流程 §8）
     *
     * 表格逐行落地：主推缺货时补货订阅 + 次推入口同时存在；
     * 无自动推荐时不提供购买 CTA。
     */
    @SuppressWarnings("unchecked")
    private Map<String, Object> buildStickyCta(String mode, List<Map<String, Object>> matches,
                                               Map<String, Object> frozen) {
        Map<String, Object> out = new LinkedHashMap<>();
        if (matches.isEmpty()) {
            out.put("primary_label", null);
            out.put("primary_action", "NONE");
            out.put("secondary_label", "重新了解说明");
            out.put("secondary_action", "BACK_TO_INTRO");
            out.put("mode", mode);
            return out;
        }

        Map<String, Object> primary = matches.get(0);
        Map<String, Object> secondary = matches.size() > 1 ? matches.get(1) : null;
        String primaryState = String.valueOf(primary.get("match_state"));
        String secondaryState = secondary == null ? null : String.valueOf(secondary.get("match_state"));

        if ("NO_MAPPED_PRODUCT".equals(primaryState)) {
            out.put("primary_label", "相关饰品暂未上架");
            out.put("primary_action", "NONE");
        } else if ("ALL_MATCHES_OUT_OF_STOCK".equals(primaryState)) {
            // 主推缺货：保留主推卡与 Rank，不把次推升为主推（§7）
            out.put("primary_label", "补货后通知我");
            out.put("primary_action", "SUBSCRIBE_WAITLIST");
        } else {
            out.put("primary_label", "GIFT_BLESSING".equals(mode) ? "为TA挑选礼物" : "查看主推饰品");
            out.put("primary_action", "VIEW_PRODUCTS");
        }

        if (secondary != null && !"NO_MAPPED_PRODUCT".equals(secondaryState)) {
            out.put("secondary_label", "查看次推饰品");
            out.put("secondary_action", "VIEW_SECONDARY");
        } else {
            out.put("secondary_label", null);
            out.put("secondary_action", "NONE");
        }
        out.put("mode", mode);
        return out;
    }

    /** 组装并冻结结果（§12 版本冻结） */
    private Map<String, Object> freeze(String mode, AdaptedChart chart, ScoreResult scored,
                                       GateResult gated, Map<String, List<String>> answers,
                                       String assessmentId) {
        List<Map<String, Object>> recommendations = new ArrayList<>();

        // 主次必须 planet 不同；最多两条（§9 / GC03 / GC04 / GC13）
        Set<String> usedPlanets = new LinkedHashSet<>();
        String[] ranks = {"PRIMARY", "SECONDARY"};

        for (int i = 0; i < gated.eligible.size() && recommendations.size() < 2; i++) {
            GatedCandidate c = gated.eligible.get(i);
            if (!usedPlanets.add(c.planet)) continue;   // GC13：同行星不同 SKU 不能占两条
            recommendations.add(recommendationNode(mode, c, ranks[recommendations.size()]));
        }

        String status;
        if (recommendations.size() == 2) {
            status = "TWO_RECOMMENDATIONS";
        } else if (recommendations.size() == 1) {
            status = "ONE_RECOMMENDATION";
        } else {
            status = "NO_AUTOMATIC_RECOMMENDATION";
        }

        Map<String, Object> out = new LinkedHashMap<>();
        out.put("assessment_mode", mode);
        out.put("resolution_status", status);
        out.put("chart_snapshot_id", chart.chartSnapshotId);
        out.put("assessment_id", assessmentId);   // engine-output.schema required
        out.put("data_quality", chart.dataQuality);
        out.put("birth_time_quality", chart.birthTimeQuality);
        out.put("ascendant_stable", chart.ascendantStable);
        out.put("goal_summary", goalSummary(mode, answers));
        out.put("recommendations", recommendations);

        // 打分阶段被淘汰的原因必须一并冻结：
        // 只回传 gate 阶段的原因时，"分数不足 / 无优先证据" 这批行星会完全消失，
        // 排查时无法区分「没进候选」与「进了候选但被门槛拦下」。
        List<Map<String, Object>> suppressed = new ArrayList<>();
        Map<String, List<String>> allSuppressed = new LinkedHashMap<>();
        scored.suppressed.forEach((planet, reasons) ->
                allSuppressed.computeIfAbsent(planet, k -> new ArrayList<>()).addAll(reasons));
        gated.suppressed.forEach((planet, reasons) ->
                allSuppressed.computeIfAbsent(planet, k -> new ArrayList<>()).addAll(reasons));
        allSuppressed.forEach((planet, reasons) -> {
            Map<String, Object> m = new LinkedHashMap<>();
            m.put("planet_code", planet);
            m.put("reason_codes", new ArrayList<>(reasons));
            suppressed.add(m);
        });
        out.put("suppressed_candidates", suppressed);

        // 审计：明确记录库存与价格未参与排序（GC10 / GC14）
        Map<String, Object> audit = new LinkedHashMap<>();
        audit.put("inventory_used_in_rank", false);
        audit.put("price_used_in_rank", false);
        audit.put("shadbala_used_as_automatic_trigger", false);
        audit.put("dasha_used_as_automatic_trigger", false);
        audit.put("notes", chart.notes);
        out.put("audit", audit);

        out.put("disclaimer", DISCLAIMER);
        out.put("valid_days", VALID_DAYS);

        Map<String, Object> versions = new LinkedHashMap<>(policies.versionSnapshot());
        out.put("versions", versions);
        out.put("resolved_at", OffsetDateTime.now(ZoneOffset.UTC)
                .format(DateTimeFormatter.ISO_OFFSET_DATE_TIME));
        return out;
    }

    private Map<String, Object> recommendationNode(String mode, GatedCandidate c, String rank) {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("rank", rank);
        m.put("planet_code", c.planet);
        m.put("planet_cn", planetCn(c.planet));
        m.put("modality", c.modality.name());
        m.put("confidence", c.confidence);
        m.put("goal_bridge", goalBridge(mode, c.planet));
        m.put("functional_role", c.functionalRole);

        Map<String, Object> sb = new LinkedHashMap<>();
        sb.put("raw_goal_score", c.rawGoalScore);
        sb.put("base_tier", c.baseTier);
        sb.put("structural_bonus", c.structuralBonus);
        sb.put("support_modifier", c.supportModifier);
        sb.put("dasha_tiebreaker", c.dashaTiebreaker);
        sb.put("downgrade_penalty", c.downgradePenalty);
        sb.put("rank_score", c.rankScore);
        m.put("score_breakdown", sb);

        List<Map<String, Object>> ev = new ArrayList<>();
        for (Evidence e : c.evidence) ev.add(e.toMap());
        m.put("evidence", ev);
        m.put("suppressed_upgrades", new ArrayList<>(c.suppressedUpgrades));

        // 商城只按标签查询，不写死任何商品 ID（§13）
        Map<String, Object> tags = new LinkedHashMap<>();
        tags.put("planet_code", c.planet);
        tags.put("remedy_type", allowedRemedyTypes(c.modality));
        tags.put("modality", c.modality.name());
        tags.put("rank", rank);
        m.put("catalog_query_tags", tags);

        m.put("fixed_copy", fixedCopy(mode, c));
        return m;
    }

    /** 模态 → 允许的补救类型；Hard Block 的行星不会有 Yantra 兜底（§10） */
    public List<String> allowedRemedyTypes(Modality modality) {
        return switch (modality) {
            case GEMSTONE_ELIGIBLE -> List.of("GEMSTONE", "YANTRA");
            case YANTRA_PREFERRED, YANTRA_ONLY, BLESSING_THEME_ONLY -> List.of("YANTRA");
            case NO_AUTOMATIC_RECOMMENDATION -> List.of();
        };
    }

    private List<String> goalSummary(String mode, Map<String, List<String>> answers) {
        List<String> out = new ArrayList<>();
        String goalQuestion = "SELF_ASSESSMENT".equals(mode) ? "S01" : "G01";
        for (String opt : answers.getOrDefault(goalQuestion, List.of())) {
            JsonNode o = policies.findOption(mode, opt);
            if (o != null) out.add(o.path("label").asText(o.path("text").asText()));
        }
        return out;
    }

    private String goalBridge(String mode, String planet) {
        JsonNode p = policies.goalPlanetMap().path("planets").path(planet);
        List<JsonNode> fn = new ArrayList<>();
        p.path("primary_functions").forEach(fn::add);
        if (fn.isEmpty()) return "";
        return fn.get(0).asText();
    }

    /**
     * 固定文案 Renderer（不依赖 LLM 可用性）
     *
     * 赠礼模式必须是正向祝福语义，不得诊断对方缺什么（GC06）。
     */
    private Map<String, Object> fixedCopy(String mode, GatedCandidate c) {
        Map<String, Object> m = new LinkedHashMap<>();
        String planet = planetCn(c.planet);

        if (MODE_GIFT.equals(mode)) {
            m.put("headline", "在你想送出的祝福方向里，" + planet + "更协调");
            m.put("body", "你选择的是一份祝福，这里不判断对方缺什么。"
                    + "以" + planet + "的主题作为礼物意向，是为了让这份支持更贴近你想传达的心意。");
        } else {
            m.put("headline", "现阶段" + planet + "更值得优先支持");
            m.put("body", "结合你最近的目标与本命盘的功能宫位判断，"
                    + planet + "对应的方向更贴近你当前想改善的主题。");
        }
        if (c.modality == Modality.GEMSTONE_ELIGIBLE) {
            m.put("modality_note", "星盘条件充分，可展示天然宝石，同时提供更温和的 Yantra 选项。");
        } else if (c.modality == Modality.YANTRA_ONLY || c.modality == Modality.YANTRA_PREFERRED) {
            m.put("modality_note", "本次不建议天然宝石，以 Yantra 方式支持更合适。");
        } else {
            m.put("modality_note", "出生信息不完整，仅按祝福主题展示 Yantra，不作宝石建议。");
        }
        m.put("suppressed_note", c.suppressedUpgrades.isEmpty() ? null
                : "未开放的升级项：" + String.join("、", c.suppressedUpgrades));
        return m;
    }

    private static final Map<String, String> PLANET_CN = Map.of(
            "SUN", "太阳", "MOON", "月亮", "MARS", "火星", "MERCURY", "水星",
            "JUPITER", "木星", "VENUS", "金星", "SATURN", "土星");

    private String planetCn(String planet) {
        return PLANET_CN.getOrDefault(planet, planet);
    }

    // ==================================================================

    private void requireMode(String mode) {
        if (!policies.validModes().contains(mode)) {
            throw new JewelryValidationException(Map.of(
                    "UNKNOWN_MODE", String.valueOf(mode)));
        }
    }

    private static String str(Map<String, String> map, String key) {
        return map == null ? null : map.get(key);
    }
}