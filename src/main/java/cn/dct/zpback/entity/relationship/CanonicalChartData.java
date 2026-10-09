package cn.dct.zpback.entity.relationship;

import lombok.Data;
import java.io.Serializable;
import java.util.List;
import java.util.Map;

/**
 * 规范星盘数据 - Canonical Astrology Data
 * 输入层：包含 D1/D9/Shadbala/Nakshatra/Dasha 等原始数据
 */
@Data
public class CanonicalChartData implements Serializable {

    private static final long serialVersionUID = 1L;

    /** D1 本命盘数据 */
    private NatalChart d1;

    /** D9 婚姻盘数据 */
    private NatalChart d9;

    /** 7 Karaka Jaimini */
    private Map<String, Object> karaka;

    /** AK = 度数最高行星 */
    private String akPlanet;

    /** DK = 度数最低行星 */
    private String dkPlanet;

    /** D1 行星 Shadbala */
    private Map<String, ShadbalaData> shadbala;

    /** D1 行星 Nakshatra */
    private Map<String, NakshatraData> nakshatra;

    /** Vimshottari Dasha 大运 */
    private List<DashaPeriod> dashaList;

    /** UL 婚姻显现层 */
    private ULData ul;

    /**
     * 本命盘/分盘通用结构
     */
    @Data
    public static class NatalChart implements Serializable {
        private static final long serialVersionUID = 1L;
        private Double ascendant;
        private String ascendantSign;
        private Map<String, PlanetPosition> planets;
        private Map<String, HouseInfo> houses;
    }

    /**
     * 行星位置
     */
    @Data
    public static class PlanetPosition implements Serializable {
        private static final long serialVersionUID = 1L;
        private String sign;
        private Double degree;
        private Integer house;
        private String dignity;
        private Boolean retrograde;
    }

    /**
     * 宫位信息
     */
    @Data
    public static class HouseInfo implements Serializable {
        private static final long serialVersionUID = 1L;
        private String sign;
        private String lord;
    }

    /**
     * Shadbala 数据
     */
    @Data
    public static class ShadbalaData implements Serializable {
        private static final long serialVersionUID = 1L;
        private String executionCapacity;
        private Double score;
        private Integer roleCount = 1;
    }

    /**
     * Nakshatra 数据
     */
    @Data
    public static class NakshatraData implements Serializable {
        private static final long serialVersionUID = 1L;
        private String name;
        private List<String> behavioralCore;
        private Integer pada;
    }

    /**
     * Dasha 大运周期
     */
    @Data
    public static class DashaPeriod implements Serializable {
        private static final long serialVersionUID = 1L;
        private String mahadasha;
        private String antardasha;
        private String startDate;
        private String endDate;
    }

    /**
     * UL 婚姻显现层数据
     */
    @Data
    public static class ULData implements Serializable {
        private static final long serialVersionUID = 1L;
        private String ulSign;
        private Integer ulHouse;
        private String ulLord;
        private String rawSign;
        private Boolean exceptionApplied;
        private String convention = "ARUDHA_1_7_EXCEPTION_10_4";
        private String secondFromUL;
    }
}
