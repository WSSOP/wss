package cn.dct.zpback.service.jewelry;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.LocalDate;
import java.time.LocalTime;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

/**
 * VedicAstro 入参转换测试
 *
 * 回归背景：Chart 表存的是 birth_date / birth_time 两列，
 * 而 VedicAstro 的 /get_jyotishya_data 要求 year/month/day/hour/minute
 * 拆分字段，缺一个就返回 422 UNPROCESSABLE_CONTENT。
 */
class VedicParamsAdapterTest {

    private static Map<String, Object> chartRow() {
        Map<String, Object> p = new LinkedHashMap<>();
        p.put("name", "summer");
        p.put("gender", "female");
        p.put("birthDate", "1997-12-20");
        p.put("birthTime", "15:05");
        p.put("birthPlace", "茂名");
        p.put("latitude", 21.64);
        p.put("longitude", 110.918);
        p.put("timezone", "Asia/Shanghai");
        return p;
    }

    @Test
    @DisplayName("chart 行 → VedicAstro 必填拆分字段")
    void splitsBirthDateAndTime() {
        Map<String, Object> v = JewelryChartAdapter.toVedicParams(chartRow());
        assertEquals(1997, v.get("year"));
        assertEquals(12, v.get("month"));
        assertEquals(20, v.get("day"));
        assertEquals(15, v.get("hour"));
        assertEquals(5, v.get("minute"));
    }

    @Test
    @DisplayName("必填字段齐全：这是 422 的根因")
    void allRequiredFieldsPresent() {
        Map<String, Object> v = JewelryChartAdapter.toVedicParams(chartRow());
        for (String k : new String[]{"name", "year", "month", "day", "hour", "minute", "place",
                "latitude", "longitude", "timezone", "ayanamsa", "houseSystem"}) {
            assertNotNull(v.get(k), "VedicAstro 必填字段缺失: " + k);
        }
    }

    @Test
    @DisplayName("日期兼容多种分隔符")
    void date_acceptsSeparators() {
        for (String s : new String[]{"1997-12-20", "1997/12/20", "1997.12.20", "19971220"}) {
            Map<String, Object> p = chartRow();
            p.put("birthDate", s);
            Map<String, Object> v = JewelryChartAdapter.toVedicParams(p);
            assertEquals(1997, v.get("year"), s);
            assertEquals(12, v.get("month"), s);
            assertEquals(20, v.get("day"), s);
        }
    }

    @Test
    @DisplayName("时间兼容 HH:mm / HH:mm:ss / HHmmss")
    void time_acceptsFormats() {
        assertEquals(15, JewelryChartAdapter.toVedicParams(withTime("15:05")).get("hour"));
        assertEquals(5, JewelryChartAdapter.toVedicParams(withTime("15:05")).get("minute"));
        assertEquals(15, JewelryChartAdapter.toVedicParams(withTime("15:05:33")).get("hour"));
        assertEquals(5, JewelryChartAdapter.toVedicParams(withTime("15:05:33")).get("minute"));
        assertEquals(0, JewelryChartAdapter.toVedicParams(withTime("0:00")).get("hour"));
        assertEquals(30, JewelryChartAdapter.toVedicParams(withTime("0030")).get("minute"));
    }

    // ==========================================================
    // 时区：chart 表存 IANA 名，排盘服务只认数字偏移
    // ==========================================================

