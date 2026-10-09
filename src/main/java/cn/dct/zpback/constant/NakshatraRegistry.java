package cn.dct.zpback.constant;

import java.util.*;

/**
 * 27宿 Nakshatra 核心注册表
 * Canonical Behavioral Core
 */
public final class NakshatraRegistry {

    private NakshatraRegistry() {}

    /** 27宿核心标签 */
    private static final Map<String, List<String>> NAKSHATRA_CORE = new LinkedHashMap<>();

    static {
        NAKSHATRA_CORE.put("Ashwini", Arrays.asList("INITIATING", "FAST", "RESTORATIVE"));
        NAKSHATRA_CORE.put("Bharani", Arrays.asList("INTENSE", "CONTAINING", "ENDURING"));
        NAKSHATRA_CORE.put("Krittika", Arrays.asList("DISCERNING", "DIRECT", "CLARIFYING"));
        NAKSHATRA_CORE.put("Rohini", Arrays.asList("NURTURING", "CONTINUOUS", "ATTACHING"));
        NAKSHATRA_CORE.put("Mrigashira", Arrays.asList("SEEKING", "CURIOUS", "EXPLORATORY"));
        NAKSHATRA_CORE.put("Ardra", Arrays.asList("INTENSE", "TRUTH_SEEKING", "RESETTING"));
        NAKSHATRA_CORE.put("Punarvasu", Arrays.asList("RESTORATIVE", "RETURNING", "FLEXIBLE"));
        NAKSHATRA_CORE.put("Pushya", Arrays.asList("SUPPORTIVE", "DUTY_BASED", "STEADY"));
        NAKSHATRA_CORE.put("Ashlesha", Arrays.asList("PRIVATE", "STRATEGIC", "BOUNDARY_SENSITIVE"));
        NAKSHATRA_CORE.put("Magha", Arrays.asList("DIGNIFIED", "LOYAL", "LINEAGE_AWARE"));
        NAKSHATRA_CORE.put("Purva Phalguni", Arrays.asList("AFFECTIONATE", "ENJOYING", "EXPRESSIVE"));
        NAKSHATRA_CORE.put("Uttara Phalguni", Arrays.asList("COMMITTED", "AGREEMENT_BASED", "SUPPORTIVE"));
        NAKSHATRA_CORE.put("Hasta", Arrays.asList("PRACTICAL", "ADAPTIVE", "HANDS_ON"));
        NAKSHATRA_CORE.put("Chitra", Arrays.asList("DISTINCTIVE", "CRAFTING", "STANDARD_AWARE"));
        NAKSHATRA_CORE.put("Swati", Arrays.asList("AUTONOMOUS", "FLEXIBLE", "SPACE_AWARE"));
        NAKSHATRA_CORE.put("Vishakha", Arrays.asList("GOAL_FOCUSED", "PERSISTENT", "INTENSIFYING"));
        NAKSHATRA_CORE.put("Anuradha", Arrays.asList("LOYAL", "COOPERATIVE", "BOND_ORIENTED"));
        NAKSHATRA_CORE.put("Jyeshtha", Arrays.asList("PROTECTIVE", "RESPONSIBLE", "GUARDED"));
        NAKSHATRA_CORE.put("Mula", Arrays.asList("ROOT_SEEKING", "STRIPPING", "RESETTING"));
        NAKSHATRA_CORE.put("Purva Ashadha", Arrays.asList("CONVINCED", "EXPRESSIVE", "FORWARD_MOVING"));
        NAKSHATRA_CORE.put("Uttara Ashadha", Arrays.asList("ENDURING", "PRINCIPLED", "LONG_TERM"));
        NAKSHATRA_CORE.put("Shravana", Arrays.asList("LISTENING", "LEARNING", "INTERPRETIVE"));
        NAKSHATRA_CORE.put("Dhanishta", Arrays.asList("COORDINATED", "RHYTHMIC", "GOAL_AWARE"));
        NAKSHATRA_CORE.put("Shatabhisha", Arrays.asList("PRIVATE", "ANALYTICAL", "AUTONOMOUS"));
        NAKSHATRA_CORE.put("Purva Bhadrapada", Arrays.asList("INTENSE", "PRINCIPLED", "TRANSFORMATIVE"));
        NAKSHATRA_CORE.put("Uttara Bhadrapada", Arrays.asList("DEEP", "PATIENT", "CONTAINING"));
        NAKSHATRA_CORE.put("Revati", Arrays.asList("GENTLE", "GUIDING", "TRANSITION_AWARE"));
    }

    /**
     * 获取指定宿的行为核心标签
     */
    public static List<String> getBehavioralCore(String nakshatraName) {
        return NAKSHATRA_CORE.getOrDefault(nakshatraName, Collections.emptyList());
    }

    /**
     * 获取所有宿名称
     */
    public static Set<String> getAllNakshatraNames() {
        return NAKSHATRA_CORE.keySet();
    }
}
