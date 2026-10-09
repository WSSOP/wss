package cn.dct.zpback.engine.education;

import java.util.*;

/**
 * 学业报告 - 语义标签与候选桥接注册表
 * 符合 SEALED 文档《01_学业深度解析报告》§8 证据注册表、§15 Canonical 输出值域
 *
 * 核心原则（§10.4）：Evidence Eligibility ≠ Automatic Support。
 * Family/行星只决定"可能与哪些 Claim 相关"，最终候选方向由
 * 标签语义映射 + 最小充分条件裁决。
 */
public class EducationTagRegistry {

    private EducationTagRegistry() {
    }

    // ============================================================
    // Family 主行星映射（§8.1）
    // ============================================================

    public static final Map<String, String> PLANET_FAMILY = new HashMap<>();
    static {
        PLANET_FAMILY.put("Mercury", "COGNITION");
        PLANET_FAMILY.put("Jupiter", "MEANING_GUIDANCE");
        PLANET_FAMILY.put("Moon", "MIND_REGULATION");
    }

    /** 主证据宫位 Family（§8.1） */
    public static final Map<Integer, String> PRIMARY_HOUSE_FAMILY = new HashMap<>();
    static {
        PRIMARY_HOUSE_FAMILY.put(2, "KNOWLEDGE_BASE");
        PRIMARY_HOUSE_FAMILY.put(4, "LEARNING_CONTAINER");
        PRIMARY_HOUSE_FAMILY.put(5, "INTELLIGENCE_APPLICATION");
        PRIMARY_HOUSE_FAMILY.put(9, "ADVANCED_LEARNING");
    }

    /** 条件性宫位 Family（§8.2，需与主 Family 一跳关系） */
    public static final Map<Integer, String> CONDITIONAL_HOUSE_FAMILY = new HashMap<>();
    static {
        CONDITIONAL_HOUSE_FAMILY.put(3, "EFFORT_SKILL");
        CONDITIONAL_HOUSE_FAMILY.put(6, "ROUTINE_COMPETITION");
        CONDITIONAL_HOUSE_FAMILY.put(8, "DEPTH_TRANSFORMATION");
        CONDITIONAL_HOUSE_FAMILY.put(11, "GOAL_NETWORK");
        CONDITIONAL_HOUSE_FAMILY.put(12, "WITHDRAWAL_IMMERSION");
    }

    /** 主宫位与主行星 */
    public static final List<Integer> PRIMARY_HOUSES = Arrays.asList(2, 4, 5, 9);
    public static final List<String> PRIMARY_PLANETS = Arrays.asList("Mercury", "Jupiter", "Moon");

    // ============================================================
    // Family 语义标签（支持面 / 挑战面）
    // ============================================================

