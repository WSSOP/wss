package cn.dct.zpback.service.jewelry;

import cn.dct.zpback.service.jewelry.JewelryChartAdapter.AdaptedChart;
import cn.dct.zpback.service.jewelry.JewelryChartAdapter.PlanetFacts;
import cn.dct.zpback.service.jewelry.JewelryQuestionnaireScorer.PlanetScore;
import com.fasterxml.jackson.databind.JsonNode;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 行星珠宝｜Chart Eligibility Gate + Modality Resolver + Ranker
 *
 * 严格实现 08_SKILL/references/scoring-and-eligibility.md：
 *
 *   答案 → 目标候选 → 星盘门槛 → 模态门槛 → 排序 → 商城查询
 *
 * 绝不实现：
 *
 *   答案 + Shadbala + 库存 → 一个总分 → 商品
 *
 * 三条不可违反的边界：
 * 1. 目标分再高也不能绕过 Hard Block（GC01 / GC16）；
 * 2. Shadbala 最低不能自动产生推荐（GC02）；
 * 3. 库存 / 价格 / 毛利不参与排序（GC10 / GC14）。
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class JewelryEligibilityGate {

    /** 模态 */
    public enum Modality {
        GEMSTONE_ELIGIBLE, YANTRA_PREFERRED, YANTRA_ONLY, BLESSING_THEME_ONLY, NO_AUTOMATIC_RECOMMENDATION
    }

    public static class Evidence {
        public final String code;
        public final String polarity;   // SUPPORT / VALIDATE / DOWNGRADE / BLOCK
        public final String priority;   // PRIMARY / VALIDATING / SUPPORTING

        public Evidence(String code, String polarity, String priority) {
            this.code = code;
            this.polarity = polarity;
            this.priority = priority;
        }

        /**
         * Evidence → engine-output.schema.json 的 evidence 节点
         *
         * Schema 的 polarity 枚举只有 SUPPORT / DOWNGRADE / VALIDATE 三值，
         * 内部用 BLOCK 表达「硬阻断」，输出时映射为 DOWNGRADE 以符合 Schema。
         * 阻断语义由 suppressed_candidates[].reason_codes 承载，不靠 polarity 表达。
         */
        public Map<String, Object> toMap() {
            Map<String, Object> m = new LinkedHashMap<>();
            m.put("code", code);
            m.put("polarity", "BLOCK".equals(polarity) ? "DOWNGRADE" : polarity);
            m.put("priority", priority);
            return m;
        }
    }

    public static class GatedCandidate {
        public String planet;
        public String functionalRole;
        public int baseTier;
        public int rawGoalScore;
        public Modality modality = Modality.NO_AUTOMATIC_RECOMMENDATION;
        public String confidence = "LOW";
        public final List<Evidence> evidence = new ArrayList<>();
        public final List<String> suppressedUpgrades = new ArrayList<>();
        public final List<String> blockReasons = new ArrayList<>();
        /** 排序用分项 */
        public int structuralBonus;
        public int supportModifier;
        public int dashaTiebreaker;
        public int downgradePenalty;
        public int rankScore;
        /** 是否出现"过度表达 + Shadbala 饱和"冲突 */
        public boolean saturationConflict;
        /** 混合宫主（三角+凶宫）——赠礼模式下的 Gemstone 审查条件 */
        public boolean mixedOwnership;
        public boolean hardBlocked;
    }

    public static class GateResult {
        public final List<GatedCandidate> eligible = new ArrayList<>();
        /** planet -> 被压制原因，用于审计与"已 Suppress 的候选及原因"冻结 */
        public final Map<String, List<String>> suppressed = new LinkedHashMap<>();
        public boolean gemstoneUnavailableByData;
    }

    private final JewelryPolicies policies;

    // ------------------------------------------------------------------

    public GateResult evaluate(String mode, AdaptedChart chart, List<PlanetScore> candidates,
                               List<String> overExpressionOptionIds) {
        GateResult result = new GateResult();
        result.gemstoneUnavailableByData = !"FULL_CHART".equals(chart.dataQuality);

        for (PlanetScore ps : candidates) {
            PlanetFacts f = chart.planets.get(ps.planet);
            if (f == null) {
                result.suppressed.computeIfAbsent(ps.planet, k -> new ArrayList<>()).add("PLANET_FACTS_MISSING");
                continue;
            }

            GatedCandidate c = new GatedCandidate();
            c.planet = ps.planet;
            c.functionalRole = f.functionalRole;
            c.baseTier = f.baseTier;
            c.rawGoalScore = ps.rawScore;

            evaluateOne(mode, chart, f, ps, overExpressionOptionIds, c);

            if (c.hardBlocked) {
                result.suppressed.computeIfAbsent(ps.planet, k -> new ArrayList<>()).addAll(c.blockReasons);
            } else if (c.modality == Modality.NO_AUTOMATIC_RECOMMENDATION) {
                result.suppressed.computeIfAbsent(ps.planet, k -> new ArrayList<>())
                        .add("MODALITY_NOT_RESOLVED");
            } else {
                result.eligible.add(c);
            }
        }

        rank(result.eligible);
        return result;
    }

    private void evaluateOne(String mode, AdaptedChart chart, PlanetFacts f, PlanetScore ps,
                             List<String> overExpressionOptionIds, GatedCandidate c) {
        // ---------- 0. Goal Bridge 证据（04 §11：每条推荐至少 1 条 Primary Goal Bridge）----------
        // 04 §5：Goal Bridge 由「Planet Function + 目标」建立，是 Functional Role 之外的独立根。
        // 放在最前面，保证用户展开「看看判断依据」时第一条就是"你为什么想要这个"。
        for (String tag : ps.goalTags) {
            c.evidence.add(new Evidence("GOAL_" + tag + "_DIRECT", "SUPPORT", "PRIMARY"));
        }

        // ---------- 1. 硬阻断（任何支持证据都不能反转）----------
        if (c.baseTier == 0) {
            // GC01：目标分再高也不能绕过 Base Tier 0
            c.hardBlocked = true;
            c.blockReasons.add("BASE_TIER_ZERO_" + c.functionalRole);
            c.evidence.add(new Evidence("FUNCTIONAL_ROLE_" + c.functionalRole, "BLOCK", "PRIMARY"));
        } else {
            c.evidence.add(new Evidence("FUNCTIONAL_ROLE_" + c.functionalRole,
                    c.baseTier >= 3 ? "SUPPORT" : "VALIDATE", "PRIMARY"));
        }

        // 节点精确合相 / 食相轴：阻断 Gemstone（GC09）
        boolean nodeBlock = false;
        if (f.nodeExactContact) {
            c.evidence.add(new Evidence("NODE_EXACT_CONJUNCTION", "BLOCK", "PRIMARY"));
            c.suppressedUpgrades.add("GEMSTONE_NOT_ALLOWED_NODE_EXACT_CONJUNCTION");
            c.downgradePenalty += policies.modifierPenalty("NODE_EXACT_CONJUNCTION");
            nodeBlock = true;
            // Yantra 仍需 base tier >= 3
            if (c.baseTier < 3) {
                c.hardBlocked = true;
                c.blockReasons.add("NODE_CONTACT_REQUIRES_BASE_TIER_3");
            }
        } else if (f.nodeAxisContact) {
            c.evidence.add(new Evidence("ECLIPSE_AXIS", "DOWNGRADE", "PRIMARY"));
            c.suppressedUpgrades.add("GEMSTONE_NOT_ALLOWED_ECLIPSE_AXIS");
            c.downgradePenalty += policies.modifierPenalty("ECLIPSE_AXIS");
            nodeBlock = true;
            if (c.baseTier < 3) {
                c.hardBlocked = true;
                c.blockReasons.add("ECLIPSE_AXIS_REQUIRES_BASE_TIER_3");
            }
        }

        // 数据质量：非 FULL_CHART 不得出 Gemstone
        boolean fullChart = "FULL_CHART".equals(chart.dataQuality);
        if (!fullChart) {
            c.suppressedUpgrades.add("GEMSTONE_NOT_ALLOWED_" + chart.dataQuality);
            c.evidence.add(new Evidence("DATA_QUALITY_" + chart.dataQuality, "DOWNGRADE", "PRIMARY"));
        }
        if (Boolean.FALSE.equals(chart.ascendantStable)) {
            c.hardBlocked = true;
            c.blockReasons.add("ASCENDANT_UNSTABLE");
            c.evidence.add(new Evidence("ASCENDANT_UNSTABLE", "BLOCK", "PRIMARY"));
        }

        // 过度表达 + Shadbala 饱和（GC16）
        boolean overExpressed = hasOverExpression(ps, overExpressionOptionIds);
        if (overExpressed && "SATURATED".equals(f.shadbalaBand)) {
            c.hardBlocked = true;
            c.saturationConflict = true;
            c.blockReasons.add("SATURATION_OVEREXPRESSION_CONFLICT");
            c.evidence.add(new Evidence("SATURATION_OVEREXPRESSION_CONFLICT", "BLOCK", "PRIMARY"));
        } else if (overExpressed) {
            c.downgradePenalty += policies.modifierPenalty("SATURATED_SHADBALA");
            c.evidence.add(new Evidence("OVEREXPRESSION_CONFLICT", "DOWNGRADE", "SUPPORTING"));
        }

        if (c.hardBlocked) return;

        // ---------- 2. 支持与降级 ----------
        boolean dignityStable = List.of("EXALTED", "MOOLATRIKONA", "OWN_SIGN").contains(f.dignity);
        boolean dignityDebilitated = "DEBILITATED".equals(f.dignity);
        boolean lowShadbala = "LOW".equals(f.shadbalaBand) && c.baseTier >= 2;
        boolean dashaSuitable = "ACTIVE".equals(f.dashaRole) && c.baseTier >= 2;

        int supportRoots = 0;
        if (dignityStable) {
            supportRoots++;
            c.supportModifier += policies.modifierSupport("DIGNITY_STABLE");
            c.evidence.add(new Evidence("DIGNITY_STABLE", "VALIDATE", "VALIDATING"));
        }
        if (lowShadbala) {
            supportRoots++;
            c.supportModifier += policies.modifierSupport("LOW_SHADBALA");
            c.evidence.add(new Evidence("SHADBALA_LOW_ELIGIBLE", "VALIDATE", "VALIDATING"));
        }
        if (dashaSuitable) {
            supportRoots++;
            c.supportModifier += policies.modifierSupport("DASHA_ACTIVE_SUITABLE");
            c.dashaTiebreaker += policies.modifierDashaTiebreaker("DASHA_ACTIVE_SUITABLE");
            c.evidence.add(new Evidence("DASHA_ACTIVE_SUITABLE", "VALIDATE", "VALIDATING"));
        }
        if (f.d9Validation && c.baseTier > 0) {
            supportRoots++;
            c.supportModifier += policies.modifierSupport("D9_VALIDATION");
            c.evidence.add(new Evidence("D9_VALIDATION", "VALIDATE", "VALIDATING"));
        }
        if ("SATURATED".equals(f.shadbalaBand)) {
            c.downgradePenalty += policies.modifierPenalty("SATURATED_SHADBALA");
            c.evidence.add(new Evidence("SHADBALA_SATURATED", "DOWNGRADE", "SUPPORTING"));
        }
        if (dignityDebilitated) {
            c.downgradePenalty += policies.modifierPenalty("DIGNITY_DEBILITATED");
            c.evidence.add(new Evidence("DIGNITY_DEBILITATED", "DOWNGRADE", "SUPPORTING"));
        }
        if (f.combust) {
            c.downgradePenalty += policies.modifierPenalty("COMBUSTION");
            c.evidence.add(new Evidence("COMBUSTION", "DOWNGRADE", "SUPPORTING"));
        }
        c.mixedOwnership = "MIXED_BENEFIC".equals(f.functionalRole);
        if (c.mixedOwnership) {
            c.evidence.add(new Evidence("MIXED_OWNERSHIP_QUALIFIER", "VALIDATE", "SUPPORTING"));
        }

        c.structuralBonus = policies.structuralBonus(c.baseTier);
        c.supportModifier = Math.min(c.supportModifier, policies.supportModifierMax());
        c.dashaTiebreaker = Math.min(c.dashaTiebreaker, policies.dashaTiebreakerMax());
        c.downgradePenalty = Math.min(c.downgradePenalty, policies.downgradePenaltyMax());

        // ---------- 3. 模态解析 ----------
        boolean gemstoneWanted = !"GEMSTONE".equals(f.planet) && !nodeBlock;
        if ("SELF_ASSESSMENT".equals(mode)) {
            c.modality = resolveSelfModality(c, fullChart, supportRoots, nodeBlock);
        } else {
            c.modality = resolveGiftModality(c, chart, fullChart, supportRoots, dignityStable, dashaSuitable, nodeBlock);
        }

        // 佩戴偏好只影响展示，不能升级 Eligibility（S06 / G04）
        if (c.modality == Modality.GEMSTONE_ELIGIBLE) {
            c.confidence = supportRoots >= 3 ? "HIGH" : "MEDIUM";
        } else if (c.modality == Modality.YANTRA_PREFERRED) {
            c.confidence = c.baseTier >= 3 ? "MEDIUM" : "LOW";
        } else {
            c.confidence = "LOW";
        }

        // ---------- 4. 排序分 ----------
        c.rankScore = c.rawGoalScore + c.structuralBonus + c.supportModifier
                + c.dashaTiebreaker - c.downgradePenalty;
    }

    private Modality resolveSelfModality(GatedCandidate c, boolean fullChart, int supportRoots, boolean nodeBlock) {
        JsonNode rule = policies.modeRule("SELF_ASSESSMENT", "gemstone");
        int tierMin = rule.path("base_tier_min").asInt(3);
        int scoreMin = rule.path("raw_score_min").asInt(12);
        int rootsMin = rule.path("supporting_roots_min").asInt(2);

        boolean gemstone = fullChart
                && !nodeBlock
                && !c.saturationConflict
                && c.baseTier >= tierMin
                && c.rawGoalScore >= scoreMin
                && supportRoots >= rootsMin;
        if (gemstone) return Modality.GEMSTONE_ELIGIBLE;

        if (!gemstone && (fullChart || c.baseTier >= 2)) {
            c.suppressedUpgrades.add(gemstoneSuppressReason(c, fullChart, tierMin, scoreMin, rootsMin, nodeBlock));
            // 04 §11：所有 Gemstone 抑制原因都要进证据，用户才知道"为什么没给宝石"
            c.evidence.add(new Evidence("GEMSTONE_THRESHOLD_NOT_MET", "DOWNGRADE", "SUPPORTING"));
        }

        JsonNode yp = policies.modeRule("SELF_ASSESSMENT", "yantra_preferred");
        if (c.baseTier >= yp.path("base_tier_min").asInt(2)
                && c.rawGoalScore >= yp.path("raw_score_min").asInt(8)) {
            return Modality.YANTRA_PREFERRED;
        }
        // 04 §9 / scoring-and-eligibility §6：
        // YANTRA_ONLY 是 NEUTRAL(tier 1) 的专属出口，必须「恰好等于 1」。
        // 若写成 >= 1，tier 2/3 的候选会绕过各自的降级原因落到 YANTRA_ONLY，
        // 与 YANTRA_PREFERRED 分支混淆。
        JsonNode yo = policies.modeRule("SELF_ASSESSMENT", "yantra_only");
        if (c.baseTier == yo.path("base_tier_min").asInt(1)
                && c.rawGoalScore >= yo.path("raw_score_min").asInt(12)) {
            return Modality.YANTRA_ONLY;
        }
        if (!fullChart) return Modality.BLESSING_THEME_ONLY;
        return Modality.NO_AUTOMATIC_RECOMMENDATION;
    }

    private Modality resolveGiftModality(GatedCandidate c, AdaptedChart chart, boolean fullChart,
                                         int supportRoots, boolean dignityStable, boolean dashaSuitable,
                                         boolean nodeBlock) {
        // 出生时间不完整 → 只能按祝福主题展示 Yantra（GC07）
        if (!fullChart) {
            c.suppressedUpgrades.add("GEMSTONE_NOT_ALLOWED_" + chart.dataQuality);
            return c.rawGoalScore >= policies.candidateRawScoreMin()
                    ? Modality.valueOf(policies.giftPartialModality())
                    : Modality.NO_AUTOMATIC_RECOMMENDATION;
        }

        JsonNode rule = policies.modeRule("GIFT_BLESSING", "gemstone");
        int tierMin = rule.path("base_tier_min").asInt(4);
        int scoreMin = rule.path("raw_score_min").asInt(12);
        boolean mixedReviewAllowed = rule.path("mixed_ownership_review_allowed").asBoolean(false);

        // 三角宫例外：base tier 3 + 稳定尊贵 + 合适 Dasha
        JsonNode trikona = policies.modeRule("GIFT_BLESSING", "gemstone_trikona_exception");
        boolean trikonaException = c.baseTier == trikona.path("base_tier").asInt(3)
                && dignityStable && dashaSuitable;

        boolean gemstone = !nodeBlock
                && !c.saturationConflict
                && c.rawGoalScore >= scoreMin
                && (c.baseTier >= tierMin || trikonaException)
                && (mixedReviewAllowed || !c.mixedOwnership);
        if (gemstone) return Modality.GEMSTONE_ELIGIBLE;

        c.suppressedUpgrades.add(gemstoneSuppressReason(c, true, tierMin, scoreMin, 0, nodeBlock));
        c.evidence.add(new Evidence("GEMSTONE_THRESHOLD_NOT_MET", "DOWNGRADE", "SUPPORTING"));

        // 赠礼默认 Yantra
        if (c.baseTier >= 1 && c.rawGoalScore >= policies.candidateRawScoreMin()) {
            return Modality.YANTRA_PREFERRED;
        }
        return Modality.NO_AUTOMATIC_RECOMMENDATION;
    }

    private String gemstoneSuppressReason(GatedCandidate c, boolean fullChart, int tierMin,
                                          int scoreMin, int rootsMin, boolean nodeBlock) {
        if (!fullChart) return "GEMSTONE_NOT_ALLOWED_PARTIAL_CHART";
        if (nodeBlock) return "GEMSTONE_NOT_ALLOWED_NODE_CONTACT";
        if (c.saturationConflict) return "GEMSTONE_NOT_ALLOWED_SATURATION_CONFLICT";
        if (c.baseTier < tierMin) return "GEMSTONE_NOT_ALLOWED_BASE_TIER_" + c.baseTier;
        if (c.rawGoalScore < scoreMin) return "GEMSTONE_NOT_ALLOWED_SCORE_" + c.rawGoalScore;
        return "GEMSTONE_NOT_ALLOWED_SUPPORT_ROOTS";
    }

    /** 过度表达：用户所选负权重选项是否命中该行星 */
    private boolean hasOverExpression(PlanetScore ps, List<String> overExpressionOptionIds) {
        if (overExpressionOptionIds == null || overExpressionOptionIds.isEmpty()) return false;
        return ps.contributions.entrySet().stream()
                .anyMatch(e -> e.getValue() < 0 && overExpressionOptionIds.contains(e.getKey()));
    }

    // ------------------------------------------------------------------

    /** 只对已通过门槛的候选排序；排序分无权让被阻断的候选复活 */
    private void rank(List<GatedCandidate> list) {
        List<String> canonical = policies.canonicalPlanetOrder();
        list.sort((a, b) -> {
            if (a.rankScore != b.rankScore) return Integer.compare(b.rankScore, a.rankScore);
            if (a.baseTier != b.baseTier) return Integer.compare(b.baseTier, a.baseTier);
            if (a.downgradePenalty != b.downgradePenalty) return Integer.compare(a.downgradePenalty, b.downgradePenalty);
            return Integer.compare(canonical.indexOf(a.planet), canonical.indexOf(b.planet));
        });
    }
}