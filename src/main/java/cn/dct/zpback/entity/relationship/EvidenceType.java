package cn.dct.zpback.entity.relationship;

/**
 * Evidence 类型枚举 - 六类 Evidence Layer
 */
public enum EvidenceType {
    STRUCTURAL_EVIDENCE("结构证据"),
    SIGNIFICATOR_EVIDENCE("守护星证据"),
    STRENGTH_EVIDENCE("力量证据"),
    NAKSHATRA_EVIDENCE("宿曜证据"),
    MODIFIER_EVIDENCE("修正证据"),
    TIMING_EVIDENCE("时间证据");

    private final String description;

    EvidenceType(String description) {
        this.description = description;
    }

    public String getDescription() {
        return description;
    }
}
