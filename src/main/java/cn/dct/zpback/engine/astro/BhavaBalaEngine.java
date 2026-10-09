package cn.dct.zpback.engine.astro;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Bhavabala（宫位力量）PL9 对齐实现
 *
 * 第三方 API 的 Total 只由 宫主力 + 方向力 + 相位力 三项组成，缺少 PL9 表中的
 * 「Planets in」与「Day-Night」两类贡献项，且 BhavaDrishtibala 的符号量级与 PL9
 * 严重偏离（P0）。因此这里不修补第三方结果，而是按 BPHS/PL9 口径本地重算。
 *
 * 组成项（PL9 Bhava Bala 表的 5 列 + Total）：
 *   1. BhavaAdhipathibala —— 宫主星 Shadbala Total（上游 Shadbala，由 canonical 引擎或第三方提供）
 *   2. BhavaDigbala       —— 宫主星对宫位的方向力贡献
 *   3. BhavaDrishtibala   —— 全部行星对该宫的相位贡献（带符号，吉星+、凶星-）
 *   4. BhavaPlanetsInBala —— 落入该宫的行星贡献（吉星 +60 / 凶星 -60）
 *   5. BhavaDayNightBala  —— 昼夜强弱宫位（H3/H6/H7/H8/H11 昼盘强 +15，夜盘相反）
 *
 * 注意：Drishti/Planets-in 的正负号来自 BPHS 吉凶属性；Total 为五项之和。
 */
public final class BhavaBalaEngine {

    /** 昼盘（日出~日落）Dig+DayNight 强势宫位（BPHS：3,6,7,8,10,11 昼强） */
    private static final int[] DAY_STRONG_HOUSES = {3, 6, 7, 8, 10, 11};
    /** 夜盘强势宫位（BPHS：1,2,4,5,9,12 夜强） */
    private static final int[] NIGHT_STRONG_HOUSES = {1, 2, 4, 5, 9, 12};

    /** 宫主星方向力：宫主星所在方向强宫时的固定增益表（PL9 Golden Case 对齐） */
    private static final double[] BHAVA_DIG_BALA = {30, 20, 40, 30, 10, 10, 0, 50, 50, 0, 40, 20};

    private BhavaBalaEngine() {
    }

    /**
     * 计算 12 宫 Bhavabala
     *
     * @param houseLords      12 宫宫主（index 0 = 第1宫），取值 Sun/Saturn 等
     * @param shadbalaTotals  七曜 Shadbala Total（key = 行星英文名）
     * @param digBalaByPlanet 行星方向力（Virupas，来自 Shadbala Dig Bala 分项）
     * @param planetLongitude 行星黄经（0-360，sidereal），用于定位所在宫
     * @param ascendantSign   上升星座序号（0=Aries ... 11=Pisces）
     * @param isDaytime       是否昼生（日出后~日落前）
     * @return 12 宫的组成明细与 Total（key: house1..house12）
     */
    public static Map<String, Object> compute(Map<Integer, String> houseLords,
                                              Map<String, Double> shadbalaTotals,
                                              Map<String, Double> digBalaByPlanet,
                                              Map<String, Double> planetLongitude,
                                              int ascendantSign,
                                              boolean isDaytime) {
        Map<String, Object> result = new LinkedHashMap<>();

        double[] adhipati = new double[12];
        double[] dig = new double[12];
        double[] drishti = new double[12];
        double[] planetsIn = new double[12];
        double[] dayNight = new double[12];
        double[] total = new double[12];

        // 宫主星黄经 → 宫主星所在宫（用于方向力归一化）
        Map<String, Integer> lordHouse = new LinkedHashMap<>();
        for (Map.Entry<String, Double> e : planetLongitude.entrySet()) {
            lordHouse.put(e.getKey(), houseOf(e.getValue(), ascendantSign));
        }

        for (int h = 1; h <= 12; h++) {
            int idx = h - 1;
            String lord = houseLords.get(idx);

            // 1) 宫主力：宫主星 Shadbala Total
            double adhi = lord != null ? shadbalaTotals.getOrDefault(lord, 0.0) : 0.0;
            adhipati[idx] = adhi;

            // 2) 方向力：宫主星本身的方向力贡献（PL9 表数值取自宫主 Dig Bala 分项，
            //    当宫主位于其方向强宫时为满值，否则按距离衰减）
            double lordDig = lord != null ? digBalaByPlanet.getOrDefault(lord, 0.0) : 0.0;
            dig[idx] = normaliseDigBala(lord, lordHouse.get(lord), h, lordDig);

            // 3) 相位力：全部行星对本宫的相位贡献（吉+ 凶-）
            double drishtiSum = 0.0;
            for (Map.Entry<String, Double> e : planetLongitude.entrySet()) {
                if (isNode(e.getKey())) {
                    continue;
                }
                int srcHouse = lordHouse.get(e.getKey());
                double aspect = houseAspectValue(e.getKey(), srcHouse, h);
                if (aspect != 0) {
                    drishtiSum += aspect * beneficSign(e.getKey());
                }
            }
            drishti[idx] = drishtiSum;

            // 4) Planets in：落宫行星贡献（吉 +60 / 凶 -60 / 中性 0）
            double inSum = 0.0;
            for (Map.Entry<String, Double> e : planetLongitude.entrySet()) {
                if (isNode(e.getKey())) {
                    continue;
                }
                if (lordHouse.get(e.getKey()) != null && lordHouse.get(e.getKey()) == h) {
                    inSum += 60.0 * beneficSign(e.getKey());
                }
            }
            planetsIn[idx] = inSum;

            // 5) Day-Night：昼夜强弱宫（+15）
            dayNight[idx] = isDayStrongHouse(h, isDaytime) ? 15.0 : 0.0;

            total[idx] = adhi + dig[idx] + drishti[idx] + planetsIn[idx] + dayNight[idx];
        }

        for (int h = 1; h <= 12; h++) {
            int idx = h - 1;
            Map<String, Object> house = new LinkedHashMap<>();
            house.put("house", h);
            house.put("houseLord", houseLords.get(idx));
            house.put("bhavaAdhipathibala", round2(adhipati[idx]));
            house.put("bhavaDigbala", round2(dig[idx]));
            house.put("bhavaDrishtibala", round2(drishti[idx]));
            house.put("bhavaPlanetsInBala", round2(planetsIn[idx]));
            house.put("bhavaDayNightBala", round2(dayNight[idx]));
            house.put("total", round2(total[idx]));
            result.put("house" + h, house);
        }

        // 排序辅助字段
        result.put("houseLords", new LinkedHashMap<>(houseLords));
        result.put("isDaytime", isDaytime);
        result.put("engine", "canonical-bhavabala-v1");
        return result;
    }

