package cn.dct.zpback.engine.lifepattern;

import java.util.*;

/**
 * 行星功能标签注册表
 * 符合 SEALED 文档 02 第 8.1 节规范
 */
public class PlanetFunctionRegistry {

    private static final Map<String, List<String>> CORE_FUNCTION_TAGS = new HashMap<>();

    static {
        CORE_FUNCTION_TAGS.put("Sun", Arrays.asList("IDENTITY", "DIRECTION", "VISIBILITY", "AUTHORITY", "SELF_EXPRESSION"));
        CORE_FUNCTION_TAGS.put("Moon", Arrays.asList("EMOTIONAL_REGULATION", "SECURITY_NEED", "RESPONSIVENESS", "BELONGING"));
        CORE_FUNCTION_TAGS.put("Mars", Arrays.asList("ACTION", "BOUNDARY", "PROTECTION", "CONFRONTATION", "AGENCY"));
        CORE_FUNCTION_TAGS.put("Mercury", Arrays.asList("COGNITION", "LANGUAGE", "ANALYSIS", "ADAPTATION", "EXCHANGE"));
        CORE_FUNCTION_TAGS.put("Jupiter", Arrays.asList("BELIEF", "MEANING", "GROWTH", "GUIDANCE", "TRUST"));
        CORE_FUNCTION_TAGS.put("Venus", Arrays.asList("VALUE_REGULATION", "HARMONY", "CONNECTION", "RECIPROCITY", "PLEASURE"));
        CORE_FUNCTION_TAGS.put("Saturn", Arrays.asList("STRUCTURE", "DUTY", "LIMIT", "ENDURANCE", "CONTROL", "RESPONSIBILITY"));
        CORE_FUNCTION_TAGS.put("Rahu", Arrays.asList("AMPLIFICATION", "APPETITE", "DISRUPTION", "EXPERIMENTATION", "VISIBILITY"));
        CORE_FUNCTION_TAGS.put("Ketu", Arrays.asList("DETACHMENT", "SELECTIVITY", "INTERNALIZATION", "NONSTANDARD_BONDING"));
    }

    public static List<String> getCoreFunctionTags(String planet) {
        return CORE_FUNCTION_TAGS.getOrDefault(planet, Collections.singletonList("PLANETARY_FUNCTION"));
    }
}
