package cn.dct.zpback.constant;

/**
 * 子女与养育深度报告常量
 * 符合 SEALED 文档《01_子女与养育深度报告_最终开发总说明_程序员唯一执行版.md》
 */
public class NatalChildrenConstants {

    // ============================================================
    // 版本
    // ============================================================

    public static final String REPORT_TYPE = "NATAL_CHILDREN_REPORT";
    public static final String DISPLAY_NAME = "子女与养育深度报告";
    public static final String RULE_VERSION = "natal_children.v1.0.0";
    public static final String SCHEMA_VERSION = "natal_children.schema.v1.0.0";
    public static final String RENDERER_VERSION = "natal_children.renderer.v1.0.0";

    // ============================================================
    // 状态机
    // ============================================================

    public static final String STATUS_CREATED = "CREATED";
    public static final String STATUS_DATA_VALIDATING = "DATA_VALIDATING";
    public static final String STATUS_EVIDENCE_RESOLVING = "EVIDENCE_RESOLVING";
    public static final String STATUS_CLAIM_RESOLVING = "CLAIM_RESOLVING";
    public static final String STATUS_RENDERING = "RENDERING";
    public static final String STATUS_QUALITY_CHECKING = "QUALITY_CHECKING";
    public static final String STATUS_COMPLETED = "COMPLETED";

    public static final String STATUS_FAILED_DATA = "FAILED_DATA";
    public static final String STATUS_FAILED_RULE_ENGINE = "FAILED_RULE_ENGINE";
    public static final String STATUS_FAILED_RENDERER = "FAILED_RENDERER";
    public static final String STATUS_FAILED_PERSISTENCE = "FAILED_PERSISTENCE";

    // ============================================================
    // 降级标志
    // ============================================================

    public static final String FLAG_D9_UNAVAILABLE = "D9_UNAVAILABLE";
    public static final String FLAG_PK_UNAVAILABLE = "PK_UNAVAILABLE";
    public static final String FLAG_YOGA_UNAVAILABLE = "YOGA_UNAVAILABLE";
    public static final String FLAG_NAKSHATRA_UNAVAILABLE = "NAKSHATRA_UNAVAILABLE";
    public static final String FLAG_SHADBALA_UNAVAILABLE = "SHADBALA_UNAVAILABLE";
    public static final String FLAG_DASHA_UNAVAILABLE = "DASHA_UNAVAILABLE";

    // ============================================================
    // Evidence Lane
    // ============================================================

    public static final String LANE_NATAL_PRIMARY = "NATAL_PRIMARY";
    public static final String LANE_CHILDREN_SPECIALIST = "CHILDREN_SPECIALIST";
    public static final String LANE_MATURITY_VALIDATION = "MATURITY_VALIDATION";
    public static final String LANE_ROLE_SUPPORT = "ROLE_SUPPORT";
    public static final String LANE_PATTERN_SUPPORT = "PATTERN_SUPPORT";
    public static final String LANE_STYLE_QUALIFIER = "STYLE_QUALIFIER";
    public static final String LANE_EXPRESSION_MODIFIER = "EXPRESSION_MODIFIER";
    public static final String LANE_TIMING_ACTIVATION = "TIMING_ACTIVATION";

    // ============================================================
    // 行星功能条件
    // ============================================================

    public static final String CONDITION_SUPPORTIVE = "SUPPORTIVE";
    public static final String CONDITION_CONSTRAINING = "CONSTRAINING";
    public static final String CONDITION_AMBIVALENT = "AMBIVALENT";
    public static final String CONDITION_CONTEXT_ONLY = "CONTEXT_ONLY";

    // ============================================================
    // Shadbala 修饰
    // ============================================================

    public static final String SHADBALA_BELOW_REQUIRED = "BELOW_REQUIRED";
    public static final String SHADBALA_NEAR_REQUIRED = "NEAR_REQUIRED";
    public static final String SHADBALA_ABOVE_REQUIRED = "ABOVE_REQUIRED";
    public static final String SHADBALA_STRONG = "STRONG";

    // ============================================================
    // Timing 窗口分类
    // ============================================================

    public static final String WINDOW_SUPPORTIVE = "SUPPORTIVE_WINDOW";
    public static final String WINDOW_MIXED = "MIXED_WINDOW";
    public static final String WINDOW_PREPARATION = "PREPARATION_WINDOW";
    public static final String WINDOW_LOW_ACTIVATION = "LOW_ACTIVATION";
    public static final String WINDOW_INSUFFICIENT = "INSUFFICIENT";

    // ============================================================
    // 12 固定章节
    // ============================================================

    public static final String SECTION_EXECUTIVE_SUMMARY = "EXECUTIVE_SUMMARY";
    public static final String SECTION_CHILDREN_PATH = "CHILDREN_PATH";
    public static final String SECTION_PATH_SUPPORT_AND_PRESSURE = "PATH_SUPPORT_AND_PRESSURE";
    public static final String SECTION_PARENT_ROLE = "PARENT_ROLE";
    public static final String SECTION_NURTURING_STRENGTHS = "NURTURING_STRENGTHS";
    public static final String SECTION_PARENTING_OVERUSE = "PARENTING_OVERUSE";
    public static final String SECTION_EMOTIONAL_SECURITY_AND_COMMUNICATION = "EMOTIONAL_SECURITY_AND_COMMUNICATION";
    public static final String SECTION_INTERACTION_ARCHETYPE = "INTERACTION_ARCHETYPE";
    public static final String SECTION_COPARENTING_ENVIRONMENT = "COPARENTING_ENVIRONMENT";
    public static final String SECTION_PARENTAL_GROWTH = "PARENTAL_GROWTH";
    public static final String SECTION_FIVE_YEAR_TIMING = "FIVE_YEAR_TIMING";
    public static final String SECTION_ACTION_GUIDE = "ACTION_GUIDE";

    // ============================================================
    // 禁用词（安全边界 16.2）
    // ============================================================

    public static final String[] FORBIDDEN_WORDS = {
        "克子", "无后", "子女劫", "前世欠债", "业力惩罚",
        "保证怀孕", "必然怀孕", "一定会怀孕",
        "不孕", "不育", "流产", "胎儿健康", "胎儿畸形",
        "生男孩", "生女孩", "儿子", "女儿",
        "死胎", "夭折", "克夫", "克妻"
    };

    /** 禁止进入 renderer 系统提示词的注入句式 */
    public static final String NEUTRAL_DISCLAIMER =
            "本报告用于自我探索，描述父母盘所显示的互动倾向，不能替代医疗、心理、法律或生育专业意见。" +
            "报告不判断生育能力、子女性别数量或孩子本人的命运。";
}
