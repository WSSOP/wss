package cn.dct.zpback.constant;

/**
 * 婚恋深度报告常量
 * 依据：单人婚恋深度报告Skill V1 LOCKED
 */
public final class MarriageReportConstants {

    private MarriageReportConstants() {}

    // ============================================================
    // 版本与固定参数
    // ============================================================
    public static final String REPORT_TYPE = "NATAL_RELATIONSHIP_REPORT";
    public static final String CLAIM_REGISTRY_VERSION = "NATAL_RELATIONSHIP_CLAIM_V1";
    public static final String RENDER_SCHEMA_VERSION = "NATAL_RELATIONSHIP_RENDER_V1";
    public static final String REPORT_OUTPUT_SCHEMA_VERSION = "NATAL_RELATIONSHIP_REPORT_OUTPUT_V1";
    public static final String UL_CALCULATION_CONVENTION = "ARUDHA_1_7_EXCEPTION_10_4";

    // ============================================================
    // Claim 状态枚举
    // ============================================================
    public static final String CLAIM_SUPPORTED = "SUPPORTED";
    public static final String CLAIM_CONDITIONAL = "CONDITIONAL";
    public static final String CLAIM_MIXED = "MIXED";
    public static final String CLAIM_INSUFFICIENT = "INSUFFICIENT";

    // Confidence
    public static final String CONFIDENCE_HIGH = "HIGH";
    public static final String CONFIDENCE_MEDIUM = "MEDIUM";
    public static final String CONFIDENCE_LOW = "LOW";
    public static final String CONFIDENCE_INSUFFICIENT = "INSUFFICIENT";

    // Sensitivity
    public static final String SENSITIVITY_STANDARD = "STANDARD";
    public static final String SENSITIVITY_HIGH = "HIGH";
    public static final String SENSITIVITY_VERY_HIGH = "VERY_HIGH";

    // Claim Level
    public static final String LEVEL_MAJOR = "MAJOR";
    public static final String LEVEL_SUPPORTING = "SUPPORTING";
    public static final String LEVEL_REFINEMENT = "REFINEMENT";

    // Claim Class
    public static final String CLASS_CORE = "CORE";
    public static final String CLASS_COMPOSITE = "COMPOSITE";
    public static final String CLASS_FACTOR = "FACTOR";
    public static final String CLASS_TIMING = "TIMING";
    public static final String CLASS_DERIVED = "DERIVED";

    // Subject
    public static final String SUBJECT_USER = "USER";
    public static final String SUBJECT_PARTNER = "PARTNER";
    public static final String SUBJECT_RELATIONSHIP = "RELATIONSHIP";
    public static final String SUBJECT_MARRIAGE = "MARRIAGE";
    public static final String SUBJECT_FUTURE_EVENT = "FUTURE_EVENT";

    // Relationship Stage
    public static final String STAGE_EARLY_ROMANCE = "EARLY_ROMANCE";
    public static final String STAGE_LONG_TERM_RELATIONSHIP = "LONG_TERM_RELATIONSHIP";
    public static final String STAGE_MARRIAGE = "MARRIAGE";
    public static final String STAGE_FUTURE_PARTNER = "FUTURE_PARTNER";
    public static final String STAGE_TIMING = "TIMING";
    public static final String STAGE_CROSS_STAGE = "CROSS_STAGE";

    // Renderer Permission
    public static final String RENDERER_DIRECT = "DIRECT";
    public static final String RENDERER_CONDITIONAL = "CONDITIONAL";
    public static final String RENDERER_DERIVED_ONLY = "DERIVED_ONLY";

    // ============================================================
    // 章节定义 (S0-S7)
    // ============================================================
    public static final String SECTION_EXECUTIVE_SUMMARY = "EXECUTIVE_SUMMARY";
    public static final String SECTION_S1 = "S1";
    public static final String SECTION_S2 = "S2";
    public static final String SECTION_S3 = "S3";
    public static final String SECTION_S4 = "S4";
    public static final String SECTION_S5 = "S5";
    public static final String SECTION_S6 = "S6";
    public static final String SECTION_S7 = "S7";

