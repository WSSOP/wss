package cn.dct.zpback.entity.marriage;

import lombok.Data;
import java.io.Serializable;
import java.time.LocalDate;

/**
 * 时间窗口
 */
@Data
public class MarriageTiming implements Serializable {

    private static final long serialVersionUID = 1L;

    /** 时间窗口ID */
    private String timingId;

    /** 窗口类型: MEETING_WINDOW, RELATIONSHIP_START_WINDOW, COMMITMENT_WINDOW, MARRIAGE_WINDOW, RELATIONSHIP_REASSESSMENT_WINDOW, RELATIONSHIP_PRESSURE_WINDOW */
    private String windowType;

    /** 开始日期 */
    private LocalDate startAt;

    /** 结束日期 */
    private LocalDate endAt;

    /** 渲染精度: MONTH */
    private String rendererPrecision;

    /** Dasha 背景 */
    private String dashaContext;

    /** 激活目标 */
    private String[] activationTargets;

    /** 正式化支持 */
    private String[] formalizationSupport;

    /** 压力上下文 */
    private String pressureContext;

    /** 激活相关性: HIGH, MODERATE, LIMITED */
    private String activationRelevance;

    /** 置信度 */
    private String claimConfidence;

    /** 焦点描述 */
    private String focus;
}
