package cn.dct.zpback.entity.relationship;

import java.io.Serializable;

/**
 * Claim 置信度枚举
 */
public enum ClaimConfidence implements Serializable {
    HIGH("高置信"),
    MEDIUM("中置信"),
    LOW("低置信"),
    INSUFFICIENT("证据不足");

    private final String description;

    ClaimConfidence(String description) {
        this.description = description;
    }

    public String getDescription() {
        return description;
    }
}
