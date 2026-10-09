package cn.dct.zpback.constant;

/**
 * 学业深度解析报告常量
 * 符合 SEALED 文档《01_学业深度解析报告_最终开发总说明_程序员唯一执行版.md》V1.0.0
 */
public class EducationReportConstants {

    // ============================================================
    // 版本
    // ============================================================

    public static final String REPORT_TYPE = "EDUCATION_LEARNING_REPORT";
    public static final String DISPLAY_NAME = "学业深度解析报告";
    public static final String RULE_VERSION = "education_learning.v1.0.0";
    public static final String SCHEMA_VERSION = "education_learning.schema.v1.0.0";
    public static final String RENDERER_VERSION = "education_learning.renderer.v1.0.0";

    // ============================================================
    // 计算设置（§3.1）
    // ============================================================

    public static final String ZODIAC = "Sidereal";
    public static final String AYANAMSHA = "Lahiri";
    public static final String HOUSE_SYSTEM = "Whole Sign";
    public static final String NODE_TYPE = "Mean Node";
    public static final String DASHA_TYPE = "Vimshottari";
    /** 默认观察窗：生成时点起未来 36 个月 */
    public static final int TIMING_HORIZON_MONTHS = 36;
    /** 阶段窗口数量：3-5 个互不重叠 */
    public static final int MIN_TIMING_WINDOWS = 3;
    public static final int MAX_TIMING_WINDOWS = 5;

    // ============================================================
    // 硬阻断条件（§3.2）
    // ============================================================

    public static final String ERR_BIRTH_TIME_UNVERIFIED = "BIRTH_TIME_UNVERIFIED";
    public static final String ERR_D24_REQUIRED_DATA_MISSING = "D24_REQUIRED_DATA_MISSING";
    public static final String ERR_D24_LAGNA_UNSTABLE = "D24_LAGNA_UNSTABLE";
    public static final String ERR_DASHA_REQUIRED_DATA_MISSING = "DASHA_REQUIRED_DATA_MISSING";
    public static final String ERR_SOURCE_VERSION_MISSING = "SOURCE_VERSION_MISSING";
    public static final String ERR_INPUT_SCHEMA_INVALID = "INPUT_SCHEMA_INVALID";

    // ============================================================
    // 可降级条件（§3.3）
    // ============================================================

    public static final String FLAG_SHADBALA_UNAVAILABLE = "SHADBALA_UNAVAILABLE";
    public static final String FLAG_NAKSHATRA_UNAVAILABLE = "NAKSHATRA_UNAVAILABLE";
    public static final String FLAG_YOGA_ENGINE_UNAVAILABLE = "YOGA_ENGINE_UNAVAILABLE";
    public static final String FLAG_USER_CONTEXT_UNAVAILABLE = "USER_CONTEXT_UNAVAILABLE";

    // ============================================================
    // D1 证据 Family（§8.1 主 / §8.2 条件性）
    // ============================================================

    public static final String FAMILY_COGNITION = "COGNITION";
    public static final String FAMILY_MEANING_GUIDANCE = "MEANING_GUIDANCE";
    public static final String FAMILY_MIND_REGULATION = "MIND_REGULATION";
    public static final String FAMILY_KNOWLEDGE_BASE = "KNOWLEDGE_BASE";
    public static final String FAMILY_LEARNING_CONTAINER = "LEARNING_CONTAINER";
    public static final String FAMILY_INTELLIGENCE_APPLICATION = "INTELLIGENCE_APPLICATION";
    public static final String FAMILY_ADVANCED_LEARNING = "ADVANCED_LEARNING";

    public static final String FAMILY_EFFORT_SKILL = "EFFORT_SKILL";
    public static final String FAMILY_ROUTINE_COMPETITION = "ROUTINE_COMPETITION";
    public static final String FAMILY_DEPTH_TRANSFORMATION = "DEPTH_TRANSFORMATION";
    public static final String FAMILY_GOAL_NETWORK = "GOAL_NETWORK";
    public static final String FAMILY_WITHDRAWAL_IMMERSION = "WITHDRAWAL_IMMERSION";

    // ============================================================
    // Evidence 角色（§2 数据源角色表）
    // ============================================================

    public static final String ROLE_NATAL_PRIMARY = "NATAL_PRIMARY";
    public static final String ROLE_DOMAIN_CONFIRMATION = "DOMAIN_CONFIRMATION";
    public static final String ROLE_TIMING_ACTIVATION = "TIMING_ACTIVATION";
    public static final String ROLE_STYLE_MODIFIER = "STYLE_MODIFIER";
    public static final String ROLE_THEME_AMPLIFIER = "THEME_AMPLIFIER";
    public static final String ROLE_EXPRESSION_MODIFIER = "EXPRESSION_MODIFIER";

    // ============================================================
    // D24 解析动作（§9.2 / §14.1）
    // ============================================================

    public static final String D24_CONFIRMS = "CONFIRMS";
    public static final String D24_QUALIFIES = "QUALIFIES";
    public static final String D24_CONTRADICTS = "CONTRADICTS";
    public static final String REASON_INSUFFICIENT_NATAL_ORIGIN = "INSUFFICIENT_NATAL_ORIGIN";

    // ============================================================
    // Shadbala 表达修饰（§12）
    // ============================================================

