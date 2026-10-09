package cn.dct.zpback.engine.lifepattern;

import java.util.*;

/**
 * 星座表达标签注册表
 */
public class SignExpressionRegistry {

    private static final Map<String, List<String>> SIGN_TAGS = new HashMap<>();

    static {
        SIGN_TAGS.put("Aries", Arrays.asList("INITIATIVE", "DIRECT", "QUICK", "COMPETITIVE", "INDEPENDENT"));
        SIGN_TAGS.put("Taurus", Arrays.asList("STABLE", "SENSORY", "STEADY", "LOYAL", "MATERIAL"));
        SIGN_TAGS.put("Gemini", Arrays.asList("CURIOUS", "COMMUNICATIVE", "ADAPTABLE", "VERSATILE", "MENTAL"));
        SIGN_TAGS.put("Cancer", Arrays.asList("NURTURING", "PROTECTIVE", "EMOTIONAL", "SECURE", "CARING"));
        SIGN_TAGS.put("Leo", Arrays.asList("EXPRESSIVE", "WARM", "DRAMATIC", "RECOGNITION", "LOYAL"));
        SIGN_TAGS.put("Virgo", Arrays.asList("ANALYTICAL", "PRACTICAL", "DETAIL_ORIENTED", "HELPFUL", "DISCRIMINATING"));
        SIGN_TAGS.put("Libra", Arrays.asList("HARMONIOUS", "BALANCED", "DIPLOMATIC", "RELATIONAL", "AESTHETIC"));
        SIGN_TAGS.put("Scorpio", Arrays.asList("DEEP", "INTENSE", "TRANSFORMATIVE", "PRIVATE", "MAGNETIC"));
        SIGN_TAGS.put("Sagittarius", Arrays.asList("EXPLORATORY", "PHILOSOPHICAL", "OPTIMISTIC", "FREE", "MEANINGFUL"));
        SIGN_TAGS.put("Capricorn", Arrays.asList("STRUCTURED", "AMBITION", "RESPONSIBLE", "DISCIPLINED", "ACHIEVING"));
        SIGN_TAGS.put("Aquarius", Arrays.asList("INDEPENDENT", "INNOVATIVE", "HUMANITARIAN", "UNCONVENTIONAL", "DETACHED"));
        SIGN_TAGS.put("Pisces", Arrays.asList("EMPATHIC", "IMAGINATIVE", "SPIRITUAL", "SENSITIVE", "DISSOLVING"));
    }

    public static List<String> getSignTags(String sign) {
        return SIGN_TAGS.getOrDefault(sign, Collections.singletonList("SIGN_EXPRESSION"));
    }
}
