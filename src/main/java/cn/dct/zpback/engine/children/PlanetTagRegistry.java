package cn.dct.zpback.engine.children;

import java.util.*;

/**
 * 行星功能语义标签注册表
 * 符合 SEALED 文档 skill/references/evidence_authority_registry.md 第 5 节
 *
 * 支持面与压力面不能仅凭行星名称同时投票；
 * 由 functional_condition 决定开放哪一面。
 */
public class PlanetTagRegistry {

    private static final Map<String, List<String>> SUPPORT_TAGS = new HashMap<>();
    private static final Map<String, List<String>> PRESSURE_TAGS = new HashMap<>();

    static {
        SUPPORT_TAGS.put("Sun", Arrays.asList("authority", "protection", "identity_guidance", "expectation"));
        SUPPORT_TAGS.put("Moon", Arrays.asList("emotional_care", "safety_building", "attunement", "rhythm"));
        SUPPORT_TAGS.put("Mars", Arrays.asList("action", "protection", "initiative", "crisis_response"));
        SUPPORT_TAGS.put("Mercury", Arrays.asList("communication", "adaptability", "learning", "reasoning"));
        SUPPORT_TAGS.put("Jupiter", Arrays.asList("guidance", "meaning", "teaching", "growth_support"));
        SUPPORT_TAGS.put("Venus", Arrays.asList("harmony", "companionship", "relational_care", "creativity"));
        SUPPORT_TAGS.put("Saturn", Arrays.asList("structure", "consistency", "duty", "patience"));
        SUPPORT_TAGS.put("Rahu", Arrays.asList("innovation", "nonlinear_path", "boundary_expansion"));
        SUPPORT_TAGS.put("Ketu", Arrays.asList("space_support", "release", "nonattachment"));

        PRESSURE_TAGS.put("Sun", Arrays.asList("authority_excess", "expectation_pressure", "self_projection"));
        PRESSURE_TAGS.put("Moon", Arrays.asList("worry_amplification", "overprotection", "mood_reactivity"));
        PRESSURE_TAGS.put("Mars", Arrays.asList("control_pressure", "reactivity", "active_challenge"));
        PRESSURE_TAGS.put("Mercury", Arrays.asList("analysis_loop", "inconsistency", "overanalysis_worry"));
        PRESSURE_TAGS.put("Jupiter", Arrays.asList("overexpectation", "certainty_excess", "projection_of_beliefs"));
        PRESSURE_TAGS.put("Venus", Arrays.asList("overaccommodation", "conflict_avoidance", "boundary_softness"));
        PRESSURE_TAGS.put("Saturn", Arrays.asList("delay_pressure", "rigidity", "high_duty", "emotional_distance"));
        PRESSURE_TAGS.put("Rahu", Arrays.asList("amplification", "future_projection", "unstable_appetite"));
        PRESSURE_TAGS.put("Ketu", Arrays.asList("distance", "withdrawal", "discontinuity"));
    }

    public static List<String> getSupportTags(String planet) {
        return SUPPORT_TAGS.getOrDefault(planet, Collections.emptyList());
    }

    public static List<String> getPressureTags(String planet) {
        return PRESSURE_TAGS.getOrDefault(planet, Collections.emptyList());
    }

    /**
     * 根据功能条件开放对应面的标签
     */
    public static List<String> getTagsByCondition(String planet, String functionalCondition) {
        switch (functionalCondition) {
            case "SUPPORTIVE": return new ArrayList<>(getSupportTags(planet));
            case "CONSTRAINING": return new ArrayList<>(getPressureTags(planet));
            case "AMBIVALENT": {
                List<String> all = new ArrayList<>(getSupportTags(planet));
                all.add("MIXED_SIGNAL");
                return all;
            }
            default: return Collections.emptyList();
        }
    }

