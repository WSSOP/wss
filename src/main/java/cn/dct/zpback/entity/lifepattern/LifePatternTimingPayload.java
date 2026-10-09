package cn.dct.zpback.entity.lifepattern;

import lombok.Data;
import java.io.Serializable;

/**
 * 人生课题报告 - 时间窗口
 */
@Data
public class LifePatternTimingPayload implements Serializable {
    private static final long serialVersionUID = 1L;

    /** 时间类型: MEETING_WINDOW / RELATIONSHIP_START_WINDOW / COMMITMENT_WINDOW / ... */
    private String timingType;

    /** 时间描述 */
    private String description;

    /** 开始日期 */
    private String startDate;

    /** 结束日期 */
    private String endDate;

    /** 激活的 Claim ID */
    private String activatedClaimId;

    /** Dasha Lord */
    private String dashaLord;
}
