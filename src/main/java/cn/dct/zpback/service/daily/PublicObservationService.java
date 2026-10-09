package cn.dct.zpback.service.daily;

import cn.dct.zpback.constant.EphemerisNameNormalizer;
import cn.dct.zpback.dto.daily.DailyObservationDtos;
import cn.dct.zpback.engine.daily.DailyObservationAssets;
import lombok.Data;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 公共今日观星（Moon 时间轴 → 关键词 + 一句话摘要）
 *
 * 对应《APP开发文档 V1.7》1.1.7：
 * - 内容唯一来源《27星宿公共日运文案配置表 V1.9》；
 * - 单星宿日：keyword_1 + keyword_2 + keyword_3；
 * - 多星宿日：按时长聚合，最长星宿贡献 keyword_1+keyword_2，第二长贡献 keyword_1，
 *   时长相同则较晚进入当天的星宿获得 2 个关键词；
 * - 公共一句话按 CASE_1 / CASE_2 / CASE_3 / CASE_4A / 4B / 4C 确定性拼接；
 * - 禁止创造“过渡关键词”，禁止 LLM 生成。
 *
 * 本 Service 不负责 Moon 边界搜索：`moon_day_segments` 由上游 MoonEventBuilder 提供。
 */
@Service
@Slf4j
public class PublicObservationService {

    @Autowired
    private DailyObservationAssets assets;

    /** 当地自然日内的一个星宿 / 星座时间段 */
    @Data
    public static class DaySegment {
        private String startLocal;   // HH:mm
        private String endLocal;     // HH:mm（末段可为 24:00）
        private String moonSign;     // 英文星座名
        private String moonNakshatra; // 英文星宿名
    }

    /** 今日经历的一次「停留」：只按星宿变化切分（同一星宿跨星座不产生新停留） */
    @Data
    public static class JourneyStop {
        private String startLocal;   // 进入该星宿的本地时刻
        private String nakshatra;
        private String nakshatraCn;
        private String moonSign;
        private String moonSignCn;
    }

    /** 公共观察结果 */
    @Data
    public static class PublicObservation {
        private String moonSign;
        private String moonSignCn;
        private String nakshatra;
        private String nakshatraCn;
        private String nextNakshatra;
        private String nextNakshatraCn;
        private String nextChangeAtLocal;
        private boolean nextChangeIsTomorrow;
        private List<String> keywords = new ArrayList<>();
        private String dailySummary;
        private String caseKey;
        /** 当天出现过的星宿（按时长降序，仅后端使用） */
        private List<String> nakshatrasToday = new ArrayList<>();
        /** 当前所在时段的起止（HH:mm），顶部月亮卡展示「13:05–24:00」 */
        private String currentSegmentStart;
        private String currentSegmentEnd;
        /** 今日经历（按星宿去重，首段必含）；长度为 1 表示当天没有星宿切换 */
        private List<JourneyStop> journey = new ArrayList<>();
    }