    /**
     * 星座元素/模式限定（qualifier，不投票）
     */
    public static List<String> getSignQualifiers(String sign) {
        if (sign == null) return Collections.emptyList();
        switch (sign) {
            case "Aries": case "Leo": case "Sagittarius":
                return Arrays.asList("initiative", "expression", "direct_guidance");
            case "Taurus": case "Virgo": case "Capricorn":
                return Arrays.asList("practical_support", "consistency", "resource_stewardship");
            case "Gemini": case "Libra": case "Aquarius":
                return Arrays.asList("communication", "reasoning", "social_learning");
            case "Cancer": case "Scorpio": case "Pisces":
                return Arrays.asList("emotional_attunement", "sensitivity", "bonding");
            default:
                return Collections.emptyList();
        }
    }

    /**
     * D1 5L 落宫桥接（evidence_authority_registry 7.1）
     */
    public static List<String> getHouseBridgeTags(int house) {
        switch (house) {
            case 1: case 5: case 9:
                return Arrays.asList("identity_guidance", "development", "continuity");
            case 2: case 4:
                return Arrays.asList("family_resources", "safety", "practical_continuity");
            case 3: case 6:
                return Arrays.asList("learning_action", "duty_routine");
            case 7: case 11:
                return Arrays.asList("partnership_support", "shared_planning");
            case 8: case 12:
                return Arrays.asList("transformation", "uncertainty", "nonlinear_path");
            case 10:
                return Arrays.asList("responsibility", "structure", "public_role_load");
            default:
                return Collections.emptyList();
        }
    }

    /**
     * 语义标签 → C02 候选方向桥（claim_section_registry 4.1）
     */
    public static final Map<String, String> C02_TAG_BRIDGE = new HashMap<>();
    static {
        C02_TAG_BRIDGE.put("path_support", "RELATIVELY_SUPPORTED");
        C02_TAG_BRIDGE.put("continuity_support", "RELATIVELY_SUPPORTED");
        C02_TAG_BRIDGE.put("stable_growth", "RELATIVELY_SUPPORTED");
        C02_TAG_BRIDGE.put("duty_structure", "SUPPORTED_WITH_RESPONSIBILITY");
        C02_TAG_BRIDGE.put("delay_pressure", "COMPLEX_OR_DELAYED");
        C02_TAG_BRIDGE.put("constraint", "COMPLEX_OR_DELAYED");
        C02_TAG_BRIDGE.put("high_duty", "COMPLEX_OR_DELAYED");
        C02_TAG_BRIDGE.put("slow_consolidation", "COMPLEX_OR_DELAYED");
        C02_TAG_BRIDGE.put("nonlinear_path", "NONLINEAR_OR_UNCONVENTIONAL");
        C02_TAG_BRIDGE.put("unconventional_structure", "NONLINEAR_OR_UNCONVENTIONAL");
        C02_TAG_BRIDGE.put("change_amplification", "NONLINEAR_OR_UNCONVENTIONAL");
        C02_TAG_BRIDGE.put("identity_guidance", "RELATIVELY_SUPPORTED");
        C02_TAG_BRIDGE.put("development", "RELATIVELY_SUPPORTED");
        C02_TAG_BRIDGE.put("continuity", "RELATIVELY_SUPPORTED");
        C02_TAG_BRIDGE.put("responsibility", "SUPPORTED_WITH_RESPONSIBILITY");
        C02_TAG_BRIDGE.put("structure", "SUPPORTED_WITH_RESPONSIBILITY");
    }