    private static final Map<String, List<String>> FAMILY_SUPPORT_TAGS = new HashMap<>();
    private static final Map<String, List<String>> FAMILY_CHALLENGE_TAGS = new HashMap<>();
    static {
        FAMILY_SUPPORT_TAGS.put("COGNITION", Arrays.asList("processing", "analysis", "language", "adaptability", "expression"));
        FAMILY_CHALLENGE_TAGS.put("COGNITION", Arrays.asList("scattered_focus", "overanalysis"));

        FAMILY_SUPPORT_TAGS.put("MEANING_GUIDANCE", Arrays.asList("synthesis", "meaning", "higher_knowledge", "mentoring", "principle"));
        FAMILY_CHALLENGE_TAGS.put("MEANING_GUIDANCE", Arrays.asList("overexpansion", "abstract_distance"));

        FAMILY_SUPPORT_TAGS.put("MIND_REGULATION", Arrays.asList("reception", "memory", "habit", "emotional_state", "attention"));
        FAMILY_CHALLENGE_TAGS.put("MIND_REGULATION", Arrays.asList("mood_dependency", "attention_drift"));

        FAMILY_SUPPORT_TAGS.put("KNOWLEDGE_BASE", Arrays.asList("accumulation", "verbal_expression", "foundation", "recall"));
        FAMILY_CHALLENGE_TAGS.put("KNOWLEDGE_BASE", Arrays.asList("shallow_breadth"));

        FAMILY_SUPPORT_TAGS.put("LEARNING_CONTAINER", Arrays.asList("environment", "basic_education", "inner_stability"));
        FAMILY_CHALLENGE_TAGS.put("LEARNING_CONTAINER", Arrays.asList("environment_instability"));

        FAMILY_SUPPORT_TAGS.put("INTELLIGENCE_APPLICATION", Arrays.asList("comprehension", "interest", "application", "creation"));
        FAMILY_CHALLENGE_TAGS.put("INTELLIGENCE_APPLICATION", Arrays.asList("interest_dependence"));

        FAMILY_SUPPORT_TAGS.put("ADVANCED_LEARNING", Arrays.asList("higher_education", "academic_vision", "system"));
        FAMILY_CHALLENGE_TAGS.put("ADVANCED_LEARNING", Arrays.asList("mentor_gap", "direction_uncertainty"));

        FAMILY_SUPPORT_TAGS.put("EFFORT_SKILL", Arrays.asList("practice", "writing", "skill", "initiative"));
        FAMILY_CHALLENGE_TAGS.put("EFFORT_SKILL", Arrays.asList("effort_volatility"));

        FAMILY_SUPPORT_TAGS.put("ROUTINE_COMPETITION", Arrays.asList("daily_training", "exam_preparation", "competition"));
        FAMILY_CHALLENGE_TAGS.put("ROUTINE_COMPETITION", Arrays.asList("load_pressure", "burnout"));

        FAMILY_SUPPORT_TAGS.put("DEPTH_TRANSFORMATION", Arrays.asList("research_depth", "complex_problems"));
        FAMILY_CHALLENGE_TAGS.put("DEPTH_TRANSFORMATION", Arrays.asList("interruption", "turning_point"));

        FAMILY_SUPPORT_TAGS.put("GOAL_NETWORK", Arrays.asList("milestone", "peer_network", "long_term_goal"));
        FAMILY_CHALLENGE_TAGS.put("GOAL_NETWORK", Arrays.asList("goal_dispersion"));

        FAMILY_SUPPORT_TAGS.put("WITHDRAWAL_IMMERSION", Arrays.asList("solitude", "immersion", "remote_context"));
        FAMILY_CHALLENGE_TAGS.put("WITHDRAWAL_IMMERSION", Arrays.asList("isolation", "dissipation"));
    }

    public static List<String> getFamilySupportTags(String family) {
        return FAMILY_SUPPORT_TAGS.getOrDefault(family, Collections.emptyList());
    }

    public static List<String> getFamilyChallengeTags(String family) {
        return FAMILY_CHALLENGE_TAGS.getOrDefault(family, Collections.emptyList());
    }

    // ============================================================
    // 候选桥接：语义标签 → Claim 候选（§15 Canonical 值域）
    // ============================================================

    /** C02 认知加工 */
    public static final Map<String, String> C02_BRIDGE = new HashMap<>();
    static {
        C02_BRIDGE.put("processing", "STRUCTURE_FIRST");
        C02_BRIDGE.put("analysis", "STRUCTURE_FIRST");
        C02_BRIDGE.put("adaptability", "DIALOGUE_ITERATIVE");
        C02_BRIDGE.put("language", "DIALOGUE_ITERATIVE");
        C02_BRIDGE.put("synthesis", "PATTERN_SYNTHESIS");
        C02_BRIDGE.put("meaning", "PATTERN_SYNTHESIS");
        C02_BRIDGE.put("higher_knowledge", "DEEP_DIVE_SEQUENTIAL");
        C02_BRIDGE.put("system", "DEEP_DIVE_SEQUENTIAL");
        C02_BRIDGE.put("reception", "CONTEXT_ASSOCIATION");
        C02_BRIDGE.put("memory", "CONTEXT_ASSOCIATION");
        C02_BRIDGE.put("comprehension", "EXAMPLE_FIRST");
        C02_BRIDGE.put("application", "EXPERIMENTAL_APPLIED");
        C02_BRIDGE.put("creation", "EXPERIMENTAL_APPLIED");
    }

