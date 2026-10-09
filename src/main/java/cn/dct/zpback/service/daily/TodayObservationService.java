package cn.dct.zpback.service.daily;

import cn.dct.zpback.engine.daily.DailyObservationAssets;
import cn.dct.zpback.engine.daily.VedicPlanetaryInfluenceEngine.PlanetPos;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 今日观星统一组装服务
 *
 * 对应《APP开发文档 V1.7》1.1.15 建议返回结构：
 *   { local_date, timezone, public_observation, personalized_available, personalized }
 *
 * 边界：
 * - 公共层（Moon 时间轴 / 关键词 / 一句话）与个性化层（评分 / Top3）严格分离；
 * - 无本人星盘时 personalized_available = false、personalized = null；
 * - 默认 0 LLM：本服务不调用任何模型。
 */
@Service
@Slf4j
public class TodayObservationService {

    @Autowired
    private DailyObservationAssets assets;
    @Autowired
    private PublicObservationService publicObservationService;
    @Autowired
    private DailyPersonalScoreService scoreService;
    @Autowired
    private Top3ImpactService top3Service;
    @Autowired
    private TransitSnapshotService transitSnapshotService;
    @Autowired
    private MoonDailyTimelineService moonDailyTimelineService;

    /**
     * 组装今日观星响应
     *
     * @param localDate          用户当地自然日 YYYY-MM-DD
     * @param timezone           用户 IANA 时区
     * @param segments           当天 moon_day_segments；为空时由 MoonDailyTimelineService 现算
     * @param currentLocalTime   当前本地时刻 HH:mm
     * @param nextNakChangeLocal 下一星宿切换本地时间 HH:mm；为空时由时间轴推导
     * @param nextNakIsTomorrow  是否次日
     * @param natalAscSignIndex  本命上升星座序号（无本人星盘传 null）
     * @param natalPlanets       本命九大行星位置（无本人星盘传 null）
     * @param transitPlanets     流年九大行星位置；为空时由 TransitSnapshotService 现算
     * @param yesterdaySignatures 昨日 Top3 impact_signature（可空）
     * @param consecutiveDaysOf   signature → 连续天数（可空）
     */
    public Map<String, Object> build(String localDate, String timezone,
                                     List<PublicObservationService.DaySegment> segments,
                                     String currentLocalTime,
                                     String nextNakChangeLocal, boolean nextNakIsTomorrow,
                                     Integer natalAscSignIndex,
                                     List<PlanetPos> natalPlanets,
                                     List<PlanetPos> transitPlanets,
                                     Double latitude, Double longitude,
                                     List<String> yesterdaySignatures,
                                     Map<String, Integer> consecutiveDaysOf) {

        java.time.LocalDate date = parseDate(localDate);
        String tz = (timezone == null || timezone.trim().isEmpty()) ? "Asia/Shanghai" : timezone;
        String now = (currentLocalTime == null || currentLocalTime.trim().isEmpty())
                ? java.time.LocalTime.now(java.time.ZoneId.of(tz)).withSecond(0).withNano(0).toString()
                : currentLocalTime;
        if (now.length() > 5) {
            now = now.substring(0, 5);
        }

        // 公共层时间轴：缺省由 MoonDailyTimelineService 现算（粗扫 + 二分）
        if (segments == null || segments.isEmpty()) {
            segments = moonDailyTimelineService.segmentsFor(date, tz, latitude, longitude);
        }
        // 下一星宿切换时间：缺省从时间轴推导
        String nextChange = nextNakChangeLocal;
        if ((nextChange == null || nextChange.trim().isEmpty()) && segments != null) {
            nextChange = nextChangeAfter(segments, now);
        }

        Map<String, Object> resp = new LinkedHashMap<>();
        resp.put("local_date", localDate);
        resp.put("timezone", tz);

        PublicObservationService.PublicObservation po =
                publicObservationService.build(segments, now, nextChange, nextNakIsTomorrow);
        Map<String, Object> publicObs = new LinkedHashMap<>();
        publicObs.put("moon_sign", po.getMoonSign());
        publicObs.put("moon_sign_cn", po.getMoonSignCn());
        publicObs.put("nakshatra", po.getNakshatra());
        publicObs.put("nakshatra_cn", po.getNakshatraCn());
        publicObs.put("next_nakshatra", po.getNextNakshatra());
        publicObs.put("next_nakshatra_cn", po.getNextNakshatraCn());
        publicObs.put("next_change_at_local", po.getNextChangeAtLocal());
        publicObs.put("next_change_is_tomorrow", po.isNextChangeIsTomorrow());
        publicObs.put("keywords", po.getKeywords());
        publicObs.put("daily_summary", po.getDailySummary());
        // 顶部月亮卡：当前时段 + 今日经历（仅当当天有星宿切换时前端才展示经历行）
        publicObs.put("current_segment_start", po.getCurrentSegmentStart());
        publicObs.put("current_segment_end", po.getCurrentSegmentEnd());
        List<Map<String, Object>> journey = new ArrayList<>();
        for (PublicObservationService.JourneyStop s : po.getJourney()) {
            Map<String, Object> m = new LinkedHashMap<>();
            m.put("start_local", s.getStartLocal());
            m.put("nakshatra", s.getNakshatra());
            m.put("nakshatra_cn", s.getNakshatraCn());
            m.put("moon_sign", s.getMoonSign());
            m.put("moon_sign_cn", s.getMoonSignCn());
            journey.add(m);
        }
        publicObs.put("today_journey", journey);
        resp.put("public_observation", publicObs);

        // ---- 个性化层 ----
        boolean hasNatal = natalAscSignIndex != null && natalPlanets != null && !natalPlanets.isEmpty();

        // 流年快照：缺省现算；拿不到就绝不返回一份“全是基础分”的假分数
        if (hasNatal && (transitPlanets == null || transitPlanets.isEmpty())) {
            TransitSnapshotService.Snapshot snap = transitSnapshotService.snapshotAt(
                    java.time.LocalDateTime.of(date,
                            java.time.LocalTime.parse(now.length() == 5 ? now : "00:00")),
                    tz, latitude, longitude);
            transitPlanets = snap.getPlanets();
        }
        boolean hasTransit = transitPlanets != null && !transitPlanets.isEmpty();

        resp.put("personalized_available", hasNatal && hasTransit);
        if (!hasNatal || !hasTransit) {
            resp.put("personalized", null);
            String reason = !hasNatal ? "NO_NATAL_CHART" : "TRANSIT_UNAVAILABLE";
            resp.put("personalized_unavailable_reason", reason);
            // 注意：hasNatal=false 时根本不会去取流年，此时报“流年快照为空”是误导
            if (!hasNatal) {
                log.info("今日观星个性化不可用：未传入本命盘（NO_NATAL_CHART），仅返回公共层");
            } else {
                log.warn("今日观星个性化不可用：本命盘已传入但流年快照为空（TRANSIT_UNAVAILABLE），已拒绝返回基础分占位数据");
            }
            return resp;
        }

        DailyPersonalScoreService.ScoreResult scoreResult =
                scoreService.computeScores(natalAscSignIndex, natalPlanets, transitPlanets);

        Map<String, Object> scores = new LinkedHashMap<>();
        for (DailyPersonalScoreService.DomainScore ds : scoreResult.getScores()) {
            Map<String, Object> one = new LinkedHashMap<>();
            one.put("score", ds.getScore());
            one.put("sentence", ds.getSentence());
            scores.put(ds.getDomain(), one);
        }

        List<Top3ImpactService.Top3Item> top3 =
                top3Service.computeTop3(scoreResult.getEvidence(), yesterdaySignatures, consecutiveDaysOf);
        List<Map<String, Object>> topImpacts = new ArrayList<>();
        for (Top3ImpactService.Top3Item item : top3) {
            Map<String, Object> one = new LinkedHashMap<>();
            one.put("title", item.getTitle());
            one.put("summary", item.getSummary());
            one.put("astrology_evidence", item.getAstrologyEvidence());
            one.put("impact_domains", item.getImpactDomains());
            // final_rank_score / impact_signature / repeat_factor 仅后端使用，不向普通用户展示
            topImpacts.add(one);
        }

        Map<String, Object> personalized = new LinkedHashMap<>();
        personalized.put("transit_snapshot_id", null);
        personalized.put("scores", scores);
        personalized.put("top_impacts", topImpacts);
        resp.put("personalized", personalized);
        return resp;
    }

