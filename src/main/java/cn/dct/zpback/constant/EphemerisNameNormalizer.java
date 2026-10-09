package cn.dct.zpback.constant;

import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * 星历名称归一化
 *
 * VedicAstro 返回的星宿 / 星座英文名与问星渡锁定命名表存在拼写差异，例如：
 *   星历 Shatabhishak → 锁定表 Shatabhisha（危宿）
 *   星历 Saggitarius  → 锁定表 Sagittarius（射手座）
 *
 * 若不归一化，按锁定名索引的查表会 miss：
 *   - DailyObservationAssets → nakshatra_cn 为空（前端出现「Shatabhishak（）」空括号）
 *   - NakshatraRegistry      → 星宿行为精修证据静默丢失
 *
 * 归一化只做「拼写对齐」，不改变任何占星计算结果。
 */
public final class EphemerisNameNormalizer {

    /**
     * 27 个锁定名，与 nakshatra-daily-copy-v1.json 的 nakshatra_en 一致
     * （DailyObservationAssets 的单测会校验归一化结果均可在资产表中命中）
     */
    private static final List<String> CANONICAL_NAKSHATRAS = Arrays.asList(
            "Aswini", "Bharani", "Krittika", "Rohini", "Mrigashira", "Ardra", "Punarvasu",
            "Pushya", "Ashlesha", "Magha", "Purva Phalguni", "Uttara Phalguni", "Hasta",
            "Chitra", "Swati", "Vishakha", "Anuradha", "Jyeshtha", "Mula", "Purva Ashadha",
            "Uttara Ashadha", "Shravana", "Dhanishta", "Shatabhisha", "Purva Bhadrapada",
            "Uttara Bhadrapada", "Revati"
    );

    private static final List<String> CANONICAL_SIGNS = Arrays.asList(
            "Aries", "Taurus", "Gemini", "Cancer", "Leo", "Virgo",
            "Libra", "Scorpio", "Sagittarius", "Capricorn", "Aquarius", "Pisces"
    );

    /** 星历历史拼写（小写）→ 锁定表拼写 */
    private static final Map<String, String> NAKSHATRA_ALIAS = Map.of(
            "ashwini", "Aswini",
            "kritika", "Krittika",
            "jyeshta", "Jyeshtha",
            "shatabhishak", "Shatabhisha",
            "anurada", "Anuradha",
            "vishaka", "Vishakha"
    );

    private static final Set<String> NAKSHATRA_LOWER = CANONICAL_NAKSHATRAS.stream()
            .map(s -> s.toLowerCase()).collect(Collectors.toSet());
    private static final Set<String> SIGN_LOWER = CANONICAL_SIGNS.stream()
            .map(s -> s.toLowerCase()).collect(Collectors.toSet());
    private static final Map<String, String> SIGN_ALIAS = Map.of(
            "saggitarius", "Sagittarius",
            "saggitarious", "Sagittarius"
    );

    private EphemerisNameNormalizer() {
    }

    /**
     * 星宿名归一化：变体拼写 → 锁定表拼写；
     * 已在锁定表内的（忽略大小写）返回锁定表标准写法；
     * 无法识别时原样返回，交由上层打日志发现。
     */
    public static String normalizeNakshatra(String raw) {
        return normalize(raw, NAKSHATRA_ALIAS, NAKSHATRA_LOWER, CANONICAL_NAKSHATRAS);
    }

    /** 星座名归一化；无法识别时原样返回 */
    public static String normalizeSign(String raw) {
        return normalize(raw, SIGN_ALIAS, SIGN_LOWER, CANONICAL_SIGNS);
    }

    private static String normalize(String raw, Map<String, String> alias,
                                    Set<String> lowerSet, List<String> canonical) {
        if (raw == null) {
            return null;
        }
        String key = raw.trim();
        if (key.isEmpty()) {
            return key;
        }
        String lower = key.toLowerCase();
        String mapped = alias.get(lower);
        if (mapped != null) {
            return mapped;
        }
        if (lowerSet.contains(lower)) {
            for (String c : canonical) {
                if (c.toLowerCase().equals(lower)) {
                    return c;
                }
            }
        }
        return key;
    }
}