    /** C03 记忆与专注 */
    public static final Map<String, String> C03_BRIDGE = new HashMap<>();
    static {
        C03_BRIDGE.put("habit", "STABLE_REPETITION");
        C03_BRIDGE.put("foundation", "STABLE_REPETITION");
        C03_BRIDGE.put("accumulation", "STABLE_REPETITION");
        C03_BRIDGE.put("recall", "VERBAL_RECALL");
        C03_BRIDGE.put("verbal_expression", "VERBAL_RECALL");
        C03_BRIDGE.put("reception", "CONTEXT_ASSOCIATION");
        C03_BRIDGE.put("emotional_state", "STATE_SENSITIVE");
        C03_BRIDGE.put("attention", "BURST_FOCUS");
        C03_BRIDGE.put("environment", "EXTERNAL_RHYTHM_SUPPORT");
        C03_BRIDGE.put("inner_stability", "STABLE_REPETITION");
        C03_BRIDGE.put("visual", "VISUAL_STRUCTURE");
    }

    /** C04 知识表达 */
    public static final Map<String, String> C04_BRIDGE = new HashMap<>();
    static {
        C04_BRIDGE.put("expression", "WRITTEN_SYNTHESIS");
        C04_BRIDGE.put("synthesis", "WRITTEN_SYNTHESIS");
        C04_BRIDGE.put("language", "ORAL_EXPLANATION");
        C04_BRIDGE.put("verbal_expression", "ORAL_EXPLANATION");
        C04_BRIDGE.put("analysis", "ANALYTICAL_PROBLEM_SOLVING");
        C04_BRIDGE.put("processing", "ANALYTICAL_PROBLEM_SOLVING");
        C04_BRIDGE.put("application", "PRACTICAL_DEMONSTRATION");
        C04_BRIDGE.put("creation", "CREATIVE_TRANSLATION");
        C04_BRIDGE.put("mentoring", "TEACHING_OUTPUT");
        C04_BRIDGE.put("meaning", "TEACHING_OUTPUT");
    }

    /** C05 学习动机 */
    public static final Map<String, String> C05_BRIDGE = new HashMap<>();
    static {
        C05_BRIDGE.put("comprehension", "CURIOSITY_MASTERY");
        C05_BRIDGE.put("interest", "CURIOSITY_MASTERY");
        C05_BRIDGE.put("application", "PRACTICAL_UTILITY");
        C05_BRIDGE.put("practice", "PRACTICAL_UTILITY");
        C05_BRIDGE.put("meaning", "PURPOSE_MEANING");
        C05_BRIDGE.put("higher_knowledge", "PURPOSE_MEANING");
        C05_BRIDGE.put("competition", "ACHIEVEMENT_RECOGNITION");
        C05_BRIDGE.put("milestone", "ACHIEVEMENT_RECOGNITION");
        C05_BRIDGE.put("initiative", "AUTONOMY_EXPLORATION");
        C05_BRIDGE.put("solitude", "AUTONOMY_EXPLORATION");
        C05_BRIDGE.put("duty", "RESPONSIBILITY_SECURITY");
        C05_BRIDGE.put("inner_stability", "RESPONSIBILITY_SECURITY");
        C05_BRIDGE.put("peer_network", "COMMUNITY_COLEARNING");
        C05_BRIDGE.put("environment", "COMMUNITY_COLEARNING");
    }

    /** C06 节律与完成 */
    public static final Map<String, String> C06_BRIDGE = new HashMap<>();
    static {
        C06_BRIDGE.put("habit", "ROUTINE_INCREMENTAL");
        C06_BRIDGE.put("daily_training", "ROUTINE_INCREMENTAL");
        C06_BRIDGE.put("foundation", "ROUTINE_INCREMENTAL");
        C06_BRIDGE.put("competition", "SPRINT_BASED");
        C06_BRIDGE.put("exam_preparation", "SPRINT_BASED");
        C06_BRIDGE.put("load_pressure", "DEADLINE_ACTIVATED");
        C06_BRIDGE.put("duty", "EXTERNAL_ACCOUNTABILITY");
        C06_BRIDGE.put("milestone", "SELF_MILESTONE");
        C06_BRIDGE.put("long_term_goal", "SELF_MILESTONE");
        C06_BRIDGE.put("mood_dependency", "CYCLICAL_VARIABILITY");
        C06_BRIDGE.put("attention_drift", "CYCLICAL_VARIABILITY");
        C06_BRIDGE.put("interruption", "CYCLICAL_VARIABILITY");
    }