    // Chapter Role
    public static final String ROLE_ENTRY = "ENTRY";
    public static final String ROLE_NEED = "NEED";
    public static final String ROLE_PARTNER = "PARTNER";
    public static final String ROLE_EVALUATION = "EVALUATION";
    public static final String ROLE_INTERACTION = "INTERACTION";
    public static final String ROLE_TIMING = "TIMING";
    public static final String ROLE_ACTION = "ACTION";

    // ============================================================
    // 38 个 Canonical Claim 定义
    // ============================================================

    // S1 Claims
    public static final String CLAIM_LOVE_PATTERN = "LOVE_PATTERN";
    public static final String CLAIM_ATTRACTION_PATTERN = "ATTRACTION_PATTERN";
    public static final String CLAIM_ROMANTIC_INITIATION_STYLE = "ROMANTIC_INITIATION_STYLE";

    // S2 Claims
    public static final String CLAIM_MARRIAGE_FOUNDATION = "MARRIAGE_FOUNDATION";
    public static final String CLAIM_LONG_TERM_RELATIONSHIP_NEED = "LONG_TERM_RELATIONSHIP_NEED";
    public static final String CLAIM_EMOTIONAL_SECURITY_NEED = "EMOTIONAL_SECURITY_NEED";
    public static final String CLAIM_COMMITMENT_STYLE = "COMMITMENT_STYLE";
    public static final String CLAIM_MARRIAGE_STAGE_SELF = "MARRIAGE_STAGE_SELF";

    // S3 Claims
    public static final String CLAIM_PARTNER_CORE_PERSONALITY = "PARTNER_CORE_PERSONALITY";
    public static final String CLAIM_PARTNER_RELATIONAL_ROLE = "PARTNER_RELATIONAL_ROLE";
    public static final String CLAIM_PARTNER_MATURITY_STYLE = "PARTNER_MATURITY_STYLE";
    public static final String CLAIM_PARTNER_SOCIAL_CAREER_TENDENCY = "PARTNER_SOCIAL_CAREER_TENDENCY";
    public static final String CLAIM_PARTNER_BACKGROUND_TENDENCY = "PARTNER_BACKGROUND_TENDENCY";
    public static final String CLAIM_PARTNER_APPEARANCE_PRESENCE = "PARTNER_APPEARANCE_PRESENCE";
    public static final String CLAIM_PARTNER_DISTANCE_CULTURE_TENDENCY = "PARTNER_DISTANCE_CULTURE_TENDENCY";
    public static final String CLAIM_PARTNER_AGE_GAP_TENDENCY = "PARTNER_AGE_GAP_TENDENCY";
    public static final String CLAIM_PARTNER_NEED_ARCHETYPE_ALIGNMENT = "PARTNER_NEED_ARCHETYPE_ALIGNMENT";

    // S4 Claims
    public static final String CLAIM_CORE_MARRIAGE_STRENGTH = "CORE_MARRIAGE_STRENGTH";
    public static final String CLAIM_CORE_ADAPTATION_NEED = "CORE_ADAPTATION_NEED";
    public static final String CLAIM_MARRIAGE_PROTECTIVE_FACTOR = "MARRIAGE_PROTECTIVE_FACTOR";
    public static final String CLAIM_MARRIAGE_PRESSURE_FACTOR = "MARRIAGE_PRESSURE_FACTOR";

    // S5 Claims
    public static final String CLAIM_MARRIAGE_DYNAMIC = "MARRIAGE_DYNAMIC";
    public static final String CLAIM_LONG_TERM_INTIMACY_PATTERN = "LONG_TERM_INTIMACY_PATTERN";
    public static final String CLAIM_EMOTIONAL_REGULATION_PATTERN = "EMOTIONAL_REGULATION_PATTERN";
    public static final String CLAIM_CONFLICT_RESPONSE_PATTERN = "CONFLICT_RESPONSE_PATTERN";
    public static final String CLAIM_BOUNDARY_PATTERN = "BOUNDARY_PATTERN";
    public static final String CLAIM_SHARED_RESPONSIBILITY_PATTERN = "SHARED_RESPONSIBILITY_PATTERN";
    public static final String CLAIM_REPAIR_PATTERN = "REPAIR_PATTERN";
    public static final String CLAIM_MARRIAGE_REALITY_PATTERN = "MARRIAGE_REALITY_PATTERN";

