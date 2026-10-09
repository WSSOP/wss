package cn.dct.zpback.service.daily;

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZonedDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Moon Daily Timeline Builder
 *
 * 对应《APP开发文档 V1.7》1.1.6：
 * - 粗扫步长 2 小时（rough_scan_interval，配置项）；
 * - 相邻查询点 moon_nakshatra / moon_sign 不同 → 在该区间二分查找；
 * - 边界终止精度 ≤ 60 秒；
 * - 程序不得假设一天最多只有 2 个 segment。
 *
 * 结果按「本地自然日 + 时区」缓存：同一天内所有用户共享同一时间轴。
 */
@Service
@Slf4j
public class MoonDailyTimelineService {

    private static final int ROUGH_STEP_MINUTES = 120;
    private static final long BINARY_STOP_SECONDS = 60;

    @Autowired
    private TransitSnapshotService transitSnapshotService;

    private final Map<String, List<PublicObservationService.DaySegment>> dayCache = new ConcurrentHashMap<>();

    /** 本地自然日的月亮时间轴 */
    public List<PublicObservationService.DaySegment> segmentsFor(LocalDate date, String timezone) {
        return segmentsFor(date, timezone, null, null);
    }

    public List<PublicObservationService.DaySegment> segmentsFor(LocalDate date, String timezone,
                                                                 Double latitude, Double longitude) {
        String key = date + "|" + (timezone == null ? "Asia/Shanghai" : timezone);
        List<PublicObservationService.DaySegment> cached = dayCache.get(key);
        if (cached != null) {
            return cached;
        }

        List<PublicObservationService.DaySegment> segments = build(date, timezone, latitude, longitude);
        if (!segments.isEmpty()) {
            dayCache.put(key, segments);
        }
        return segments;
    }

    private List<PublicObservationService.DaySegment> build(LocalDate date, String timezone,
                                                            Double latitude, Double longitude) {
        ZonedDateTime dayStart = transitSnapshotService.dayStart(date, timezone);
        int totalMinutes = 24 * 60;

        // 1) 粗扫取样：每 2 小时一个点（含 00:00 与 24:00 边界）
        List<Sample> samples = new ArrayList<>();
        for (int m = 0; m <= totalMinutes; m += ROUGH_STEP_MINUTES) {
            Sample s = sampleAt(dayStart, m, timezone, latitude, longitude);
            if (s == null) {
                return new ArrayList<>(); // 星历不可用：不伪造时间轴
            }
            samples.add(s);
        }

        // 2) 相邻点状态不同 → 二分查找边界（≤60 秒）
        List<Integer> boundaries = new ArrayList<>();
        for (int i = 1; i < samples.size(); i++) {
            Sample prev = samples.get(i - 1);
            Sample cur = samples.get(i);
            if (!prev.sameState(cur)) {
                int loMin = samples.get(i - 1).minute;
                int hiMin = samples.get(i).minute;
                int boundary = refine(dayStart, loMin, hiMin, prev, timezone, latitude, longitude);
                boundaries.add(boundary);
            }
        }

        // 3) 按边界切分 segment
        List<PublicObservationService.DaySegment> out = new ArrayList<>();
        int cursor = 0;
        for (int b : boundaries) {
            if (b <= cursor) {
                continue;
            }
            out.add(segment(dayStart, cursor, b, timezone, latitude, longitude));
            cursor = b;
        }
        out.add(segment(dayStart, cursor, totalMinutes, timezone, latitude, longitude));
        log.info("Moon 时间轴生成: date={}, tz={}, segments={}", date, timezone, out.size());
        return out;
    }

    /** 二分查找状态变化点（分钟精度），终止精度 ≤ 60 秒 */
    private int refine(ZonedDateTime dayStart, int loMin, int hiMin, Sample leftState, String timezone,
                       Double latitude, Double longitude) {
        int lo = loMin;
        int hi = hiMin;
        while ((hi - lo) * 60L > BINARY_STOP_SECONDS) {
            int mid = (lo + hi) / 2;
            Sample midState = sampleAt(dayStart, mid, timezone, latitude, longitude);
            if (midState == null) {
                return hi;
            }
            if (midState.sameState(leftState)) {
                lo = mid;
            } else {
                hi = mid;
            }
        }
        return hi;
    }

    private PublicObservationService.DaySegment segment(ZonedDateTime dayStart, int startMin,
                                                        int endMin, String timezone,
                                                        Double latitude, Double longitude) {
        Sample s = sampleAt(dayStart, startMin, timezone, latitude, longitude);
        PublicObservationService.DaySegment seg = new PublicObservationService.DaySegment();
        seg.setStartLocal(hhmm(startMin));
        seg.setEndLocal(hhmm(endMin));
        if (s != null) {
            seg.setMoonSign(s.sign);
            seg.setMoonNakshatra(s.nakshatra);
        }
        return seg;
    }

    private Sample sampleAt(ZonedDateTime dayStart, int minute, String timezone,
                            Double latitude, Double longitude) {
        LocalDateTime local = dayStart.toLocalDateTime().plusMinutes(minute);
        TransitSnapshotService.MoonState st =
                transitSnapshotService.moonStateAt(local, timezone, latitude, longitude);
        if (!st.isFound()) {
            return null;
        }
        Sample s = new Sample();
        s.minute = minute;
        s.sign = st.getSign();
        s.nakshatra = st.getNakshatra();
        return s;
    }

    private static String hhmm(int minutes) {
        if (minutes >= 24 * 60) {
            return "24:00";
        }
        return String.format("%02d:%02d", minutes / 60, minutes % 60);
    }

    private static class Sample {
        int minute;
        String sign;
        String nakshatra;

        boolean sameState(Sample o) {
            return o != null && eq(sign, o.sign) && eq(nakshatra, o.nakshatra);
        }

        private static boolean eq(String a, String b) {
            return a == null ? b == null : a.equals(b);
        }
    }
}