    /** C07 方法环境 */
    public static final Map<String, String> C07_BRIDGE = new HashMap<>();
    static {
        C07_BRIDGE.put("processing", "STRUCTURED_CURRICULUM");
        C07_BRIDGE.put("reception", "QUIET_INDEPENDENT");
        C07_BRIDGE.put("inner_stability", "QUIET_INDEPENDENT");
        C07_BRIDGE.put("mentoring", "GUIDED_MENTOR");
        C07_BRIDGE.put("higher_knowledge", "GUIDED_MENTOR");
        C07_BRIDGE.put("language", "COLLABORATIVE_DIALOGUE");
        C07_BRIDGE.put("peer_network", "COLLABORATIVE_DIALOGUE");
        C07_BRIDGE.put("system", "STRUCTURED_CURRICULUM");
        C07_BRIDGE.put("application", "PROJECT_BASED");
        C07_BRIDGE.put("creation", "PROJECT_BASED");
        C07_BRIDGE.put("adaptability", "FLEXIBLE_MODULAR");
        C07_BRIDGE.put("immersion", "IMMERSIVE_INTENSIVE");
        C07_BRIDGE.put("research_depth", "IMMERSIVE_INTENSIVE");
    }

    /** C08 优势资源 */
    public static final Map<String, String> C08_BRIDGE = new HashMap<>();
    static {
        C08_BRIDGE.put("processing", "ANALYTICAL_REASONING");
        C08_BRIDGE.put("analysis", "ANALYTICAL_REASONING");
        C08_BRIDGE.put("synthesis", "INFORMATION_SYNTHESIS");
        C08_BRIDGE.put("meaning", "CONCEPTUAL_UNDERSTANDING");
        C08_BRIDGE.put("comprehension", "CONCEPTUAL_UNDERSTANDING");
        C08_BRIDGE.put("language", "LANGUAGE_EXPRESSION");
        C08_BRIDGE.put("verbal_expression", "LANGUAGE_EXPRESSION");
        C08_BRIDGE.put("memory", "MEMORY_RETENTION");
        C08_BRIDGE.put("recall", "MEMORY_RETENTION");
        C08_BRIDGE.put("research_depth", "RESEARCH_DEPTH");
        C08_BRIDGE.put("complex_problems", "RESEARCH_DEPTH");
        C08_BRIDGE.put("application", "PRACTICAL_APPLICATION");
        C08_BRIDGE.put("creation", "CREATIVE_ASSOCIATION");
        C08_BRIDGE.put("habit", "PERSISTENCE");
        C08_BRIDGE.put("daily_training", "PERSISTENCE");
        C08_BRIDGE.put("mentoring", "TEACHING_TRANSLATION");
        C08_BRIDGE.put("teaching", "TEACHING_TRANSLATION");
    }

    /** C09 阻力循环 */
    public static final Map<String, String> C09_BRIDGE = new HashMap<>();
    static {
        C09_BRIDGE.put("scattered_focus", "OVERLOAD_DISTRACTION");
        C09_BRIDGE.put("attention_drift", "OVERLOAD_DISTRACTION");
        C09_BRIDGE.put("overanalysis", "PERFECTIONISM_DELAY");
        C09_BRIDGE.put("interest_dependence", "INTEREST_DEPENDENCE");
        C09_BRIDGE.put("mood_dependency", "INCONSISTENT_ROUTINE");
        C09_BRIDGE.put("attention_variability", "INCONSISTENT_ROUTINE");
        C09_BRIDGE.put("load_pressure", "PRESSURE_BLOCK");
        C09_BRIDGE.put("burnout", "PRESSURE_BLOCK");
        C09_BRIDGE.put("overexpansion", "OVEREXPANSION");
        C09_BRIDGE.put("abstract_distance", "THEORY_PRACTICE_GAP");
        C09_BRIDGE.put("interruption", "PREMATURE_SWITCHING");
        C09_BRIDGE.put("turning_point", "PREMATURE_SWITCHING");
        C09_BRIDGE.put("goal_dispersion", "OVEREXPANSION");
        C09_BRIDGE.put("isolation", "MISSING_FEEDBACK");
        C09_BRIDGE.put("dissipation", "OVERLOAD_DISTRACTION");
        C09_BRIDGE.put("effort_volatility", "INCONSISTENT_ROUTINE");
        C09_BRIDGE.put("environment_instability", "INCONSISTENT_ROUTINE");
        C09_BRIDGE.put("mentor_gap", "MISSING_FEEDBACK");
        C09_BRIDGE.put("direction_uncertainty", "PREMATURE_SWITCHING");
        C09_BRIDGE.put("shallow_breadth", "THEORY_PRACTICE_GAP");
    }

