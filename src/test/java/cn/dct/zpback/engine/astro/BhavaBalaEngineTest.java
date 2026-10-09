package cn.dct.zpback.engine.astro;

import org.junit.jupiter.api.Test;

import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Bhavabala 五项组成与 PL9 Golden Case 结构校验（规范 §4）。
 */
class BhavaBalaEngineTest {

    private Map<String, Double> shadbalaTotals() {
        Map<String, Double> m = new LinkedHashMap<>();
        m.put("Sun", 324.25);
        m.put("Moon", 390.751);
        m.put("Mars", 472.409);
        m.put("Mercury", 428.577);
        m.put("Jupiter", 392.664);
        m.put("Venus", 388.204);
        m.put("Saturn", 417.312);
        return m;
    }

    private Map<String, Double> longitudes() {
        Map<String, Double> m = new LinkedHashMap<>();
        m.put("Sun", 244.60);   // Sagittarius 4°36'
        m.put("Moon", 137.20);  // Leo 17°12'
        m.put("Mars", 277.75);  // Capricorn 7°45'
        m.put("Mercury", 237.66); // Scorpio 27°39'
        m.put("Jupiter", 296.08); // Capricorn 26°04'
        m.put("Venus", 279.26); // Capricorn 9°15'
        m.put("Saturn", 349.72); // Pisces 19°43'
        return m;
    }

    @Test
    void total_isSumOfFiveComponents() {
        Map<String, Object> result = BhavaBalaEngine.compute(
                lordsOf(0), shadbalaTotals(), new HashMap<>(), longitudes(), 0, true);

        for (int h = 1; h <= 12; h++) {
            @SuppressWarnings("unchecked")
            Map<String, Object> house = (Map<String, Object>) result.get("house" + h);
            double total = ((Number) house.get("total")).doubleValue();
            double sum = 0;
            for (String k : new String[]{
                    "bhavaAdhipathibala", "bhavaDigbala", "bhavaDrishtibala",
                    "bhavaPlanetsInBala", "bhavaDayNightBala"}) {
                sum += ((Number) house.get(k)).doubleValue();
            }
            assertEquals(sum, total, 0.011, "第" + h + "宫 Total 必须等于五项之和");
        }
    }

    @Test
    void dayNightBala_followsDayStrongHouses() {
        Map<String, Object> day = BhavaBalaEngine.compute(
                lordsOf(0), shadbalaTotals(), new HashMap<>(), longitudes(), 0, true);
        Map<String, Object> night = BhavaBalaEngine.compute(
                lordsOf(0), shadbalaTotals(), new HashMap<>(), longitudes(), 0, false);

        for (int h = 1; h <= 12; h++) {
            @SuppressWarnings("unchecked")
            Map<String, Object> dh = (Map<String, Object>) day.get("house" + h);
            @SuppressWarnings("unchecked")
            Map<String, Object> nh = (Map<String, Object>) night.get("house" + h);
            double dVal = ((Number) dh.get("bhavaDayNightBala")).doubleValue();
            double nVal = ((Number) nh.get("bhavaDayNightBala")).doubleValue();
            // 昼盘与夜盘互补：昼强宫 +15 时夜盘必为 0，反之亦然
            assertTrue((dVal == 15 && nVal == 0) || (dVal == 0 && nVal == 15),
                    "第" + h + "宫 昼/夜 DayNightBala 必须互补");
        }
    }

    @Test
    void planetsInBala_contributesToOccupiedHouses() {
        // 火星在 Capricorn（黄经 277.75），Aries 上升时 Capricorn = 第10宫；火星为凶星 → -60
        // 同一星座还落有 Jupiter（296.08）与 Venus（279.26），均为吉星 → +60
        // 第10宫 Planets-in = -60 + 60 + 60 = +60
        Map<String, Object> result = BhavaBalaEngine.compute(
                lordsOf(0), shadbalaTotals(), new HashMap<>(), longitudes(), 0, true);
        @SuppressWarnings("unchecked")
        Map<String, Object> house10 = (Map<String, Object>) result.get("house10");
        double in10 = ((Number) house10.get("bhavaPlanetsInBala")).doubleValue();
        assertEquals(60.0, in10, 0.011,
                "第10宫 Planets-in 应为火星(-60)+木星(+60)+金星(+60)=+60");
    }

    @Test
    void adhipatiBala_usesHouseLordShadbalaTotal() {
        // Aries 上升：第1宫宫主 Mars，宫主力 = Mars Shadbala Total = 472.409
        Map<String, Object> result = BhavaBalaEngine.compute(
                lordsOf(0), shadbalaTotals(), new HashMap<>(), longitudes(), 0, true);
        @SuppressWarnings("unchecked")
        Map<String, Object> house1 = (Map<String, Object>) result.get("house1");
        assertEquals("Mars", house1.get("houseLord"));
        assertEquals(472.409, ((Number) house1.get("bhavaAdhipathibala")).doubleValue(), 0.011);
    }

    @Test
    void houseOf_wholeSignMapping() {
        // 上升 Aries：Capricorn（黄经 277.75 → sign 9）= 第 10 宫
        assertEquals(10, BhavaBalaEngine.houseOf(277.75, 0));
        // 上升 Taurus（ascSign=1）：Aries = 第 12 宫
        assertEquals(12, BhavaBalaEngine.houseOf(5.0, 1));
    }

    /** Aries 上升的 12 宫主 */
    private Map<Integer, String> lordsOf(int ascSign) {
        String[] lordBySign = {"Mars", "Venus", "Mercury", "Moon", "Sun", "Mercury",
                "Venus", "Mars", "Jupiter", "Saturn", "Saturn", "Jupiter"};
        Map<Integer, String> m = new HashMap<>();
        for (int h = 1; h <= 12; h++) {
            m.put(h - 1, lordBySign[(ascSign + h - 1) % 12]);
        }
        return m;
    }
}
