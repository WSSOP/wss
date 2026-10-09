package cn.dct.zpback.service.jewelry;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

/**
 * 行星名大小写归一测试
 *
 * 回归背景：排盘服务返回 "Sun"/"Mercury"/"Mula" 这类首字母大写形式，
 * 而题库与配置统一用 "SUN"/"MERCURY"。不做归一时查不到任何行星，
 * 且不报错，只是静默退化成「缺少行星」→ 全盘 BLOCKED → 无推荐。
 */
class PlanetNameCaseTest {

    private final ObjectMapper mapper = new ObjectMapper();

    /** 排盘服务 D1.planets 的真实片段（截取自 get_jyotishya_data 实际返回） */
    private static final String D1_PLANETS = """
            {
              "Sun":     {"name":"Sun","sign":"Saggitarius","pos":{"deg":4,"min":36,"sec":4},"house-num":9,"conjuncts":[]},
              "Moon":    {"name":"Moon","sign":"Leo","pos":{"deg":17,"min":12,"sec":6},"house-num":5,"conjuncts":["Rahu"]},
              "Mercury": {"name":"Mercury","sign":"Scorpio","pos":{"deg":27,"min":39,"sec":31},"house-num":8,"retro":true,"conjuncts":[]},
              "Jupiter": {"name":"Jupiter","sign":"Capricorn","pos":{"deg":26,"min":4,"sec":47},"house-num":10,"conjuncts":["Mars","Venus"]}
            }
            """;

    @Test
    @DisplayName("indexPlanets：key 归一为大写，能用 SEVEN 常量查到")
    void indexPlanets_keysAreUpperCase() throws Exception {
        JsonNode planets = mapper.readTree(D1_PLANETS);
        Map<String, JsonNode> idx = JewelryChartAdapter.indexPlanets(planets);

        for (String p : JewelryPolicies.SEVEN) {
            // 真实数据里有的行星必须能查到，否则 gate 会判 PLANET_FACTS_MISSING
            if (planets.has(p)) {
                assertTrue(idx.containsKey(p), "查不到行星: " + p + "，实际 key=" + idx.keySet());
            }
        }
        assertTrue(idx.containsKey("SUN"));
        assertTrue(idx.containsKey("MERCURY"));
    }

    @Test
    @DisplayName("indexPlanets：数组形式同样归一")
    void indexPlanets_handlesArrayForm() throws Exception {
        JsonNode arr = mapper.readTree("""
                [{"name":"Sun","sign":"Leo"},{"name":"Saturn","sign":"Pisces"}]
                """);
        Map<String, JsonNode> idx = JewelryChartAdapter.indexPlanets(arr);
        assertTrue(idx.containsKey("SUN"));
        assertTrue(idx.containsKey("SATURN"));
    }

    @Test
    @DisplayName("indexPlanets：缺少 name 字段时回退用 map key")
    void indexPlanets_fallsBackToMapKey() throws Exception {
        JsonNode planets = mapper.readTree("{\"Mars\":{\"sign\":\"Capricorn\"}}");
        Map<String, JsonNode> idx = JewelryChartAdapter.indexPlanets(planets);
        assertTrue(idx.containsKey("MARS"));
    }

    @Test
    @DisplayName("buildLordships：sign-lord 归一，能与 SEVEN 对上")
    void buildLordships_keysAreUpperCase() throws Exception {
        // Aries 上升，Mars 掌 1/5/9 宫；Capricorn 宫 Mars 亦为宫主
        JsonNode houses = mapper.readTree("""
                [
                  {"house-num":1,"sign":"Aries","sign-lord":"Mars"},
                  {"house-num":4,"sign":"Cancer","sign-lord":"Moon"},
                  {"house-num":5,"sign":"Leo","sign-lord":"Sun"},
                  {"house-num":7,"sign":"Libra","sign-lord":"Venus"},
                  {"house-num":9,"sign":"Saggitarius","sign-lord":"Jupiter"},
                  {"house-num":10,"sign":"Capricorn","sign-lord":"Saturn"},
                  {"house-num":2,"sign":"Taurus","sign-lord":"Venus"}
                ]
                """);
        Map<String, List<Integer>> owned = JewelryChartAdapter.buildLordships(houses);

        // Aries 上升时 Mars 是宫主（LAGNA_LORD）
        assertEquals(List.of(1), owned.get("MARS"));
        assertEquals(List.of(5), owned.get("SUN"));
        assertEquals(List.of(9), owned.get("JUPITER"));
        assertEquals(List.of(10), owned.get("SATURN"));
        assertEquals(List.of(4), owned.get("MOON"));
        // Venus 掌 7 宫（Maraka）和 2 宫（Maraka）
        assertEquals(List.of(2, 7), owned.get("VENUS"));

        // 宫主 key 必须能被 SEVEN 直接查到，否则宫主判定会失效
        for (String p : JewelryPolicies.SEVEN) {
            if (!owned.containsKey(p)) continue;
            assertTrue(JewelryPolicies.SEVEN.contains(p));
        }
    }