    /**
     * 语义标签 → C03 压力来源候选（claim_section_registry 5）
     */
    public static final Map<String, String> C03_TAG_BRIDGE = new HashMap<>();
    static {
        C03_TAG_BRIDGE.put("delay_pressure", "TIMING_AND_PATIENCE");
        C03_TAG_BRIDGE.put("slow_consolidation", "TIMING_AND_PATIENCE");
        C03_TAG_BRIDGE.put("patience_required", "TIMING_AND_PATIENCE");
        C03_TAG_BRIDGE.put("coparenting_negotiation", "PARTNERSHIP_COORDINATION");
        C03_TAG_BRIDGE.put("partner_alignment", "PARTNERSHIP_COORDINATION");
        C03_TAG_BRIDGE.put("resource_load", "PRACTICAL_RESOURCE_LOAD");
        C03_TAG_BRIDGE.put("financial_or_logistic_load", "PRACTICAL_RESOURCE_LOAD");
        C03_TAG_BRIDGE.put("control_pressure", "CONTROL_OR_WORRY_LOAD");
        C03_TAG_BRIDGE.put("worry_amplification", "CONTROL_OR_WORRY_LOAD");
        C03_TAG_BRIDGE.put("analysis_loop", "CONTROL_OR_WORRY_LOAD");
        C03_TAG_BRIDGE.put("high_duty", "RESPONSIBILITY_OVERLOAD");
        C03_TAG_BRIDGE.put("care_burden", "RESPONSIBILITY_OVERLOAD");
        C03_TAG_BRIDGE.put("role_overload", "RESPONSIBILITY_OVERLOAD");
        C03_TAG_BRIDGE.put("change_amplification", "CHANGE_OR_UNPREDICTABILITY");
        C03_TAG_BRIDGE.put("nonlinear_path", "CHANGE_OR_UNPREDICTABILITY");
        C03_TAG_BRIDGE.put("unstable_rhythm", "CHANGE_OR_UNPREDICTABILITY");
    }

    /**
     * 语义标签 → C04 父母角色候选（claim_section_registry 6）
     */
    public static final Map<String, String> C04_TAG_BRIDGE = new HashMap<>();
    static {
        C04_TAG_BRIDGE.put("protection", "PROTECTIVE_CAREGIVER");
        C04_TAG_BRIDGE.put("emotional_care", "PROTECTIVE_CAREGIVER");
        C04_TAG_BRIDGE.put("safety_building", "PROTECTIVE_CAREGIVER");
        C04_TAG_BRIDGE.put("guidance", "GUIDING_TEACHER");
        C04_TAG_BRIDGE.put("meaning", "GUIDING_TEACHER");
        C04_TAG_BRIDGE.put("teaching", "GUIDING_TEACHER");
        C04_TAG_BRIDGE.put("structure", "STRUCTURE_BUILDER");
        C04_TAG_BRIDGE.put("consistency", "STRUCTURE_BUILDER");
        C04_TAG_BRIDGE.put("rules", "STRUCTURE_BUILDER");
        C04_TAG_BRIDGE.put("independence", "INDEPENDENCE_COACH");
        C04_TAG_BRIDGE.put("initiative", "INDEPENDENCE_COACH");
        C04_TAG_BRIDGE.put("space_support", "INDEPENDENCE_COACH");
        C04_TAG_BRIDGE.put("practical_provision", "PRACTICAL_PROVIDER");
        C04_TAG_BRIDGE.put("resource_stewardship", "PRACTICAL_PROVIDER");
        C04_TAG_BRIDGE.put("emotional_presence", "EMOTIONAL_COMPANION");
        C04_TAG_BRIDGE.put("companionship", "EMOTIONAL_COMPANION");
        C04_TAG_BRIDGE.put("relational_care", "EMOTIONAL_COMPANION");
        C04_TAG_BRIDGE.put("relational_harmony", "EMOTIONAL_COMPANION");
        C04_TAG_BRIDGE.put("planning", "DEVELOPMENT_PLANNER");
        C04_TAG_BRIDGE.put("learning_design", "DEVELOPMENT_PLANNER");
        C04_TAG_BRIDGE.put("development_focus", "DEVELOPMENT_PLANNER");
    }