    /**
     * 生成公共今日观星
     *
     * @param segments            当天 moon_day_segments（按时序，不得假设最多 2 段）
     * @param currentLocalTime    当前本地时刻 HH:mm，用于定位“现在”落在哪个 segment
     * @param nextNakChangeAtLocal 下一次星宿切换的本地时间（HH:mm）
     * @param nextNakIsTomorrow    该切换是否在次日
     */
    public PublicObservation build(List<DaySegment> segments,
                                   String currentLocalTime,
                                   String nextNakChangeAtLocal,
                                   boolean nextNakIsTomorrow) {
        PublicObservation out = new PublicObservation();
        if (segments == null || segments.isEmpty()) {
            return out;
        }
        segments = normalizeSegments(segments);

        // ---- 当前状态：按当前本地时刻定位所在 segment（缺省取最后一段）----
        DaySegment current = segments.get(segments.size() - 1);
        int now = toMinutes(currentLocalTime);
        if (currentLocalTime != null) {
            for (DaySegment s : segments) {
                if (now >= toMinutes(s.getStartLocal()) && now < toMinutes(s.getEndLocal())) {
                    current = s;
                    break;
                }
            }
        }
        out.setMoonSign(current.getMoonSign());
        DailyObservationDtos.SignCopy signCopy = assets.sign(current.getMoonSign());
        out.setMoonSignCn(signCopy == null ? null : signCopy.getSign_cn());
        out.setNakshatra(current.getMoonNakshatra());
        DailyObservationDtos.NakshatraCopy nakCopy = assets.nakshatra(current.getMoonNakshatra());
        out.setNakshatraCn(nakCopy == null ? null : nakCopy.getNakshatra_cn());

        // ---- 当前时段起止 + 今日经历（顶部月亮卡）----
        out.setCurrentSegmentStart(current.getStartLocal());
        out.setCurrentSegmentEnd(current.getEndLocal());
        out.setJourney(buildJourney(segments));

        // ---- 下一星宿切换（结构化字段，前端自行拼同日 / 次日）----
        String nextNakEn = nextNakshatraOf(segments, current.getMoonNakshatra());
        if (nextNakEn != null) {
            DailyObservationDtos.NakshatraCopy nc = assets.nakshatra(nextNakEn);
            out.setNextNakshatra(nextNakEn);
            out.setNextNakshatraCn(nc == null ? null : nc.getNakshatra_cn());
        }
        out.setNextChangeAtLocal(nextNakChangeAtLocal);
        out.setNextChangeIsTomorrow(nextNakIsTomorrow);

        // ---- 按时长聚合当天星宿 ----
        List<Duration> durations = aggregate(segments);
        out.setNakshatrasToday(durations.stream().map(d -> d.name).toList());

        // ---- 3 个关键词 ----
        out.setKeywords(pickKeywords(durations));

        // ---- 公共一句话 ----
        CaseResult caseResult = buildSummary(segments, durations);
        out.setDailySummary(caseResult.text);
        out.setCaseKey(caseResult.key);
        return out;
    }

    /**
     * 星历拼写 → 锁定表命名
     *
     * 上游（MoonDailyTimelineService / 前端可能直接传入的 segments）给出的英文名可能是
     * Shatabhishak / Kritika 这类变体，先对齐再查表，否则中文名取不到（前端出现空括号）。
     */
    private List<DaySegment> normalizeSegments(List<DaySegment> in) {
        List<DaySegment> out = new ArrayList<>(in.size());
        for (DaySegment s : in) {
            DaySegment copy = new DaySegment();
            copy.setStartLocal(s.getStartLocal());
            copy.setEndLocal(s.getEndLocal());
            copy.setMoonSign(EphemerisNameNormalizer.normalizeSign(s.getMoonSign()));
            copy.setMoonNakshatra(EphemerisNameNormalizer.normalizeNakshatra(s.getMoonNakshatra()));
            out.add(copy);
        }
        return out;
    }

    /**
     * 今日经历：按星宿去重
     *
     * 星座切换但星宿未变（如摩羯 Dhanishta → 水瓶 Dhanishta）不产生新停留，
     * 停留的 startLocal 取该星宿连续时段的起点。
     */
    private List<JourneyStop> buildJourney(List<DaySegment> segments) {
        List<JourneyStop> out = new ArrayList<>();
        String prevNak = null;
        for (DaySegment s : segments) {
            String nak = s.getMoonNakshatra();
            if (nak == null || nak.equals(prevNak)) {
                continue;
            }
            JourneyStop stop = new JourneyStop();
            stop.setStartLocal(s.getStartLocal());
            stop.setNakshatra(nak);
            DailyObservationDtos.NakshatraCopy nc = assets.nakshatra(nak);
            stop.setNakshatraCn(nc == null ? null : nc.getNakshatra_cn());
            stop.setMoonSign(s.getMoonSign());
            DailyObservationDtos.SignCopy sc = assets.sign(s.getMoonSign());
            stop.setMoonSignCn(sc == null ? null : sc.getSign_cn());
            out.add(stop);
            prevNak = nak;
        }
        return out;
    }

    private static class Duration {
        String name;
        int minutes;
        int firstStart;  // 用于“较晚进入”比较
    }

