package cn.dct.zpback.engine.relationship;

import cn.dct.zpback.constant.EphemerisNameNormalizer;
import cn.dct.zpback.entity.relationship.*;
import cn.dct.zpback.entity.relationship.CanonicalChartData.NatalChart;
import cn.dct.zpback.entity.relationship.CanonicalChartData.PlanetPosition;
import cn.dct.zpback.entity.relationship.CanonicalChartData.HouseInfo;
import cn.dct.zpback.entity.relationship.CanonicalChartData.ShadbalaData;
import cn.dct.zpback.entity.relationship.CanonicalChartData.NakshatraData;
import cn.dct.zpback.entity.relationship.CanonicalChartData.DashaPeriod;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.*;

/**
 * 星盘数据解析器
 * 将 VedicAstro 返回的原始数据转换为 CanonicalChartData
 */
@Component
@Slf4j
@SuppressWarnings("unchecked")
public class ChartDataParser {

    /**
     * 解析 VedicAstro 原始数据为 CanonicalChartData
     * VedicAstro 响应结构: { "vargas": { "D1": {...}, "D9": {...} }, "shadbala": {...}, "dasha": [...], ... }
     */
    public CanonicalChartData parse(Map<String, Object> rawData) {
        if (rawData == null || rawData.isEmpty()) {
            return null;
        }

        CanonicalChartData data = new CanonicalChartData();

        // 提取 vargas 层（VedicAstro 响应结构）
        Map<String, Object> vargas = (Map<String, Object>) rawData.getOrDefault("vargas", rawData);

        // 解析 D1
        if (vargas.containsKey("D1") || vargas.containsKey("d1")) {
            Map<String, Object> d1Raw = (Map<String, Object>) vargas.getOrDefault("D1", vargas.get("d1"));
            data.setD1(parseNatalChart(d1Raw));
        }

        // 解析 D9
        if (vargas.containsKey("D9") || vargas.containsKey("d9")) {
            Map<String, Object> d9Raw = (Map<String, Object>) vargas.getOrDefault("D9", vargas.get("d9"));
            data.setD9(parseNatalChart(d9Raw));
        }

        // 解析 Shadbala
        if (rawData.containsKey("shadbala")) {
            Object shadbalaRaw = rawData.get("shadbala");
            if (shadbalaRaw instanceof Map) {
                data.setShadbala(parseShadbala((Map<String, Object>) shadbalaRaw));
            }
        }

        // 解析 Nakshatra
        if (rawData.containsKey("nakshatra")) {
            Object nakshatraRaw = rawData.get("nakshatra");
            if (nakshatraRaw instanceof Map) {
                data.setNakshatra(parseNakshatra((Map<String, Object>) nakshatraRaw));
            }
        }

        // 解析 Dasha
        if (rawData.containsKey("dasha")) {
            Object dashaRaw = rawData.get("dasha");
            if (dashaRaw instanceof List) {
                data.setDashaList(parseDasha((List<Map<String, Object>>) dashaRaw));
            }
        }

        // 解析 Karaka
        if (rawData.containsKey("karaka")) {
            Object karakaRaw = rawData.get("karaka");
            if (karakaRaw instanceof Map) {
                data.setKaraka((Map<String, Object>) karakaRaw);
                calculateAKDK(data);
            }
        }

        return data;
    }

    /**
     * 校验必填的出生地坐标
     *
     * 缺坐标时抛异常，避免静默套用默认值（例如北京）导致上升与全部宫位错位。
     * 各处排盘参数组装统一调用本方法，保证行为一致。
     *
     * @param value 待校验值，必须是 Number
     * @param label 字段中文名（纬度/经度），用于错误提示
     * @param min   最小合法值
     * @param max   最大合法值
     * @return 合法的坐标值
     * @throws IllegalArgumentException 缺失或越界
     */
    public static double requireCoordinate(Object value, String label, double min, double max) {
        if (!(value instanceof Number)) {
            throw new IllegalArgumentException(
                    "缺少出生地" + label + "，无法排盘：出生地坐标决定上升星座与全部宫位，请先补全档案的出生地坐标");
        }
        double v = ((Number) value).doubleValue();
        if (Double.isNaN(v) || v < min || v > max) {
            throw new IllegalArgumentException(
                    "出生地" + label + "超出有效范围[" + (int) min + ", " + (int) max + "]：" + v);
        }
        return v;
    }

