package cn.dct.zpback.entity.marriage;

import lombok.Data;
import java.io.Serializable;

/**
 * 婚恋段落
 */
@Data
public class MarriageParagraph implements Serializable {

    private static final long serialVersionUID = 1L;

    /** 段落ID */
    private String paragraphId;

    /** 段落角色: DIRECT_ANSWER, SECONDARY_EXPLANATION, TIMING_REMINDER */
    private String role;

    /** 段落文本 */
    private String text;

    /** 来源 Claim 引用 */
    private String[] sourceClaimRefs;
}
