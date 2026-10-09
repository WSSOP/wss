package cn.dct.zpback.constant;

/**
 * 单人婚恋深度报告常量
 * 报告类型：NATAL_RELATIONSHIP_REPORT
 * 版本：V1 LOCKED
 */
public final class NatalRelationshipConstants {

    private NatalRelationshipConstants() {}

    // ============================================================
    // 版本与标识
    // ============================================================

    /** 报告类型 */
    public static final String REPORT_TYPE = "NATAL_RELATIONSHIP_REPORT";

    /** 版本信息 */
    public static final String CLAIM_REGISTRY_VERSION = "NATAL_RELATIONSHIP_CLAIM_V1";
    public static final String RENDER_SCHEMA_VERSION = "NATAL_RELATIONSHIP_RENDER_V1";
    public static final String EVIDENCE_ENGINE_VERSION = "NATAL_RELATIONSHIP_EVIDENCE_V1";

    // ============================================================
    // 报告状态
    // ============================================================

    public static final String STATUS_GENERATING = "GENERATING";
    public static final String STATUS_COMPLETED = "COMPLETED";
    public static final String STATUS_FAILED = "FAILED";

    // ============================================================
    // 覆盖状态
    // ============================================================

    public static final String COVERAGE_FULL = "FULL";
    public static final String COVERAGE_PARTIAL = "PARTIAL";
    public static final String COVERAGE_INSUFFICIENT = "INSUFFICIENT";

    // ============================================================
    // Claim 状态
    // ============================================================

    public static final String CLAIM_SUPPORTED = "SUPPORTED";
    public static final String CLAIM_CONDITIONAL = "CONDITIONAL";
    public static final String CLAIM_MIXED = "MIXED";
    public static final String CLAIM_INSUFFICIENT = "INSUFFICIENT";

    // ============================================================
    // 置信度
    // ============================================================

    public static final String CONFIDENCE_HIGH = "HIGH";
    public static final String CONFIDENCE_MEDIUM = "MEDIUM";
    public static final String CONFIDENCE_LOW = "LOW";
    public static final String CONFIDENCE_INSUFFICIENT = "INSUFFICIENT";

    // ============================================================
    // 敏感度
    // ============================================================

    public static final String SENSITIVITY_STANDARD = "STANDARD";
    public static final String SENSITIVITY_HIGH = "HIGH";
    public static final String SENSITIVITY_VERY_HIGH = "VERY_HIGH";

    // ============================================================
    // 主体
    // ============================================================

    public static final String SUBJECT_USER = "USER";
    public static final String SUBJECT_PARTNER = "PARTNER";
    public static final String SUBJECT_RELATIONSHIP = "RELATIONSHIP";
    public static final String SUBJECT_MARRIAGE = "MARRIAGE";
    public static final String SUBJECT_FUTURE_EVENT = "FUTURE_EVENT";

    // ============================================================
    // 关系阶段
    // ============================================================

    public static final String STAGE_EARLY_ROMANCE = "EARLY_ROMANCE";
    public static final String STAGE_LONG_TERM = "LONG_TERM_RELATIONSHIP";
    public static final String STAGE_MARRIAGE = "MARRIAGE";
    public static final String STAGE_FUTURE_PARTNER = "FUTURE_PARTNER";
    public static final String STAGE_TIMING = "TIMING";
    public static final String STAGE_CROSS_STAGE = "CROSS_STAGE";

    // ============================================================
    // Claim Class
    // ============================================================

    public static final String CLASS_CORE = "CORE";
    public static final String CLASS_COMPOSITE = "COMPOSITE";
    public static final String CLASS_FACTOR = "FACTOR";
    public static final String CLASS_TIMING = "TIMING";
    public static final String CLASS_DERIVED = "DERIVED";

    // ============================================================
    // 章节ID
    // ============================================================

    public static final String SECTION_S0 = "S0";
    public static final String SECTION_S1 = "S1";
    public static final String SECTION_S2 = "S2";
    public static final String SECTION_S3 = "S3";
    public static final String SECTION_S4 = "S4";
    public static final String SECTION_S5 = "S5";
    public static final String SECTION_S6 = "S6";
    public static final String SECTION_S7 = "S7";

    // ============================================================
    // 推荐类型（Behavioral Task Code）
    // ============================================================

    public static final String REC_VALIDATE_BEFORE_SOLVING = "VALIDATE_BEFORE_SOLVING";
    public static final String REC_SIGNAL_SPACE_AND_RETURN = "SIGNAL_SPACE_AND_RETURN";
    public static final String REC_STATE_NEEDS_EARLY = "STATE_NEEDS_EARLY";
    public static final String REC_NEGOTIATE_BEFORE_TAKING_OVER = "NEGOTIATE_BEFORE_TAKING_OVER";
    public static final String REC_VOICE_DIFFERENCE_BEFORE_HARMONY = "VOICE_DIFFERENCE_BEFORE_HARMONY";
    public static final String REC_SLOW_HIGH_STAKES_DECISIONS = "SLOW_HIGH_STAKES_DECISIONS";
    public static final String REC_DEFINE_COMMITMENT_EXPECTATIONS = "DEFINE_COMMITMENT_EXPECTATIONS";
    public static final String REC_SEPARATE_FACTS_FROM_ASSUMPTIONS = "SEPARATE_FACTS_FROM_ASSUMPTIONS";
    public static final String REC_SET_LIMIT_BEFORE_SACRIFICE = "SET_LIMIT_BEFORE_SACRIFICE";
    public static final String REC_REPAIR_AFTER_COOLDOWN = "REPAIR_AFTER_COOLDOWN";
    public static final String REC_CHECK_EXPECTATIONS_AGAINST_BEHAVIOR = "CHECK_EXPECTATIONS_AGAINST_BEHAVIOR";
    public static final String REC_PLAN_CONNECTION_AND_SPACE = "PLAN_CONNECTION_AND_SPACE";

