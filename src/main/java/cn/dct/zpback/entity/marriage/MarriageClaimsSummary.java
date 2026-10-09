package cn.dct.zpback.entity.marriage;

import lombok.Data;
import java.io.Serializable;

/**
 * Claim 摘要
 */
@Data
public class MarriageClaimsSummary implements Serializable {

    private static final long serialVersionUID = 1L;

    /** 总数 */
    private Integer total;

    /** 支持数 */
    private Integer supported;

    /** 高置信度数 */
    private Integer highConfidence;

    /** 中置信度数 */
    private Integer mediumConfidence;

    /** 低置信度数 */
    private Integer lowConfidence;
}
