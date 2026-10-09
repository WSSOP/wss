package cn.dct.zpback.entity.lifepattern;

import lombok.Data;
import java.io.Serializable;
import java.util.List;
import java.util.Map;

/**
 * 人生课题报告 - 星盘数据结构
 */
@Data
public class LifePatternChartData implements Serializable {
    private static final long serialVersionUID = 1L;

    /** D1 本命盘 */
    private LifePatternNatalChart d1;

    /** D7 子女分盘 */
    private LifePatternNatalChart d7;

    /** D9 分盘 */
    private LifePatternNatalChart d9;

    /** D24 学业分盘 */
    private LifePatternNatalChart d24;

    /** AK 行星 */
    private String akPlanet;

    /** Shadbala 六力 */
    private Map<String, LifePatternShadbalaData> shadbala;

    /** Dasha 大运列表 */
    private List<LifePatternDashaPeriod> dashaList;

    /** Nakshatra 数据 */
    private Map<String, LifePatternNakshatraData> nakshatra;
}
