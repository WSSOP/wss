package cn.dct.zpback.engine.lifepattern;

import java.util.*;

/**
 * 相位引擎 - Parashari Graha Drishti
 * 符合 SEALED 文档 01 第 2.3 节规范
 */
public class AspectEngine {

    /**
     * 获取 Graha Drishti（行星相位）
     * @param planet 行星名称
     * @param fromHouse 起始宫位
     * @param toHouse 目标宫位
     * @return 相位类型，无相位返回 null
     */
    public static String getGrahaDrishti(String planet, int fromHouse, int toHouse) {
        int diff = toHouse - fromHouse;
        if (diff < 0) diff += 12;

        List<Integer> aspects = getAspectHouses(planet);
        if (aspects.contains(diff)) {
            return getAspectName(diff);
        }
        return null;
    }

    /**
     * 获取行星的相位宫位列表
     */
    private static List<Integer> getAspectHouses(String planet) {
        switch (planet) {
            case "Sun":
            case "Moon":
            case "Mercury":
            case "Venus":
                return Arrays.asList(1, 7);
            case "Jupiter":
                return Arrays.asList(1, 5, 7, 9);
            case "Mars":
                return Arrays.asList(1, 4, 7, 8);
            case "Saturn":
                return Arrays.asList(1, 3, 7, 10);
            default:
                return Collections.emptyList();
        }
    }

    /**
     * 获取相位名称
     */
    private static String getAspectName(int diff) {
        switch (diff) {
            case 1: return "CONJUNCTION";
            case 3: return "TRINE_3";
            case 4: return "SQUARE";
            case 5: return "TRINE_5";
            case 7: return "OPPOSITION";
            case 8: return "SQUARE_8";
            case 9: return "TRINE_9";
            case 10: return "TRINE_10";
            default: return "ASPECT_" + diff;
        }
    }
}
