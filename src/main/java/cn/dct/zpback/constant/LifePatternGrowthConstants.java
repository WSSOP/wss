package cn.dct.zpback.constant;

import java.util.*;

/**
 * 人生课题深度报告常量定义
 * 规则版本：life_pattern_growth.v1.0.0
 */
public class LifePatternGrowthConstants {

    // ============================================================
    // 报告类型和状态
    // ============================================================

    public static final String REPORT_TYPE = "LIFE_PATTERN_GROWTH";
    public static final String SKILL_ID = "LIFE_PATTERN_GROWTH_REPORT_SKILL";
    public static final String RULE_VERSION = "life_pattern_growth.v1.0.0";
    public static final String SCHEMA_VERSION = "life_pattern_growth.schema.v1.0.0";
    public static final String RENDERER_VERSION = "life_pattern_growth.renderer.v1.0.0";

    // report_status
    public static final String STATUS_GENERATING = "GENERATING";
    public static final String STATUS_COMPLETED = "COMPLETED";
    public static final String STATUS_FAILED = "FAILED";

    // ============================================================
    // Section Keys（9章 + 总结）
    // ============================================================

    public static final String SECTION_EXECUTIVE_SUMMARY = "EXECUTIVE_SUMMARY";
    public static final String SECTION_CORE_THEME_AND_NEED = "CORE_THEME_AND_NEED";
    public static final String SECTION_SECURITY_SELF_WORTH_RESOURCES = "SECURITY_SELF_WORTH_RESOURCES";
    public static final String SECTION_PROTECTION_AND_OVERUSE = "PROTECTION_AND_OVERUSE";
    public static final String SECTION_INNER_TENSION = "INNER_TENSION";
    public static final String SECTION_REPEATING_LOOP = "REPEATING_LOOP";
    public static final String SECTION_GROWTH_AND_MATURITY = "GROWTH_AND_MATURITY";
    public static final String SECTION_MEANING_ORIENTATION = "MEANING_ORIENTATION";
    public static final String SECTION_CURRENT_ACTIVATION = "CURRENT_ACTIVATION";
    public static final String SECTION_PERSONAL_ACTION_GUIDE = "PERSONAL_ACTION_GUIDE";

    // section_type
    public static final String SECTION_TYPE_OVERVIEW = "OVERVIEW";
    public static final String SECTION_TYPE_DEEP_ANALYSIS = "DEEP_ANALYSIS";
    public static final String SECTION_TYPE_TENSION = "TENSION";
    public static final String SECTION_TYPE_LOOP = "LOOP";
    public static final String SECTION_TYPE_GROWTH = "GROWTH";
    public static final String SECTION_TYPE_MEANING = "MEANING";
    public static final String SECTION_TYPE_ACTIVATION = "ACTIVATION";
    public static final String SECTION_TYPE_ACTION_GUIDE = "ACTION_GUIDE";

    // ============================================================
    // 13 Claim Types
    // ============================================================

    public static final String CLAIM_C01_CORE_LIFE_THEME = "CORE_LIFE_THEME";
    public static final String CLAIM_C02_CORE_INNER_NEED = "CORE_INNER_NEED";
    public static final String CLAIM_C03_SECURITY_REGULATION_PATTERN = "SECURITY_REGULATION_PATTERN";
    public static final String CLAIM_C04_SELF_WORTH_ANCHOR = "SELF_WORTH_ANCHOR";
    public static final String CLAIM_C05_PROTECTIVE_RESPONSE_PATTERN = "PROTECTIVE_RESPONSE_PATTERN";
    public static final String CLAIM_C06_INNER_CONFLICT = "INNER_CONFLICT";
    public static final String CLAIM_C07_OVERUSE_PATTERN = "OVERUSE_PATTERN";
    public static final String CLAIM_C08_STRENGTH_RESOURCE = "STRENGTH_RESOURCE";
    public static final String CLAIM_C09_REPEATING_LIFE_LOOP = "REPEATING_LIFE_LOOP";
    public static final String CLAIM_C10_GROWTH_CHALLENGE = "GROWTH_CHALLENGE";
    public static final String CLAIM_C11_MATURITY_CAPACITY = "MATURITY_CAPACITY";
    public static final String CLAIM_C12_MEANING_ORIENTATION = "MEANING_ORIENTATION";
    public static final String CLAIM_C13_CURRENT_GROWTH_ACTIVATION = "CURRENT_GROWTH_ACTIVATION";

    // ============================================================
    // Claim States
    // ============================================================

    public static final String STATE_SUPPORTED = "SUPPORTED";
    public static final String STATE_CONDITIONAL = "CONDITIONAL";
    public static final String STATE_MIXED = "MIXED";
    public static final String STATE_CONTRADICTED = "CONTRADICTED";
    public static final String STATE_INSUFFICIENT = "INSUFFICIENT";
    public static final String STATE_SUPPRESSED = "SUPPRESSED";

