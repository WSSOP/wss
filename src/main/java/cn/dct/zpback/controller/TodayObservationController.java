package cn.dct.zpback.controller;

import cn.dct.zpback.dto.ApiResponse;
import cn.dct.zpback.engine.daily.VedicPlanetaryInfluenceEngine.PlanetPos;
import cn.dct.zpback.service.daily.PublicObservationService;
import cn.dct.zpback.service.daily.TodayObservationService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.*;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 今日观星控制器
 *
 * POST /v1/daily/today-observation
 *
 * 公共层（Moon 时间轴 / 关键词 / 一句话）+ 个性化层（四领域评分 / Top3）。
 * 默认 0 LLM：全部为确定性计算与固定文案。
 */
@RestController
@RequestMapping("/v1/daily")
@RequiredArgsConstructor
@Slf4j
@SuppressWarnings("unchecked")
public class TodayObservationController {

    private final TodayObservationService todayObservationService;

    /**
     * POST /v1/daily/today-observation
     *
     * 请求体：
     * {
     *   "local_date": "2026-08-15",
     *   "timezone": "Asia/Shanghai",
     *   "moon_day_segments": [
     *      {"start_local":"00:00","end_local":"06:13","moon_sign":"Leo","moon_nakshatra":"Purva Phalguni"},
     *      ...
     *   ],
     *   "next_nakshatra_change_at_local": "06:13",
     *   "next_nakshatra_change_is_tomorrow": false,
     *   "natal": { "asc_sign_index": 0,
     *              "planets": [{"planet":"Sun","sign_index":8,"degree_in_sign":4.6}, ...] },
     *   "transit": { "planets": [ ... ] },
     *   "yesterday_top3_signatures": ["P2P|Saturn|Moon|graha_drishti|7"],
     *   "consecutive_days": {"P2P|...": 2}
     * }
     */
    @PostMapping("/today-observation")
    public ApiResponse<Map<String, Object>> todayObservation(@RequestBody Map<String, Object> request) {
        try {
            List<PublicObservationService.DaySegment> segments = parseSegments(request.get("moon_day_segments"));
            String localDate = (String) request.get("local_date");
            String timezone = (String) request.get("timezone");
            String nextChange = (String) request.get("next_nakshatra_change_at_local");
            boolean nextTomorrow = Boolean.TRUE.equals(request.get("next_nakshatra_change_is_tomorrow"));

            Integer ascIndex = null;
            List<PlanetPos> natalPlanets = null;
            Double latitude = null;
            Double longitude = null;
            Map<String, Object> natal = (Map<String, Object>) request.get("natal");
            if (natal != null) {
                Object asc = natal.get("asc_sign_index");
                if (asc instanceof Number) {
                    ascIndex = ((Number) asc).intValue();
                }
                natalPlanets = parsePlanets(natal.get("planets"));
                // 复用本命坐标：流年快照同样需要合法坐标，不能用 0 顶替
                if (natal.get("latitude") instanceof Number) {
                    latitude = ((Number) natal.get("latitude")).doubleValue();
                }
                if (natal.get("longitude") instanceof Number) {
                    longitude = ((Number) natal.get("longitude")).doubleValue();
                }
            }

            Map<String, Object> transit = (Map<String, Object>) request.get("transit");
            List<PlanetPos> transitPlanets = transit == null
                    ? new ArrayList<>() : parsePlanets(transit.get("planets"));

            List<String> yesterday = (List<String>) request.get("yesterday_top3_signatures");
            Map<String, Integer> consecutive = new LinkedHashMap<>();
            Object cd = request.get("consecutive_days");
            if (cd instanceof Map) {
                for (Map.Entry<String, Object> e : ((Map<String, Object>) cd).entrySet()) {
                    if (e.getValue() instanceof Number) {
                        consecutive.put(e.getKey(), ((Number) e.getValue()).intValue());
                    }
                }
            }

            Map<String, Object> data = todayObservationService.build(
                    localDate, timezone, segments,
                    str(request.get("current_local_time")),
                    nextChange, nextTomorrow,
                    ascIndex, natalPlanets, transitPlanets,
                    latitude, longitude, yesterday, consecutive);
            data.put("versions", todayObservationService.versions());
            return ApiResponse.success(data);
        } catch (Exception e) {
            log.error("今日观星计算失败: {}", e.getMessage(), e);
            return ApiResponse.error(500, "今日观星计算失败：" + e.getMessage());
        }
    }

    @SuppressWarnings("unchecked")
    private List<PublicObservationService.DaySegment> parseSegments(Object raw) {
        List<PublicObservationService.DaySegment> out = new ArrayList<>();
        if (!(raw instanceof List)) {
            return out;
        }
        for (Object o : (List<Object>) raw) {
            if (!(o instanceof Map)) {
                continue;
            }
            Map<String, Object> m = (Map<String, Object>) o;
            PublicObservationService.DaySegment s = new PublicObservationService.DaySegment();
            s.setStartLocal(str(m.get("start_local")));
            s.setEndLocal(str(m.get("end_local")));
            s.setMoonSign(str(m.get("moon_sign")));
            s.setMoonNakshatra(str(m.get("moon_nakshatra")));
            out.add(s);
        }
        return out;
    }

    @SuppressWarnings("unchecked")
    private List<PlanetPos> parsePlanets(Object raw) {
        List<PlanetPos> out = new ArrayList<>();
        if (!(raw instanceof List)) {
            return out;
        }
        for (Object o : (List<Object>) raw) {
            if (!(o instanceof Map)) {
                continue;
            }
            Map<String, Object> m = (Map<String, Object>) o;
            String planet = str(m.get("planet"));
            Object si = m.get("sign_index");
            Object deg = m.get("degree_in_sign");
            if (planet == null || !(si instanceof Number)) {
                continue;
            }
            double d = deg instanceof Number ? ((Number) deg).doubleValue() : 0;
            out.add(new PlanetPos(planet, ((Number) si).intValue(), d));
        }
        return out;
    }

    private static String str(Object o) {
        return o == null ? null : String.valueOf(o);
    }
}
