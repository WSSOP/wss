package cn.dct.zpback.service.jewelry;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.Getter;
import lombok.extern.slf4j.Slf4j;
import org.springframework.core.io.ClassPathResource;
import org.springframework.stereotype.Component;

import jakarta.annotation.PostConstruct;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 行星珠宝建议｜策略配置加载器
 *
 * 配置来源唯一：resources/planetary-jewelry/*.json
 * （即 09_CONFIG 的机器执行版本；正文文档只作产品解释，不参与计算）
 *
 * 加载后全部只读，运行期不修改。配置解析失败直接启动失败，
 * 不允许回退到代码里的默认值——否则会出现"配置改了但不生效"的静默偏差。
 */
@Slf4j
@Component
public class JewelryPolicies {

    public static final String QUESTIONNAIRE_VERSION = "PJ_QUESTIONNAIRE_V1";
    public static final String RULESET_VERSION = "PJ_RULESET_V1";
    public static final String RENDERER_VERSION = "PJ_RENDERER_V1";
    public static final String CATALOG_CONTRACT_VERSION = "PJ_CATALOG_V1";

    /** V1 自动推荐候选范围：Rahu / Ketu 只能是 Modifier，永不成为候选 */
    public static final List<String> CANDIDATE_PLANETS = List.of(
            "SUN", "MOON", "MARS", "MERCURY", "JUPITER", "VENUS", "SATURN");

    /** 需要完整星盘证据的七曜（与候选范围一致，但语义不同：证据齐备要求） */
    public static final List<String> SEVEN = CANDIDATE_PLANETS;

    private final ObjectMapper mapper = new ObjectMapper();

    @Getter private JsonNode questionBank;
    @Getter private JsonNode suitability;
    @Getter private JsonNode recommendationPolicy;
    @Getter private JsonNode goalPlanetMap;
    @Getter private JsonNode catalogContract;

    @PostConstruct
    void load() {
        questionBank = read("question_bank.v1.json");
        suitability = read("suitability_policy.v1.json");
        recommendationPolicy = read("recommendation_policy.v1.json");
        goalPlanetMap = read("goal_planet_map.v1.json");
        catalogContract = read("catalog_contract.v1.json");
        log.info("行星珠宝策略配置已加载：questionnaire={} ruleset={}",
                questionBank.path("version").asText(), recommendationPolicy.path("version").asText());
    }

    private JsonNode read(String name) {
        try (InputStream in = new ClassPathResource("planetary-jewelry/" + name).getInputStream()) {
            return mapper.readTree(in);
        } catch (Exception e) {
            throw new IllegalStateException("行星珠宝配置加载失败：" + name, e);
        }
    }

    // ------------------------------------------------------------------
    // 题库
    // ------------------------------------------------------------------

    /** 指定模式的主干题 + 分支题（含触发条件） */
    public List<JsonNode> questions(String mode) {
        return toList(questionBank.path("modes").path(mode).path("questions"));
    }

    public List<JsonNode> branchQuestions(String mode) {
        return toList(questionBank.path("modes").path(mode).path("branch_questions"));
    }

    // ------------------------------------------------------------------
    // 计分 / 门槛策略（recommendation_policy.v1.json）
    // ------------------------------------------------------------------

    /** 目标题（多选）最多选几项 */
    public int maxGoalSelect(String mode) {
        String first = requiredQuestionIds(mode).isEmpty() ? null : requiredQuestionIds(mode).get(0);
        int v = maxSelect(mode, first);
        return v == Integer.MAX_VALUE ? 2 : v;
    }

    /** JsonNode 数组 → List<JsonNode>；缺节点或非数组时返回空列表 */
    private static List<JsonNode> toList(JsonNode node) {
        List<JsonNode> out = new ArrayList<>();
        if (node != null && node.isArray()) {
            node.forEach(out::add);
        }
        return out;
    }

    private static List<String> stringsOf(JsonNode node) {
        List<String> out = new ArrayList<>();
        if (node != null && node.isArray()) {
            node.forEach(n -> out.add(n.asText()));
        }
        return out;
    }

    /** 该题的选项上限；SINGLE_SELECT 固定 1，缺配置时视为不限制 */
    public int maxSelect(String mode, String questionId) {
        JsonNode q = findQuestion(mode, questionId);
        if (q == null) return Integer.MAX_VALUE;
        if ("SINGLE_SELECT".equals(q.path("type").asText())) return 1;
        int v = q.path("max_select").asInt(0);
        return v > 0 ? v : Integer.MAX_VALUE;
    }

    public boolean isMultiSelect(String mode, String questionId) {
        JsonNode q = findQuestion(mode, questionId);
        return q != null && "MULTI_SELECT".equals(q.path("type").asText());
    }

    public boolean isCopyOnly(String mode, String questionId) {
        JsonNode q = findQuestion(mode, questionId);
        return q != null && q.path("copy_only").asBoolean(false);
    }