    // ============================================================
    // Claim Confidence
    // ============================================================

    public static final String CONFIDENCE_HIGH = "HIGH";
    public static final String CONFIDENCE_MEDIUM = "MEDIUM";
    public static final String CONFIDENCE_LOW = "LOW";
    public static final String CONFIDENCE_NONE = "NONE";

    // ============================================================
    // Strength Modifier
    // ============================================================

    public static final String STRENGTH_STRONG = "STRONG";
    public static final String STRENGTH_MODERATE = "MODERATE";
    public static final String STRENGTH_CONDITIONAL = "CONDITIONAL";

    // ============================================================
    // Semantic Evidence Families
    // ============================================================

    public static final String FAMILY_A_SELF_IDENTITY = "A_SELF_IDENTITY";
    public static final String FAMILY_B_EMOTIONAL_SECURITY = "B_EMOTIONAL_SECURITY";
    public static final String FAMILY_C_ACTION_PROTECTION = "C_ACTION_PROTECTION";
    public static final String FAMILY_D_VALUE_RELATIONAL_REGULATION = "D_VALUE_RELATIONAL_REGULATION";
    public static final String FAMILY_E_BELIEF_MEANING = "E_BELIEF_MEANING";
    public static final String FAMILY_F_CONSTRAINT_RESPONSIBILITY = "F_CONSTRAINT_RESPONSIBILITY";
    public static final String FAMILY_G_NODE_DYNAMICS = "G_NODE_DYNAMICS";

    // ============================================================
    // Timing Stage Types
    // ============================================================

    public static final String STAGE_FOUNDATION = "FOUNDATION";
    public static final String STAGE_ACCUMULATION = "ACCUMULATION";
    public static final String STAGE_PRESSURE_TEST = "PRESSURE_TEST";
    public static final String STAGE_TRANSITION = "TRANSITION";
    public static final String STAGE_EXPANSION = "EXPANSION";

    // ============================================================
    // Activation Strength
    // ============================================================

    public static final String ACTIVATION_STRONG = "STRONG";
    public static final String ACTIVATION_MODERATE = "MODERATE";
    public static final String ACTIVATION_WEAK = "WEAK";

    // ============================================================
    // C02 Core Inner Need Candidates
    // ============================================================

    public static final String NEED_STABILITY_PREDICTABILITY = "STABILITY_PREDICTABILITY";
    public static final String NEED_EMOTIONAL_RESPONSIVENESS = "EMOTIONAL_RESPONSIVENESS";
    public static final String NEED_AUTONOMY_SPACE = "AUTONOMY_SPACE";
    public static final String NEED_RECOGNITION_VISIBILITY = "RECOGNITION_VISIBILITY";
    public static final String NEED_BELONGING_CONNECTION = "BELONGING_CONNECTION";
    public static final String NEED_ORDER_CONTROL = "ORDER_CONTROL";
    public static final String NEED_GROWTH_MEANING = "GROWTH_MEANING";
    public static final String NEED_MATERIAL_SECURITY = "MATERIAL_SECURITY";

    // ============================================================
    // C03 Security Regulation Pattern Candidates
    // ============================================================

    public static final String SECURITY_ROUTINE_STRUCTURE = "ROUTINE_STRUCTURE";
    public static final String SECURITY_RELATIONAL_CO_REGULATION = "RELATIONAL_CO_REGULATION";
    public static final String SECURITY_INFORMATIONAL_CLARITY = "INFORMATIONAL_CLARITY";
    public static final String SECURITY_ACTIVE_MASTERY = "ACTIVE_MASTERY";
    public static final String SECURITY_RESOURCE_BUFFER = "RESOURCE_BUFFER";
    public static final String SECURITY_SOLITUDE_WITHDRAWAL = "SOLITUDE_WITHDRAWAL";
    public static final String SECURITY_BELIEF_REFRAMING = "BELIEF_REFRAMING";
    public static final String SECURITY_ADAPTIVE_VARIETY = "ADAPTIVE_VARIETY";

    // ============================================================
    // C04 Self Worth Anchor Candidates
    // ============================================================

    public static final String WORTH_SELF_DEFINITION_AUTONOMY = "SELF_DEFINITION_AUTONOMY";
    public static final String WORTH_COMPETENCE_ACHIEVEMENT = "COMPETENCE_ACHIEVEMENT";
    public static final String WORTH_RELATIONAL_VALUE = "RELATIONAL_VALUE";
    public static final String WORTH_RESPONSIBILITY_RELIABILITY = "RESPONSIBILITY_RELIABILITY";
    public static final String WORTH_KNOWLEDGE_WISDOM = "KNOWLEDGE_WISDOM";
    public static final String WORTH_VISIBILITY_RECOGNITION = "VISIBILITY_RECOGNITION";
    public static final String WORTH_MATERIAL_CAPABILITY = "MATERIAL_CAPABILITY";
    public static final String WORTH_RESILIENCE_TRANSFORMATION = "RESILIENCE_TRANSFORMATION";