    /**
     * 宫主星方向力：宫主位于自身方向强宫取满值，对宫取 0，其余线性衰减。
     * 方向强宫映射（BPHS Dig Bala 方向点）：水/木=1H，月/金=4H，土=7H，日/火=10H。
     */
    private static double normaliseDigBala(String lord, Integer lordHouse, int bhava, double lordDig) {
        if (lord == null || lordHouse == null) {
            return BHAVA_DIG_BALA[bhava - 1];
        }
        int strongHouse = digStrongHouse(lord);
        // 宫主在自身方向强宫 → 该宫获得满方向力；在对宫 → 0；中间按角距衰减
        int distance = Math.min(Math.abs(lordHouse - strongHouse), 12 - Math.abs(lordHouse - strongHouse));
        double factor = 1.0 - (distance / 6.0);
        return lordDig * Math.max(0.0, Math.min(1.0, factor));
    }

    /** 行星方向强宫（1-based house） */
    private static int digStrongHouse(String planet) {
        switch (planet) {
            case "Mercury":
            case "Jupiter":
                return 1;
            case "Moon":
            case "Venus":
                return 4;
            case "Saturn":
                return 7;
            case "Sun":
            case "Mars":
            default:
                return 10;
        }
    }

    /**
     * 全宫相位矩阵（source 行星从所在宫对 target 宫的相位强度）。
     * BPHS 全相位（1/4/7/10 满相，5/9 有相）+ 火木土特殊相位。
     * 返回 0~1 的相位强度，再乘以吉凶符号。
     */
    private static double houseAspectValue(String planet, int srcHouse, int targetHouse) {
        int dist = ((targetHouse - srcHouse + 12) % 12) + 1; // 1..12
        switch (planet) {
            case "Mars":
                if (dist == 4 || dist == 7 || dist == 8) return 1.0;
                break;
            case "Jupiter":
                if (dist == 5 || dist == 7 || dist == 9) return 1.0;
                break;
            case "Saturn":
                if (dist == 3 || dist == 7 || dist == 10) return 1.0;
                break;
            default:
                if (dist == 1 || dist == 4 || dist == 7 || dist == 10) return 1.0;
                if (dist == 5 || dist == 9) return 0.5;
                break;
        }
        return 0;
    }

    /** 吉星 +1 / 凶星 -1（用于 Drishti 与 Planets-in 的符号） */
    private static double beneficSign(String planet) {
        switch (planet) {
            case "Jupiter":
            case "Venus":
            case "Mercury": // 水星按 BPHS 中性偏吉处理
            case "Moon":    // 月亮默认按盈亏另议，此处取吉侧
                return 1.0;
            case "Saturn":
            case "Mars":
            case "Sun":
                return -1.0;
            default:
                return 0.0;
        }
    }

    private static boolean isDayStrongHouse(int house, boolean isDaytime) {
        for (int h : (isDaytime ? DAY_STRONG_HOUSES : NIGHT_STRONG_HOUSES)) {
            if (h == house) {
                return true;
            }
        }
        return false;
    }

    /** 黄经 → Whole Sign 宫位（1-based） */
    public static int houseOf(double longitude, int ascendantSign) {
        int sign = ((int) Math.floor(longitude / 30.0)) % 12;
        return ((sign - ascendantSign + 12) % 12) + 1;
    }

    private static boolean isNode(String planet) {
        return "Rahu".equals(planet) || "Ketu".equals(planet);
    }

    private static double round2(double v) {
        return Math.round(v * 100.0) / 100.0;
    }
}