    @Test
    @DisplayName("IANA 时区名转数字偏移")
    void ianaTimezone_toNumericOffset() {
        assertEquals("+8.0", JewelryChartAdapter.toNumericTimezone("Asia/Shanghai"));
        assertEquals("+8.0", JewelryChartAdapter.toNumericTimezone("Asia/Chongqing"));
        assertEquals("+5.5", JewelryChartAdapter.toNumericTimezone("Asia/Kolkata"));
        assertEquals("+9.0", JewelryChartAdapter.toNumericTimezone("Asia/Tokyo"));
        assertEquals("-5.0", JewelryChartAdapter.toNumericTimezone("America/New_York"));
        // 零偏移不带符号
        assertEquals("0.0", JewelryChartAdapter.toNumericTimezone("Europe/London"));
    }
    @Test
    @DisplayName("已是数字偏移的形式直接透传")
    void numericTimezone_passthrough() {
        assertEquals("+8.0", JewelryChartAdapter.toNumericTimezone("+8.0"));
        assertEquals("+8.0", JewelryChartAdapter.toNumericTimezone("8"));
        assertEquals("-5.5", JewelryChartAdapter.toNumericTimezone("-5.5"));
        assertEquals("+8.0", JewelryChartAdapter.toNumericTimezone(8));
        assertEquals("+5.5", JewelryChartAdapter.toNumericTimezone("+05:30"));
        assertEquals("-5.0", JewelryChartAdapter.toNumericTimezone("-05:00"));
    }

    @Test
    @DisplayName("时区无法识别时兜底 +8.0，不得让整次排盘失败")
    void unknownTimezone_fallsBack() {
        assertEquals("+8.0", JewelryChartAdapter.toNumericTimezone(null));
        assertEquals("+8.0", JewelryChartAdapter.toNumericTimezone(""));
        assertEquals("+8.0", JewelryChartAdapter.toNumericTimezone("Not/AZone"));
    }

    @Test
    @DisplayName("toVedicParams 输出的一定是排盘服务能接受的形状")
    void outputShape_isAcceptedByVedicAstro() {
        Map<String, Object> p = chartRow();
        p.put("timezone", "Asia/Shanghai");
        Map<String, Object> out = JewelryChartAdapter.toVedicParams(p);

        for (String required : List.of("name", "year", "month", "day", "hour",
                "minute", "second", "place", "latitude", "longitude", "timezone")) {
            assertTrue(out.containsKey(required), "缺少字段: " + required);
        }
        // 时区必须是数字形式，否则排盘服务 400
        assertTrue(String.valueOf(out.get("timezone")).matches("^[+-]\\d+(\\.\\d+)?$"),
                "timezone 必须是数字偏移，实际=" + out.get("timezone"));
    }

    @Test
    @DisplayName("缺出生时间按 00:00 排盘，而不是拒绝排盘")
    void missingBirthTime_defaultsToMidnight() {
        Map<String, Object> p = chartRow();
        p.remove("birthTime");
        Map<String, Object> v = JewelryChartAdapter.toVedicParams(p);
        assertEquals(0, v.get("hour"));
        assertEquals(0, v.get("minute"));
    }

    @Test
    @DisplayName("已拆分的入参不重复转换")
    void alreadySplit_isIdempotent() {
        Map<String, Object> p = new LinkedHashMap<>();
        p.put("year", 1990);
        p.put("month", 1);
        p.put("day", 2);
        p.put("hour", 3);
        p.put("minute", 4);
        p.put("latitude", 1.0);
        p.put("longitude", 2.0);
        Map<String, Object> v = JewelryChartAdapter.toVedicParams(p);
        assertEquals(1990, v.get("year"));
        assertEquals(2, v.get("day"));
    }

    @Test
    @DisplayName("出生日期无法解析时抛明确异常，不静默产出错星盘")
    void unparsableDate_throws() {
        Map<String, Object> p = chartRow();
        p.put("birthDate", "not-a-date");
        assertThrows(IllegalArgumentException.class, () -> JewelryChartAdapter.toVedicParams(p));
    }

    @Test
    @DisplayName("缺时区时默认 +8.0")
    void missingTimezone_defaultsToShanghai() {
        Map<String, Object> p = chartRow();
        p.remove("timezone");
        assertEquals("+8.0", JewelryChartAdapter.toVedicParams(p).get("timezone"));
    }

    @Test
    @DisplayName("出生地点映射到 VedicAstro 的 place 字段")
    void birthPlace_mapsToPlace() {
        assertEquals("茂名", JewelryChartAdapter.toVedicParams(chartRow()).get("place"));
    }

    private static Map<String, Object> withTime(String t) {
        Map<String, Object> p = chartRow();
        p.put("birthTime", t);
        return p;
    }
}