    /** 主干选项 → 它触发的分支题 id（题干由 option.branch_question_id 声明） */
    /**
     * 选项 → 它触发的分支题 id。
     *
     * 触发关系写在<b>分支题</b>的 trigger_option_ids 上（question_bank.v1.json），
     * 不是写在选项的 branch_question_id 上——后者在配置里并不存在。
     * 读错方向会导致分支题永远不被触发。
     */
    public List<String> branchOfOption(String mode, String optionId) {
        List<String> out = new ArrayList<>();
        for (JsonNode b : branchQuestions(mode)) {
            for (JsonNode t : b.path("trigger_option_ids")) {
                if (optionId.equals(t.asText())) {
                    out.add(b.path("id").asText());
                    break;
                }
            }
        }
        return out;
    }

    /** 题库顶层 rules */
    public JsonNode rules() {
        return questionBank.path("rules");
    }

    public List<String> validModes() {
        List<String> out = new ArrayList<>();
        questionBank.path("modes").fieldNames().forEachRemaining(out::add);
        return out;
    }

    /** 该模式下的全部合法选项 id（主干 + 分支），用于校验答案归属 */
    public List<String> allOptionIds(String mode) {
        List<String> out = new ArrayList<>();
        for (JsonNode q : questions(mode)) {
            for (JsonNode o : q.path("options")) {
                out.add(o.path("id").asText());
            }
        }
        for (JsonNode q : branchQuestions(mode)) {
            for (JsonNode o : q.path("options")) {
                out.add(o.path("id").asText());
            }
        }
        return out;
    }

    public List<String> requiredQuestionIds(String mode) {
        List<String> out = new ArrayList<>();
        questionBank.path("modes").path(mode).path("required_question_ids")
                .forEach(n -> out.add(n.asText()));
        return out;
    }

    /** 按 id 找题（主干或分支均可） */
    public JsonNode findQuestion(String mode, String questionId) {
        for (JsonNode q : questions(mode)) {
            if (questionId.equals(q.path("id").asText())) return q;
        }
        for (JsonNode q : branchQuestions(mode)) {
            if (questionId.equals(q.path("id").asText())) return q;
        }
        return null;
    }

    public JsonNode findOption(String mode, String optionId) {
        for (JsonNode q : questions(mode)) {
            JsonNode o = optionOf(q, optionId);
            if (o != null) return o;
        }
        for (JsonNode q : branchQuestions(mode)) {
            JsonNode o = optionOf(q, optionId);
            if (o != null) return o;
        }
        return null;
    }

    private JsonNode optionOf(JsonNode question, String optionId) {
        for (JsonNode o : question.path("options")) {
            if (optionId.equals(o.path("id").asText())) return o;
        }
        return null;
    }

    /** 分支题：被哪些主干选项触发 */
    public JsonNode branchTriggeredBy(String mode, String branchId) {
        for (JsonNode b : branchQuestions(mode)) {
            if (branchId.equals(b.path("id").asText())) return b;
        }
        return null;
    }

    public int maxActiveBranchQuestions() {
        return questionBank.path("rules").path("max_active_branch_questions").asInt(2);
    }

    // ------------------------------------------------------------------
    // 推荐策略阈值（全部读配置，代码里不写死）
    // ------------------------------------------------------------------

    /**
     * 进入 Goal Candidate 的意图分阈值。
     * 优先读 recommendation_policy，两份配置同值；缺失时才回退题库 rules。
     */
    public int candidateRawScoreMin() {
        int v = recommendationPolicy.path("candidate_raw_score_min").asInt(0);
        return v > 0 ? v : questionBank.path("rules").path("candidate_raw_score_min").asInt(8);
    }

    /** 进入星盘 Gate 的候选上限 */
    public int maxGoalCandidates() {
        int v = recommendationPolicy.path("max_goal_candidates").asInt(0);
        return v > 0 ? v
                : questionBank.path("rules").path("max_goal_candidates_before_chart_gate").asInt(4);
    }

    public int gemstoneRawScoreMin() {
        return recommendationPolicy.path("gemstone_raw_score_min").asInt(12);
    }

    public int maxRecommendations() {
        return recommendationPolicy.path("max_recommendations").asInt(2);
    }

    public int structuralBonus(int baseTier) {
        return recommendationPolicy.path("rank_score").path("structural_bonus_by_base_tier")
                .path(String.valueOf(baseTier)).asInt(0);
    }

    public int supportModifierMax() {
        return recommendationPolicy.path("rank_score").path("support_modifier_max").asInt(4);
    }

    public int dashaTiebreakerMax() {
        return recommendationPolicy.path("rank_score").path("dasha_tiebreaker_max").asInt(2);
    }