    /** 按时长聚合（分钟），同长时以“较晚进入当天”优先 */
    private List<Duration> aggregate(List<DaySegment> segments) {
        Map<String, Duration> map = new LinkedHashMap<>();
        for (DaySegment s : segments) {
            if (s.getMoonNakshatra() == null) {
                continue;
            }
            Duration d = map.computeIfAbsent(s.getMoonNakshatra(), k -> {
                Duration nd = new Duration();
                nd.name = k;
                nd.minutes = 0;
                nd.firstStart = Integer.MAX_VALUE;
                return nd;
            });
            d.minutes += minutesBetween(s.getStartLocal(), s.getEndLocal());
            d.firstStart = Math.min(d.firstStart, toMinutes(s.getStartLocal()));
        }
        List<Duration> list = new ArrayList<>(map.values());
        list.sort(Comparator.comparingInt((Duration d) -> d.minutes).reversed()
                .thenComparing((Duration d) -> d.firstStart, Comparator.reverseOrder()));
        return list;
    }

    /** 关键词：单星宿 3 个；多星宿 2 + 1 */
    private List<String> pickKeywords(List<Duration> durations) {
        List<String> out = new ArrayList<>();
        if (durations.isEmpty()) {
            return out;
        }
        if (durations.size() == 1) {
            DailyObservationDtos.NakshatraCopy n = assets.nakshatra(durations.get(0).name);
            if (n != null) {
                addIfPresent(out, n.getKeyword_1());
                addIfPresent(out, n.getKeyword_2());
                addIfPresent(out, n.getKeyword_3());
            }
            return out;
        }
        DailyObservationDtos.NakshatraCopy first = assets.nakshatra(durations.get(0).name);
        DailyObservationDtos.NakshatraCopy second = assets.nakshatra(durations.get(1).name);
        if (first != null) {
            addIfPresent(out, first.getKeyword_1());
            addIfPresent(out, first.getKeyword_2());
        }
        if (second != null) {
            addIfPresent(out, second.getKeyword_1());
        }
        return out;
    }

    private static void addIfPresent(List<String> list, String v) {
        if (v != null && !v.isEmpty() && !list.contains(v)) {
            list.add(v);
        }
    }

    private static class CaseResult {
        String key;
        String text;
    }

