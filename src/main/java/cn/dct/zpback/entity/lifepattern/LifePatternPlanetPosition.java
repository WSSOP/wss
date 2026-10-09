package cn.dct.zpback.entity.lifepattern;

import lombok.Data;
import java.io.Serializable;

/**
 * 人生课题报告 - 行星位置
 */
@Data
public class LifePatternPlanetPosition implements Serializable {
    private static final long serialVersionUID = 1L;

    /** 星座 */
    private String sign;

    /** 宫位 */
    private Integer house;

    /** 度数 */
    private Double degree;

    /** 逆行 */
    private Boolean retrograde;

    /** 尊严 */
    private String dignity;

    /** Vargottama */
    private Boolean vargottama;
}
