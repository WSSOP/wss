package cn.dct.zpback.entity.relationship;

import java.io.Serializable;

/**
 * Claim 类别枚举
 */
public enum ClaimClass implements Serializable {
    CORE("核心"),
    COMPOSITE("复合"),
    FACTOR("因子"),
    DERIVED("衍生"),
    TIMING("时间");

    private final String description;

    ClaimClass(String description) {
        this.description = description;
    }

    public String getDescription() {
        return description;
    }
}
