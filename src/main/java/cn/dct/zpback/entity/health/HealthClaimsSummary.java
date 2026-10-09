package cn.dct.zpback.entity.health;

import lombok.Data;
import java.io.Serializable;

/**
 * Claim 摘要
 */
@Data
public class HealthClaimsSummary implements Serializable {

    private static final long serialVersionUID = 1L;

    /** 总数 */
    private Integer total;

    /** 支持数 */
    private Integer supported;

    /** 条件数 */
    private Integer conditional;

    /** 混合数 */
    private Integer mixed;

    /** 不足数 */
    private Integer insufficient;
}
