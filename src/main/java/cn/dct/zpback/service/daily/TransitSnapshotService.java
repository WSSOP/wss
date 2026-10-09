package cn.dct.zpback.service.daily;

import cn.dct.zpback.constant.EphemerisNameNormalizer;
import cn.dct.zpback.engine.daily.VedicPlanetaryInfluenceEngine.PlanetPos;
import cn.dct.zpback.service.VedicAstroService;
import lombok.Data;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Transit Snapshot 服务
 *
 * 对应《APP开发文档 V1.7》1.1.8：Transit Snapshot 是“全球公共、按时刻缓存”的数据层，
 * 与用户无关；用户侧只做 Natal overlay。
 *
 * 关键口径：
 * - 星座是全局量，与所在地无关；P2H 的宫位由「本命上升」决定，
 *   因此理论上任意参考点都能算出同样的流年星座；
 * - 但星历服务会校验坐标合法性，且 0 坐标会被误判为非法（见下），
 *   所以这里必须带上调用方真实经纬度，缺省时退到一个非零参考点；
 * - 结果按「小时」缓存，避免每次请求都打星历服务。
 */
@Service
@Slf4j
public class TransitSnapshotService {

    /**
     * 缺省参考点：北京。
     *
     * 不能用 0 —— 星历服务把「经度 0」判为非法（isfloat 返回数值 + == False 判断），
     * 本初子午线 / 赤道上的合法请求会被拒。
     */
    private static final double DEFAULT_LAT = 39.9042;
    private static final double DEFAULT_LON = 116.4074;

    private static final String[] NINE = {"Sun", "Moon", "Mercury", "Venus", "Mars",
            "Jupiter", "Saturn", "Rahu", "Ketu"};

    @Autowired
    private VedicAstroService vedicAstroService;

    /** "yyyy-MM-ddTHH" → 行星位置 */
    private final Map<String, List<PlanetPos>> hourlyCache = new ConcurrentHashMap<>();
    /** 失败标记：同一天内不重复重试同一个失败请求（避免一次页面加载打多次星历） */
    private final Map<String, Long> failedCache = new ConcurrentHashMap<>();

    @Data
    public static class Snapshot {
        private String snapshotId;
        private String snapshotTimeUtc;
        private List<PlanetPos> planets = new ArrayList<>();
    }

    /** 按小时取流年快照（不带坐标时使用非零缺省参考点） */
    public Snapshot snapshotAt(LocalDateTime localTime, String timezone) {
        return snapshotAt(localTime, timezone, null, null);
    }

    /**
     * 按小时取流年快照
     *
     * @param latitude  调用方真实纬度，null 时使用缺省参考点
     * @param longitude 调用方真实经度，null 时使用缺省参考点
     */
    public Snapshot snapshotAt(LocalDateTime localTime, String timezone,
                               Double latitude, Double longitude) {
        ZonedDateTime zoned = localTime.atZone(ZoneId.of(timezone == null ? "Asia/Shanghai" : timezone));
        String key = zoned.getYear() + "-" + zoned.getMonthValue() + "-" + zoned.getDayOfMonth()
                + "T" + zoned.getHour();

        Snapshot snap = new Snapshot();
        snap.setSnapshotId("transit_" + key);
        snap.setSnapshotTimeUtc(zoned.toInstant().toString());
        List<PlanetPos> cached = hourlyCache.get(key);
        if (cached != null) {
            snap.setPlanets(cached);
            return snap;
        }
        // 本小时已失败过：直接返回空，不再重复打星历
        if (failedCache.containsKey(key)) {
            return snap;
        }

        try {
            Map<String, Object> params = new LinkedHashMap<>();
            params.put("name", "Transit");
            params.put("gender", "male");
            params.put("year", zoned.getYear());
            params.put("month", zoned.getMonthValue());
            params.put("day", zoned.getDayOfMonth());
            params.put("hour", zoned.getHour());
            params.put("minute", zoned.getMinute());
            params.put("second", 0);
            params.put("place", "Reference");
            params.put("latitude", latitude == null ? DEFAULT_LAT : latitude);
            params.put("longitude", longitude == null ? DEFAULT_LON : longitude);
            params.put("timezone", offsetString(zoned));
            params.put("ayanamsa", "Lahiri");
            params.put("house_system", "Whole Sign");
            params.put("charts", List.of("D1"));

            List<PlanetPos> planets = extractPlanets(vedicAstroService.getJyotishyaData(params));
            if (planets.isEmpty()) {
                throw new IllegalStateException("星历返回空行星列表");
            }
            hourlyCache.put(key, planets);
            snap.setPlanets(planets);
            log.info("Transit Snapshot 生成: id={}, planets={}", snap.getSnapshotId(), planets.size());
        } catch (Exception e) {
            failedCache.put(key, System.currentTimeMillis());
            // 星历服务不可用时返回空列表，由上层判定 personalized_available=false，不伪造分数
            log.error("Transit Snapshot 生成失败: {}", e.getMessage());
        }
        return snap;
    }

