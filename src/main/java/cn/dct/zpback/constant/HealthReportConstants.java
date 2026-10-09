package cn.dct.zpback.constant;

/**
 * 健康深度报告常量
 */
public final class HealthReportConstants {

    private HealthReportConstants() {}

    /** 报告类型 */
    public static final String REPORT_TYPE = "HEALTH_WELLBEING_REPORT";

    /** 报告状态: 生成中 */
    public static final String STATUS_GENERATING = "GENERATING";

    /** 报告状态: 完成 */
    public static final String STATUS_COMPLETED = "COMPLETED";

    /** 报告状态: 失败 */
    public static final String STATUS_FAILED = "FAILED";

    /** 覆盖状态: 完整 */
    public static final String COVERAGE_FULL = "FULL";

    /** 覆盖状态: 部分 */
    public static final String COVERAGE_PARTIAL = "PARTIAL";

    /** 覆盖状态: 不足 */
    public static final String COVERAGE_INSUFFICIENT = "INSUFFICIENT";

    /** Claim 状态: 支持 */
    public static final String CLAIM_SUPPORTED = "SUPPORTED";

    /** Claim 状态: 条件 */
    public static final String CLAIM_CONDITIONAL = "CONDITIONAL";

    /** Claim 状态: 混合 */
    public static final String CLAIM_MIXED = "MIXED";

    /** Claim 状态: 不足 */
    public static final String CLAIM_INSUFFICIENT = "INSUFFICIENT";

    /** 置信度: 高 */
    public static final String CONFIDENCE_HIGH = "HIGH";

    /** 置信度: 中 */
    public static final String CONFIDENCE_MEDIUM = "MEDIUM";

    /** 置信度: 低 */
    public static final String CONFIDENCE_LOW = "LOW";

    /** 强度: 常规关注 */
    public static final String INTENSITY_NORMAL = "NORMAL_ATTENTION";

    /** 强度: 加强自护 */
    public static final String INTENSITY_ELEVATED = "ELEVATED_SELF_CARE";

    /** 强度: 恢复优先 */
    public static final String INTENSITY_RECOVERY = "RECOVERY_PRIORITY";

    /** 最小推荐数 */
    public static final int MIN_RECOMMENDATIONS = 4;

    /** 最大推荐数 */
    public static final int MAX_RECOMMENDATIONS = 6;

    /** 最小 Claim 数 */
    public static final int MIN_CLAIMS = 10;

    /** 最大 Claim 数 */
    public static final int MAX_CLAIMS = 14;

    /** 最小 Evidence 数 */
    public static final int MIN_EVIDENCE = 10;

    /** 最大推荐窗口数 */
    public static final int MAX_TIMING_WINDOWS = 5;

    /** 章节Key: 核心结论 */
    public static final String SECTION_EXECUTIVE_SUMMARY = "EXECUTIVE_SUMMARY";

    /** 章节Key: 健康底盘 */
    public static final String SECTION_HEALTH_BASELINE = "HEALTH_BASELINE";

    /** 章节Key: 体能恢复 */
    public static final String SECTION_LOAD_AND_RECOVERY = "LOAD_AND_RECOVERY";

    /** 章节Key: 身体主题 */
    public static final String SECTION_BODY_ATTENTION = "BODY_ATTENTION";

    /** 章节Key: 心身联动 */
    public static final String SECTION_MIND_BODY_COUPLING = "MIND_BODY_COUPLING";

    /** 章节Key: 消耗模式 */
    public static final String SECTION_DEPLETION_AND_RHYTHM = "DEPLETION_AND_RHYTHM";

    /** 章节Key: 保护因素 */
    public static final String SECTION_PROTECTION_AND_RECOVERY = "PROTECTION_AND_RECOVERY";

    /** 章节Key: 阶段提醒 */
    public static final String SECTION_DASHA_HEALTH_WINDOWS = "DASHA_HEALTH_WINDOWS";

    /** 章节Key: 行动方案 */
    public static final String SECTION_ACTION_PLAN = "ACTION_PLAN";

    /** 推荐类型: 规律睡眠窗口 */
    public static final String REC_REGULAR_SLEEP_WINDOW = "REGULAR_SLEEP_WINDOW";

    /** 推荐类型: 工作休息边界 */
    public static final String REC_WORK_REST_BOUNDARY = "WORK_REST_BOUNDARY";

    /** 推荐类型: 温和可持续运动 */
    public static final String REC_GENTLE_SUSTAINABLE_MOVEMENT = "GENTLE_SUSTAINABLE_MOVEMENT";

    /** 推荐类型: 身体信号记录 */
    public static final String REC_BODY_SIGNAL_LOG = "BODY_SIGNAL_LOG";

    /** 推荐类型: 恢复缓冲 */
    public static final String REC_RECOVERY_BUFFER = "RECOVERY_BUFFER";

    /** 推荐类型: 情绪卸载 */
    public static final String REC_EMOTIONAL_UNLOADING = "EMOTIONAL_UNLOADING";

    /** 推荐类型: 定期健康复盘 */
    public static final String REC_ROUTINE_HEALTH_CHECK = "ROUTINE_HEALTH_CHECK";

    /** 段落角色: 直接回答 */
    public static final String ROLE_DIRECT_ANSWER = "DIRECT_ANSWER";

    /** 段落角色: 补充解释 */
    public static final String ROLE_SECONDARY_EXPLANATION = "SECONDARY_EXPLANATION";

    /** 段落角色: 时间提醒 */
    public static final String ROLE_TIMING_REMINDER = "TIMING_REMINDER";

    /** 身体主题: 精力与恢复 */
    public static final String THEME_ENERGY_AND_RECOVERY = "ENERGY_AND_RECOVERY";

    /** 身体主题: 睡眠与休息 */
    public static final String THEME_SLEEP_AND_REST = "SLEEP_AND_REST";

    /** 身体主题: 消化与食欲节律 */
    public static final String THEME_DIGESTION_AND_APPETITE_RHYTHM = "DIGESTION_AND_APPETITE_RHYTHM";

    /** 身体主题: 运动与肌肉紧张 */
    public static final String THEME_MOVEMENT_AND_MUSCLE_TENSION = "MOVEMENT_AND_MUSCLE_TENSION";

    /** 身体主题: 压力反应与过度驱动 */
    public static final String THEME_STRESS_REACTIVITY_AND_OVERDRIVE = "STRESS_REACTIVITY_AND_OVERDRIVE";

    /** 身体主题: 感官过载与恢复 */
    public static final String THEME_SENSORY_OVERLOAD_AND_RECOVERY = "SENSORY_OVERLOAD_AND_RECOVERY";
}