    // ============================================================
    // C05 Protective Response Pattern Candidates
    // ============================================================

    public static final String PROTECT_CONTROL_TIGHTEN = "CONTROL_TIGHTEN";
    public static final String PROTECT_CONFRONT_ACT = "CONFRONT_ACT";
    public static final String PROTECT_WITHDRAW_AVOID = "WITHDRAW_AVOID";
    public static final String PROTECT_APPEASE_ADAPT = "APPEASE_ADAPT";
    public static final String PROTECT_OVERFUNCTION_CARRY = "OVERFUNCTION_CARRY";
    public static final String PROTECT_OVERTHINK_ANALYZE = "OVERTHINK_ANALYZE";
    public static final String PROTECT_EMOTIONAL_CLOSE = "EMOTIONAL_CLOSE";
    public static final String PROTECT_PIVOT_ESCAPE = "PIVOT_ESCAPE";

    // ============================================================
    // C07 Overuse Pattern Candidates
    // ============================================================

    public static final String OVERUSE_OVERCONTROL = "OVERCONTROL";
    public static final String OVERUSE_OVERRESPONSIBILITY = "OVERRESPONSIBILITY";
    public static final String OVERUSE_OVERACHIEVEMENT = "OVERACHIEVEMENT";
    public static final String OVERUSE_OVERADAPTATION = "OVERADAPTATION";
    public static final String OVERUSE_OVERANALYSIS = "OVERANALYSIS";
    public static final String OVERUSE_OVERWITHDRAWAL = "OVERWITHDRAWAL";
    public static final String OVERUSE_OVERINDEPENDENCE = "OVERINDEPENDENCE";
    public static final String OVERUSE_OVEREXPLORATION = "OVEREXPLORATION";

    // ============================================================
    // C08 Strength Resource Candidates
    // ============================================================

    public static final String STRENGTH_INITIATIVE_COURAGE = "INITIATIVE_COURAGE";
    public static final String STRENGTH_CONSISTENCY_ENDURANCE = "CONSISTENCY_ENDURANCE";
    public static final String STRENGTH_PERCEPTION_ANALYSIS = "PERCEPTION_ANALYSIS";
    public static final String STRENGTH_CONNECTION_EMPATHY = "CONNECTION_EMPATHY";
    public static final String STRENGTH_ORGANIZATION_EXECUTION = "ORGANIZATION_EXECUTION";
    public static final String STRENGTH_LEARNING_ADAPTABILITY = "LEARNING_ADAPTABILITY";
    public static final String STRENGTH_REBUILDING_RESILIENCE = "REBUILDING_RESILIENCE";
    public static final String STRENGTH_MEANING_GUIDANCE = "MEANING_GUIDANCE";

    // ============================================================
    // C09 Repeating Life Loop Candidates
    // ============================================================

    public static final String LOOP_PROVE_WORTH_THROUGH_PERFORMANCE = "PROVE_WORTH_THROUGH_PERFORMANCE";
    public static final String LOOP_SECURE_CONNECTION_THROUGH_ADAPTATION = "SECURE_CONNECTION_THROUGH_ADAPTATION";
    public static final String LOOP_SECURE_SAFETY_THROUGH_CONTROL = "SECURE_SAFETY_THROUGH_CONTROL";
    public static final String LOOP_AVOID_VULNERABILITY_THROUGH_WITHDRAWAL = "AVOID_VULNERABILITY_THROUGH_WITHDRAWAL";
    public static final String LOOP_PRESERVE_FREEDOM_THROUGH_EXIT = "PRESERVE_FREEDOM_THROUGH_EXIT";
    public static final String LOOP_REDUCE_UNCERTAINTY_THROUGH_OVERTHINKING = "REDUCE_UNCERTAINTY_THROUGH_OVERTHINKING";
    public static final String LOOP_MAINTAIN_STABILITY_THROUGH_OVERRESPONSIBILITY = "MAINTAIN_STABILITY_THROUGH_OVERRESPONSIBILITY";
    public static final String LOOP_SEEK_MEANING_THROUGH_IDEALIZATION = "SEEK_MEANING_THROUGH_IDEALIZATION";

    // ============================================================
    // C10 Growth Challenge Candidates
    // ============================================================

