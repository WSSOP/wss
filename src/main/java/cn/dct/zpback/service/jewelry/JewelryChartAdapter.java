package cn.dct.zpback.service.jewelry;

import cn.dct.zpback.constant.EphemerisNameNormalizer;
import cn.dct.zpback.service.VedicAstroService;
import com.fasterxml.jackson.databind.JsonNode;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 行星珠宝｜Chart Adapter
 *
 * 职责：把问星渡 Canonical 排盘输出，收敛成 04_星盘适配与饰品门槛.md §2 约定的 Adapter 结构。
 *
 * 硬约束（00_程序员先读.md §3）：
 * - 不新建第二套星盘计算。宫主 / 宫位 / 星座 / Shadbala / Dasha 全部读现有排盘输出；
 * - 尊贵度用 Parashari 标准表自行判定（星历返回的 status 字段恒为 2，不承载尊贵语义）；
 * - 燃烧按行星到太阳的黄经角距判定；
 * - Rahu / Ketu 只作为风险 Modifier，不进入候选。
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class JewelryChartAdapter {

    private final VedicAstroService vedicAstroService;

    // ===== Parashari 标准表（确定性，V1 锁定）=====

    private static final List<String> SIGNS = List.of("Aries", "Taurus", "Gemini", "Cancer", "Leo",
            "Virgo", "Libra", "Scorpio", "Sagittarius", "Capricorn", "Aquarius", "Pisces");

    /** 旺（exaltation） */
    private static final Map<String, String> EXALTED = Map.of(
            "Sun", "Aries", "Moon", "Taurus", "Mars", "Capricorn", "Mercury", "Virgo",
            "Jupiter", "Cancer", "Venus", "Pisces", "Saturn", "Libra");

    /** 落（debilitation）= 旺的对面星座 */
    private static final Map<String, List<String>> OWN_SIGNS = Map.of(
            "Sun", List.of("Leo"),
            "Moon", List.of("Cancer"),
            "Mars", List.of("Aries", "Scorpio"),
            "Mercury", List.of("Gemini", "Virgo"),
            "Jupiter", List.of("Sagittarius", "Pisces"),
            "Venus", List.of("Taurus", "Libra"),
            "Saturn", List.of("Capricorn", "Aquarius"));

    /** Moolatrikona 星座 + 起止度数 */
    private static final Map<String, Object[]> MOOLATRIKONA = Map.of(
            "Sun", new Object[]{"Leo", 0, 20},
            "Moon", new Object[]{"Taurus", 4, 30},
            "Mars", new Object[]{"Aries", 0, 12},
            "Mercury", new Object[]{"Virgo", 16, 20},
            "Jupiter", new Object[]{"Cancer", 0, 10},
            "Venus", new Object[]{"Libra", 0, 15},
            "Saturn", new Object[]{"Aquarius", 0, 20});

    /** 燃烧：与太阳的黄经角距上限（度） */
    private static final Map<String, Double> COMBUSTION_ORB = Map.of(
            "Moon", 12.0, "Mars", 17.0, "Mercury", 14.0,
            "Jupiter", 11.0, "Venus", 10.0, "Saturn", 15.0);

    /** YOGAKARAKA 登记表：按上升星座 */
    private static final Map<String, String> YOGAKARAKA = Map.of(
            "Aries", "Mars", "Scorpio", "Mars",
            "Gemini", "Mercury", "Virgo", "Mercury",
            "Sagittarius", "Jupiter", "Pisces", "Jupiter",
            "Capricorn", "Saturn", "Aquarius", "Saturn");

    /** 节点接触容差（度）——「精确节点合相」判定 */
    private static final double NODE_EXACT_ORB = 1.0;

    // ===== 输出结构 =====

    /** 单颗行星的适配结果 */
    public static class PlanetFacts {
        public String planet;
        public String sign;
        public int house;
        public List<Integer> ownedHouses = new ArrayList<>();
        public String dignity = "NEUTRAL_DIGNITY";
        public boolean combust;
        public boolean retrograde;
        public boolean nodeExactContact;
        public boolean nodeAxisContact;
        public double shadbalaRatio;
        public String shadbalaBand;
        public String dashaRole = "INACTIVE";
        public boolean d9Validation;
        public String functionalRole;
        public int baseTier;
    }

    public static class AdaptedChart {
        public String chartId;
        public String chartSnapshotId;
        public String subjectName;
        public String birthTimeQuality;
        public Boolean ascendantStable;
        public String dataQuality;
        public String calculationVersion;
        public String ascendantSign;
        public String lagnaLord;
        public Map<String, PlanetFacts> planets = new LinkedHashMap<>();
        public String mahadashaLord;
        public String antardashaLord;
        public List<String> notes = new ArrayList<>();
    }

    // ===== 主入口 =====

    /**
     * @param birthParams 传给 VedicAstroService 的标准出生参数
     * @param chartId     现有星盘 ID
     * @param snapshotId  版本快照 ID（用于版本冻结）
     * @param subjectName 星盘主体名
     * @param birthTime   出生时间原文；空串视为 NO_TIME
     */
    public AdaptedChart adapt(Map<String, Object> birthParams, String chartId,
                             String snapshotId, String subjectName, String birthTime) {
        AdaptedChart out = new AdaptedChart();
        out.chartId = chartId;
        out.chartSnapshotId = snapshotId;
        out.subjectName = subjectName;
        out.calculationVersion = "V1_PARASHARI";

        Map<String, Object> raw;
        try {
            raw = vedicAstroService.getJyotishyaData(toVedicParams(birthParams));
        } catch (Exception e) {
            // 排盘失败不能当成"没数据"：必须显式降级，否则会误报 INSUFFICIENT_CHART
            out.birthTimeQuality = birthParams.get("birthTime") == null ? "NO_TIME" : "APPROXIMATE";
            out.dataQuality = "INSUFFICIENT_CHART";
            out.notes.add("排盘计算失败：" + e.getMessage());
            return out;
        }
        if (raw == null || raw.isEmpty()) {
            out.birthTimeQuality = birthParams.get("birthTime") == null ? "NO_TIME" : "APPROXIMATE";
            out.dataQuality = "INSUFFICIENT_CHART";
            out.notes.add("排盘服务返回空数据");
            return out;
        }

        JsonNode d1 = raw.get("D1") == null ? null
                : mapper.convertValue(raw.get("D1"), JsonNode.class);
        JsonNode d9 = raw.get("D9") == null ? null
                : mapper.convertValue(raw.get("D9"), JsonNode.class);
        JsonNode bal = raw.get("Balas") == null ? null
                : mapper.convertValue(raw.get("Balas"), JsonNode.class);
        JsonNode dashas = raw.get("Dashas") == null ? null
                : mapper.convertValue(raw.get("Dashas"), JsonNode.class);

        if (d1 == null || d1.path("planets").isMissingNode()) {
            out.birthTimeQuality = "NO_TIME";
            out.dataQuality = "INSUFFICIENT_CHART";
            out.notes.add("排盘输出缺少 D1.planets");
            return out;
        }

        out.ascendantSign = EphemerisNameNormalizer.normalizeSign(
                text(d1.path("ascendant"), "sign", null));
        out.lagnaLord = text(d1.path("ascendant"), "lagna-lord", null);

        // 宫主：从 houses[].sign-lord 反向建立 planet -> owned houses
        Map<String, List<Integer>> lordships = buildLordships(d1.path("houses"));
        Map<String, JsonNode> planets = indexPlanets(d1.path("planets"));
        Map<String, Double> ratios = shadbalaRatios(bal);
        List<String> currentDasha = currentDasha(dashas);

        // 节点所在黄经（用于接触判定）
        Map<String, Double> nodeLongitudes = new LinkedHashMap<>();
        for (String node : List.of("Rahu", "Ketu")) {
            JsonNode n = planets.get(node);
            if (n != null) nodeLongitudes.put(node, longitudeOf(n));
        }

        for (String p : JewelryPolicies.SEVEN) {
            JsonNode n = planets.get(p);
            if (n == null) {
                out.notes.add("D1 缺少行星：" + p);
                continue;
            }
            PlanetFacts f = new PlanetFacts();
            f.planet = p;
            f.sign = EphemerisNameNormalizer.normalizeSign(text(n, "sign", null));
            f.house = n.path("house-num").asInt(0);
            f.ownedHouses = lordships.getOrDefault(p, new ArrayList<>());
            f.retrograde = n.path("retro").asBoolean(false);
            f.dignity = dignityOf(p, f.sign, longitudeOf(n));
            f.combust = combusted(p, n, planets.get("Sun"));
            f.nodeExactContact = nodeExact(nodeLongitudes, longitudeOf(n));
            f.nodeAxisContact = nodeAxis(nodeLongitudes, f.sign);
            Double ratio = ratios.get(p);
            f.shadbalaRatio = ratio == null ? -1 : ratio;
            f.shadbalaBand = ratio == null ? "UNKNOWN" : bandOf(ratio);
            f.dashaRole = currentDasha.contains(p) ? "ACTIVE" : "INACTIVE";
            f.d9Validation = d9Validation(d9, p, f.sign);
            f.functionalRole = functionalRole(p, f.ownedHouses, out.ascendantSign);
            f.baseTier = tierOf(f.functionalRole);
            out.planets.put(p, f);
        }
        out.mahadashaLord = currentDasha.size() > 1 ? currentDasha.get(1) : null;
        out.antardashaLord = currentDasha.isEmpty() ? null : currentDasha.get(0);

        out.birthTimeQuality = birthTimeQuality(birthTime);
        // FULL_CHART 要求上升稳定：V1 无独立上升稳定性输入，
        // 只要出生时间达到 DECLARED_EXACT 级别即视为稳定；
        // 排盘返回了合法上升星座时也可确认稳定。
        out.ascendantStable = out.ascendantSign != null && !out.ascendantSign.isEmpty();
        out.dataQuality = dataQuality(out.birthTimeQuality, out.ascendantStable);
        return out;
    }

    // ===== 数据质量 =====

    /**
     * V1 口径：
     * NO_TIME → INSUFFICIENT_CHART
     * 缺分/缺分钟/带"约"字 → APPROXIMATE → PARTIAL_CHART
     * 其余 → DECLARED_EXACT → FULL_CHART（要求上升稳定性未标记为不稳定）
     */
    static String birthTimeQuality(String birthTime) {
        if (birthTime == null || birthTime.trim().isEmpty()) return "NO_TIME";
        String t = birthTime.trim();
        if (t.contains("约") || t.contains("大概") || t.contains("不确定")) return "APPROXIMATE";
        // HH:mm 或 HH:mm:ss
        if (!t.matches("^\\d{1,2}:\\d{2}(:\\d{2})?$")) return "APPROXIMATE";
        return "DECLARED_EXACT";
    }

    static String dataQuality(String birthTimeQuality, boolean ascendantStable) {
        // FULL_CHART 需同时满足：出生时间精确 + 上升可确定（suitability_policy.data_quality）
        return switch (birthTimeQuality) {
            case "RECTIFIED", "DOCUMENTED" -> "FULL_CHART";
            case "DECLARED_EXACT" -> ascendantStable ? "FULL_CHART" : "PARTIAL_CHART";
            case "APPROXIMATE", "UNKNOWN_STABILITY" -> "PARTIAL_CHART";
            default -> "INSUFFICIENT_CHART";
        };
    }

    // ===== 尊贵 / 燃烧 / 接触 =====

    static String dignityOf(String planet, String sign, double longitude) {
        if (sign == null || sign.isEmpty()) return "UNKNOWN";
        if (sign.equals(EXALTED.get(planet))) return "EXALTED";
        if (OWN_SIGNS.getOrDefault(planet, List.of()).contains(sign)) {
            Object[] mt = MOOLATRIKONA.get(planet);
            if (mt != null && sign.equals(mt[0])) {
                double deg = degInSign(longitude);
                if (deg >= (Double) mt[1] && deg <= (Double) mt[2]) return "MOOLATRIKONA";
            }
            return "OWN_SIGN";
        }
        // 落 = 旺的对面
        String exalt = EXALTED.get(planet);
        if (exalt != null && sign.equals(SIGNS.get((SIGNS.indexOf(exalt) + 6) % 12))) return "DEBILITATED";
        return "NEUTRAL_DIGNITY";
    }

    static boolean combusted(String planet, JsonNode node, JsonNode sun) {
        if (planet.equals("Sun") || sun == null || node == null) return false;
        Double orb = COMBUSTION_ORB.get(planet);
        if (orb == null) return false;
        double dist = Math.abs(longitudeOf(node) - longitudeOf(sun));
        return Math.min(dist, 360 - dist) <= orb;
    }

    static boolean nodeExact(Map<String, Double> nodeLongitudes, double longitude) {
        for (double nl : nodeLongitudes.values()) {
            double d = Math.abs(nl - longitude);
            if (Math.min(d, 360 - d) <= NODE_EXACT_ORB) return true;
        }
        return false;
    }

    /** 食相轴：与节点同宫或对宫 */
    static boolean nodeAxis(Map<String, Double> nodeLongitudes, String sign) {
        if (sign == null) return false;
        int idx = SIGNS.indexOf(sign);
        if (idx < 0) return false;
        for (String node : nodeLongitudes.keySet()) {
            int axis = SIGNS.indexOf(node.equals("Rahu") ? sign : oppositeSign(sign));
            if (axis == idx) return true;
        }
        return false;
    }

    static String oppositeSign(String sign) {
        int i = SIGNS.indexOf(sign);
        return i < 0 ? null : SIGNS.get((i + 6) % 12);
    }

    // ===== D9 =====

    /** D9 验证：九曜盘同星座且不受凶星强扰，标记为正向验证（V1 仅作 +1 支持） */
    static boolean d9Validation(JsonNode d9, String planet, String d1Sign) {
        if (d9 == null) return false;
        JsonNode n = indexPlanets(d9.path("planets")).get(planet);
        if (n == null) return false;
        String d9Sign = EphemerisNameNormalizer.normalizeSign(text(n, "sign", null));
        return d9Sign != null && d9Sign.equals(d1Sign);
    }

    // ===== Functional Role Root（04_星盘适配与饰品门槛.md §4）=====

    /**
     * 严格按文档给定的优先级顺序求唯一 Vote-bearing Root。
     */
    static String functionalRole(String planet, List<Integer> owned, String ascendantSign) {
        boolean ownsTrikona = owns(owned, 5) || owns(owned, 9);
        boolean ownsKendra = owns(owned, 4) || owns(owned, 10);
        boolean ownsDusthana = owns(owned, 6) || owns(owned, 8) || owns(owned, 12);
        boolean ownsMaraka = owns(owned, 2) || owns(owned, 7);

        if (ownsTrikona && ownsKendra && planet.equals(YOGAKARAKA.get(ascendantSign))) return "YOGAKARAKA";
        if (owns(owned, 1)) return "LAGNA_LORD";
        if (ownsTrikona && (ownsDusthana || ownsMaraka)) return "MIXED_BENEFIC";
        if (ownsTrikona) return "TRIKONA_LORD";
        if (ownsMaraka) return "MARAKA_ONLY";
        if (ownsDusthana) return "DUSTHANA_ONLY";
        if (owns(owned, 3) || owns(owned, 11)) return "UPACHAYA_ONLY";
        if (ownsKendra) return "KENDRA_ONLY";
        return "NEUTRAL";
    }

    private static boolean owns(List<Integer> owned, int house) {
        return owned != null && owned.contains(house);
    }

    private static int tierOf(String role) {
        return switch (role) {
            case "YOGAKARAKA", "LAGNA_LORD" -> 4;
            case "TRIKONA_LORD" -> 3;
            case "MIXED_BENEFIC", "KENDRA_ONLY" -> 2;
            case "NEUTRAL" -> 1;
            default -> 0; // MARAKA_ONLY / DUSTHANA_ONLY / UPACHAYA_ONLY
        };
    }

    // ===== 解析辅助 =====

    static Map<String, List<Integer>> buildLordships(JsonNode houses) {
        Map<String, List<Integer>> out = new LinkedHashMap<>();
        if (houses == null || houses.isMissingNode()) return out;
        for (JsonNode h : houses) {
            int num = h.path("house-num").asInt(0);
            String lord = h.path("sign-lord").asText(null);
            if (num <= 0 || lord == null || lord.isEmpty()) continue;
            // 排盘服务返回 "Sun" 这类首字母大写形式，统一大写与 SEVEN 对齐
            out.computeIfAbsent(lord.toUpperCase(), k -> new ArrayList<>()).add(num);
        }
        // List::sort 在 Collection<List<Integer>> 上无法推断 E，必须显式给出比较器
        out.values().forEach(v -> v.sort(Comparator.naturalOrder()));
        return out;
    }

    /**
     * 行星节点索引。
     *
     * key 一律大写：排盘服务返回的 name 是 "Sun"/"Moon" 这种首字母大写形式，
     * 而题库 / 配置里统一用 "SUN"/"MOON"，不做归一就会全部查不到、
     * 静默退化成"缺少行星"。
     */
    static Map<String, JsonNode> indexPlanets(JsonNode planets) {
        Map<String, JsonNode> out = new LinkedHashMap<>();
        if (planets == null || planets.isMissingNode()) return out;
        if (planets.isArray()) {
            for (JsonNode p : planets) {
                String name = p.path("name").asText(null);
                if (name != null && !name.isBlank()) out.put(name.toUpperCase(), p);
            }
        } else {
            planets.fields().forEachRemaining(e -> {
                JsonNode p = e.getValue();
                String name = p.path("name").asText(e.getKey());
                if (name != null && !name.isBlank()) out.put(name.toUpperCase(), p);
            });
        }
        return out;
    }

    /** ratio_to_required = Total / Required；缺任一项则不产出该行星比率 */
    static Map<String, Double> shadbalaRatios(JsonNode balas) {
        Map<String, Double> out = new LinkedHashMap<>();
        if (balas == null) return out;
        JsonNode total = balas.path("Shadbala").path("Total");
        JsonNode required = balas.path("Shadbala").path("Required");
        if (total.isMissingNode() || required.isMissingNode()) return out;
        // 排盘服务用 "Sun"/"Moon" 形式，这里按各自实际 key 查，而不是拿大写常量去查
        for (String p : JewelryPolicies.SEVEN) {
            double t = shadbalaValue(total, p);
            double r = shadbalaValue(required, p);
            if (r > 0) out.put(p, t / r);
        }
        return out;
    }

    /**
     * Shadbala 取值，容错大小写。
     *
     * 排盘服务返回的 key 是 "Sun"/"Moon"，而 SEVEN 常量是大写 "SUN"/"MOON"，
     * 直接 path(planet) 恒为缺失。必须遍历 holder 自身的字段名做忽略大小写匹配，
     * 不能拿 SEVEN 再去查一遍——那样查的仍是大写 key。
     */
    private static double shadbalaValue(JsonNode holder, String planet) {
        JsonNode exact = holder.path(planet);
        if (!exact.isMissingNode()) return exact.asDouble(0);
        var it = holder.fields();
        while (it.hasNext()) {
            var e = it.next();
            if (e.getKey().equalsIgnoreCase(planet)) return e.getValue().asDouble(0);
        }
        return 0;
    }

    static String bandOf(double ratio) {
        if (ratio < 0.75) return "LOW";
        if (ratio > 1.25) return "SATURATED";
        return "NORMAL";
    }

    /** 当前 MD / AD 主星（来自 Dashas.Vimshottari.current 或时间覆盖） */
    static List<String> currentDasha(JsonNode dashas) {
        List<String> out = new ArrayList<>();
        if (dashas == null) return out;
        JsonNode v = dashas.path("Vimshottari");
        if (v.isMissingNode()) v = dashas.path("vimshottari");
        if (v.isMissingNode()) return out;
        JsonNode cur = v.path("current");
        if (!cur.isMissingNode()) {
            addIfPresent(out, cur.path("bhukti").asText(null));
            addIfPresent(out, cur.path("dasha").asText(null));
        }
        // 归一为大写：dasha lord 返回 "Mercury" 这类形式，与 SEVEN 的 "MERCURY" 对齐
        return out.stream().map(s -> s.toUpperCase()).toList();
    }

    private static void addIfPresent(List<String> list, String v) {
        if (v != null && !v.isEmpty() && !list.contains(v)) list.add(v);
    }

    static String text(JsonNode parent, String field, String fallback) {
        if (parent == null) return fallback;
        JsonNode n = parent.path(field);
        return n.isMissingNode() || n.isNull() ? fallback : n.asText();
    }

    // ============================================================
    // VedicAstro 入参适配
    // ============================================================

    /**
     * 把内部出生参数（birthDate/birthTime 字符串）转成 VedicAstro 要求的
     * 拆分字段（year/month/day/hour/minute/second + place）。
     *
     * 背景：VedicAstro 对缺字段直接返回 422，而 Chart 表里存的是
     * birth_date / birth_time 两列，必须拆开才能排盘。
     * 口径与 CanonicalStrengthService.buildVedicParams 保持一致。
     */
    static Map<String, Object> toVedicParams(Map<String, Object> birthParams) {
        Map<String, Object> p = new LinkedHashMap<>(birthParams == null ? Map.of() : birthParams);

        // 已经拆分过就直接用，避免二次转换
        boolean alreadySplit = p.get("year") != null && p.get("month") != null && p.get("day") != null;

        if (!alreadySplit) {
            LocalDate date = parseDate(first(p, "birthDate", "birth_date", "date"));
            if (date == null) {
                throw new IllegalArgumentException("出生日期无法解析：" + p.get("birthDate"));
            }
            p.put("year", date.getYear());
            p.put("month", date.getMonthValue());
            p.put("day", date.getDayOfMonth());

            LocalTime time = parseTime(first(p, "birthTime", "birth_time", "time"));
            // 缺出生时间按 00:00 排盘，但由 data_quality=PARTIAL_CHART 阻止宝石推荐，
            // 不能因为缺时间就拒绝排盘（否则 INSUFFICIENT_CHART 会被误用）
            p.put("hour", time == null ? 0 : time.getHour());
            p.put("minute", time == null ? 0 : time.getMinute());
            p.put("second", 0);
        }

        p.putIfAbsent("name", "User");
        p.put("place", String.valueOf(first(p, "birthPlace", "birth_place", "place", "place")));
        p.put("latitude", toDouble(first(p, "latitude")));
        p.put("longitude", toDouble(first(p, "longitude")));
        p.put("timezone", toNumericTimezone(first(p, "timezone")));
        p.put("timezoneName", String.valueOf(p.getOrDefault("timezone", "")));
        p.put("ayanamsa", "Lahiri");
        p.put("houseSystem", "Whole Sign");
        p.put("charts", null);
        return p;
    }

    /**
     * 时区归一：把 IANA 名或 +08:00 形式的偏移，统一成 VedicAstro 要的数字偏移字符串。
     *
     * chart 表存的是 Asia/Shanghai 这类 IANA 名，而排盘服务只接受 +8.0 形式的数字偏移，
     * 直接透传会被判为 "Timezone field must be a number" 而整次排盘 400。
     */
    static String toNumericTimezone(Object tz) {
        if (tz == null) return "+8.0";
        if (tz instanceof Number n) {
            return signed(n.doubleValue());
        }
        String s = String.valueOf(tz).trim();
        if (s.isEmpty()) return "+8.0";

        if (s.matches("^[+-]?\\d+(\\.\\d+)?$")) {
            return signed(Double.parseDouble(s));
        }
        if (s.matches("^[+-]?\\d{1,2}:\\d{2}$")) {
            boolean negative = s.startsWith("-");
            String[] parts = s.substring(1).split(":");
            int h = Integer.parseInt(parts[0]);
            int m = Integer.parseInt(parts[1]);
            return signed(negative ? -(h + m / 60.0) : h + m / 60.0);
        }
        try {
            ZoneId zone = ZoneId.of(s);
            // 取该时区的标准偏移：用 1 月中旬避开夏令时切换
            java.time.ZonedDateTime ref = java.time.ZonedDateTime.of(2000, 1, 15, 12, 0, 0, 0, zone);
            return signed(zone.getRules().getOffset(ref.toInstant()).getTotalSeconds() / 3600.0);
        } catch (Exception e) {
            return "+8.0";
        }
    }

    /** 正偏移带 '+'，负偏移带 '-'，零偏移不带符号 */
    private static String signed(double hours) {
        if (hours > 0) return "+" + trimZero(hours);
        if (hours < 0) return "-" + trimZero(Math.abs(hours));
        return trimZero(0);
    }

    /** 统一保留一位小数：+8.0 / +5.5 / -5.0，避免整点时输出 "+8" 这种两种解析器行为不一致的形式 */
    private static String trimZero(double v) {
        return String.format(java.util.Locale.ROOT, "%.1f", v);
    }

    private static Object first(Map<String, Object> m, String... keys) {
        for (String k : keys) {
            Object v = m.get(k);
            if (v != null && !String.valueOf(v).isBlank()) return v;
        }
        return null;
    }

    private static LocalDate parseDate(Object v) {
        if (v == null) return null;
        if (v instanceof LocalDate d) return d;
        if (v instanceof java.util.Date d) {
            return d.toInstant().atZone(ZoneId.systemDefault()).toLocalDate();
        }
        String s = String.valueOf(v).trim();
        // 兼容 1997-12-20 / 1997/12/20 / 1997.12.20 / 19971220
        String digits = s.replaceAll("[^0-9]", "");
        if (digits.length() >= 8) {
            try {
                int y = Integer.parseInt(digits.substring(0, 4));
                int mo = Integer.parseInt(digits.substring(4, 6));
                int d = Integer.parseInt(digits.substring(6, 8));
                return LocalDate.of(y, mo, d);
            } catch (Exception ignored) {
                // 落回 ISO 解析
            }
        }
        try {
            return LocalDate.parse(s.length() > 10 ? s.substring(0, 10) : s);
        } catch (Exception e) {
            return null;
        }
    }

    private static LocalTime parseTime(Object v) {
        if (v == null) return null;
        if (v instanceof LocalTime t) return t;
        String s = String.valueOf(v).trim();
        if (s.isEmpty() || s.equalsIgnoreCase("null")) return null;
        String digits = s.replaceAll("[^0-9]", "");

        // 紧凑格式：HHmmss(6) / HHmm(4) / H(1) / HH(2)
        if (!s.contains(":") && !digits.isEmpty() && digits.length() <= 6) {
            try {
                int hour;
                int minute = 0;
                int second = 0;
                if (digits.length() <= 2) {
                    hour = Integer.parseInt(digits);
                } else if (digits.length() <= 4) {
                    hour = Integer.parseInt(digits.substring(0, digits.length() - 2));
                    minute = Integer.parseInt(digits.substring(digits.length() - 2));
                } else {
                    hour = Integer.parseInt(digits.substring(0, digits.length() - 4));
                    minute = Integer.parseInt(digits.substring(digits.length() - 4, digits.length() - 2));
                    second = Integer.parseInt(digits.substring(digits.length() - 2));
                }
                return LocalTime.of(hour, minute, second);
            } catch (Exception ignored) {
                // 落回分隔符解析
            }
        }

        // 分隔格式：HH:mm / HH:mm:ss
        try {
            return LocalTime.parse(s.length() > 5 ? s.substring(0, 5) : s);
        } catch (Exception e) {
            return null;
        }
    }

    private static double toDouble(Object v) {
        if (v == null) return 0d;
        if (v instanceof Number n) return n.doubleValue();
        try {
            return Double.parseDouble(String.valueOf(v));
        } catch (Exception e) {
            return 0d;
        }
    }

    static double longitudeOf(JsonNode planet) {
        if (planet == null) return Double.NaN;
        JsonNode pos = planet.path("pos");
        double deg = pos.path("deg").asDouble(Double.NaN);
        if (Double.isNaN(deg)) return Double.NaN;
        double min = pos.path("min").asDouble(0);
        double sec = pos.path("sec").asDouble(0);
        return deg + min / 60.0 + sec / 3600.0;
    }

    static double degInSign(double longitude) {
        if (Double.isNaN(longitude)) return Double.NaN;
        double d = longitude % 30;
        return d < 0 ? d + 30 : d;
    }

    private final com.fasterxml.jackson.databind.ObjectMapper mapper =
            new com.fasterxml.jackson.databind.ObjectMapper();

    /** 仅用于审计留痕 */
    public static String nowIso() {
        return LocalDateTime.now().toString();
    }
}