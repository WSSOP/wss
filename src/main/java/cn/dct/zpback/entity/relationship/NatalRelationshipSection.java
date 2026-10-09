package cn.dct.zpback.entity.relationship;

import lombok.Data;
import java.io.Serializable;
import java.util.List;

/**
 * 婚恋报告章节
 */
@Data
public class NatalRelationshipSection implements Serializable {

    private static final long serialVersionUID = 1L;

    /** 章节ID (S0-S7) */
    private String sectionId;

    /** 章节标题 */
    private String title;

    /** 章节顺序 */
    private Integer order;

    /** 覆盖状态: FULL, PARTIAL, INSUFFICIENT */
    private String coverageState;

    /** 段落列表 */
    private List<NatalRelationshipParagraph> paragraphs;

    /** Claim列表 */
    private List<NatalRelationshipClaim> claims;

    /** 推荐列表 */
    private List<NatalRelationshipRecommendation> recommendations;

    /** 核心结论摘要 */
    private String takeaway;
}