    public static final String CHALLENGE_TOLERATE_UNCERTAINTY = "TOLERATE_UNCERTAINTY";
    public static final String CHALLENGE_EXPRESS_NEEDS_BOUNDARIES = "EXPRESS_NEEDS_BOUNDARIES";
    public static final String CHALLENGE_RECEIVE_SUPPORT = "RECEIVE_SUPPORT";
    public static final String CHALLENGE_SUSTAIN_FOCUSED_ACTION = "SUSTAIN_FOCUSED_ACTION";
    public static final String CHALLENGE_SEPARATE_WORTH_FROM_OUTCOME = "SEPARATE_WORTH_FROM_OUTCOME";
    public static final String CHALLENGE_ALLOW_VULNERABILITY_TRUST = "ALLOW_VULNERABILITY_TRUST";
    public static final String CHALLENGE_BALANCE_RESPONSIBILITY_RECOVERY = "BALANCE_RESPONSIBILITY_RECOVERY";
    public static final String CHALLENGE_INTEGRATE_IDEAL_REALITY = "INTEGRATE_IDEAL_REALITY";

    // ============================================================
    // C11 Maturity Capacity Candidates
    // ============================================================

    public static final String MATURITY_SELF_REGULATION = "SELF_REGULATION";
    public static final String MATURITY_FLEXIBLE_BOUNDARIES = "FLEXIBLE_BOUNDARIES";
    public static final String MATURITY_COLLABORATIVE_AUTONOMY = "COLLABORATIVE_AUTONOMY";
    public static final String MATURITY_SUSTAINABLE_EXECUTION = "SUSTAINABLE_EXECUTION";
    public static final String MATURITY_EMOTIONAL_EXPRESSION = "EMOTIONAL_EXPRESSION";
    public static final String MATURITY_DISCERNMENT_TRUST = "DISCERNMENT_TRUST";
    public static final String MATURITY_INTEGRATED_SELF_WORTH = "INTEGRATED_SELF_WORTH";
    public static final String MATURITY_MEANINGFUL_REALISM = "MEANINGFUL_REALISM";

    // ============================================================
    // C12 Meaning Orientation Candidates
    // ============================================================

    public static final String MEANING_SELF_MASTERY = "SELF_MASTERY";
    public static final String MEANING_CARE_CONNECTION = "CARE_CONNECTION";
    public static final String MEANING_CONTRIBUTION_SERVICE = "CONTRIBUTION_SERVICE";
    public static final String MEANING_CREATION_EXPRESSION = "CREATION_EXPRESSION";
    public static final String MEANING_KNOWLEDGE_TRUTH = "KNOWLEDGE_TRUTH";
    public static final String MEANING_GROWTH_EXPLORATION = "GROWTH_EXPLORATION";
    public static final String MEANING_ORDER_STEWARDSHIP = "ORDER_STEWARDSHIP";
    public static final String MEANING_TRANSFORMATION_RENEWAL = "TRANSFORMATION_RENEWAL";

    // ============================================================
    // Recommendation Types
    // ============================================================

    public static final String REC_TYPE_BUILD = "BUILD";
    public static final String REC_TYPE_ADJUST = "ADJUST";
    public static final String REC_TYPE_PREPARE = "PREPARE";

    // ============================================================
    // Report Config
    // ============================================================

    public static final int DEFAULT_CORE_FINDINGS_COUNT = 4;
    public static final int MIN_CORE_FINDINGS = 3;
    public static final int MAX_CORE_FINDINGS = 5;
    public static final int MAX_RECOMMENDATIONS = 5;
    public static final int MIN_RECOMMENDATIONS = 3;
    public static final int MAX_EVIDENCE_PER_SECTION = 5;
    public static final int MIN_EVIDENCE_PER_SECTION = 2;

    // 字数目标（统一 3000-4000 字）
    public static final int BODY_CHAR_TARGET_MIN = 3000;
    public static final int BODY_CHAR_TARGET_MAX = 4000;
    public static final int BODY_CHAR_HARD_CAP = 4200;

    // 各章节字数分配
    public static final int EXECUTIVE_SUMMARY_MIN = 200;
    public static final int EXECUTIVE_SUMMARY_MAX = 300;
    public static final int SECTION_1_TO_3_MIN = 350;
    public static final int SECTION_1_TO_3_MAX = 450;
    public static final int SECTION_4_MIN = 250;
    public static final int SECTION_4_MAX = 350;
    public static final int SECTION_5_MIN = 350;
    public static final int SECTION_5_MAX = 450;
    public static final int SECTION_6_MIN = 350;
    public static final int SECTION_6_MAX = 450;
    public static final int SECTION_7_MIN = 250;
    public static final int SECTION_7_MAX = 350;
    public static final int SECTION_8_MIN = 200;
    public static final int SECTION_8_MAX = 300;
    public static final int SECTION_9_MIN = 400;
    public static final int SECTION_9_MAX = 500;
}