    /**
     * 公共一句话 CASE 规则（1.1.7 B）
     *
     * 变化事件按时间顺序取自 segments 的边界；星宿事件与星座事件分别识别。
     */
    private CaseResult buildSummary(List<DaySegment> segments, List<Duration> durations) {
        CaseResult r = new CaseResult();

        // 识别变化事件（按时间序）
        List<Change> nakChanges = new ArrayList<>();
        List<Change> signChanges = new ArrayList<>();
        for (int i = 1; i < segments.size(); i++) {
            DaySegment prev = segments.get(i - 1);
            DaySegment cur = segments.get(i);
            if (!eq(prev.getMoonNakshatra(), cur.getMoonNakshatra())) {
                Change c = new Change();
                c.time = cur.getStartLocal();
                c.targetNak = cur.getMoonNakshatra();
                c.targetSign = cur.getMoonSign();
                nakChanges.add(c);
            }
            if (!eq(prev.getMoonSign(), cur.getMoonSign())) {
                Change c = new Change();
                c.time = cur.getStartLocal();
                c.targetSign = cur.getMoonSign();
                c.targetNak = cur.getMoonNakshatra();
                signChanges.add(c);
            }
        }

        // CASE_1：全天同一星宿
        if (nakChanges.isEmpty() && signChanges.isEmpty()) {
            DailyObservationDtos.NakshatraCopy n = assets.nakshatra(segments.get(0).getMoonNakshatra());
            r.key = "CASE_1";
            r.text = n == null ? null : n.getDaily_core_sentence();
            return r;
        }

        // CASE_4B：星宿与星座近似同时变化（≤60 秒，本地 HH:mm 精度下即同一分钟）
        if (!nakChanges.isEmpty() && !signChanges.isEmpty()) {
            Change nk = nakChanges.get(0);
            Change sg = signChanges.get(0);
            if (eq(nk.time, sg.time)) {
                DailyObservationDtos.NakshatraCopy nn = assets.nakshatra(nk.targetNak);
                DailyObservationDtos.SignCopy ss = assets.sign(sg.targetSign);
                r.key = "CASE_4B";
                r.text = "今天会在" + nk.time + "出现一次明显转向：更适合"
                        + safe(nn == null ? null : nn.getTransition_action())
                        + "，表达上也更偏向" + safe(ss == null ? null : ss.getSign_transition_phrase()) + "。";
                return r;
            }
        }

        // CASE_4C：变化事件 ≥3
        int totalChanges = nakChanges.size() + signChanges.size();
        if (totalChanges >= 3) {
            List<String> parts = new ArrayList<>();
            List<Change> all = new ArrayList<>();
            all.addAll(nakChanges);
            all.addAll(signChanges);
            all.sort(Comparator.comparing(c -> toMinutes(c.time)));
            for (int i = 0; i < Math.min(3, all.size()); i++) {
                Change c = all.get(i);
                parts.add(c.time + "后" + targetPhrase(c));
            }
            r.key = "CASE_4C";
            r.text = "今天节奏会多次调整：" + String.join("，", parts) + "。";
            return r;
        }

        // CASE_4A：既跨星宿又跨星座，两个事件不同时间
        if (!nakChanges.isEmpty() && !signChanges.isEmpty()) {
            Change e1 = nakChanges.get(0);
            Change e2 = signChanges.get(0);
            List<Change> ordered = new ArrayList<>(List.of(e1, e2));
            ordered.sort(Comparator.comparing(c -> toMinutes(c.time)));
            r.key = "CASE_4A";
            r.text = "今天有两次节奏变化：" + ordered.get(0).time + "后更适合" + targetPhrase(ordered.get(0))
                    + "，" + ordered.get(1).time + "后再转向" + targetPhrase(ordered.get(1)) + "。";
            return r;
        }

        // CASE_2：跨星宿、不跨星座
        if (!nakChanges.isEmpty()) {
            Change c = nakChanges.get(0);
            DailyObservationDtos.NakshatraCopy cur = assets.nakshatra(segments.get(0).getMoonNakshatra());
            DailyObservationDtos.NakshatraCopy next = assets.nakshatra(c.targetNak);
            r.key = "CASE_2";
            r.text = "先" + safe(cur == null ? null : cur.getTransition_action()) + "，"
                    + c.time + "后更适合" + safe(next == null ? null : next.getTransition_action()) + "。";
            return r;
        }

        // CASE_3：跨星座、不跨星宿
        Change c = signChanges.get(0);
        DailyObservationDtos.NakshatraCopy cur = assets.nakshatra(segments.get(0).getMoonNakshatra());
        DailyObservationDtos.SignCopy newSign = assets.sign(c.targetSign);
        r.key = "CASE_3";
        r.text = "今天的主线仍是" + safe(cur == null ? null : cur.getTransition_action()) + "，"
                + c.time + "后会更偏向" + safe(newSign == null ? null : newSign.getSign_transition_phrase()) + "。";
        return r;
    }

    private static class Change {
        String time;
        String targetNak;
        String targetSign;
    }

    private String targetPhrase(Change c) {
        if (c.targetNak != null) {
            DailyObservationDtos.NakshatraCopy n = assets.nakshatra(c.targetNak);
            if (n != null) {
                return safe(n.getTransition_action());
            }
        }
        DailyObservationDtos.SignCopy s = assets.sign(c.targetSign);
        return safe(s == null ? null : s.getSign_transition_phrase());
    }

    private static String safe(String v) {
        return v == null ? "" : v;
    }

    private static boolean eq(String a, String b) {
        return a == null ? b == null : a.equals(b);
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

    private static int minutesBetween(String start, String end) {
        return Math.max(0, toMinutes(end) - toMinutes(start));
    }

    /** 下一星宿：取当天最后一段之后的下一段星宿；若当天只有一段则回落到“下一星宿”字段由上游给出 */
    private String nextNakshatraOf(List<DaySegment> segments, String currentNak) {
        for (int i = segments.size() - 1; i >= 0; i--) {
            String n = segments.get(i).getMoonNakshatra();
            if (n != null && !n.equals(currentNak)) {
                return n;
            }
        }
        return null;
    }
}
