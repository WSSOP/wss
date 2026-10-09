package cn.dct.zpback.entity.relationship;

import lombok.Data;
import java.io.Serializable;
import java.util.List;

/**
 * Evidence - 证据引擎输出
 */
@Data
public class Evidence implements Serializable {

    private static final long serialVersionUID = 1L;

    /** 证据ID */
    private String evidenceId;

    /** 证据类型 */
    private EvidenceType type;

    /** 来源行星 */
    private String sourcePlanet;

    /** 来源宫位 */
    private String sourceHouse;

    /** 证据描述 */
    private String description;

    /** 证据权重 (0.0 - 1.0) */
    private Double weight;

    /** 独立家族计数 */
    private Integer independentFamilyCount;

    /** 目标 Claim Domain */
    private String targetDomain;

    /** 证据角色 */
    private String role;

    /** 是否为主证据 */
    private Boolean primary;

    /** 引用计数 */
    private Integer referenceCount = 1;
}