    public int downgradePenaltyMax() {
        return recommendationPolicy.path("rank_score").path("downgrade_penalty_max").asInt(6);
    }

    public List<String> canonicalPlanetOrder() {
        List<String> out = new ArrayList<>();
        recommendationPolicy.path("canonical_planet_order").forEach(n -> out.add(n.asText()));
        return out;
    }

    /** 模式 × 模态的门槛规则（gemstone / yantra_preferred / yantra_only） */
    public JsonNode modeRule(String mode, String ruleName) {
        return recommendationPolicy.path("mode_rules").path(mode).path(ruleName);
    }

    public String giftDefaultModality() {
        return recommendationPolicy.path("mode_rules").path("GIFT_BLESSING")
                .path("default_modality").asText("YANTRA_PREFERRED");
    }

    public String giftPartialModality() {
        return recommendationPolicy.path("mode_rules").path("GIFT_BLESSING")
                .path("partial_chart_modality").asText("BLESSING_THEME_ONLY");
    }

    public List<String> tieBreakOrder() {
        return stringsOf(recommendationPolicy.path("tie_break_order"));
    }

    // ------------------------------------------------------------------
    // Functional Role → Base Tier
    // ------------------------------------------------------------------

    public int baseTierOf(String role) {
        int v = suitability.path("functional_roles").path(role).path("base_tier").asInt(1);
        return v;
    }

    public List<String> functionalRoles() {
        List<String> out = new ArrayList<>();
        suitability.path("functional_roles").fieldNames().forEachRemaining(out::add);
        return out;
    }

    // ------------------------------------------------------------------
    // Modifier 数值
    // ------------------------------------------------------------------

    public int modifierSupport(String key) {
        return suitability.path("modifiers").path(key).path("support_modifier").asInt(0);
    }

    public int modifierPenalty(String key) {
        return suitability.path("modifiers").path(key).path("downgrade_penalty").asInt(0);
    }

    public int modifierDashaTiebreaker(String key) {
        return suitability.path("modifiers").path(key).path("dasha_tiebreaker").asInt(0);
    }

    public int modifierRequiresBaseTierMin(String key) {
        return suitability.path("modifiers").path(key).path("requires_base_tier_min").asInt(0);
    }

    /** Shadbala 分档：LOW / NORMAL / SATURATED */
    public String shadbalaBand(double ratio) {
        JsonNode bands = suitability.path("shadbala_bands");
        double lowMax = bands.path("LOW").path("max_exclusive").asDouble(0.75);
        double satMin = bands.path("SATURATED").path("min_exclusive").asDouble(1.25);
        if (ratio < lowMax) return "LOW";
        if (ratio > satMin) return "SATURATED";
        return "NORMAL";
    }

    // ------------------------------------------------------------------
    // Goal → Planet 映射（校验用）
    // ------------------------------------------------------------------

    /** 目标→行星映射表（goal_planet_map.v1.json） */
    public JsonNode goalPlanetMap() {
        return goalPlanetMap;
    }

    public List<String> allowedGoalTags(String planet) {
        List<String> out = new ArrayList<>();
        goalPlanetMap.path("planets").path(planet).path("allowed_goal_tags")
                .forEach(n -> out.add(n.asText()));
        return out;
    }

    /**
     * 该行星是否禁止作为推荐候选（GC17）。
     *
     * goal_planet_map.nodes 只登记了 RAHU / KETU 两条：
     * <pre>
     *   "RAHU": {"candidate_allowed": false, "modifier_only": true},
     *   "KETU": {"candidate_allowed": false, "modifier_only": true}
     * </pre>
     * 因此判定必须是「<b>在</b> nodes 里且 candidate_allowed 为 false」才禁止；
     * 若把 isMissingNode() 当成禁止，SUN 等七颗正常行星会因不在 nodes 表中而被
     * 全部误判为节点，导致所有候选被清空、永远没有推荐。
     */
    public boolean isNode(String planet) {
        JsonNode node = goalPlanetMap.path("nodes").path(planet);
        if (node.isMissingNode()) return false;   // 未登记 → 普通行星，允许作为候选
        return !node.path("candidate_allowed").asBoolean(true);
    }

    /** 商城枚举（补货等商品域复用） */
    public List<String> catalogEnum(String name) {
        List<String> out = new ArrayList<>();
        catalogContract.path("enums").path(name).forEach(n -> out.add(n.asText()));
        return out;
    }

    /** 供审计留存的配置快照 */
    public Map<String, Object> versionSnapshot() {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("questionnaire_version", questionBank.path("version").asText());
        m.put("ruleset_version", recommendationPolicy.path("version").asText());
        m.put("suitability_version", suitability.path("version").asText());
        m.put("renderer_version", RENDERER_VERSION);
        m.put("catalog_contract_version", catalogContract.path("version").asText());
        return Collections.unmodifiableMap(m);
    }
}