package cn.dct.zpback.entity.relationship;

import java.io.Serializable;

/**
 * D1×D9 关系枚举
 */
public enum D1D9Relation implements Serializable {
    REINFORCED("强化"),
    MATURED_ADAPTED("成熟适应"),
    MITIGATED("缓解"),
    TENSION("张力"),
    INSUFFICIENT("证据不足");

    private final String description;

    D1D9Relation(String description) {
        this.description = description;
    }

    public String getDescription() {
        return description;
    }
}
