package cn.dct.zpback.entity.career;

import lombok.Data;
import java.time.LocalDate;
import java.util.List;

/**
 * 事业报告时间窗口
 */
@Data
public class CareerTiming {

    private String timingId;

    /** 报告实例ID */
    private String reportInstanceId;

    /** Section ID */
    private String sectionId;

    /** 开始日期 */
    private LocalDate startDate;

    /** 结束日期 */
    private LocalDate endDate;

    /** 时间类型: DASHA_PHASE / COMBINED_WINDOW */
    private String timingType;

    /** 大运主星 */
    private String mahadasaLord;

    /** 小运主星 */
    private String antardashaLord;

    /** 阶段类型: FOUNDATION / ACCUMULATION / PRESSURE_TEST / TRANSITION / RESTRUCTURING / EXPANSION / CONSOLIDATION / VISIBILITY / LEADERSHIP */
    private String stageType;

    /** 主题 */
    private String theme;

    /** 方向: SUPPORTIVE / CHALLENGING / MIXED / TRANSITIONAL */
    private String direction;

    /** 强度 */
    private String intensity;

    /** 激活强度: DIRECT / STRONG / MODERATE / WEAK */
    private String activationStrength;

    /** 显现能力 */
    private String manifestationCapacity;

    /** 摩擦级别 */
    private String frictionLevel;

    /** 用户端标题 */
    private String userFacingTitle;

    /** 用户端摘要 */
    private String userFacingSummary;

    /** 激活的Claim IDs */
    private List<String> activatedClaimIds;

    /** 证据IDs */
    private List<String> evidenceIds;

    /** Yoga激活IDs */
    private List<String> yogaActivationIds;
}
