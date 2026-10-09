package cn.dct.zpback.entity.lifepattern;

import lombok.Data;
import java.io.Serializable;

/**
 * 人生课题报告 - Dasha 大运周期
 */
@Data
public class LifePatternDashaPeriod implements Serializable {
    private static final long serialVersionUID = 1L;

    /** 大运 */
    private String mahadasha;

    /** 小运 */
    private String antardasha;

    /** 开始日期 */
    private String startDate;

    /** 结束日期 */
    private String endDate;

    /** 是否当前 */
    private Boolean isCurrent;
}
