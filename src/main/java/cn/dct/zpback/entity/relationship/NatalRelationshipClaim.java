package cn.dct.zpback.entity.relationship;

import lombok.Data;
import java.io.Serializable;
import java.util.List;

/**
 * Canonical Claim
 */
@Data
public class NatalRelationshipClaim implements Serializable {

    private static final long serialVersionUID = 1L;

    /** Claim ID */
    private String claimId;

    /** Canonical Name (38个之一) */
    private String canonicalName;

    /** Claim Class: CORE, COMPOSITE, FACTOR, TIMING, DERIVED */
    private String claimClass;

    /** 置信度: HIGH, MEDIUM, LOW, INSUFFICIENT */
    private String confidence;

    /** 敏感度: STANDARD, HIGH, VERY_HIGH */
    private String sensitivity;

    /** 主体: USER, PARTNER, RELATIONSHIP, MARRIAGE, FUTURE_EVENT */
    private String subject;

    /** 关系阶段: EARLY_ROMANCE, LONG_TERM_RELATIONSHIP, MARRIAGE, FUTURE_PARTNER, TIMING, CROSS_STAGE */
    private String relationshipStage;

    /** Claim摘要 */
    private String summary;

    /** 主要章节Owner */
    private String primaryChapterOwner;
}