    /** 取某个本地自然日内的月亮状态（用于 Moon 时间轴取样） */
    public MoonState moonStateAt(LocalDateTime localTime, String timezone) {
        return moonStateAt(localTime, timezone, null, null);
    }

    public MoonState moonStateAt(LocalDateTime localTime, String timezone,
                                 Double latitude, Double longitude) {
        Snapshot snap = snapshotAt(localTime, timezone, latitude, longitude);
        MoonState st = new MoonState();
        for (PlanetPos p : snap.getPlanets()) {
            if ("Moon".equals(p.getPlanet())) {
                st.setSignIndex(p.getSignIndex());
                st.setSign(signName(p.getSignIndex()));
                st.setNakshatra(nakshatraOf(p));
                st.setFound(true);
                return st;
            }
        }
        return st;
    }

    @Data
    public static class MoonState {
        private boolean found;
        private int signIndex;
        private String sign;
        private String nakshatra;
    }

    /** 本地自然日 00:00 对应的 ZonedDateTime */
    public ZonedDateTime dayStart(LocalDate date, String timezone) {
        return date.atStartOfDay(ZoneId.of(timezone == null ? "Asia/Shanghai" : timezone));
    }

    // ============================================================
    // 解析
    // ============================================================

    @SuppressWarnings("unchecked")
    private List<PlanetPos> extractPlanets(Map<String, Object> data) {
        List<PlanetPos> out = new ArrayList<>();
        if (data == null) {
            return out;
        }
        // 兼容 { vargas: { D1: ... } } 与顶层 { D1: ... } 两种包装
        Map<String, Object> root = data;
        Object vargas = data.get("vargas");
        if (vargas instanceof Map) {
            root = (Map<String, Object>) vargas;
        }
        Map<String, Object> d1 = (Map<String, Object>) root.get("D1");
        if (d1 == null) {
            d1 = root;
        }
        Object planetsObj = d1.get("planets");
        if (planetsObj instanceof List) {
            // 数组格式（VedicAstro 主格式）: [ { "name": "Sun", "sign": "Leo", pos: { dec_deg } }, ... ]
            for (Object item : (List<Object>) planetsObj) {
                if (!(item instanceof Map)) {
                    continue;
                }
                Map<String, Object> p = (Map<String, Object>) item;
                addPlanet(out, p, (String) p.get("name"));
            }
        } else if (planetsObj instanceof Map) {
            // 兼容 Map 格式: { "Sun": { "sign": "Leo", ... }, ... }
            Map<String, Object> planets = (Map<String, Object>) planetsObj;
            for (String name : NINE) {
                Object raw = planets.get(name);
                if (raw instanceof Map) {
                    addPlanet(out, (Map<String, Object>) raw, name);
                }
            }
        } else {
            // 解析失败时把结构打出来，避免下次还要盲猜
            log.warn("Transit 响应中没有可识别的 planets 字段，顶层键={}, D1键={}",
                    data.keySet(), d1 == null ? "无" : d1.keySet());
        }
        return out;
    }

