package cn.dct.zpback.constant;

import java.util.*;

/**
 * DK 行星基础注册表 - Daraka Karaka Base
 */
public final class DKPlanetBaseRegistry {

    private DKPlanetBaseRegistry() {}

    /** DK 行星基础标签 */
    private static final Map<String, List<String>> DK_PLANET_BASE = new LinkedHashMap<>();

    /** DK 行星角色 */
    private static final Map<String, String> DK_PLANET_ROLE = new LinkedHashMap<>();

    static {
        DK_PLANET_BASE.put("Sun", Arrays.asList("VISIBLE", "SELF_DIRECTED", "DIGNIFIED", "IDENTITY_STRONG"));
        DK_PLANET_BASE.put("Moon", Arrays.asList("RESPONSIVE", "CARING", "EMOTIONALLY_ATTUNED", "BELONGING_ORIENTED"));
        DK_PLANET_BASE.put("Mars", Arrays.asList("ACTIVE", "ASSERTIVE", "DRIVEN", "DIRECT"));
        DK_PLANET_BASE.put("Mercury", Arrays.asList("COMMUNICATIVE", "INTELLECTUAL", "ADAPTIVE", "CURIOUS"));
        DK_PLANET_BASE.put("Jupiter", Arrays.asList("GROWTH_ORIENTED", "VALUE_DRIVEN", "WISE", "MEANING_SEEKING"));
        DK_PLANET_BASE.put("Venus", Arrays.asList("RELATIONAL", "HARMONIZING", "AFFECTIONATE", "AESTHETICALLY_AWARE"));
        DK_PLANET_BASE.put("Saturn", Arrays.asList("MATURE", "RESPONSIBLE", "STRUCTURED", "TIME_ORIENTED"));

        DK_PLANET_ROLE.put("Sun", "IDENTITY_ACTIVATOR");
        DK_PLANET_ROLE.put("Moon", "EMOTIONAL_BONDING_ACTIVATOR");
        DK_PLANET_ROLE.put("Mars", "AGENCY_AND_ASSERTION_ACTIVATOR");
        DK_PLANET_ROLE.put("Mercury", "COMMUNICATION_AND_UNDERSTANDING_ACTIVATOR");
        DK_PLANET_ROLE.put("Jupiter", "GROWTH_AND_MEANING_ACTIVATOR");
        DK_PLANET_ROLE.put("Venus", "LOVE_AND_RECIPROCITY_ACTIVATOR");
        DK_PLANET_ROLE.put("Saturn", "COMMITMENT_AND_BOUNDARY_ACTIVATOR");
    }

    /**
     * 获取 DK 行星基础标签
     */
    public static List<String> getDKPlanetBase(String planet) {
        return DK_PLANET_BASE.getOrDefault(planet, Collections.emptyList());
    }

    /**
     * 获取 DK 行星角色
     */
    public static String getDKPlanetRole(String planet) {
        return DK_PLANET_ROLE.getOrDefault(planet, "UNKNOWN");
    }

    /**
     * 获取所有 DK 行星名称
     */
    public static Set<String> getAllDKPlanets() {
        return DK_PLANET_BASE.keySet();
    }
}
