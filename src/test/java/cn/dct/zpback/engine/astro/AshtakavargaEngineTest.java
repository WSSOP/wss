package cn.dct.zpback.engine.astro;

import org.junit.jupiter.api.Test;

import java.util.HashMap;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Ashtakavarga 固定总量 invariant 校验（规范 §5.2）。
 * 标准 raw BAV 总量：Sun 48 / Moon 49 / Mars 39 / Mercury 54 / Jupiter 56 / Venus 52 / Saturn 39 / SAV 337。
 * 任何一行不过都意味着 bindu 表或偏移方向写错。
 */
class AshtakavargaEngineTest {

    private Map<String, Integer> goldenSigns() {
        // Golden Case：Aries 上升，太阳 Sagittarius(8)、月亮 Leo(4)、火星 Capricorn(9)、
        // 水星 Scorpio(7)、木星 Capricorn(9)、金星 Capricorn(9)、土星 Pisces(11)
        Map<String, Integer> signs = new HashMap<>();
        signs.put("Sun", 8);
        signs.put("Moon", 4);
        signs.put("Mars", 9);
        signs.put("Mercury", 7);
        signs.put("Jupiter", 9);
        signs.put("Venus", 9);
        signs.put("Saturn", 11);
        return signs;
    }

    @Test
    void bavRowTotals_matchClassicalInvariants() {
        Map<String, Object> result = AshtakavargaEngine.compute(goldenSigns(), 0);
        @SuppressWarnings("unchecked")
        Map<String, Object> invariant = (Map<String, Object>) result.get("invariant");
        assertEquals(Boolean.TRUE, invariant.get("passed"), "BAV/SAV 固定总量校验必须全部通过");

        @SuppressWarnings("unchecked")
        Integer[] bavTotals = (Integer[]) invariant.get("bavTotals");
        for (int i = 0; i < AshtakavargaEngine.BAV_TOTAL_INVARIANT.length; i++) {
            assertEquals(AshtakavargaEngine.BAV_TOTAL_INVARIANT[i], bavTotals[i].intValue(),
                    AshtakavargaEngine.TARGET_PLANETS[i] + " BAV 总 bindu 必须等于经典值");
        }
        assertEquals(AshtakavargaEngine.SAV_TOTAL_INVARIANT, invariant.get("savTotal"), "SAV 总量必须为 337");
    }

    @Test
    void sav_isRowSumOfBav() {
        Map<String, Object> result = AshtakavargaEngine.compute(goldenSigns(), 0);
        @SuppressWarnings("unchecked")
        Map<String, Object> rawBav = (Map<String, Object>) result.get("rawBav");
        @SuppressWarnings("unchecked")
        Integer[] sav = (Integer[]) result.get("rawSav");

        for (int s = 0; s < 12; s++) {
            int sum = 0;
            for (String p : AshtakavargaEngine.TARGET_PLANETS) {
                @SuppressWarnings("unchecked")
                Integer[] row = (Integer[]) rawBav.get(p);
                sum += row[s];
            }
            assertEquals(sum, sav[s].intValue(), "SAV[" + s + "] 必须等于七曜 BAV 同列之和");
        }
    }

    @Test
    void indexSemantics_isSignIndexedFromAries() {
        Map<String, Object> result = AshtakavargaEngine.compute(goldenSigns(), 0);
        assertEquals("SIGN_INDEXED_0_ARIES", result.get("indexSemantics"),
                "数组 index 0 必须固定对应 Aries，与上升无关");
    }

    @Test
    void signOf_wrapsCorrectly() {
        assertEquals(0, AshtakavargaEngine.signOf(0));
        assertEquals(0, AshtakavargaEngine.signOf(29.9));
        assertEquals(1, AshtakavargaEngine.signOf(30));
        assertEquals(11, AshtakavargaEngine.signOf(359));
        assertEquals(0, AshtakavargaEngine.signOf(360)); // 环绕
    }
}