    /**
     * 构建 VedicAstro API 请求参数
     * VedicAstro 期望格式: name, year, month, day, hour, minute, second, place, latitude, longitude, timezone
     */
    public Map<String, Object> buildVedicAstroParams(Map<String, Object> birthParams) {
        Map<String, Object> params = new HashMap<>();

        // name
        params.put("name", birthParams.getOrDefault("name", "User"));

        // 解析 birth_date (格式: yyyy-MM-dd)
        String birthDate = (String) birthParams.getOrDefault("birth_date", "1990-01-01");
        if (birthDate != null && birthDate.contains("-")) {
            String[] dateParts = birthDate.split("-");
            if (dateParts.length == 3) {
                params.put("year", Integer.parseInt(dateParts[0]));
                params.put("month", Integer.parseInt(dateParts[1]));
                params.put("day", Integer.parseInt(dateParts[2]));
            }
        } else {
            params.put("year", 1990);
            params.put("month", 1);
            params.put("day", 1);
        }

        // 解析 birth_time (格式: HH:mm 或 HH:mm:ss)
        String birthTime = (String) birthParams.getOrDefault("birth_time", "12:00");
        if (birthTime != null && birthTime.contains(":")) {
            String[] timeParts = birthTime.split(":");
            if (timeParts.length >= 2) {
                params.put("hour", Integer.parseInt(timeParts[0]));
                params.put("minute", Integer.parseInt(timeParts[1]));
                params.put("second", timeParts.length > 2 ? Integer.parseInt(timeParts[2]) : 0);
            }
        } else {
            params.put("hour", 12);
            params.put("minute", 0);
            params.put("second", 0);
        }

        // place
        params.put("place", birthParams.getOrDefault("birth_place", "Beijing"));

        // latitude / longitude —— 必填
        // 出生地坐标决定上升星座与全部宫位：同一出生时刻，北京和茂名的上升可以差一个星座，
        // 12 个宫位整体错位。缺失时绝不套用默认坐标，宁可失败也不出错误结果。
        double latitude = requireCoordinate(birthParams.get("latitude"), "纬度", -90, 90);
        double longitude = requireCoordinate(birthParams.get("longitude"), "经度", -180, 180);
        params.put("latitude", latitude);
        params.put("longitude", longitude);

        // timezone - VedicAstro 期望格式: "+8.0"，不是 "Asia/Shanghai"
        String tz = (String) birthParams.getOrDefault("timezone", "Asia/Shanghai");
        params.put("timezone", convertTimezoneToOffset(tz));

        // ayanamsa (岁差系统)
        params.put("ayanamsa", birthParams.getOrDefault("ayanamsa", "Lahiri"));

        // houseSystem (宫位系统)
        params.put("houseSystem", birthParams.getOrDefault("houseSystem", "Whole Sign"));

        // charts (可选，null 表示返回全部分盘)
        params.put("charts", birthParams.getOrDefault("charts", null));

        return params;
    }

    /**
     * 将时区名称转换为 VedicAstro 期望的偏移格式
     * 例如: "Asia/Shanghai" -> "+8.0"
     */
    private String convertTimezoneToOffset(String timezone) {
        if (timezone == null || timezone.isEmpty()) {
            return "+8.0";
        }
        // 如果已经是偏移格式，直接返回
        if (timezone.matches("^[+-]\\d+(\\.\\d+)?$")) {
            return timezone;
        }
        // 常见时区映射
        Map<String, String> tzMap = new HashMap<>();
        tzMap.put("Asia/Shanghai", "+8.0");
        tzMap.put("Asia/Hong_Kong", "+8.0");
        tzMap.put("Asia/Taipei", "+8.0");
        tzMap.put("Asia/Tokyo", "+9.0");
        tzMap.put("Asia/Seoul", "+9.0");
        tzMap.put("Asia/Singapore", "+8.0");
        tzMap.put("Asia/Kolkata", "+5.5");
        tzMap.put("Asia/Dubai", "+4.0");
        tzMap.put("Europe/London", "+0.0");
        tzMap.put("Europe/Paris", "+1.0");
        tzMap.put("America/New_York", "-5.0");
        tzMap.put("America/Los_Angeles", "-8.0");
        tzMap.put("Australia/Sydney", "+11.0");
        return tzMap.getOrDefault(timezone, "+8.0");
    }