    @Test
    @DisplayName("shadbalaRatios：用排盘服务实际 key 取值，算得出比率")
    void shadbalaRatios_readsActualKeys() throws Exception {
        // 入参是 raw["Balas"] 节点本身（已含 Shadbala 层），
        // 行星 key 用 "Sun" 这类真实形式而非大写常量
        JsonNode balas = mapper.readTree("""
                {"Shadbala":{
                   "Total":{"Sun":300.0,"Moon":280.0,"Mars":260.0,"Mercury":300.0,
                            "Jupiter":260.0,"Venus":260.0,"Saturn":420.0},
                   "Required":{"Sun":390.0,"Moon":360.0,"Mars":300.0,"Mercury":420.0,
                               "Jupiter":390.0,"Venus":330.0,"Saturn":300.0}}}
                """);
        assertTrue(balas.path("Shadbala").path("Total").has("Sun"), "前置断言：真实 key 是 Sun");

        Map<String, Double> ratios = JewelryChartAdapter.shadbalaRatios(balas);

        // 比率 = Total / Required，取不到就是 0，必须能算出非零值
        assertNotNull(ratios.get("SUN"), "SUN 比率缺失，实际=" + ratios);
        assertEquals(300.0 / 390.0, ratios.get("SUN"), 1e-9);
        assertEquals(7, ratios.size(), "七颗行星都应产出比率，实际=" + ratios);
        // 420/300 = 1.4 > 1.25 → SATURATED
        assertEquals("SATURATED", JewelryChartAdapter.bandOf(ratios.get("SATURN")));
        // 280/360 ≈ 0.78 → NORMAL
        assertEquals("NORMAL", JewelryChartAdapter.bandOf(ratios.get("MOON")));
        // 260/330 ≈ 0.788，取不到会变 0 → LOW，这里必须不是 LOW
        assertNotEquals("LOW", JewelryChartAdapter.bandOf(ratios.get("VENUS")));
    }

    @Test
    @DisplayName("currentDasha：lord 名归一为大写")
    void currentDasha_upperCases() throws Exception {
        JsonNode dashas = mapper.readTree("""
                {"Vimshottari":{"current":{"dasha":"Mercury","bhukti":"Saturn"}}}
                """);
        List<String> d = JewelryChartAdapter.currentDasha(dashas);
        assertTrue(d.contains("MERCURY"), "实际=" + d);
        assertTrue(d.contains("SATURN"), "实际=" + d);
    }

    @Test
    @DisplayName("dataQuality：上升不可确定时不得给 FULL_CHART")
    void dataQuality_requiresAscendant() {
        assertEquals("FULL_CHART", JewelryChartAdapter.dataQuality("DECLARED_EXACT", true));
        assertEquals("PARTIAL_CHART", JewelryChartAdapter.dataQuality("DECLARED_EXACT", false));
        assertEquals("FULL_CHART", JewelryChartAdapter.dataQuality("RECTIFIED", false));
        assertEquals("PARTIAL_CHART", JewelryChartAdapter.dataQuality("APPROXIMATE", true));
        assertEquals("INSUFFICIENT_CHART", JewelryChartAdapter.dataQuality("NO_TIME", true));
    }

    @Test
    @DisplayName("Shadbala 带位边界")
    void band_boundaries() {
        assertEquals("LOW", JewelryChartAdapter.bandOf(0.74));
        assertEquals("NORMAL", JewelryChartAdapter.bandOf(0.75));
        assertEquals("NORMAL", JewelryChartAdapter.bandOf(1.25));
        assertEquals("SATURATED", JewelryChartAdapter.bandOf(1.26));
    }
}