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
 * 问星渡｜学习课程 Service
 *
 * 对应文档 14_API_Contract：「Domain Service独立」「Course Service处理学习权限」。
 * 活动侧请用 {@link LearningEventService}，交易侧请用 {@link LearningCommerceService}。
 *
 * <b>规格来源</b>：原始 skill 目录 13 个文件共 3993 字节，
 * 其中与本类直接相关的只有两条原则（Domain 隔离、Payment 不直接创建权限）。
 * 字段、枚举、状态机均为实现方所拟，<b>不是原始规格</b>。
 *
 * 【设计假设 A1】课程以「场次」售卖：course_session 承载时间、名额、票价。
 * 【设计假设 A2】免费与付费并存：price=0 走免费直接发权限，
 * price&gt;0 必须走「下单 → 支付 → Fulfillment」才发权限。
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class LearningCourseService {

    private final LearningCourseMapper courseMapper;
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

    public static final String TYPE_COURSE = "COURSE";

    // ==============================================================
    // 浏览
    // ==============================================================

    /** 已发布课程列表 */
    public List<LearningCourse> listCourses(String category, String level, String userId) {
        List<LearningCourse> list = courseMapper.findPublished(category, level);
        // tags 仅详情使用，列表不返回，控制传输体积
        list.forEach(c -> c.setTags(null));
        return list;
    }

    /**
     * 课程详情 + 场次。
     *
     * 未发布与不存在要区分：不存在是「已删除」，未发布是「暂未开放」，
     * 两者对用户意义不同，不能一律返回空列表。
     */
    public Map<String, Object> courseDetail(String courseId, String userId) {
        LearningCourse c = courseMapper.findById(courseId);
        if (c == null) throw new LearningValidationException(E_NOT_FOUND, "课程不存在或已删除");
        if (!"PUBLISHED".equals(c.getCourseStatus())) {
            throw new LearningValidationException(E_NOT_PUBLISHED, "该课程暂未开放");
        }
        Map<String, Object> out = new LinkedHashMap<>();
        out.put("course", c);
        // 场次用「公开视角」：不含 live_url / recording_url
        out.put("sessions", sessionMapper.findCourseSessions(courseId));
        out.put("my_entitlement", summarizeEntitlement(userId, courseId, null));
        return out;
    }

    // ==============================================================
    // 报名
    // ==============================================================

    /**
     * 免费场次报名：直接发放权限再报名。
     *
     * 顺序不可颠倒——先发权限再占名额。反过来的话，名额占成功但发权限失败，
     * 用户就既没权限又白占了别人的位置，而且没有任何补偿路径。
     *
     * 付费场次走 {@link LearningCommerceService#createOrder}，本方法直接拒绝。
     */
    @Transactional
    public Map<String, Object> enrollFree(String courseId, String sessionId, String userId) {
        requireLogin(userId);
        LearningSession session = requireCourseSession(courseId, sessionId);

        if (isPaid(session)) {
            throw new LearningValidationException(E_REQUIRES_ORDER, "该场次需要付费购买后才能报名");
        }

        // 已有 ACTIVE 权限就不必重发，但仍要确保报名记录存在
        if (entitlementMapper.findActive(userId, TYPE_COURSE, courseId, sessionId) == null) {
            grantFreeEntitlement(userId, courseId, sessionId);
        }

        Map<String, Object> r = doEnroll(userId, courseId, sessionId);
        r.put("entitlement_source", "FREE_GRANT");
        return r;
    }

    /** 取消报名并释放名额 */
    @Transactional
    public Map<String, Object> cancelEnrollment(String userId, String sessionId) {
        requireLogin(userId);
        LearningEnrollment existing = enrollmentMapper.findExisting(userId, sessionId);
        if (existing == null) {
            throw new LearningValidationException(E_SESSION_NOT_FOUND, "没有找到报名记录");
        }
        if ("CANCELLED".equals(existing.getEnrollmentStatus())) {
            // 幂等：已取消过，不再减名额（否则会减成负数）
            return cancelView(existing, true);
        }
        if ("ATTENDED".equals(existing.getEnrollmentStatus())) {
            throw new LearningValidationException(E_SESSION_CLOSED, "已核销出勤的场次不可取消");
        }

        if (enrollmentMapper.cancel(userId, sessionId) > 0) {
            sessionMapper.releaseCourseSeat(sessionId);
        }
        return cancelView(existing, false);
    }

    // ==============================================================
    // 讲师侧（对应 Frontend Page Mapping 的「我的课程」「班级管理」）
    // ==============================================================

    /** 讲师名下的课程。assistant 也会看到自己辅助的课。 */
    public List<LearningCourse> listMyCourses(String teacherId) {
        return courseMapper.findByTeacher(teacherId);
    }

    /**
     * 某场次的报名名单（点名用）。
     *
     * 归属校验在调用前完成：讲师只能核验自己名下的课。
     */
    public List<LearningEnrollment> sessionRoster(String sessionId) {
        return enrollmentMapper.findBySession(sessionId);
    }

    /**
     * 出勤核销。
     *
     * isAdmin=true 时跳过归属校验（Admin 全权）；
     * 否则必须 teacherId 命中 course.teacher_id 或 assistant_teacher_id。
     */
    @Transactional
    public Map<String, Object> checkAttendance(String sessionId, String studentId,
                                               String status, String operatorId, boolean isAdmin) {
        if (!"ATTENDED".equals(status) && !"ABSENT".equals(status)) {
            throw new LearningValidationException(E_SESSION_CLOSED,
                    "出勤状态只能是 ATTENDED 或 ABSENT");
        }
        LearningSession session = sessionMapper.findCourseSessionById(sessionId);
        if (session == null) {
            throw new LearningValidationException(E_SESSION_NOT_FOUND, "场次不存在");
        }
        if (!isAdmin) {
            LearningCourse c = courseMapper.findById(session.getTargetId());
            if (c == null || !ownsCourse(c, operatorId)) {
                throw new LearningValidationException(E_NO_PERMISSION, "只能核验自己负责的课程");
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
        // 必须回填状态：existing 是更新前读出来的快照，
        // 直接返回会让讲师看到「点了点名但还是 ENROLLED」，
        // 白点一次还要再点一次。
        LearningEnrollment updated = enrollmentMapper.findExisting(studentId, sessionId);
        return attendanceView(updated != null ? updated : existing, false);
    }

    // ==============================================================
    // 直播入口（仅已报名者）
    // ==============================================================

    /**
     * 已报名用户的场次详情，含直播 / 回放地址。
     *
     * 走 JOIN learning_enrollment 的 SQL：未报名者查不到行。
     * 即便前端绕过页面直接调接口，也拿不到直播地址。
     */
    public Map<String, Object> enrolledSession(String sessionId, String userId) {
        LearningSession s = sessionMapper.findEnrolledCourseSession(sessionId, userId);
        if (s == null) {
            throw new LearningValidationException(E_NO_PERMISSION, "尚未报名，无法查看该场次");
        }
        Map<String, Object> out = new LinkedHashMap<>();
        out.put("session", s);   // 此处含 live_url / recording_url
        return out;
    }

    // ==============================================================
    // 我的
    // ==============================================================

    public List<LearningEnrollment> myEnrollments(String userId) {
        return userId == null ? List.of() : enrollmentMapper.findActiveByUser(userId);
    }

    /** 是否已拥有可用权限 */
    public boolean hasEntitlement(String userId, String courseId, String sessionId) {
        return userId != null
                && entitlementMapper.findActive(userId, TYPE_COURSE, courseId, sessionId) != null;
    }

    // ==============================================================
    // 内部工具
    // ==============================================================

    /**
     * 报名落库。名额扣减与写入在同一事务内，失败一起回滚。
     *
     * 先扣名额再写记录：若名额不足，占位失败，
     * 用户不会留下一条「有报名记录但没占到位置」的脏数据。
     */
    private Map<String, Object> doEnroll(String userId, String courseId, String sessionId) {
        LearningEnrollment existing = enrollmentMapper.findExisting(userId, sessionId);
        if (existing != null && "ENROLLED".equals(existing.getEnrollmentStatus())) {
            throw new LearningValidationException(E_ALREADY_ENROLLED, "你已经报名过这场了");
        }
        if (existing != null && "ATTENDED".equals(existing.getEnrollmentStatus())) {
            throw new LearningValidationException(E_ALREADY_ENROLLED, "你已参加过这场");
        }

        if (sessionMapper.occupyCourseSeat(sessionId) == 0) {
            throw new LearningValidationException(E_SEAT_FULL, "名额已满", seatDetail(sessionId));
        }

        LearningEnrollment e = existing;
        if (existing != null) {
            // 曾取消过：复用原记录改回 ENROLLED，
            // 直接 INSERT 会撞 (user_id, session_id) 唯一索引
            enrollmentMapper.revive(existing.getEnrollmentId(), TYPE_COURSE);
        } else {
            e = new LearningEnrollment();
            e.setEnrollmentId(UUID.randomUUID().toString().replace("-", ""));
            e.setUserId(userId);
            e.setSessionId(sessionId);
            e.setTargetType(TYPE_COURSE);
            e.setEnrollmentStatus("ENROLLED");
            enrollmentMapper.insert(e);
        }

        Map<String, Object> out = new LinkedHashMap<>();
        out.put("enrollment_id", e.getEnrollmentId());
        out.put("session_id", sessionId);
        out.put("target_type", TYPE_COURSE);
        out.put("target_id", courseId);
        out.put("enrollment_status", "ENROLLED");
        return out;
    }

    /** 免费权限发放（幂等：已有就不发） */
    private void grantFreeEntitlement(String userId, String courseId, String sessionId) {
        LearningEntitlement e = new LearningEntitlement();
        e.setEntitlementId(UUID.randomUUID().toString().replace("-", ""));
        e.setUserId(userId);
        e.setTargetType(TYPE_COURSE);
        e.setTargetId(courseId);
        e.setSessionId(sessionId);
        e.setEntitlementStatus("ACTIVE");
        e.setSource("FREE_GRANT");
        e.setOrderId(null);
        e.setGrantedAt(LocalDateTime.now());
        try {
            entitlementMapper.insert(e);
        } catch (RuntimeException ex) {
            // 唯一索引冲突 = 并发下另一请求已发，按幂等处理
            log.debug("免费权限已存在（并发），跳过：course={}", courseId);
        }
    }

    /**
     * 校验场次属于该课程。
     *
     * 防止拿 A 课程的场次 ID 去报 B 课程——否则会出现
     * 「报了 B 课却占掉 A 课名额」的数据错乱。
     */
    private LearningSession requireCourseSession(String courseId, String sessionId) {
        if (sessionId == null || sessionId.isBlank()) {
            throw new LearningValidationException(E_SESSION_NOT_FOUND, "请选择具体场次");
        }
        LearningSession s = sessionMapper.findCourseSessionById(sessionId);
        if (s == null) {
            throw new LearningValidationException(E_SESSION_NOT_FOUND, "场次不存在");
        }
        if (!Objects.equals(courseId, s.getTargetId())) {
            throw new LearningValidationException(E_SESSION_NOT_FOUND, "场次与内容不匹配");
        }
        return s;
    }

    private LearningCourse requireCourse(String courseId) {
        LearningCourse c = courseMapper.findById(courseId);
        if (c == null) throw new LearningValidationException(E_NOT_FOUND, "课程不存在或已删除");
        return c;
    }

    private static boolean ownsCourse(LearningCourse c, String userId) {
        return Objects.equals(c.getTeacherId(), userId)
                || Objects.equals(c.getAssistantTeacherId(), userId);
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
        LearningSession s = sessionMapper.findCourseSessionById(sessionId);
        if (s == null) return Map.of();
        String st = s.getSessionStatus();
        if (!"SCHEDULED".equals(st) && !"ONGOING".equals(st)) {
            throw new LearningValidationException(E_SESSION_CLOSED, "该场次已结束或已取消");
        }
        return Map.of(
                "capacity", s.getCapacity() == null ? 0 : s.getCapacity(),
                "enrolled_count", s.getEnrolledCount() == null ? 0 : s.getEnrolledCount());
    }

    private Map<String, Object> summarizeEntitlement(String userId, String courseId, String sessionId) {
        Map<String, Object> m = new LinkedHashMap<>();
        if (userId == null || userId.isBlank()) {
            m.put("status", "NONE");
            return m;
        }
        LearningEntitlement e = entitlementMapper.findActive(userId, TYPE_COURSE, courseId, sessionId);
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
    public String courseTitle(String courseId, LearningSession session) {
        if (session != null && session.getTitle() != null && !session.getTitle().isBlank()) {
            return session.getTitle();
        }
        LearningCourse c = courseMapper.findById(courseId);
        return c == null ? courseId : c.getTitle();
    }

    /** 供 Commerce Service 校验场次归属 */
    public LearningSession requireSessionForOrder(String courseId, String sessionId) {
        return requireCourseSession(courseId, sessionId);
    }

    /** 供 Commerce Service 判断是否付费 */
    public static boolean isPaidSession(LearningSession s) {
        return isPaid(s);
    }

    /** 到期权限清理（定时任务调用） */
    public int expireOverdueEntitlements() {
        return entitlementMapper.expireOverdue();
    }

    /** 供 Controller 取课程（校验归属用） */
    public LearningCourse loadCourse(String courseId) {
        return requireCourse(courseId);
    }
}