    private NatalChart parseNatalChart(Map<String, Object> raw) {
        NatalChart chart = new NatalChart();
        if (raw == null) return chart;

        // 解析上升星座
        Object ascendantRaw = raw.get("ascendant");
        if (ascendantRaw instanceof String) {
            chart.setAscendantSign((String) ascendantRaw);
        } else if (ascendantRaw instanceof Map) {
            chart.setAscendantSign((String) ((Map<String, Object>) ascendantRaw).get("sign"));
            Object deg = ((Map<String, Object>) ascendantRaw).get("degree");
            if (deg instanceof Number) {
                chart.setAscendant(((Number) deg).doubleValue());
            }
        }
        if (raw.containsKey("ascendant_sign")) {
            chart.setAscendantSign((String) raw.get("ascendant_sign"));
        }

        // 解析行星 - VedicAstro 返回数组格式: [ { "name": "Sun", "sign": "Leo", ... }, ... ]
        Map<String, PlanetPosition> planets = new HashMap<>();
        Object planetsObj = raw.get("planets");
        if (planetsObj instanceof List) {
            List<?> planetsList = (List<?>) planetsObj;
            for (Object item : planetsList) {
                if (item instanceof Map) {
                    Map<String, Object> planetMap = (Map<String, Object>) item;
                    String planetName = (String) planetMap.get("name");
                    if (planetName != null) {
                        PlanetPosition pos = parsePlanetPosition(planetMap);
                        planets.put(planetName, pos);
                    }
                }
            }
        } else if (planetsObj instanceof Map) {
            // 兼容 Map 格式: { "Sun": { "sign": "Leo", ... }, ... }
            Map<String, Object> planetsRaw = (Map<String, Object>) planetsObj;
            for (Map.Entry<String, Object> entry : planetsRaw.entrySet()) {
                if (entry.getValue() instanceof Map) {
                    PlanetPosition pos = parsePlanetPosition((Map<String, Object>) entry.getValue());
                    planets.put(entry.getKey(), pos);
                }
            }
        }
        chart.setPlanets(planets);

        // 解析宫位 - VedicAstro 可能返回数组或 Map
        Map<String, HouseInfo> houses = new HashMap<>();
        Object housesObj = raw.get("houses");
        if (housesObj instanceof List) {
            List<?> housesList = (List<?>) housesObj;
            for (int i = 0; i < housesList.size(); i++) {
                Object item = housesList.get(i);
                if (item instanceof Map) {
                    Map<String, Object> houseMap = (Map<String, Object>) item;
                    HouseInfo hi = new HouseInfo();
                    hi.setSign((String) houseMap.get("sign"));
                    hi.setLord((String) houseMap.get("lord"));
                    houses.put(String.valueOf(i + 1), hi);
                }
            }
        } else if (housesObj instanceof Map) {
            Map<String, Object> housesRaw = (Map<String, Object>) housesObj;
            for (Map.Entry<String, Object> entry : housesRaw.entrySet()) {
                if (entry.getValue() instanceof Map) {
                    HouseInfo hi = new HouseInfo();
                    Map<String, Object> hiRaw = (Map<String, Object>) entry.getValue();
                    hi.setSign((String) hiRaw.get("sign"));
                    hi.setLord((String) hiRaw.get("lord"));
                    houses.put(entry.getKey(), hi);
                }
            }
        }
        chart.setHouses(houses);

        return chart;
    }

