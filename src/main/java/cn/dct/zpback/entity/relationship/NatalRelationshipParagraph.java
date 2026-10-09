package cn.dct.zpback.entity.relationship;

import lombok.Data;
import java.io.Serializable;

/**
 * 段落
 */
@Data
public class NatalRelationshipParagraph implements Serializable {

    private static final long serialVersionUID = 1L;

    /** 段落ID */
    private String paragraphId;

    /** 段落角色: DIRECT_ANSWER, SECONDARY_EXPLANATION, THESIS, TAKEAWAY */
    private String role;

    /** 段落文本 */
    private String text;

    /** 来源Claim ID列表 */
    private String[] sourceClaimRefs;
}