    private void addPlanet(List<PlanetPos> out, Map<String, Object> p, String name) {
        if (name == null) {
            return;
        }
        // 只收九大行星，Ascendant 之类不进入证据链
        boolean known = false;
        for (String n : NINE) {
            if (n.equals(name)) {
                known = true;
                break;
            }
        }
        if (!known) {
            return;
        }
        Integer idx = signIndex((String) p.get("sign"));
        if (idx == null) {
            log.warn("Transit 行星 {} 的星座无法识别: {}", name, p.get("sign"));
            return;
        }
        double decDeg = 0;
        Object pos = p.get("pos");
        if (pos instanceof Map && ((Map<String, Object>) pos).get("dec_deg") instanceof Number) {
            decDeg = ((Number) ((Map<String, Object>) pos).get("dec_deg")).doubleValue();
        }
        PlanetPos pp = new PlanetPos(name, idx, decDeg);
        // 星历拼写变体统一到锁定表命名（Shatabhishak → Shatabhisha），否则下游查表取不到中文名
        pp.setNakshatra(EphemerisNameNormalizer.normalizeNakshatra((String) p.get("nakshatra")));
        out.add(pp);
    }

    private static String nakshatraOf(PlanetPos p) {
        return p.getNakshatra();
    }

    /** 固定偏移字符串（星历服务使用 "+8.0" 形式） */
    private static String offsetString(ZonedDateTime zoned) {
        int seconds = zoned.getOffset().getTotalSeconds();
        double hours = seconds / 3600.0;
        return (hours >= 0 ? "+" : "") + hours;
    }

    // ============================================================
    // 星座名 → 0-based index（兼容旧拼写）
    // ============================================================

    private static final Map<String, Integer> SIGN_INDEX = new LinkedHashMap<>();
    static {
        String[] canonical = {"Aries", "Taurus", "Gemini", "Cancer", "Leo", "Virgo",
                "Libra", "Scorpio", "Sagittarius", "Capricorn", "Aquarius", "Pisces"};
        for (int i = 0; i < canonical.length; i++) {
            SIGN_INDEX.put(canonical[i].toLowerCase(), i);
        }
        // 星历服务历史拼写与常见别名
        SIGN_INDEX.put("saggitarius", 8);
        SIGN_INDEX.put("sagittarious", 8);
        SIGN_INDEX.put("mesha", 0);
        SIGN_INDEX.put("vrishabha", 1);
        SIGN_INDEX.put("mithuna", 2);
        SIGN_INDEX.put("karka", 3);
        SIGN_INDEX.put("simha", 4);
        SIGN_INDEX.put("kanya", 5);
        SIGN_INDEX.put("tula", 6);
        SIGN_INDEX.put("vrishchika", 7);
        SIGN_INDEX.put("dhanu", 8);
        SIGN_INDEX.put("makara", 9);
        SIGN_INDEX.put("kumbha", 10);
        SIGN_INDEX.put("meena", 11);
    }

    /** 解析星座名 → index；无法解析返回 null（不猜测） */
    public static Integer signIndex(String raw) {
        if (raw == null) {
            return null;
        }
        return SIGN_INDEX.get(raw.trim().toLowerCase());
    }

    private static final String[] SIGN_NAMES = {"Aries", "Taurus", "Gemini", "Cancer", "Leo",
            "Virgo", "Libra", "Scorpio", "Sagittarius", "Capricorn", "Aquarius", "Pisces"};

    public static String signName(int index) {
        return SIGN_NAMES[((index % 12) + 12) % 12];
    }

    public List<PlanetPos> emptyPlanets() {
        return Collections.emptyList();
    }
}
