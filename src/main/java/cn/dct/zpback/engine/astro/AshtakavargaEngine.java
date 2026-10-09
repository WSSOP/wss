package cn.dct.zpback.engine.astro;

import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Ashtakavarga（八值图）PL9 对齐实现 —— 标准 raw Bhinnashtakavarga / Sarvashtakavarga
 *
 * 第三方 API 返回值呈「2 倍计数」特征，且即使 ÷2，Moon/Mars/Saturn 各少 1 个 bindu、
 * SAV 为 334 而标准 raw 是 337（P0），不能作为 canonical 数据。
 * 因此这里按 BPHS 经典 bindu 表本地重算，保证固定总量 invariant。
 *
 * 数据层级与索引语义（规范 §5.3/§5.4）：
 *   - 层级：raw BAV / raw SAV（未做 Trikona / Ekadhipatya reduction），字段命名区分。
 *   - 索引：sign-indexed，index 0 = Aries ... 11 = Pisces，与上升无关；
 *     前端展示「固定黄道十二星座顺序」。
 *   - 贡献者：Sun/Moon/Mars/Mercury/Jupiter/Venus/Saturn/Ascendant 八个；
 *     BAV 展示范围仅七曜，Ascendant 只参与计票。
 *
 * 固定总量校验（必须通过，否则视为实现错误）：
 *   Sun 48 / Moon 49 / Mars 39 / Mercury 54 / Jupiter 56 / Venus 52 / Saturn 39 / SAV 337
 */
public final class AshtakavargaEngine {

    public static final String[] TARGET_PLANETS = {
            "Sun", "Moon", "Mars", "Mercury", "Jupiter", "Venus", "Saturn"
    };

    /**
     * BPHS 经典 bindu 表：每个目标行星，各贡献者占据某宫时，从该贡献者所在星座起数的
     * 1-based 宫位偏移列表（offset 从贡献者星座本身 = 1 开始计）。
     * 顺序：Sun, Moon, Mars, Mercury, Jupiter, Venus, Saturn, Ascendant
     */
    private static final int[][][] BINDU_TABLE = {
            // Sun BAV（总 48）
            {
                    {1, 2, 4, 7, 8, 9, 10, 11},          // Sun
                    {3, 6, 10, 11},                       // Moon
                    {1, 2, 4, 7, 8, 9, 10, 11},          // Mars
                    {3, 5, 6, 9, 10, 11, 12},            // Mercury
                    {5, 6, 9, 11},                        // Jupiter
                    {6, 7, 12},                           // Venus
                    {1, 2, 4, 7, 8, 9, 10, 11},          // Saturn
                    {3, 4, 6, 10, 11, 12}                // Ascendant
            },
            // Moon BAV（总 49）
            {
                    {3, 6, 7, 8, 10, 11},
                    {1, 3, 6, 7, 10, 11},
                    {2, 3, 5, 6, 9, 10, 11},
                    {1, 3, 4, 5, 7, 8, 10, 11},
                    {1, 4, 7, 8, 10, 11, 12},
                    {3, 4, 5, 7, 9, 10, 11},
                    {3, 5, 6, 11},
                    {3, 6, 10, 11}
            },
            // Mars BAV（总 39）
            {
                    {3, 5, 6, 10, 11},
                    {3, 6, 11},
                    {1, 2, 4, 7, 8, 10, 11},
                    {3, 5, 6, 11},
                    {6, 10, 11, 12},
                    {6, 8, 11, 12},
                    {1, 4, 7, 8, 9, 10, 11},
                    {1, 3, 6, 10, 11}
            },
            // Mercury BAV（总 54）
            {
                    {5, 6, 9, 11, 12},
                    {2, 4, 6, 8, 10, 11},
                    {1, 2, 4, 7, 8, 9, 10, 11},
                    {1, 3, 5, 6, 9, 10, 11, 12},
                    {6, 8, 11, 12},
                    {1, 2, 3, 4, 5, 8, 9, 11},
                    {1, 2, 4, 7, 8, 9, 10, 11},
                    {1, 2, 4, 6, 8, 10, 11}
            },
            // Jupiter BAV（总 56）
            {
                    {1, 2, 3, 4, 7, 8, 9, 10, 11},
                    {2, 5, 7, 9, 11},
                    {1, 2, 4, 7, 8, 10, 11},
                    {1, 2, 4, 5, 6, 9, 10, 11},
                    {1, 2, 3, 4, 7, 8, 10, 11},
                    {2, 5, 6, 9, 10, 11},
                    {3, 5, 6, 12},
                    {1, 2, 4, 5, 6, 7, 9, 10, 11}
            },
            // Venus BAV（总 52）
            {
                    {8, 11, 12},
                    {1, 2, 3, 4, 5, 8, 9, 11, 12},
                    {3, 5, 6, 9, 11, 12},
                    {3, 5, 6, 9, 11},
                    {5, 8, 9, 10, 11},
                    {1, 2, 3, 4, 5, 8, 9, 10, 11},
                    {3, 4, 5, 8, 9, 10, 11},
                    {1, 2, 3, 4, 5, 8, 9, 11}
            },
            // Saturn BAV（总 39）
            {
                    {1, 2, 4, 7, 8, 10, 11},
                    {3, 6, 11},
                    {3, 5, 6, 10, 11, 12},
                    {6, 8, 9, 10, 11, 12},
                    {5, 6, 11, 12},
                    {6, 11, 12},
                    {3, 5, 6, 11},
                    {1, 3, 4, 6, 10, 11}
            }
    };