    /** C10 知识方向簇 */
    public static final Map<String, String> C10_BRIDGE = new HashMap<>();
    static {
        C10_BRIDGE.put("language", "LANGUAGE_COMMUNICATION");
        C10_BRIDGE.put("verbal_expression", "LANGUAGE_COMMUNICATION");
        C10_BRIDGE.put("expression", "LANGUAGE_COMMUNICATION");
        C10_BRIDGE.put("meaning", "HUMANITIES_CULTURE");
        C10_BRIDGE.put("higher_knowledge", "HUMANITIES_CULTURE");
        C10_BRIDGE.put("principle", "HUMANITIES_CULTURE");
        C10_BRIDGE.put("emotional_state", "PSYCHOLOGY_EDUCATION_HELPING");
        C10_BRIDGE.put("reception", "PSYCHOLOGY_EDUCATION_HELPING");
        C10_BRIDGE.put("mentoring", "PSYCHOLOGY_EDUCATION_HELPING");
        C10_BRIDGE.put("competition", "BUSINESS_MANAGEMENT");
        C10_BRIDGE.put("milestone", "BUSINESS_MANAGEMENT");
        C10_BRIDGE.put("long_term_goal", "BUSINESS_MANAGEMENT");
        C10_BRIDGE.put("accumulation", "FINANCE_COMMERCE_ANALYSIS");
        C10_BRIDGE.put("duty", "LAW_POLICY_PUBLIC_AFFAIRS");
        C10_BRIDGE.put("system", "LAW_POLICY_PUBLIC_AFFAIRS");
        C10_BRIDGE.put("analysis", "MATH_LOGIC");
        C10_BRIDGE.put("processing", "MATH_LOGIC");
        C10_BRIDGE.put("application", "ENGINEERING_TECHNOLOGY");
        C10_BRIDGE.put("creation", "ENGINEERING_TECHNOLOGY");
        C10_BRIDGE.put("research_depth", "DATA_RESEARCH");
        C10_BRIDGE.put("complex_problems", "DATA_RESEARCH");
        C10_BRIDGE.put("habit", "MEDICINE_LIFE_SCIENCE");
        C10_BRIDGE.put("daily_training", "MEDICINE_LIFE_SCIENCE");
        C10_BRIDGE.put("creation", "ART_DESIGN_MEDIA");
        C10_BRIDGE.put("synthesis", "ART_DESIGN_MEDIA");
        C10_BRIDGE.put("solitude", "SPIRITUAL_PHILOSOPHICAL_ESOTERIC");
        C10_BRIDGE.put("immersion", "SPIRITUAL_PHILOSOPHICAL_ESOTERIC");
    }

    /** C11 学习路线 */
    public static final Map<String, String> C11_BRIDGE = new HashMap<>();
    static {
        C11_BRIDGE.put("exam_preparation", "STANDARDIZED_EXAM");
        C11_BRIDGE.put("competition", "STANDARDIZED_EXAM");
        C11_BRIDGE.put("research_depth", "RESEARCH_ACADEMIC");
        C11_BRIDGE.put("complex_problems", "RESEARCH_ACADEMIC");
        C11_BRIDGE.put("application", "PROJECT_PORTFOLIO");
        C11_BRIDGE.put("creation", "PROJECT_PORTFOLIO");
        C11_BRIDGE.put("practice", "PROFESSIONAL_CERTIFICATION");
        C11_BRIDGE.put("skill", "PROFESSIONAL_CERTIFICATION");
        C11_BRIDGE.put("initiative", "APPRENTICESHIP_PRACTICE");
        C11_BRIDGE.put("mentoring", "TEACHING_KNOWLEDGE_OUTPUT");
        C11_BRIDGE.put("teaching", "TEACHING_KNOWLEDGE_OUTPUT");
        C11_BRIDGE.put("adaptability", "INTERDISCIPLINARY_EXPLORATION");
        C11_BRIDGE.put("peer_network", "INTERDISCIPLINARY_EXPLORATION");
    }

