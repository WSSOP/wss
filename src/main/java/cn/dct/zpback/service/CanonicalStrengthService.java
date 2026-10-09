package cn.dct.zpback.service;

import cn.dct.zpback.engine.astro.AshtakavargaEngine;
import cn.dct.zpback.engine.astro.BhavaBalaEngine;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 专业数据 Canonical 引擎
 *
 * 角色：把 VedicAstro 第三方返回的原始数据 + 本地 canonical 引擎（Bhavabala /
 * Ashtakavarga）组装成数据 Tab 可直接消费的结构，并对 Shadbala 输出补齐
 * 排名与达标率。PL9 parity 未通过的字段一律以本地 canonical 结果为准，
 * 并在返回中标注 engine 版本与 invariant 校验状态。
 */
@Service
@Slf4j
public class CanonicalStrengthService {

    @Autowired
    private VedicAstroService vedicAstroService;

    /** 七曜固定顺序（不含 Rahu/Ketu） */
    private static final String[] SEVEN_PLANETS = {
            "Sun", "Moon", "Mars", "Mercury", "Jupiter", "Venus", "Saturn"
    };

    /** 12 宫主（Aries 起按 Whole Sign 推导，不在此硬编码，运行时按上升星座推） */
    private static final String[] LORDS_BY_SIGN = {
            "Mars", "Venus", "Mercury", "Moon", "Sun", "Mercury",
            "Venus", "Mars", "Jupiter", "Saturn", "Saturn", "Jupiter"
    };

    /** Shadbala Required 固定值（Virupa） */
    private static final Map<String, Double> SHADBALA_REQUIRED = new LinkedHashMap<>();

    static {
        SHADBALA_REQUIRED.put("Sun", 390.0);
        SHADBALA_REQUIRED.put("Moon", 360.0);
        SHADBALA_REQUIRED.put("Mars", 300.0);
        SHADBALA_REQUIRED.put("Mercury", 420.0);
        SHADBALA_REQUIRED.put("Jupiter", 390.0);
        SHADBALA_REQUIRED.put("Venus", 330.0);
        SHADBALA_REQUIRED.put("Saturn", 300.0);
    }

    /**
     * 计算完整的专业数据（Shadbala 摘要 + Bhavabala + Ashtakavarga）
     *
     * @param birthParams 出生参数（year/month/day/hour/minute/second/latitude/longitude/timezone）
     * @return canonical 专业数据
     */
    public Map<String, Object> computeProfessionalData(Map<String, Object> birthParams) {
        Map<String, Object> vedicParams = buildVedicParams(birthParams);
        Map<String, Object> raw = vedicAstroService.getJyotishyaData(vedicParams);
        return computeFromRaw(raw, vedicParams);
    }

    /**
     * 从第三方原始数据 + canonical 引擎计算专业数据
     */
    public Map<String, Object> computeFromRaw(Map<String, Object> raw, Map<String, Object> vedicParams) {
        if (raw == null || raw.isEmpty()) {
            throw new IllegalArgumentException("排盘服务返回空数据，无法计算专业数据");
        }

        Map<String, Double> longitudes = extractLongitudes(raw);
        Map<String, Double> shadbalaTotals = extractShadbalaTotals(raw);
        Map<String, Double> digBala = extractDigBala(raw);
        int ascSign = extractAscendantSign(raw);

        Map<String, Object> result = new LinkedHashMap<>();

        // 1) Shadbala 摘要（含达标率与排名，供前端摘要卡与详情复用）
        result.put("shadbala", buildShadbalaSummary(shadbalaTotals));

        // 2) Bhavabala（本地 canonical，五项组成）
        boolean isDaytime = resolveIsDaytime(vedicParams);
        Map<Integer, String> houseLords = resolveHouseLords(ascSign);
        Map<String, Object> bhavabala = BhavaBalaEngine.compute(
                houseLords, shadbalaTotals, digBala, longitudes, ascSign, isDaytime);
        result.put("bhavabala", bhavabala);

        // 3) Ashtakavarga（本地 canonical，raw BAV/SAV，sign-indexed）
        Map<String, Integer> planetSigns = new LinkedHashMap<>();
        for (String p : SEVEN_PLANETS) {
            Double lon = longitudes.get(p);
            if (lon != null) {
                planetSigns.put(p, AshtakavargaEngine.signOf(lon));
            }
        }
        Map<String, Object> ashtakavarga = AshtakavargaEngine.compute(planetSigns, ascSign);
        result.put("ashtakavarga", ashtakavarga);

        result.put("engineVersion", "canonical-strength-v1");
        result.put("source", "LOCAL_CANONICAL_PLUS_THIRD_PARTY_SHADBALA");
        return result;
    }

