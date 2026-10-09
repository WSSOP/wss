package cn.dct.zpback.engine.relationship;

import org.junit.jupiter.api.Test;

import java.util.HashMap;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

/**
 * 出生地坐标校验：缺坐标必须报错，绝不静默套用默认值。
 */
class ChartDataParserCoordinateTest {

    private final ChartDataParser parser = new ChartDataParser();

    @Test
    void missingCoordinate_throwsWithActionableMessage() {
        IllegalArgumentException ex = assertThrows(IllegalArgumentException.class,
                () -> ChartDataParser.requireCoordinate(null, "纬度", -90, 90));
        assertTrue(ex.getMessage().contains("纬度"), "提示应指明缺失字段");
        assertTrue(ex.getMessage().contains("出生地坐标"), "提示应告诉用户怎么修");
    }

    @Test
    void nonNumericCoordinate_throws() {
        assertThrows(IllegalArgumentException.class,
                () -> ChartDataParser.requireCoordinate("39.9042", "纬度", -90, 90));
    }

    @Test
    void outOfRangeCoordinate_throws() {
        assertThrows(IllegalArgumentException.class,
                () -> ChartDataParser.requireCoordinate(120.0, "纬度", -90, 90));
        assertThrows(IllegalArgumentException.class,
                () -> ChartDataParser.requireCoordinate(200.0, "经度", -180, 180));
    }

    @Test
    void validCoordinate_isReturned() {
        assertEquals(21.64, ChartDataParser.requireCoordinate(21.64, "纬度", -90, 90), 1e-9);
        assertEquals(110.918, ChartDataParser.requireCoordinate(110.918, "经度", -180, 180), 1e-9);
        // JSON 里的整数坐标（Integer 而非 Double）也要能通过
        assertEquals(0.0, ChartDataParser.requireCoordinate(0, "纬度", -90, 90), 1e-9);
    }

    @Test
    void buildParams_rejectsMissingCoordinatesInsteadOfDefaultingToBeijing() {
        Map<String, Object> birthParams = new HashMap<>();
        birthParams.put("birth_date", "1993-09-18");
        birthParams.put("birth_time", "20:49");
        birthParams.put("birth_place", "茂名");
        // 故意不传 latitude / longitude

        IllegalArgumentException ex = assertThrows(IllegalArgumentException.class,
                () -> parser.buildVedicAstroParams(birthParams));
        assertFalse(ex.getMessage().contains("39.9042"), "不得静默套用北京默认坐标");
    }

    @Test
    void buildParams_usesProvidedCoordinates() {
        Map<String, Object> birthParams = new HashMap<>();
        birthParams.put("birth_date", "1993-09-18");
        birthParams.put("birth_time", "20:49");
        birthParams.put("birth_place", "茂名");
        birthParams.put("latitude", 21.64);
        birthParams.put("longitude", 110.918);

        Map<String, Object> params = parser.buildVedicAstroParams(birthParams);
        assertEquals(21.64, ((Number) params.get("latitude")).doubleValue(), 1e-9);
        assertEquals(110.918, ((Number) params.get("longitude")).doubleValue(), 1e-9);
        assertEquals(1993, params.get("year"));
        assertEquals(20, params.get("hour"));
    }
}
