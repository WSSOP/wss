package cn.dct.zpback.entity.health;

import lombok.Data;
import java.io.Serializable;

/**
 * Evidence 证据
 */
@Data
public class HealthEvidence implements Serializable {

    private static final long serialVersionUID = 1L;

    /** Evidence ID */
    private String evidenceId;

    /** 星盘来源: D1, D9, D27, D30, SHADBALA, NAKSHATRA, HEALTH_YOGA, DASHA */
    private String chartSource;

    /** Evidence 类型 */
    private String evidenceKind;

    /** 锚点Key */
    private String anchorKey;

    /** 独立簇Key */
    private String independenceClusterKey;

    /** 依赖状态: INDEPENDENT, DEPENDENT */
    private String dependencyStatus;

    /** Evidence 状态: ACTIVE, INACTIVE */
    private String evidenceStatus;

    /** Evidence 通道: PRIMARY, VALIDATING, SUPPORTING */
    private String evidenceLane;

    /** 描述 */
    private String description;
}