    // S6 Claims
    public static final String CLAIM_MEETING_WINDOW = "MEETING_WINDOW";
    public static final String CLAIM_RELATIONSHIP_START_WINDOW = "RELATIONSHIP_START_WINDOW";
    public static final String CLAIM_COMMITMENT_WINDOW = "COMMITMENT_WINDOW";
    public static final String CLAIM_MARRIAGE_WINDOW = "MARRIAGE_WINDOW";
    public static final String CLAIM_RELATIONSHIP_REASSESSMENT_WINDOW = "RELATIONSHIP_REASSESSMENT_WINDOW";
    public static final String CLAIM_RELATIONSHIP_PRESSURE_WINDOW = "RELATIONSHIP_PRESSURE_WINDOW";

    // S7 Claims
    public static final String CLAIM_RELATIONSHIP_GROWTH_THEME = "RELATIONSHIP_GROWTH_THEME";
    public static final String CLAIM_GROWTH_TASK = "GROWTH_TASK";
    public static final String CLAIM_RELATIONSHIP_RECOMMENDATION = "RELATIONSHIP_RECOMMENDATION";

    // ============================================================
    // 推荐类型
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

    // Goal State
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

    // Action Granularity
    public static final String GRANULARITY_MICRO = "MICRO";
    public static final String GRANULARITY_SITUATIONAL = "SITUATIONAL";
    public static final String GRANULARITY_STRUCTURAL = "STRUCTURAL";

    // ============================================================
    // 时间窗口类型
    // ============================================================
    public static final String WINDOW_MEETING = "MEETING_WINDOW";
    public static final String WINDOW_RELATIONSHIP_START = "RELATIONSHIP_START_WINDOW";
    public static final String WINDOW_COMMITMENT = "COMMITMENT_WINDOW";
    public static final String WINDOW_MARRIAGE = "MARRIAGE_WINDOW";
    public static final String WINDOW_REASSESSMENT = "RELATIONSHIP_REASSESSMENT_WINDOW";
    public static final String WINDOW_PRESSURE = "RELATIONSHIP_PRESSURE_WINDOW";

    // ============================================================
    // 段落角色
    // ============================================================
    public static final String ROLE_DIRECT_ANSWER = "DIRECT_ANSWER";
    public static final String ROLE_SECONDARY_EXPLANATION = "SECONDARY_EXPLANATION";
    public static final String ROLE_TIMING_REMINDER = "TIMING_REMINDER";

    // ============================================================
    // 覆盖状态
    // ============================================================
    public static final String COVERAGE_FULL = "FULL";
    public static final String COVERAGE_PARTIAL = "PARTIAL";
    public static final String COVERAGE_INSUFFICIENT = "INSUFFICIENT";

    // Render Status
    public static final String RENDER_FULL = "FULL";
    public static final String RENDER_NORMAL = "NORMAL";
    public static final String RENDER_SHORT = "SHORT";
    public static final String RENDER_MINIMAL = "MINIMAL";

    // Synthesis Mode
    public static final String SYNTHESIS_SINGLE_CORE = "SINGLE_CORE";
    public static final String SYNTHESIS_DUAL_COMPLEMENT = "DUAL_COMPLEMENT";
    public static final String SYNTHESIS_TENSION_INTEGRATION = "TENSION_INTEGRATION";
    public static final String SYNTHESIS_CAUSAL_CHAIN = "CAUSAL_CHAIN";
    public static final String SYNTHESIS_LAYERED_PROFILE = "LAYERED_PROFILE";

