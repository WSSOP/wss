package cn.dct.zpback.entity.relationship;

import java.io.Serializable;

/**
 * Claim 敏感度枚举
 */
public enum ClaimSensitivity implements Serializable {
    STANDARD("标准"),
    HIGH("高敏感"),
    VERY_HIGH("极高敏感");

    private final String description;

    ClaimSensitivity(String description) {
        this.description = description;
    }

    public String getDescription() {
        return description;
    }
}