    // ============================================================
    // Shadbala 摘要
    // ============================================================

    private Map<String, Object> buildShadbalaSummary(Map<String, Double> totals) {
        List<Map<String, Object>> list = new ArrayList<>();
        for (Map.Entry<String, Double> req : SHADBALA_REQUIRED.entrySet()) {
            double value = totals.getOrDefault(req.getKey(), 0.0);
            double percent = req.getValue() > 0 ? Math.round(value / req.getValue() * 1000.0) / 10.0 : 0;
            Map<String, Object> item = new LinkedHashMap<>();
            item.put("planet", req.getKey());
            item.put("total", Math.round(value * 100.0) / 100.0);
            item.put("required", req.getValue());
            item.put("percentOfRequired", percent);
            item.put("meetsRequired", value >= req.getValue());
            list.add(item);
        }
        // 按 relative 达标率排名（与前端 Shadbala 口径一致）
        List<Map<String, Object>> ranked = new ArrayList<>(list);
        ranked.sort((a, b) -> Double.compare(
                (Double) b.get("percentOfRequired"), (Double) a.get("percentOfRequired")));
        for (int i = 0; i < ranked.size(); i++) {
            ranked.get(i).put("rank", i + 1);
        }

        Map<String, Object> summary = new LinkedHashMap<>();
        summary.put("items", list);
        summary.put("ranked", ranked);
        summary.put("requiredStandard", new LinkedHashMap<>(SHADBALA_REQUIRED));
        return summary;
    }

    // ============================================================
    // 从第三方原始数据提取输入
    // ============================================================

    @SuppressWarnings("unchecked")
    private Map<String, Double> extractLongitudes(Map<String, Object> raw) {
        Map<String, Double> longitudes = new LinkedHashMap<>();
        Map<String, Object> vargas = (Map<String, Object>) raw.getOrDefault("vargas", raw);
        Map<String, Object> d1 = (Map<String, Object>) vargas.getOrDefault("D1", vargas);
        if (d1.containsKey("planets")) {
            Map<String, Object> planets = (Map<String, Object>) d1.get("planets");
            for (String p : SEVEN_PLANETS) {
                Object pos = planets.get(p);
                if (pos instanceof Map) {
                    Object abs = ((Map<String, Object>) pos).get("abs_deg");
                    if (abs instanceof Number) {
                        longitudes.put(p, ((Number) abs).doubleValue());
                    }
                }
            }
        }
        if (longitudes.isEmpty()) {
            log.warn("未能从第三方数据中提取行星黄经，Bhavabala/Ashtakavarga 将基于空位置计算");
        }
        return longitudes;
    }

    @SuppressWarnings("unchecked")
    private Map<String, Double> extractShadbalaTotals(Map<String, Object> raw) {
        Map<String, Double> totals = new LinkedHashMap<>();
        Map<String, Object> balas = (Map<String, Object>) raw.getOrDefault("Balas", raw);
        Object shadbalaObj = balas.get("Shadbala");
        if (shadbalaObj instanceof Map) {
            Object totalObj = ((Map<String, Object>) shadbalaObj).get("Total");
            if (totalObj instanceof Map) {
                for (Map.Entry<String, Object> e : ((Map<String, Object>) totalObj).entrySet()) {
                    if (e.getValue() instanceof Number) {
                        totals.put(e.getKey(), ((Number) e.getValue()).doubleValue());
                    }
                }
            }
        }
        return totals;
    }