    /** 版本信息（便于前端 / 日志核对） */
    public Map<String, String> versions() {
        Map<String, String> v = new LinkedHashMap<>();
        v.put("score_rules_version", assets.getScoreRules().getVersion());
        v.put("daily_copy_version", assets.getDailyCopy().getVersion());
        v.put("engine", "vedic_planetary_influence_v1");
        return v;
    }

    private static java.time.LocalDate parseDate(String localDate) {
        try {
            return java.time.LocalDate.parse(localDate);
        } catch (Exception e) {
            return java.time.LocalDate.now();
        }
    }

    /** 当前时刻之后的下一个星宿切换时间（HH:mm）；无则返回 null */
    private static String nextChangeAfter(List<PublicObservationService.DaySegment> segments, String now) {
        int nowMin = toMinutes(now);
        for (PublicObservationService.DaySegment s : segments) {
            int start = toMinutes(s.getStartLocal());
            if (start > nowMin) {
                return s.getStartLocal();
            }
        }
        return null;
    }

    private static int toMinutes(String hhmm) {
        if (hhmm == null) {
            return 0;
        }
        String[] p = hhmm.split(":");
        try {
            return Integer.parseInt(p[0]) * 60 + (p.length > 1 ? Integer.parseInt(p[1]) : 0);
        } catch (Exception e) {
            return 0;
        }
    }
}