    // D1/D9 Cross-chart Relation
    public static final String CROSS_REINFORCED = "REINFORCED";
    public static final String CROSS_MATURED_ADAPTED = "MATURED_ADAPTED";
    public static final String CROSS_MITIGATED = "MITIGATED";
    public static final String CROSS_TENSION = "TENSION";
    public static final String CROSS_INSUFFICIENT = "INSUFFICIENT";

    // Marriage Overall Pattern
    public static final String PATTERN_STRUCTURALLY_SUPPORTED = "STRUCTURALLY_SUPPORTED";
    public static final String PATTERN_SUPPORTED_WITH_ADAPTATION = "SUPPORTED_WITH_ADAPTATION";
    public static final String PATTERN_MIXED = "MIXED";
    public static final String PATTERN_PRESSURE_HEAVY_BUT_RESOURCED = "PRESSURE_HEAVY_BUT_RESOURCED";
    public static final String PATTERN_HIGH_ADAPTATION_REQUIRED = "HIGH_ADAPTATION_REQUIRED";

    // Partner Cross-System Relation
    public static final String PARTNER_CROSS_CONSISTENT = "CONSISTENT";
    public static final String PARTNER_CROSS_PARTIALLY_CONSISTENT = "PARTIALLY_CONSISTENT";
    public static final String PARTNER_CROSS_COMPLEMENTARY = "COMPLEMENTARY";
    public static final String PARTNER_CROSS_CONFLICTING = "CONFLICTING";

    // Dignity
    public static final String DIGNITY_EXALTED = "EXALTED";
    public static final String DIGNITY_MOOLATRIKONA = "MOOLATRIKONA";
    public static final String DIGNITY_OWN_SIGN = "OWN_SIGN";
    public static final String DIGNITY_FRIEND_SIGN = "FRIEND_SIGN";
    public static final String DIGNITY_NEUTRAL_SIGN = "NEUTRAL_SIGN";
    public static final String DIGNITY_ENEMY_SIGN = "ENEMY_SIGN";
    public static final String DIGNITY_DEBILITATED = "DEBILITATED";

    // Modifier Type
    public static final String MODIFIER_SUPPORTIVE = "SUPPORTIVE";
    public static final String MODIFIER_PRESSURIZING = "PRESSURIZING";
    public static final String MODIFIER_INTENSIFYING = "INTENSIFYING";
    public static final String MODIFIER_COMPLEXIFYING = "COMPLEXIFYING";
    public static final String MODIFIER_MIXED = "MIXED";

    // Protection Level
    public static final String PROTECTION_STRONG = "STRONG_PROTECTIVE";
    public static final String PROTECTION_MODERATE = "MODERATE_PROTECTIVE";
    public static final String PROTECTION_CONDITIONAL = "CONDITIONAL_PROTECTIVE";
    public static final String PROTECTION_NOT_ESTABLISHED = "NOT_ESTABLISHED";

    // Relationship Status
    public static final String STATUS_SINGLE = "SINGLE";
    public static final String STATUS_DATING = "DATING";
    public static final String STATUS_COMMITTED = "COMMITTED";
    public static final String STATUS_MARRIED = "MARRIED";
    public static final String STATUS_SEPARATED = "SEPARATED";
    public static final String STATUS_DIVORCED = "DIVORCED";
    public static final String STATUS_UNKNOWN = "UNKNOWN";

    // Activation Relevance
    public static final String RELEVANCE_HIGH = "HIGH";
    public static final String RELEVANCE_MODERATE = "MODERATE";
    public static final String RELEVANCE_LIMITED = "LIMITED";

    // ============================================================
    // 默认字数配置（统一 3000-4000 字）
    // ============================================================
    public static final int MIN_BODY_CHAR_TARGET_MIN = 3000;
    public static final int MIN_BODY_CHAR_TARGET_MAX = 4000;
    public static final int HARD_BODY_CHAR_MAX = 4200;
    public static final int MIN_RECOMMENDATIONS = 3;
    public static final int MAX_RECOMMENDATIONS = 6;
    public static final int MIN_CLAIMS = 20;
    public static final int MAX_CLAIMS = 38;
    public static final int MAX_TIMING_WINDOWS = 6;
}
