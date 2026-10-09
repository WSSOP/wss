package cn.dct.zpback.entity.health;

import lombok.Data;
import java.io.Serializable;
import java.time.LocalDate;

/**
 * 时间窗口
 */
@Data
public class HealthTiming implements Serializable {

    private static final long serialVersionUID = 1L;

    /** 时间窗口ID */
    private String timingId;

    /** 时间段描述 */
    private String period;

    /** 主星 */
    private String lord;

    /** 开始日期 */
    private LocalDate startDate;

    /** 结束日期 */
    private LocalDate endDate;

    /** 强度: NORMAL_ATTENTION, ELEVATED_SELF_CARE, RECOVERY_PRIORITY */
    private String intensity;

    /** 焦点描述 */
    private String focus;
}
