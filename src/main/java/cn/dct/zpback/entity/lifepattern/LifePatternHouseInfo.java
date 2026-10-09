package cn.dct.zpback.entity.lifepattern;

import lombok.Data;
import java.io.Serializable;

/**
 * 人生课题报告 - 宫位信息
 */
@Data
public class LifePatternHouseInfo implements Serializable {
    private static final long serialVersionUID = 1L;

    /** 星座 */
    private String sign;

    /** 宫主 */
    private String lord;

    /** 度数 */
    private Double degree;
}