    // ============================================================
    // 目标状态（Goal State）
    // ============================================================

    public static final String GOAL_EMOTIONAL_CLARITY = "EMOTIONAL_CLARITY";
    public static final String GOAL_EMOTIONAL_CO_REGULATION = "EMOTIONAL_CO_REGULATION";
    public static final String GOAL_ASSERTIVE_COMMUNICATION = "ASSERTIVE_COMMUNICATION";
    public static final String GOAL_RECIPROCAL_NEGOTIATION = "RECIPROCAL_NEGOTIATION";
    public static final String GOAL_HEALTHY_BOUNDARIES = "HEALTHY_BOUNDARIES";
    public static final String GOAL_CLOSENESS_WITH_AUTONOMY = "CLOSENESS_WITH_AUTONOMY";
    public static final String GOAL_SHARED_RESPONSIBILITY = "SHARED_RESPONSIBILITY";
    public static final String GOAL_SUSTAINABLE_COMMITMENT = "SUSTAINABLE_COMMITMENT";
    public static final String GOAL_CONSTRUCTIVE_CONFLICT = "CONSTRUCTIVE_CONFLICT";
    public static final String GOAL_RELIABLE_REPAIR = "RELIABLE_REPAIR";
    public static final String GOAL_REALISTIC_EXPECTATIONS = "REALISTIC_EXPECTATIONS";
    public static final String GOAL_TRUST_WITHOUT_OVERCONTROL = "TRUST_WITHOUT_OVERCONTROL";
    public static final String GOAL_MUTUAL_INTIMACY = "MUTUAL_INTIMACY";
    public static final String GOAL_RELATIONAL_FLEXIBILITY = "RELATIONAL_FLEXIBILITY";

    // ============================================================
    // 段落角色
    // ============================================================

    public static final String ROLE_DIRECT_ANSWER = "DIRECT_ANSWER";
    public static final String ROLE_SECONDARY_EXPLANATION = "SECONDARY_EXPLANATION";
    public static final String ROLE_THESIS = "THESIS";
    public static final String ROLE_TAKEAWAY = "TAKEAWAY";
    public static final String ROLE_TIMING_REMINDER = "TIMING_REMINDER";

    // ============================================================
    // D1/D9 Cross-chart Relation
    // ============================================================

    public static final String CROSS_REINFORCED = "REINFORCED";
    public static final String CROSS_MATURED_ADAPTED = "MATURED_ADAPTED";
    public static final String CROSS_MITIGATED = "MITIGATED";
    public static final String CROSS_TENSION = "TENSION";
    public static final String CROSS_INSUFFICIENT = "INSUFFICIENT";

    // ============================================================
    // Marriage Overall Pattern
    // ============================================================

    public static final String PATTERN_STRUCTURALLY_SUPPORTED = "STRUCTURALLY_SUPPORTED";
    public static final String PATTERN_SUPPORTED_WITH_ADAPTATION = "SUPPORTED_WITH_ADAPTATION";
    public static final String PATTERN_MIXED = "MIXED";
    public static final String PATTERN_PRESSURE_HEAVY_BUT_RESOURCED = "PRESSURE_HEAVY_BUT_RESOURCED";
    public static final String PATTERN_HIGH_ADAPTATION_REQUIRED = "HIGH_ADAPTATION_REQUIRED";

    // ============================================================
    // Partner Cross-System Relation
    // ============================================================

    public static final String PARTNER_CONSISTENT = "CONSISTENT";
    public static final String PARTNER_PARTIALLY_CONSISTENT = "PARTIALLY_CONSISTENT";
    public static final String PARTNER_COMPLEMENTARY = "COMPLEMENTARY";
    public static final String PARTNER_CONFLICTING = "CONFLICTING";

    // ============================================================
    // Dignity / Condition / Execution
    // ============================================================

    public static final String DIGNITY_EXALTED = "EXALTED";
    public static final String DIGNITY_MOOLATRIKONA = "MOOLATRIKONA";
    public static final String DIGNITY_OWN_SIGN = "OWN_SIGN";
    public static final String DIGNITY_FRIEND_SIGN = "FRIEND_SIGN";
    public static final String DIGNITY_NEUTRAL_SIGN = "NEUTRAL_SIGN";
    public static final String DIGNITY_ENEMY_SIGN = "ENEMY_SIGN";
    public static final String DIGNITY_DEBILITATED = "DEBILITATED";

    public static final String OPERATING_VERY_SUPPORTIVE = "VERY_SUPPORTIVE";
    public static final String OPERATING_SUPPORTIVE = "SUPPORTIVE";
    public static final String OPERATING_NEUTRAL = "NEUTRAL";
    public static final String OPERATING_CHALLENGED = "CHALLENGED";
    public static final String OPERATING_HIGHLY_CHALLENGED = "HIGHLY_CHALLENGED";

    public static final String EXECUTION_HIGH = "HIGH";
    public static final String EXECUTION_ADEQUATE = "ADEQUATE";
    public static final String EXECUTION_LOW = "LOW";
    public static final String EXECUTION_VERY_LOW = "VERY_LOW";

    // ============================================================
    // 配置参数
    // ============================================================

    /** 最少推荐数 */
    public static final int MIN_RECOMMENDATIONS = 3;

    /** 最多推荐数 */
    public static final int MAX_RECOMMENDATIONS = 6;
}