    private PlanetPosition parsePlanetPosition(Map<String, Object> raw) {
        PlanetPosition pos = new PlanetPosition();
        pos.setSign((String) raw.get("sign"));
        pos.setDegree(getDouble(raw.get("degree")));
        pos.setHouse(getInteger(raw.get("house")));
        pos.setDignity((String) raw.get("dignity"));
        pos.setRetrograde((Boolean) raw.get("retrograde"));
        return pos;
    }

    private Map<String, ShadbalaData> parseShadbala(Map<String, Object> raw) {
        Map<String, ShadbalaData> result = new HashMap<>();
        if (raw == null) return result;

        for (Map.Entry<String, Object> entry : raw.entrySet()) {
            if (entry.getValue() instanceof Map) {
                ShadbalaData sd = new ShadbalaData();
                Map<String, Object> sdRaw = (Map<String, Object>) entry.getValue();
                sd.setScore(getDouble(sdRaw.get("score")));
                sd.setExecutionCapacity(calculateExecutionCapacity(sd.getScore()));
                result.put(entry.getKey(), sd);
            }
        }
        return result;
    }

    private Map<String, NakshatraData> parseNakshatra(Map<String, Object> raw) {
        Map<String, NakshatraData> result = new HashMap<>();
        if (raw == null) return result;

        for (Map.Entry<String, Object> entry : raw.entrySet()) {
            if (entry.getValue() instanceof Map) {
                NakshatraData nd = new NakshatraData();
                Map<String, Object> ndRaw = (Map<String, Object>) entry.getValue();
                // 星历拼写变体统一到锁定表命名，否则 NakshatraRegistry 等按名查表会静默 miss
                nd.setName(EphemerisNameNormalizer.normalizeNakshatra((String) ndRaw.get("name")));
                nd.setPada(getInteger(ndRaw.get("pada")));
                result.put(entry.getKey(), nd);
            }
        }
        return result;
    }

    private List<DashaPeriod> parseDasha(List<Map<String, Object>> raw) {
        List<DashaPeriod> result = new ArrayList<>();
        if (raw == null) return result;

        for (Map<String, Object> item : raw) {
            DashaPeriod dp = new DashaPeriod();
            dp.setMahadasha((String) item.get("mahadasha"));
            dp.setAntardasha((String) item.get("antardasha"));
            dp.setStartDate((String) item.get("start_date"));
            dp.setEndDate((String) item.get("end_date"));
            result.add(dp);
        }
        return result;
    }

    private void calculateAKDK(CanonicalChartData data) {
        NatalChart d1 = data.getD1();
        if (d1 == null || d1.getPlanets() == null) return;

        String ak = null, dk = null;
        double maxDeg = 0, minDeg = 360;

        for (Map.Entry<String, PlanetPosition> entry : d1.getPlanets().entrySet()) {
            Double deg = entry.getValue().getDegree();
            if (deg != null) {
                if (deg > maxDeg) {
                    maxDeg = deg;
                    ak = entry.getKey();
                }
                if (deg < minDeg) {
                    minDeg = deg;
                    dk = entry.getKey();
                }
            }
        }

        data.setAkPlanet(ak);
        data.setDkPlanet(dk);
    }

    private String calculateExecutionCapacity(Double score) {
        if (score == null) return "ADEQUATE";
        if (score >= 60) return "HIGH";
        if (score >= 40) return "ADEQUATE";
        if (score >= 20) return "LOW";
        return "VERY_LOW";
    }

    private Double getDouble(Object value) {
        if (value instanceof Number) return ((Number) value).doubleValue();
        if (value instanceof String) {
            try { return Double.parseDouble((String) value); } catch (Exception e) { return null; }
        }
        return null;
    }

    private Integer getInteger(Object value) {
        if (value instanceof Number) return ((Number) value).intValue();
        if (value instanceof String) {
            try { return Integer.parseInt((String) value); } catch (Exception e) { return null; }
        }
        return null;
    }
}