    /**
     * 语义标签 → C05 养育资源候选（claim_section_registry 7）
     */
    public static final Map<String, String> C05_TAG_BRIDGE = new HashMap<>();
    static {
        C05_TAG_BRIDGE.put("emotional_attunement", "EMOTIONAL_ATTUNEMENT");
        C05_TAG_BRIDGE.put("sensitivity", "EMOTIONAL_ATTUNEMENT");
        C05_TAG_BRIDGE.put("patience", "PATIENCE_CONSISTENCY");
        C05_TAG_BRIDGE.put("consistency", "PATIENCE_CONSISTENCY");
        C05_TAG_BRIDGE.put("steady_care", "PATIENCE_CONSISTENCY");
        C05_TAG_BRIDGE.put("guidance", "GUIDANCE_MEANING");
        C05_TAG_BRIDGE.put("meaning", "GUIDANCE_MEANING");
        C05_TAG_BRIDGE.put("ethical_teaching", "GUIDANCE_MEANING");
        C05_TAG_BRIDGE.put("practical_support", "PRACTICAL_SUPPORT");
        C05_TAG_BRIDGE.put("resource_stewardship", "PRACTICAL_SUPPORT");
        C05_TAG_BRIDGE.put("protection", "BOUNDARY_PROTECTION");
        C05_TAG_BRIDGE.put("boundary_building", "BOUNDARY_PROTECTION");
        C05_TAG_BRIDGE.put("communication", "COMMUNICATION_ADAPTABILITY");
        C05_TAG_BRIDGE.put("adaptability", "COMMUNICATION_ADAPTABILITY");
        C05_TAG_BRIDGE.put("translation", "COMMUNICATION_ADAPTABILITY");
        C05_TAG_BRIDGE.put("curiosity", "LEARNING_ENCOURAGEMENT");
        C05_TAG_BRIDGE.put("learning_encouragement", "LEARNING_ENCOURAGEMENT");
        C05_TAG_BRIDGE.put("development_focus", "LEARNING_ENCOURAGEMENT");
        C05_TAG_BRIDGE.put("crisis_response", "CRISIS_STEADINESS");
        C05_TAG_BRIDGE.put("resilience", "CRISIS_STEADINESS");
        C05_TAG_BRIDGE.put("decisive_support", "CRISIS_STEADINESS");
    }

    /**
     * 语义标签 → C06 过度模式候选（claim_section_registry 8）
     */
    public static final Map<String, String> C06_TAG_BRIDGE = new HashMap<>();
    static {
        C06_TAG_BRIDGE.put("control_pressure", "OVERCONTROL");
        C06_TAG_BRIDGE.put("rigidity", "OVERCONTROL");
        C06_TAG_BRIDGE.put("authority_excess", "OVERCONTROL");
        C06_TAG_BRIDGE.put("protection_excess", "OVERPROTECTION");
        C06_TAG_BRIDGE.put("worry_amplification", "OVERPROTECTION");
        C06_TAG_BRIDGE.put("separation_difficulty", "OVERPROTECTION");
        C06_TAG_BRIDGE.put("expectation_pressure", "OVEREXPECTATION");
        C06_TAG_BRIDGE.put("achievement_projection", "OVEREXPECTATION");
        C06_TAG_BRIDGE.put("high_duty", "OVERRESPONSIBILITY");
        C06_TAG_BRIDGE.put("care_burden", "OVERRESPONSIBILITY");
        C06_TAG_BRIDGE.put("self_sacrifice", "OVERRESPONSIBILITY");
        C06_TAG_BRIDGE.put("harmony_over_boundary", "OVERACCOMMODATION");
        C06_TAG_BRIDGE.put("conflict_avoidance", "OVERACCOMMODATION");
        C06_TAG_BRIDGE.put("giving_excess", "OVERACCOMMODATION");
        C06_TAG_BRIDGE.put("emotional_distance", "EMOTIONAL_WITHDRAWAL");
        C06_TAG_BRIDGE.put("shutdown", "EMOTIONAL_WITHDRAWAL");
        C06_TAG_BRIDGE.put("duty_without_expression", "EMOTIONAL_WITHDRAWAL");
        C06_TAG_BRIDGE.put("unstable_rhythm", "INCONSISTENT_BOUNDARIES");
        C06_TAG_BRIDGE.put("boundary_variability", "INCONSISTENT_BOUNDARIES");
        C06_TAG_BRIDGE.put("reactive_change", "INCONSISTENT_BOUNDARIES");
        C06_TAG_BRIDGE.put("analysis_loop", "OVERANALYSIS_WORRY");
        C06_TAG_BRIDGE.put("future_projection", "OVERANALYSIS_WORRY");
    }

