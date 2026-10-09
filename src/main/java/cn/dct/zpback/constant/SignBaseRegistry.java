package cn.dct.zpback.constant;

import java.util.*;

/**
 * 星座基础注册表 - Sign Base Registry
 * 用于 5H/7H Sign Base 解析
 */
public final class SignBaseRegistry {

    private SignBaseRegistry() {}

    /** 5H Sign Base - 恋爱领域 */
    private static final Map<String, List<String>> FIFTH_HOUSE_BASE = new LinkedHashMap<>();

    /** 7H Sign Base - 婚姻关系领域 */
    private static final Map<String, List<String>> SEVENTH_HOUSE_BASE = new LinkedHashMap<>();

    /** D9 Lagna Sign Base - 婚姻阶段自我基调 */
    private static final Map<String, List<String>> D9_LAGNA_BASE = new LinkedHashMap<>();

    /** D9 7H Sign Base - 成熟伴侣领域 */
    private static final Map<String, List<String>> D9_SEVENTH_HOUSE_BASE = new LinkedHashMap<>();

    /** 宫位 Domain */
    private static final Map<Integer, List<String>> HOUSE_DOMAINS = new LinkedHashMap<>();

    static {
        // 5H Sign Base
        FIFTH_HOUSE_BASE.put("Aries", Arrays.asList("INITIATIVE", "SPARK", "MOMENTUM"));
        FIFTH_HOUSE_BASE.put("Taurus", Arrays.asList("STABILITY", "SENSORY", "CONTINUITY"));
        FIFTH_HOUSE_BASE.put("Gemini", Arrays.asList("CURIOSITY", "EXCHANGE", "VARIETY"));
        FIFTH_HOUSE_BASE.put("Cancer", Arrays.asList("SAFETY", "CARE", "FAMILIARITY"));
        FIFTH_HOUSE_BASE.put("Leo", Arrays.asList("WARMTH", "RECOGNITION", "EXPRESSION"));
        FIFTH_HOUSE_BASE.put("Virgo", Arrays.asList("DISCERNMENT", "USEFULNESS", "RELIABILITY"));
        FIFTH_HOUSE_BASE.put("Libra", Arrays.asList("RECIPROCITY", "COURTSHIP", "HARMONY"));
        FIFTH_HOUSE_BASE.put("Scorpio", Arrays.asList("DEPTH", "EXCLUSIVITY", "MAGNETISM"));
        FIFTH_HOUSE_BASE.put("Sagittarius", Arrays.asList("EXPLORATION", "GROWTH", "MEANING"));
        FIFTH_HOUSE_BASE.put("Capricorn", Arrays.asList("SERIOUSNESS", "RESPECT", "BUILDING"));
        FIFTH_HOUSE_BASE.put("Aquarius", Arrays.asList("FRIENDSHIP", "INDIVIDUALITY", "NOVELTY"));
        FIFTH_HOUSE_BASE.put("Pisces", Arrays.asList("RESONANCE", "EMPATHY", "IMAGINATION"));

        // 7H Sign Base
        SEVENTH_HOUSE_BASE.put("Aries", Arrays.asList("DIRECTNESS", "AUTONOMY", "ACTION"));
        SEVENTH_HOUSE_BASE.put("Taurus", Arrays.asList("STABILITY", "LOYALTY", "CONSISTENCY"));
        SEVENTH_HOUSE_BASE.put("Gemini", Arrays.asList("COMMUNICATION", "FLEXIBILITY", "EXCHANGE"));
        SEVENTH_HOUSE_BASE.put("Cancer", Arrays.asList("BELONGING", "CARE", "EMOTIONAL_SECURITY"));
        SEVENTH_HOUSE_BASE.put("Leo", Arrays.asList("LOYALTY", "RECOGNITION", "HEART"));
        SEVENTH_HOUSE_BASE.put("Virgo", Arrays.asList("FUNCTION", "RESPONSIBILITY", "IMPROVEMENT"));
        SEVENTH_HOUSE_BASE.put("Libra", Arrays.asList("RECIPROCITY", "BALANCE", "NEGOTIATION"));
        SEVENTH_HOUSE_BASE.put("Scorpio", Arrays.asList("TRUST", "DEPTH", "EXCLUSIVITY"));
        SEVENTH_HOUSE_BASE.put("Sagittarius", Arrays.asList("FREEDOM", "GROWTH", "SHARED_MEANING"));
        SEVENTH_HOUSE_BASE.put("Capricorn", Arrays.asList("COMMITMENT", "STRUCTURE", "RESPONSIBILITY"));
        SEVENTH_HOUSE_BASE.put("Aquarius", Arrays.asList("EQUALITY", "SPACE", "FRIENDSHIP"));
        SEVENTH_HOUSE_BASE.put("Pisces", Arrays.asList("COMPASSION", "ACCEPTANCE", "BONDING"));

        // D9 Lagna Sign Base
        D9_LAGNA_BASE.put("Aries", Arrays.asList("ACTIVE", "DIRECT", "SELF_LED"));
        D9_LAGNA_BASE.put("Taurus", Arrays.asList("STEADY", "GROUNDED", "CONSISTENT"));
        D9_LAGNA_BASE.put("Gemini", Arrays.asList("COMMUNICATIVE", "ADAPTIVE", "MENTAL"));
        D9_LAGNA_BASE.put("Cancer", Arrays.asList("NURTURING", "BELONGING", "EMOTIONALLY_INVOLVED"));
        D9_LAGNA_BASE.put("Leo", Arrays.asList("DIGNIFIED", "EXPRESSIVE", "HEART_LED"));
        D9_LAGNA_BASE.put("Virgo", Arrays.asList("PRACTICAL", "IMPROVING", "RESPONSIBILITY_AWARE"));
        D9_LAGNA_BASE.put("Libra", Arrays.asList("PARTNERSHIP_ORIENTED", "NEGOTIATING", "RECIPROCAL"));
        D9_LAGNA_BASE.put("Scorpio", Arrays.asList("DEEP", "INTENSE", "TRANSFORMATIVE"));
        D9_LAGNA_BASE.put("Sagittarius", Arrays.asList("GROWTH_ORIENTED", "MEANINGFUL", "EXPANSIVE"));
        D9_LAGNA_BASE.put("Capricorn", Arrays.asList("STRUCTURED", "RESPONSIBLE", "COMMITMENT_DRIVEN"));
        D9_LAGNA_BASE.put("Aquarius", Arrays.asList("AUTONOMOUS", "EQUAL", "SYSTEMIC"));
        D9_LAGNA_BASE.put("Pisces", Arrays.asList("EMPATHIC", "ADAPTIVE", "TRANSCENDENT"));

        // D9 7H Sign Base
        D9_SEVENTH_HOUSE_BASE.put("Aries", Arrays.asList("DIRECTNESS", "AUTONOMY", "ACTIVE_RESOLUTION"));
        D9_SEVENTH_HOUSE_BASE.put("Taurus", Arrays.asList("CONTINUITY", "RELIABILITY", "MATERIAL_STABILITY"));
        D9_SEVENTH_HOUSE_BASE.put("Gemini", Arrays.asList("COMMUNICATION", "ADAPTABILITY", "CONTINUOUS_EXCHANGE"));
        D9_SEVENTH_HOUSE_BASE.put("Cancer", Arrays.asList("BELONGING", "CARE", "EMOTIONAL_HOME"));
        D9_SEVENTH_HOUSE_BASE.put("Leo", Arrays.asList("LOYALTY", "RECOGNITION", "HEART_INVESTMENT"));
        D9_SEVENTH_HOUSE_BASE.put("Virgo", Arrays.asList("FUNCTION", "RESPONSIBILITY", "DAILY_IMPROVEMENT"));
        D9_SEVENTH_HOUSE_BASE.put("Libra", Arrays.asList("RECIPROCITY", "NEGOTIATION", "PARTNERSHIP_BALANCE"));
        D9_SEVENTH_HOUSE_BASE.put("Scorpio", Arrays.asList("TRUST", "DEPTH", "SHARED_VULNERABILITY"));
        D9_SEVENTH_HOUSE_BASE.put("Sagittarius", Arrays.asList("GROWTH", "SHARED_DIRECTION", "FREEDOM_WITHIN_COMMITMENT"));
        D9_SEVENTH_HOUSE_BASE.put("Capricorn", Arrays.asList("COMMITMENT", "STRUCTURE", "LONG_TERM_BUILDING"));
        D9_SEVENTH_HOUSE_BASE.put("Aquarius", Arrays.asList("EQUALITY", "AUTONOMY", "COMPANIONSHIP"));
        D9_SEVENTH_HOUSE_BASE.put("Pisces", Arrays.asList("COMPASSION", "ACCEPTANCE", "DEEP_BONDING"));

        // House Domains
        HOUSE_DOMAINS.put(1, Arrays.asList("self", "identity", "body", "direction"));
        HOUSE_DOMAINS.put(2, Arrays.asList("family", "values", "resources", "money", "continuity"));
        HOUSE_DOMAINS.put(3, Arrays.asList("communication", "action", "skills", "local"));
        HOUSE_DOMAINS.put(4, Arrays.asList("home", "emotional foundation", "belonging"));
        HOUSE_DOMAINS.put(5, Arrays.asList("romance", "creativity", "interests", "children"));
        HOUSE_DOMAINS.put(6, Arrays.asList("daily responsibility", "service", "conflict", "adjustment"));
        HOUSE_DOMAINS.put(7, Arrays.asList("partnership", "cooperation", "commitment"));
        HOUSE_DOMAINS.put(8, Arrays.asList("deep bonding", "shared resources", "privacy", "crisis", "transformation"));
        HOUSE_DOMAINS.put(9, Arrays.asList("beliefs", "learning", "distance", "culture", "mentor", "meaning"));
        HOUSE_DOMAINS.put(10, Arrays.asList("career", "responsibility", "social identity", "public role"));
        HOUSE_DOMAINS.put(11, Arrays.asList("network", "long-term goals", "gains", "groups"));
        HOUSE_DOMAINS.put(12, Arrays.asList("privacy", "distance", "overseas", "retreat", "sacrifice", "expenses"));
    }

    public static List<String> get5HSignBase(String sign) {
        return FIFTH_HOUSE_BASE.getOrDefault(sign, Collections.emptyList());
    }

    public static List<String> get7HSignBase(String sign) {
        return SEVENTH_HOUSE_BASE.getOrDefault(sign, Collections.emptyList());
    }

    public static List<String> getD9LagnaBase(String sign) {
        return D9_LAGNA_BASE.getOrDefault(sign, Collections.emptyList());
    }

    public static List<String> getD97HSignBase(String sign) {
        return D9_SEVENTH_HOUSE_BASE.getOrDefault(sign, Collections.emptyList());
    }

    public static List<String> getHouseDomain(Integer house) {
        return HOUSE_DOMAINS.getOrDefault(house, Collections.emptyList());
    }
}