    /** C12 深造方式（由 C06/C09/C10/C11 派生时的辅助映射） */
    public static final Map<String, String> C12_BRIDGE = new HashMap<>();
    static {
        C12_BRIDGE.put("STANDARDIZED_EXAM", "FORMAL_DEGREE");
        C12_BRIDGE.put("RESEARCH_ACADEMIC", "RESEARCH_SPECIALIZATION");
        C12_BRIDGE.put("PROJECT_PORTFOLIO", "APPLIED_MASTERY");
        C12_BRIDGE.put("PROFESSIONAL_CERTIFICATION", "PROFESSIONAL_CREDENTIAL");
        C12_BRIDGE.put("APPRENTICESHIP_PRACTICE", "APPLIED_MASTERY");
        C12_BRIDGE.put("TEACHING_KNOWLEDGE_OUTPUT", "RESEARCH_SPECIALIZATION");
        C12_BRIDGE.put("INTERDISCIPLINARY_EXPLORATION", "MODULAR_LIFELONG_LEARNING");
    }

    // ============================================================
    // 查询辅助
    // ============================================================

    private static final Map<String, Map<String, String>> BRIDGES = new HashMap<>();
    static {
        BRIDGES.put("C02", C02_BRIDGE);
        BRIDGES.put("C03", C03_BRIDGE);
        BRIDGES.put("C04", C04_BRIDGE);
        BRIDGES.put("C05", C05_BRIDGE);
        BRIDGES.put("C06", C06_BRIDGE);
        BRIDGES.put("C07", C07_BRIDGE);
        BRIDGES.put("C08", C08_BRIDGE);
        BRIDGES.put("C09", C09_BRIDGE);
        BRIDGES.put("C10", C10_BRIDGE);
        BRIDGES.put("C11", C11_BRIDGE);
        BRIDGES.put("C12", C12_BRIDGE);
    }

    public static Map<String, String> getBridge(String claimId) {
        return BRIDGES.getOrDefault(claimId, Collections.emptyMap());
    }

    /** 各 Claim 的直接投票资格 Family 集合 */
    public static List<String> getClaimFamilies(String claimId) {
        switch (claimId) {
            case "C02": case "C04": case "C08":
                return Arrays.asList("COGNITION", "MEANING_GUIDANCE", "KNOWLEDGE_BASE",
                        "INTELLIGENCE_APPLICATION", "ADVANCED_LEARNING");
            case "C03":
                return Arrays.asList("MIND_REGULATION", "LEARNING_CONTAINER", "KNOWLEDGE_BASE",
                        "INTELLIGENCE_APPLICATION");
            case "C05":
                return Arrays.asList("MIND_REGULATION", "MEANING_GUIDANCE", "INTELLIGENCE_APPLICATION",
                        "ADVANCED_LEARNING", "GOAL_NETWORK");
            case "C06":
                return Arrays.asList("MIND_REGULATION", "ROUTINE_COMPETITION", "EFFORT_SKILL",
                        "LEARNING_CONTAINER", "GOAL_NETWORK");
            case "C07": case "C01": case "C12":
                return Collections.emptyList(); // Derived：不接受原始投票
            case "C09":
                return Arrays.asList("COGNITION", "MIND_REGULATION", "ROUTINE_COMPETITION",
                        "DEPTH_TRANSFORMATION", "WITHDRAWAL_IMMERSION", "EFFORT_SKILL",
                        "LEARNING_CONTAINER", "GOAL_NETWORK");
            case "C10":
                return Arrays.asList("COGNITION", "MEANING_GUIDANCE", "KNOWLEDGE_BASE",
                        "LEARNING_CONTAINER", "INTELLIGENCE_APPLICATION", "ADVANCED_LEARNING",
                        "MIND_REGULATION");
            case "C11":
                return Arrays.asList("COGNITION", "MEANING_GUIDANCE", "INTELLIGENCE_APPLICATION",
                        "ADVANCED_LEARNING", "ROUTINE_COMPETITION", "EFFORT_SKILL");
            case "C13":
                return Collections.emptyList(); // Timing：不接受 natal vote
            default:
                return Collections.emptyList();
        }
    }
}
