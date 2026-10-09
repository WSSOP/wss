package cn.dct.zpback.entity.lifepattern;

import lombok.Data;
import java.io.Serializable;
import java.util.Map;

/**
 * 人生课题报告 - D1/D9 本命盘数据结构
 */
@Data
public class LifePatternNatalChart implements Serializable {
    private static final long serialVersionUID = 1L;

    /** 上升星座 */
    private String ascendantSign;

    /** 上升度数 */
    private Double ascendant;

    /** 行星位置 */
    private Map<String, LifePatternPlanetPosition> planets;

    /** 宫位信息 */
    private Map<String, LifePatternHouseInfo> houses;
}