    private static final String[] CONTRIBUTORS = {
            "Sun", "Moon", "Mars", "Mercury", "Jupiter", "Venus", "Saturn", "Ascendant"
    };

    /** 标准 raw BAV 总 bindu（invariant 校验用） */
    public static final int[] BAV_TOTAL_INVARIANT = {48, 49, 39, 54, 56, 52, 39};
    public static final int SAV_TOTAL_INVARIANT = 337;

    private AshtakavargaEngine() {
    }

    /**
     * 计算 raw BAV + raw SAV
     *
     * @param planetSign 各行星所在星座序号（0=Aries），不含 Rahu/Ketu
     * @param ascSign    上升星座序号（0=Aries）
     * @return rawBav{Planet}[12]（sign-indexed）、rawSav[12]、invariant 校验结果
     */
    public static Map<String, Object> compute(Map<String, Integer> planetSign, int ascSign) {
        int[][] bav = new int[7][12];

        // 每个贡献者所在的星座（Ascendant 单独给出）
        int[] contributorSign = new int[8];
        for (int c = 0; c < 7; c++) {
            Integer s = planetSign.get(CONTRIBUTORS[c]);
            contributorSign[c] = s != null ? s : 0;
        }
        contributorSign[7] = ascSign;

        for (int t = 0; t < 7; t++) {
            for (int c = 0; c < 8; c++) {
                for (int offset : BINDU_TABLE[t][c]) {
                    // 从贡献者星座起数 offset 个星座（1-based），落点 +1 bindu
                    int sign = (contributorSign[c] + offset - 1) % 12;
                    bav[t][sign]++;
                }
            }
        }

        int[] sav = new int[12];
        for (int s = 0; s < 12; s++) {
            for (int t = 0; t < 7; t++) {
                sav[s] += bav[t][s];
            }
        }

        Map<String, Object> result = new LinkedHashMap<>();
        Map<String, Object> rawBav = new LinkedHashMap<>();
        boolean invariantOk = true;
        for (int t = 0; t < 7; t++) {
            rawBav.put(TARGET_PLANETS[t], Arrays.stream(bav[t]).boxed().toArray(Integer[]::new));
            int sum = Arrays.stream(bav[t]).sum();
            if (sum != BAV_TOTAL_INVARIANT[t]) {
                invariantOk = false;
            }
        }
        int savSum = Arrays.stream(sav).sum();
        if (savSum != SAV_TOTAL_INVARIANT) {
            invariantOk = false;
        }

        result.put("rawBav", rawBav);
        result.put("rawSav", Arrays.stream(sav).boxed().toArray(Integer[]::new));
        result.put("indexSemantics", "SIGN_INDEXED_0_ARIES");
        result.put("level", "RAW_BAV_SAV_NO_REDUCTION");
        result.put("contributors", CONTRIBUTORS);
        Integer[] bavTotals = new Integer[7];
        for (int t = 0; t < 7; t++) {
            bavTotals[t] = Arrays.stream(bav[t]).sum();
        }
        result.put("invariant", Map.of(
                "passed", invariantOk,
                "bavTotals", bavTotals,
                "savTotal", savSum
        ));
        result.put("engine", "canonical-ashtakavarga-v1");
        return result;
    }

    /** 黄经（0-360）→ 星座序号（0=Aries） */
    public static int signOf(double longitude) {
        return ((int) Math.floor(longitude / 30.0)) % 12;
    }
}
