package cn.dct.zpback.entity.health;

import lombok.Data;
import java.io.Serializable;

/**
 * 段落
 */
@Data
public class HealthParagraph implements Serializable {

    private static final long serialVersionUID = 1L;

    /** 段落ID */
    private String paragraphId;

    /** 段落角色: DIRECT_ANSWER, SECONDARY_EXPLANATION, TIMING_REMINDER */
    private String role;

    /** 段落文本 */
    private String text;
}