    /**
     * 语义标签 → C07 沟通方式候选（claim_section_registry 9）
     */
    public static final Map<String, String> C07_TAG_BRIDGE = new HashMap<>();
    static {
        C07_TAG_BRIDGE.put("emotion_before_solution", "EMOTION_FIRST");
        C07_TAG_BRIDGE.put("emotional_attunement", "EMOTION_FIRST");
        C07_TAG_BRIDGE.put("reasoning", "REASON_FIRST");
        C07_TAG_BRIDGE.put("explanation", "REASON_FIRST");
        C07_TAG_BRIDGE.put("analysis", "REASON_FIRST");
        C07_TAG_BRIDGE.put("action", "ACTION_FIRST");
        C07_TAG_BRIDGE.put("modeling", "ACTION_FIRST");
        C07_TAG_BRIDGE.put("direct_response", "ACTION_FIRST");
        C07_TAG_BRIDGE.put("rules", "RULE_FIRST");
        C07_TAG_BRIDGE.put("structure", "RULE_FIRST");
        C07_TAG_BRIDGE.put("consistency", "RULE_FIRST");
        C07_TAG_BRIDGE.put("space_support", "SPACE_FIRST");
        C07_TAG_BRIDGE.put("nonintrusion", "SPACE_FIRST");
        C07_TAG_BRIDGE.put("self_processing", "SPACE_FIRST");
        C07_TAG_BRIDGE.put("play", "PLAY_EXPRESSION_FIRST");
        C07_TAG_BRIDGE.put("creativity", "PLAY_EXPRESSION_FIRST");
        C07_TAG_BRIDGE.put("expressive_bonding", "PLAY_EXPRESSION_FIRST");
        C07_TAG_BRIDGE.put("guidance", "GUIDANCE_FIRST");
        C07_TAG_BRIDGE.put("teaching", "GUIDANCE_FIRST");
        C07_TAG_BRIDGE.put("meaning", "GUIDANCE_FIRST");
    }

    /**
     * 语义标签 → C09 共同养育候选（claim_section_registry 11）
     */
    public static final Map<String, String> C09_TAG_BRIDGE = new HashMap<>();
    static {
        C09_TAG_BRIDGE.put("shared_duty", "SHARED_RESPONSIBILITY");
        C09_TAG_BRIDGE.put("partner_alignment", "SHARED_RESPONSIBILITY");
        C09_TAG_BRIDGE.put("collaboration", "SHARED_RESPONSIBILITY");
        C09_TAG_BRIDGE.put("role_specialization", "ROLE_SPECIALIZATION");
        C09_TAG_BRIDGE.put("complementary_functions", "ROLE_SPECIALIZATION");
        C09_TAG_BRIDGE.put("coparenting_negotiation", "NEGOTIATION_REQUIRED");
        C09_TAG_BRIDGE.put("different_rhythms", "NEGOTIATION_REQUIRED");
        C09_TAG_BRIDGE.put("expectation_gap", "NEGOTIATION_REQUIRED");
        C09_TAG_BRIDGE.put("extended_family_influence", "EXTERNAL_FAMILY_INFLUENCE");
        C09_TAG_BRIDGE.put("family_norms", "EXTERNAL_FAMILY_INFLUENCE");
        C09_TAG_BRIDGE.put("resource_coordination", "RESOURCE_COORDINATION");
        C09_TAG_BRIDGE.put("practical_planning", "RESOURCE_COORDINATION");
        C09_TAG_BRIDGE.put("extended_family_boundary", "BOUNDARY_WITH_EXTENDED_FAMILY");
        C09_TAG_BRIDGE.put("authority_boundary", "BOUNDARY_WITH_EXTENDED_FAMILY");
    }
}