    public static final String SHADBALA_STABLY_AVAILABLE = "STABLY_AVAILABLE";
    public static final String SHADBALA_AVAILABLE_WITH_EFFORT = "AVAILABLE_WITH_EFFORT";
    public static final String SHADBALA_INCONSISTENT_ACCESS = "INCONSISTENT_ACCESS";
    public static final String SHADBALA_UNKNOWN = "UNKNOWN";

    // ============================================================
    // Yoga 白名单（§13）
    // ============================================================

    public static final String[] YOGA_WHITELIST = {
        "SARASWATI_YOGA",
        "BHADRA_MAHAPURUSHA_YOGA",
        "BUDHA_ADITYA_YOGA",
        "HAMSA_MAHAPURUSHA_YOGA",
        "GAJAKESARI_YOGA",
        "EDUCATION_LORD_LINK"
    };

    // ============================================================
    // Claim 状态与置信度（§6）
    // ============================================================

    public static final String STATE_SUPPORTED = "SUPPORTED";
    public static final String STATE_MIXED = "MIXED";
    public static final String STATE_INSUFFICIENT = "INSUFFICIENT";
    public static final String STATE_SUPPRESSED = "SUPPRESSED";

    public static final String CONF_HIGH = "HIGH";
    public static final String CONF_MEDIUM = "MEDIUM";
    public static final String CONF_LOW = "LOW";

    /** 低证据缩短策略（§4 证据总法则） */
    public static final String SECTION_MODE_SHORTEN_ON_LOW_EVIDENCE = "SHORTEN_ON_LOW_EVIDENCE";
    public static final String SECTION_MODE_FULL = "FULL";

    // ============================================================
    // 永久禁用表达（§1.3）
    // ============================================================

    public static final String[] FORBIDDEN_WORDS = {
        "一定考上", "一定不会考上", "一定能通过", "一定不会通过",
        "保证录取", "必然录取", "拿到Offer", "拿offer",
        "智商高", "智商低", "天生学霸", "没有读书命", "读书命",
        "智力低下", "学习障碍", "多动症", "ADHD",
        "最高学历", "学历上限", "排名第几", "考试分数",
        "退学", "留级"
    };

    /** 禁断结论路由提示（§1.2） */
    public static final String NEUTRAL_DISCLAIMER =
            "本报告用于理解你的学习机制、优势、阻力与适合的方法环境，" +
            "不判断考试通过与否、录取结果、学历上限或智力水平。" +
            "涉及学习障碍、情绪困扰等请寻求合格专业评估。" +
            "报告内容不替代教育、心理或职业规划专业意见。";

    // ============================================================
    // 固定章节（§5 章节框架：摘要 + 8 章 + Evidence Fold）
    // ============================================================

    public static final String SECTION_SUMMARY = "SUMMARY";
    public static final String SECTION_CH1_LEARNING_MECHANISM = "LEARNING_MECHANISM";
    public static final String SECTION_CH2_STRENGTHS = "STRENGTHS";
    public static final String SECTION_CH3_FRICTION_LOOPS = "FRICTION_LOOPS";
    public static final String SECTION_CH4_METHOD_ENVIRONMENT = "METHOD_ENVIRONMENT";
    public static final String SECTION_CH5_SUBJECT_DIRECTIONS = "SUBJECT_DIRECTIONS";
    public static final String SECTION_CH6_LEARNING_ROUTES = "LEARNING_ROUTES";
    public static final String SECTION_CH7_TIMING_WINDOWS = "TIMING_WINDOWS";
    public static final String SECTION_CH8_ACTION_PLAN = "ACTION_PLAN";

    /** 章节 key → 标题 */
    public static final String[][] SECTION_DEFS = {
        {SECTION_SUMMARY, "开篇摘要"},
        {SECTION_CH1_LEARNING_MECHANISM, "你是怎样把知识真正学进去的"},
        {SECTION_CH2_STRENGTHS, "你最容易建立优势的地方"},
        {SECTION_CH3_FRICTION_LOOPS, "为什么有时很能学，有时却容易卡住"},
        {SECTION_CH4_METHOD_ENVIRONMENT, "什么样的方法和环境更适合你"},
        {SECTION_CH5_SUBJECT_DIRECTIONS, "你更适合深入哪些知识方向"},
        {SECTION_CH6_LEARNING_ROUTES, "考试、研究、项目还是证书：哪条路更适合你"},
        {SECTION_CH7_TIMING_WINDOWS, "当前及未来36个月的学习阶段"},
        {SECTION_CH8_ACTION_PLAN, "给你的学习行动方案"},
    };

    /** 正文目标篇幅（统一 3000-4000 字） */
    public static final int BODY_TARGET_MIN = 3000;
    public static final int BODY_TARGET_MAX = 4000;
    /** 优势最多 4 项（§5 第 2 章） */
    public static final int MAX_STRENGTHS = 4;
    /** 知识方向 2-4 项（§5 第 5 章） */
    public static final int MIN_DIRECTIONS = 2;
    public static final int MAX_DIRECTIONS = 4;
    /** 主路线 1 个 + 次路线最多 2 个（§5 第 6 章） */
    public static final int MAX_SECONDARY_ROUTES = 2;
    /** 建议 3-5 条（§5 第 8 章） */
    public static final int MIN_RECOMMENDATIONS = 3;
    public static final int MAX_RECOMMENDATIONS = 5;
    /** Nakshatra 正文最多展示 4 个修饰（§11.1） */
    public static final int MAX_VISIBLE_NAKSHATRA = 4;
}