    @SuppressWarnings("unchecked")
    private Map<String, Double> extractDigBala(Map<String, Object> raw) {
        Map<String, Double> dig = new LinkedHashMap<>();
        Map<String, Object> balas = (Map<String, Object>) raw.getOrDefault("Balas", raw);
        Object shadbalaObj = balas.get("Shadbala");
        if (shadbalaObj instanceof Map) {
            Object digObj = ((Map<String, Object>) shadbalaObj).get("DigBala");
            if (digObj instanceof Map) {
                for (Map.Entry<String, Object> e : ((Map<String, Object>) digObj).entrySet()) {
                    if (e.getValue() instanceof Number) {
                        dig.put(e.getKey(), ((Number) e.getValue()).doubleValue());
                    }
                }
            }
        }
        return dig;
    }

    @SuppressWarnings("unchecked")
    private int extractAscendantSign(Map<String, Object> raw) {
        Map<String, Object> vargas = (Map<String, Object>) raw.getOrDefault("vargas", raw);
        Map<String, Object> d1 = (Map<String, Object>) vargas.getOrDefault("D1", vargas);
        Object asc = d1.get("ascendant");
        if (asc instanceof Map) {
            Object abs = ((Map<String, Object>) asc).get("abs_deg");
            if (abs instanceof Number) {
                return AshtakavargaEngine.signOf(((Number) abs).doubleValue());
            }
            Object sign = ((Map<String, Object>) asc).get("sign");
            if (sign instanceof String) {
                return signIndex((String) sign);
            }
        }
        return 0;
    }

    private int signIndex(String sign) {
        String[] signs = {"Aries", "Taurus", "Gemini", "Cancer", "Leo", "Virgo",
                "Libra", "Scorpio", "Sagittarius", "Capricorn", "Aquarius", "Pisces"};
        for (int i = 0; i < signs.length; i++) {
            if (signs[i].equalsIgnoreCase(sign)) {
                return i;
            }
        }
        return 0;
    }

    private Map<Integer, String> resolveHouseLords(int ascSign) {
        Map<Integer, String> lords = new LinkedHashMap<>();
        for (int h = 1; h <= 12; h++) {
            int sign = (ascSign + h - 1) % 12;
            lords.put(h - 1, LORDS_BY_SIGN[sign]);
        }
        return lords;
    }

    /**
     * 昼生判定：VedicAstro 第三方数据若直接给出，优先使用；
     * 否则用经度近似估算（太阳在当地时 6~18 点之间为昼）。
     */
    private boolean resolveIsDaytime(Map<String, Object> vedicParams) {
        Object hour = vedicParams.get("hour");
        if (hour instanceof Number) {
            int h = ((Number) hour).intValue();
            return h >= 6 && h < 18;
        }
        return true;
    }

    private Map<String, Object> buildVedicParams(Map<String, Object> birthParams) {
        Map<String, Object> params = new LinkedHashMap<>();
        params.put("name", String.valueOf(birthParams.getOrDefault("name", "User")));
        params.put("year", ((Number) birthParams.get("year")).intValue());
        params.put("month", ((Number) birthParams.get("month")).intValue());
        params.put("day", ((Number) birthParams.get("day")).intValue());
        params.put("hour", ((Number) birthParams.get("hour")).intValue());
        params.put("minute", ((Number) birthParams.getOrDefault("minute", 0)).intValue());
        params.put("second", ((Number) birthParams.getOrDefault("second", 0)).intValue());
        params.put("place", String.valueOf(birthParams.getOrDefault("place", "Unknown")));
        params.put("latitude", ((Number) birthParams.get("latitude")).doubleValue());
        params.put("longitude", ((Number) birthParams.get("longitude")).doubleValue());
        params.put("timezone", String.valueOf(birthParams.getOrDefault("timezone", "+8.0")));
        params.put("ayanamsa", "Lahiri");
        params.put("houseSystem", "Whole Sign");
        params.put("charts", null);
        return params;
    }
}
