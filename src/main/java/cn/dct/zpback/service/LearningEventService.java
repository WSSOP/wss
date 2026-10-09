package cn.dct.zpback.service;

import cn.dct.zpback.entity.*;
import cn.dct.zpback.exception.LearningValidationException;
import cn.dct.zpback.mapper.*;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;

/**
 * 问星渡｜主题活动 Service
 *
 * 对应文档 14_API_Contract：「Event Service处理报名权限」
 * 以及 04_Final_System_Map 的「Event: Registration + Attendance」。
 *
 * 活动侧与课程侧<b>刻意分开</b>（文档要求 Domain Service 独立）：
 * 场次表、计数字段名（registered_count vs enrolled_count）、
 * 归属字段（host_id vs teacher_id）都不同，混在一起必然出现分支判断。
 *
 * <b>规格来源</b>：原始 skill 目录共 3993 字节，
 * 与本类相关的只有「Domain 独立」这一条原则。
 * 字段、枚举、状态机均为实现方所拟，<b>不是原始规格</b>。
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class LearningEventService {

    private final LearningEventMapper eventMapper;
    private final LearningSessionMapper sessionMapper;
    private final LearningEntitlementMapper entitlementMapper;
    private final LearningEnrollmentMapper enrollmentMapper;

    public static final String E_NOT_FOUND = "LEARNING_TARGET_NOT_FOUND";
    public static final String E_NOT_PUBLISHED = "LEARNING_NOT_PUBLISHED";
    public static final String E_SESSION_NOT_FOUND = "LEARNING_SESSION_NOT_FOUND";
    public static final String E_NO_PERMISSION = "LEARNING_NO_ENTITLEMENT";
    public static final String E_ALREADY_ENROLLED = "LEARNING_ALREADY_ENROLLED";
    public static final String E_SEAT_FULL = "LEARNING_SEAT_FULL";
    public static final String E_SESSION_CLOSED = "LEARNING_SESSION_CLOSED";
    public static final String E_REQUIRES_ORDER = "LEARNING_REQUIRES_ORDER";
    public static final String E_FREE_ITEM = "LEARNING_FREE_ITEM";

    public static final String TYPE_EVENT = "EVENT";

    // ==============================================================
    // 浏览
    // ==============================================================

    /** 已发布活动列表。不返回 live_url——未报名者不得拿到直播入口 */
    public List<LearningEvent> listEvents(String eventType) {
        return eventMapper.findPublished(eventType);
    }

    public Map<String, Object> eventDetail(String eventId, String userId) {
        LearningEvent e = eventMapper.findById(eventId);
        if (e == null) throw new LearningValidationException(E_NOT_FOUND, "活动不存在或已删除");
        if (!"PUBLISHED".equals(e.getEventStatus())) {
            throw new LearningValidationException(E_NOT_PUBLISHED, "该活动暂未开放");
        }
        Map<String, Object> out = new LinkedHashMap<>();
        // findById 不含 live_url
        out.put("event", e);
        out.put("sessions", sessionMapper.findEventSessions(eventId));
        out.put("my_entitlement", summarizeEntitlement(userId, eventId, null));
        return out;
    }

    // ==============================================================
    // 报名
    // ==============================================================

    /**
     * 免费活动报名：直接发放权限再报名。
     *
     * 与课程侧同构，但走活动场次表与 registered_count。
     */
    @Transactional
    public Map<String, Object> enrollFree(String eventId, String sessionId, String userId) {
        requireLogin(userId);
        LearningSession session = requireEventSession(eventId, sessionId);

        if (isPaid(session)) {
            throw new LearningValidationException(E_REQUIRES_ORDER, "该场次需要付费购买后才能报名");
        }
        if (entitlementMapper.findActive(userId, TYPE_EVENT, eventId, sessionId) == null) {
            grantFreeEntitlement(userId, eventId, sessionId);
        }
        Map<String, Object> r = doEnroll(userId, eventId, sessionId);
        r.put("entitlement_source", "FREE_GRANT");
        return r;
    }

    @Transactional
    public Map<String, Object> cancelEnrollment(String userId, String sessionId) {
        requireLogin(userId);
        LearningEnrollment existing = enrollmentMapper.findExisting(userId, sessionId);
        if (existing == null) {
            throw new LearningValidationException(E_SESSION_NOT_FOUND, "没有找到报名记录");
        }
        if ("CANCELLED".equals(existing.getEnrollmentStatus())) {
            return cancelView(existing, true);
        }
        if ("ATTENDED".equals(existing.getEnrollmentStatus())) {
            throw new LearningValidationException(E_SESSION_CLOSED, "已核销出勤的场次不可取消");
        }
        if (enrollmentMapper.cancel(userId, sessionId) > 0) {
            sessionMapper.releaseEventSeat(sessionId);
        }
        return cancelView(existing, false);
    }

    // ==============================================================
    // 主持人侧：出勤核销 + 名单
    // ==============================================================

    /**
     * 出勤核销。
     *
     * isAdmin=true 跳过归属校验；否则要求 host_id 命中。
     *
     * 【已知限制】助手主持人（assistant_host）尚未建模，
     * 因此当前只能核验主持人本人。若后续要支持助理代管，
     * 需在 learning_event 增加 assistant_host_id 并在此放开校验。
     */
    @Transactional
    public Map<String, Object> checkAttendance(String sessionId, String studentId,
                                               String status, String operatorId, boolean isAdmin) {
        if (!"ATTENDED".equals(status) && !"ABSENT".equals(status)) {
            throw new LearningValidationException(E_SESSION_CLOSED,
                    "出勤状态只能是 ATTENDED 或 ABSENT");
        }
        LearningSession session = sessionMapper.findEventSessionById(sessionId);
        if (session == null) {
            throw new LearningValidationException(E_SESSION_NOT_FOUND, "场次不存在");
        }
        if (!isAdmin) {
            LearningEvent ev = eventMapper.findById(session.getTargetId());
            if (ev == null || !Objects.equals(ev.getHostId(), operatorId)) {
                throw new LearningValidationException(E_NO_PERMISSION, "只能核验自己主持的活动");
            }
        }
        LearningEnrollment existing = enrollmentMapper.findExisting(studentId, sessionId);
        if (existing == null) {
            throw new LearningValidationException(E_SESSION_NOT_FOUND, "该用户未报名这场");
        }
        if (!"ENROLLED".equals(existing.getEnrollmentStatus())) {
            return attendanceView(existing, true);
        }
        if (enrollmentMapper.checkAttendance(studentId, sessionId, status) == 0) {
            return attendanceView(existing, true);
        }
        // 回填更新后的状态，避免返回更新前快照让主持人以为没点上
        LearningEnrollment updated = enrollmentMapper.findExisting(studentId, sessionId);
        return attendanceView(updated != null ? updated : existing, false);
    }

    public List<LearningEnrollment> sessionRoster(String sessionId) {
        return enrollmentMapper.findBySession(sessionId);
    }

    /** 主持人名下的活动 */
    public List<LearningEvent> listMyEvents(String hostId) {
        return eventMapper.findByHost(hostId);
    }

    // ==============================================================
    // 直播入口（仅已报名者）
    // ==============================================================

    public Map<String, Object> enrolledSession(String sessionId, String userId) {
        LearningSession s = sessionMapper.findEnrolledEventSession(sessionId, userId);
        if (s == null) {
            throw new LearningValidationException(E_NO_PERMISSION, "尚未报名，无法查看该场次");
        }
        Map<String, Object> out = new LinkedHashMap<>();
        out.put("session", s);
        return out;
    }

    // ==============================================================
    // 我的
    // ==============================================================

    public List<LearningEnrollment> myEnrollments(String userId) {
        return userId == null ? List.of() : enrollmentMapper.findActiveByUser(userId);
    }

    public boolean hasEntitlement(String userId, String eventId, String sessionId) {
        return userId != null
                && entitlementMapper.findActive(userId, TYPE_EVENT, eventId, sessionId) != null;
    }

    // ==============================================================
    // 内部
    // ==============================================================

    private Map<String, Object> doEnroll(String userId, String eventId, String sessionId) {
        LearningEnrollment existing = enrollmentMapper.findExisting(userId, sessionId);
        if (existing != null && "ENROLLED".equals(existing.getEnrollmentStatus())) {
            throw new LearningValidationException(E_ALREADY_ENROLLED, "你已经报名过这场了");
        }
        if (existing != null && "ATTENDED".equals(existing.getEnrollmentStatus())) {
            throw new LearningValidationException(E_ALREADY_ENROLLED, "你已参加过这场");
        }

        if (sessionMapper.occupyEventSeat(sessionId) == 0) {
            throw new LearningValidationException(E_SEAT_FULL, "名额已满", seatDetail(sessionId));
        }

        LearningEnrollment e = existing;
        if (existing != null) {
            enrollmentMapper.revive(existing.getEnrollmentId(), TYPE_EVENT);
        } else {
            e = new LearningEnrollment();
            e.setEnrollmentId(UUID.randomUUID().toString().replace("-", ""));
            e.setUserId(userId);
            e.setSessionId(sessionId);
            e.setTargetType(TYPE_EVENT);
            e.setEnrollmentStatus("ENROLLED");
            enrollmentMapper.insert(e);
        }

        Map<String, Object> out = new LinkedHashMap<>();
        out.put("enrollment_id", e.getEnrollmentId());
        out.put("session_id", sessionId);
        out.put("target_type", TYPE_EVENT);
        out.put("target_id", eventId);
        out.put("enrollment_status", "ENROLLED");
        return out;
    }

    private void grantFreeEntitlement(String userId, String eventId, String sessionId) {
        LearningEntitlement e = new LearningEntitlement();
        e.setEntitlementId(UUID.randomUUID().toString().replace("-", ""));
        e.setUserId(userId);
        e.setTargetType(TYPE_EVENT);
        e.setTargetId(eventId);
        e.setSessionId(sessionId);
        e.setEntitlementStatus("ACTIVE");
        e.setSource("FREE_GRANT");
        e.setOrderId(null);
        e.setGrantedAt(LocalDateTime.now());
        try {
            entitlementMapper.insert(e);
        } catch (RuntimeException ex) {
            log.debug("免费权限已存在（并发），跳过：event={}", eventId);
        }
    }

    /** 校验场次属于该活动，防止跨活动占用名额 */
    private LearningSession requireEventSession(String eventId, String sessionId) {
        if (sessionId == null || sessionId.isBlank()) {
            throw new LearningValidationException(E_SESSION_NOT_FOUND, "请选择具体场次");
        }
        LearningSession s = sessionMapper.findEventSessionById(sessionId);
        if (s == null) {
            throw new LearningValidationException(E_SESSION_NOT_FOUND, "场次不存在");
        }
        if (!Objects.equals(eventId, s.getTargetId())) {
            throw new LearningValidationException(E_SESSION_NOT_FOUND, "场次与内容不匹配");
        }
        return s;
    }

    private static void requireLogin(String userId) {
        if (userId == null || userId.isBlank() || "anonymous".equals(userId)) {
            throw new LearningValidationException(E_NO_PERMISSION, "请先登录后再操作");
        }
    }

    private static boolean isPaid(LearningSession s) {
        return s.getPrice() != null && s.getPrice().compareTo(BigDecimal.ZERO) > 0;
    }

    private Map<String, Object> seatDetail(String sessionId) {
        LearningSession s = sessionMapper.findEventSessionById(sessionId);
        if (s == null) return Map.of();
        String st = s.getSessionStatus();
        if (!"SCHEDULED".equals(st) && !"ONGOING".equals(st)) {
            throw new LearningValidationException(E_SESSION_CLOSED, "该场次已结束或已取消");
        }
        return Map.of(
                "capacity", s.getCapacity() == null ? 0 : s.getCapacity(),
                "enrolled_count", s.getEnrolledCount() == null ? 0 : s.getEnrolledCount());
    }

    private Map<String, Object> summarizeEntitlement(String userId, String eventId, String sessionId) {
        Map<String, Object> m = new LinkedHashMap<>();
        if (userId == null || userId.isBlank()) {
            m.put("status", "NONE");
            return m;
        }
        LearningEntitlement e = entitlementMapper.findActive(userId, TYPE_EVENT, eventId, sessionId);
        m.put("status", e == null ? "NONE" : "ACTIVE");
        if (e != null) {
            m.put("source", e.getSource());
            m.put("granted_at", e.getGrantedAt());
            m.put("expires_at", e.getExpiresAt());
        }
        return m;
    }

    private Map<String, Object> cancelView(LearningEnrollment e, boolean already) {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("enrollment_id", e.getEnrollmentId());
        m.put("session_id", e.getSessionId());
        m.put("target_type", e.getTargetType());
        m.put("enrollment_status", "CANCELLED");
        m.put("already_cancelled", already);
        m.put("seat_released", !already);
        return m;
    }

    private Map<String, Object> attendanceView(LearningEnrollment e, boolean already) {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("enrollment_id", e.getEnrollmentId());
        m.put("session_id", e.getSessionId());
        m.put("target_type", e.getTargetType());
        m.put("user_id", e.getUserId());
        m.put("enrollment_status", e.getEnrollmentStatus());
        m.put("attendance_checked_at", e.getAttendanceCheckedAt());
        m.put("already_checked", already);
        return m;
    }

    /** 供 Commerce Service 下单时取标题快照 */
    public String eventTitle(String eventId, LearningSession session) {
        if (session != null && session.getTitle() != null && !session.getTitle().isBlank()) {
            return session.getTitle();
        }
        LearningEvent e = eventMapper.findById(eventId);
        return e == null ? eventId : e.getTitle();
    }

    /** 供 Commerce Service 校验场次归属 */
    public LearningSession requireSessionForOrder(String eventId, String sessionId) {
        return requireEventSession(eventId, sessionId);
    }

    /** 供 Commerce Service 判断是否付费 */
    public static boolean isPaidSession(LearningSession s) {
        return isPaid(s);
    }

    public LearningEvent loadEvent(String eventId) {
        LearningEvent e = eventMapper.findById(eventId);
        if (e == null) throw new LearningValidationException(E_NOT_FOUND, "活动不存在或已删除");
        return e;
    }
}