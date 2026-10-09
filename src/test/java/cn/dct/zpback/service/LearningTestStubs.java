package cn.dct.zpback.service;

import cn.dct.zpback.entity.*;
import cn.dct.zpback.mapper.*;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.*;

/**
 * 课程与活动测试共用的 Mapper 桩。
 *
 * 抽出来是因为三 Service 拆分后，两套测试都要同样的桩；
 * 各自复制一份会导致「一边改了另一边没改」，测试之间悄悄失去一致性。
 *
 * 桩必须忠实模拟真实 SQL 的约束，否则测试会给出假的安全感。
 * 尤其这三点：
 *   1. findEnrolled*Session 必须校验报名关系（真实实现是 JOIN + user_id 过滤）；
 *   2. occupy*Seat 必须受 capacity 与 session_status 约束；
 *   3. release*Seat 必须夹到 0，不能减成负数。
 */
final class LearningTestStubs {

    private LearningTestStubs() {}

    // ==========================================================
    // 课程
    // ==========================================================

    static final class CourseMapperStub implements LearningCourseMapper {
        final Map<String, LearningCourse> byId = new LinkedHashMap<>();
        final List<LearningCourse> published = new ArrayList<>();

        @Override
        public List<LearningCourse> findPublished(String category, String level) {
            if (category == null && level == null) return published;
            List<LearningCourse> out = new ArrayList<>();
            for (LearningCourse c : published) {
                if (category != null && !category.equals(c.getCategory())) continue;
                if (level != null && !level.equals(c.getLevel())) continue;
                out.add(c);
            }
            return out;
        }

        @Override public LearningCourse findById(String id) { return byId.get(id); }
        @Override public int insert(LearningCourse c) { byId.put(c.getCourseId(), c); return 1; }

        @Override
        public List<LearningCourse> findByTeacher(String userId) {
            List<LearningCourse> out = new ArrayList<>();
            for (LearningCourse c : byId.values()) {
                if (Objects.equals(c.getTeacherId(), userId)
                        || Objects.equals(c.getAssistantTeacherId(), userId)) out.add(c);
            }
            return out;
        }
    }

    // ==========================================================
    // 活动
    // ==========================================================

    static final class EventMapperStub implements LearningEventMapper {
        final Map<String, LearningEvent> byId = new LinkedHashMap<>();
        final List<LearningEvent> published = new ArrayList<>();

        @Override
        public List<LearningEvent> findPublished(String eventType) {
            if (eventType == null) return published;
            List<LearningEvent> out = new ArrayList<>();
            for (LearningEvent e : published) {
                if (eventType.equals(e.getEventType())) out.add(e);
            }
            return out;
        }

        /** 公开视角：不返回 live_url，与真实 COLUMNS_SAFE 一致 */
        @Override
        public LearningEvent findById(String id) {
            LearningEvent e = byId.get(id);
            return e == null ? null : withoutLive(e);
        }

        @Override
        public LearningEvent findByIdWithLiveUrl(String id) { return byId.get(id); }
        @Override public int insert(LearningEvent e) { byId.put(e.getEventId(), e); return 1; }

        @Override
        public List<LearningEvent> findByHost(String hostId) {
            List<LearningEvent> out = new ArrayList<>();
            for (LearningEvent e : byId.values()) {
                if (Objects.equals(e.getHostId(), hostId)) out.add(withoutLive(e));
            }
            return out;
        }

        private LearningEvent withoutLive(LearningEvent e) {
            LearningEvent c = new LearningEvent();
            c.setEventId(e.getEventId());
            c.setTitle(e.getTitle());
            c.setSubtitle(e.getSubtitle());
            c.setDescription(e.getDescription());
            c.setCoverUrl(e.getCoverUrl());
            c.setEventType(e.getEventType());
            c.setEventStatus(e.getEventStatus());
            c.setHostId(e.getHostId());
            c.setVenueName(e.getVenueName());
            c.setVenueAddress(e.getVenueAddress());
            c.setIsOnline(e.getIsOnline());
            c.setDurationMinutes(e.getDurationMinutes());
            c.setLiveUrl(null);          // 关键：公开视角必须抹掉
            return c;
        }
    }

    // ==========================================================
    // 场次
    // ==========================================================

    static final class SessionMapperStub implements LearningSessionMapper {
        final Map<String, LearningSession> byId = new LinkedHashMap<>();
        final Map<String, Integer> enrolledCount = new HashMap<>();
        final Map<String, Integer> seatDelta = new HashMap<>();
        final Map<String, String> liveUrl = new HashMap<>();
        EnrollmentMapperStub enrollments;
        List<MyLearningRow> myCourseRows = new ArrayList<>();
        List<MyLearningRow> myEventRows = new ArrayList<>();

        void bind(EnrollmentMapperStub e) { this.enrollments = e; }
        void put(String id, LearningSession s) { byId.put(id, s); }
        int courseSeatDelta(String id) { return seatDelta.getOrDefault(id, 0); }
        int currentCount(String id) { return enrolledCount.getOrDefault(id, 0); }

        @Override
        public List<LearningSession> findCourseSessions(String targetId) {
            List<LearningSession> out = new ArrayList<>();
            for (LearningSession s : byId.values()) {
                if ("COURSE".equals(s.getTargetType()) && Objects.equals(s.getTargetId(), targetId)) {
                    out.add(publicView(s));
                }
            }
            return out;
        }

        @Override
        public List<LearningSession> findEventSessions(String targetId) {
            List<LearningSession> out = new ArrayList<>();
            for (LearningSession s : byId.values()) {
                if ("EVENT".equals(s.getTargetType()) && Objects.equals(s.getTargetId(), targetId)) {
                    out.add(publicView(s));
                }
            }
            return out;
        }

        @Override
        public LearningSession findCourseSessionById(String id) {
            LearningSession s = byId.get(id);
            return s != null && "COURSE".equals(s.getTargetType()) ? fullView(s) : null;
        }

        @Override
        public LearningSession findEventSessionById(String id) {
            LearningSession s = byId.get(id);
            return s != null && "EVENT".equals(s.getTargetType()) ? fullView(s) : null;
        }

        /**
         * 模拟真实 SQL：JOIN learning_enrollment + user_id 过滤 + 状态过滤。
         * 未报名者<b>必须</b>返回 null，否则「未报名拿不到直播地址」这条
         * 安全约束在测试里永远测不出来。
         */
        @Override
        public LearningSession findEnrolledCourseSession(String sessionId, String userId) {
            LearningSession s = byId.get(sessionId);
            if (s == null || !"COURSE".equals(s.getTargetType())) return null;
            if (enrollments == null || !enrollments.isEffective(userId, sessionId)) return null;
            return fullView(s);
        }

        @Override
        public LearningSession findEnrolledEventSession(String sessionId, String userId) {
            LearningSession s = byId.get(sessionId);
            if (s == null || !"EVENT".equals(s.getTargetType())) return null;
            if (enrollments == null || !enrollments.isEffective(userId, sessionId)) return null;
            return fullView(s);
        }

        @Override
        public int occupyCourseSeat(String sessionId) {
            LearningSession s = byId.get(sessionId);
            if (s == null || !"COURSE".equals(s.getTargetType())) return 0;
            return occupy(sessionId, s.getCapacity());
        }

        @Override
        public int occupyEventSeat(String sessionId) {
            LearningSession s = byId.get(sessionId);
            if (s == null || !"EVENT".equals(s.getTargetType())) return 0;
            return occupy(sessionId, s.getCapacity());
        }

        /** 释放必须夹到 0，否则重复取消会把人数减成负数 */
        @Override
        public int releaseCourseSeat(String sessionId) { return release(sessionId); }
        @Override
        public int releaseEventSeat(String sessionId) { return release(sessionId); }

        @Override public List<MyLearningRow> findMyCourseEnrollments(String userId) { return myCourseRows; }
        @Override public List<MyLearningRow> findMyEventEnrollments(String userId) { return myEventRows; }

        @Override public int insertCourseSession(LearningSession s) { byId.put(s.getSessionId(), s); return 1; }
        @Override public int insertEventSession(LearningSession s) { byId.put(s.getSessionId(), s); return 1; }

        private int occupy(String id, Integer capacity) {
            LearningSession s = byId.get(id);
            if (s == null) return 0;
            String st = s.getSessionStatus();
            if (!"SCHEDULED".equals(st) && !"ONGOING".equals(st)) return 0;
            int cap = capacity == null ? 0 : capacity;
            int cur = enrolledCount.getOrDefault(id, 0);
            if (cap != 0 && cur >= cap) return 0;
            enrolledCount.put(id, cur + 1);
            seatDelta.merge(id, 1, Integer::sum);
            return 1;
        }

        private int release(String id) {
            int cur = enrolledCount.getOrDefault(id, 0);
            int next = Math.max(cur - 1, 0);
            enrolledCount.put(id, next);
            seatDelta.merge(id, next - cur, Integer::sum);
            return 1;
        }

        /** 公开视角：抹掉 live_url / recording_url，对应 COLUMNS_SAFE */
        private LearningSession publicView(LearningSession s) {
            LearningSession c = base(s);
            c.setLiveUrl(null);
            c.setRecordingUrl(null);
            return c;
        }

        /** 已报名视角：带直播地址，对应 COLUMNS_JOINED */
        private LearningSession fullView(LearningSession s) {
            LearningSession c = base(s);
            c.setLiveUrl(liveUrl.get(s.getSessionId()));
            return c;
        }

        private LearningSession base(LearningSession s) {
            LearningSession c = new LearningSession();
            c.setSessionId(s.getSessionId());
            c.setTargetId(s.getTargetId());
            c.setTargetType(s.getTargetType());
            c.setSessionNo(s.getSessionNo());
            c.setTitle(s.getTitle());
            c.setStartAt(s.getStartAt());
            c.setEndAt(s.getEndAt());
            c.setDurationMinutes(s.getDurationMinutes());
            c.setIsLive(s.getIsLive());
            c.setSessionStatus(s.getSessionStatus());
            c.setCapacity(s.getCapacity());
            c.setPrice(s.getPrice());
            c.setEnrolledCount(enrolledCount.getOrDefault(s.getSessionId(), 0));
            return c;
        }
    }

    // ==========================================================
    // 权限
    // ==========================================================

    static final class EntitlementMapperStub implements LearningEntitlementMapper {
        final Map<String, LearningEntitlement> all = new LinkedHashMap<>();

        @Override
        public LearningEntitlement findActive(String userId, String type, String targetId, String sessionId) {
            for (LearningEntitlement e : all.values()) {
                if ("ACTIVE".equals(e.getEntitlementStatus())
                        && Objects.equals(e.getUserId(), userId)
                        && Objects.equals(e.getTargetType(), type)
                        && Objects.equals(e.getTargetId(), targetId)
                        && Objects.equals(e.getSessionId(), sessionId)) return e;
            }
            return null;
        }

        @Override
        public List<LearningEntitlement> findActiveByUser(String userId) {
            List<LearningEntitlement> out = new ArrayList<>();
            for (LearningEntitlement e : all.values()) {
                if (Objects.equals(e.getUserId(), userId) && "ACTIVE".equals(e.getEntitlementStatus())) {
                    out.add(e);
                }
            }
            return out;
        }

        @Override
        public LearningEntitlement findByOrderId(String orderId) {
            for (LearningEntitlement e : all.values()) {
                if (Objects.equals(e.getOrderId(), orderId)) return e;
            }
            return null;
        }

        /** 退款：只改 ACTIVE 的，已失效的不动 */
        @Override
        public int revokeByOrderId(String orderId) {
            int n = 0;
            for (LearningEntitlement e : all.values()) {
                if (Objects.equals(e.getOrderId(), orderId) && "ACTIVE".equals(e.getEntitlementStatus())) {
                    e.setEntitlementStatus("REVOKED");
                    n++;
                }
            }
            return n;
        }

        @Override public int expireOverdue() { return 0; }

        @Override
        public int insert(LearningEntitlement e) { all.put(e.getEntitlementId(), e); return 1; }

        @Override
        public int updateStatus(String id, String status, LocalDateTime grantedAt) {
            LearningEntitlement e = all.get(id);
            if (e == null) return 0;
            e.setEntitlementStatus(status);
            return 1;
        }

        int activeCount() {
            int n = 0;
            for (LearningEntitlement e : all.values()) {
                if ("ACTIVE".equals(e.getEntitlementStatus())) n++;
            }
            return n;
        }
    }

    // ==========================================================
    // 报名
    // ==========================================================

    static final class EnrollmentMapperStub implements LearningEnrollmentMapper {
        final List<LearningEnrollment> rows = new ArrayList<>();
        final Map<String, LearningEnrollment> byUserSession = new HashMap<>();

        /** ENROLLED / ATTENDED 才算有效报名（与真实 SQL 的状态过滤一致） */
        boolean isEffective(String userId, String sessionId) {
            LearningEnrollment e = byUserSession.get(userId + "|" + sessionId);
            return e != null && ("ENROLLED".equals(e.getEnrollmentStatus())
                    || "ATTENDED".equals(e.getEnrollmentStatus()));
        }

        /**
         * 返回一份拷贝，模拟真实 MyBatis 每次查询都产生新对象。
         *
         * 若直接返回表里那个对象，Service 就和「更新前读到的引用」共享状态，
         * 即使它忘了回填，断言也照样通过——缺陷会被测试完美隐藏。
         */
        @Override
        public LearningEnrollment findExisting(String userId, String sessionId) {
            return copy(byUserSession.get(userId + "|" + sessionId));
        }

        private LearningEnrollment copy(LearningEnrollment s) {
            if (s == null) return null;
            LearningEnrollment c = new LearningEnrollment();
            c.setEnrollmentId(s.getEnrollmentId());
            c.setUserId(s.getUserId());
            c.setSessionId(s.getSessionId());
            c.setTargetType(s.getTargetType());
            c.setEnrollmentStatus(s.getEnrollmentStatus());
            c.setAttendanceCheckedAt(s.getAttendanceCheckedAt());
            c.setCancelledAt(s.getCancelledAt());
            return c;
        }

        @Override
        public List<LearningEnrollment> findActiveByUser(String userId) {
            List<LearningEnrollment> out = new ArrayList<>();
            for (LearningEnrollment e : rows) {
                if (Objects.equals(e.getUserId(), userId) && isEffective(e.getUserId(), e.getSessionId())) {
                    out.add(e);
                }
            }
            return out;
        }

        @Override
        public List<LearningEnrollment> findBySession(String sessionId) {
            List<LearningEnrollment> out = new ArrayList<>();
            for (LearningEnrollment e : rows) {
                if (Objects.equals(e.getSessionId(), sessionId) && isEffective(e.getUserId(), sessionId)) {
                    out.add(e);
                }
            }
            return out;
        }

        @Override
        public int insert(LearningEnrollment e) {
            rows.add(e);
            byUserSession.put(e.getUserId() + "|" + e.getSessionId(), e);
            return 1;
        }

        @Override
        public int revive(String enrollmentId, String targetType) {
            for (LearningEnrollment e : rows) {
                if (Objects.equals(e.getEnrollmentId(), enrollmentId)) {
                    e.setEnrollmentStatus("ENROLLED");
                    e.setTargetType(targetType);
                    e.setCancelledAt(null);
                    e.setAttendanceCheckedAt(null);
                    return 1;
                }
            }
            return 0;
        }

        @Override
        public int cancel(String userId, String sessionId) {
            LearningEnrollment e = byUserSession.get(userId + "|" + sessionId);
            if (e == null || !"ENROLLED".equals(e.getEnrollmentStatus())) return 0;
            e.setEnrollmentStatus("CANCELLED");
            e.setCancelledAt(LocalDateTime.now());
            return 1;
        }

        /**
 * @Override
         * 出勤核销。<b>故意回填 checked_at</b>：
         * 真实 SQL 是 UPDATE ... SET attendance_checked_at = NOW()，
         * 若桩不更新该字段，「核销后响应里 should 有核销时间」这条断言
         * 会因为桩偷懒而失败或被绕过，从而掩盖真实缺陷。
         */
        @Override
        public int checkAttendance(String userId, String sessionId, String status) {
            LearningEnrollment e = byUserSession.get(userId + "|" + sessionId);
            if (e == null || !"ENROLLED".equals(e.getEnrollmentStatus())) return 0;
            e.setEnrollmentStatus(status);
            e.setAttendanceCheckedAt(LocalDateTime.now());
            return 1;
        }
    }

    // ==========================================================
    // 订单
    // ==========================================================

    static final class OrderMapperStub implements LearningOrderMapper {
        final Map<String, LearningOrder> byId = new LinkedHashMap<>();

        @Override public LearningOrder findById(String id) { return byId.get(id); }
        @Override public LearningOrder findByNo(String no) { return null; }

        @Override
        public LearningOrder findOpenOrder(String userId, String type, String targetId, String sessionId) {
            for (LearningOrder o : byId.values()) {
                if (Objects.equals(o.getUserId(), userId)
                        && Objects.equals(o.getOrderType(), type)
                        && Objects.equals(o.getTargetId(), targetId)
                        && Objects.equals(o.getSessionId(), sessionId)
                        && ("CREATED".equals(o.getOrderStatus()) || "PAID".equals(o.getOrderStatus()))) {
                    return o;
                }
            }
            return null;
        }

        @Override
        public List<LearningOrder> findByUser(String userId, int limit) {
            List<LearningOrder> out = new ArrayList<>();
            for (LearningOrder o : byId.values()) {
                if (Objects.equals(o.getUserId(), userId)) out.add(o);
            }
            return out;
        }

        @Override
        public List<LearningOrder> findPendingFulfillment(int limit) {
            List<LearningOrder> out = new ArrayList<>();
            for (LearningOrder o : byId.values()) {
                if ("PAID".equals(o.getOrderStatus())) out.add(o);
            }
            return out;
        }

        @Override public int insert(LearningOrder o) { byId.put(o.getOrderId(), o); return 1; }

        @Override
        public int markPaid(String id, String channel, BigDecimal paid) {
            LearningOrder o = byId.get(id);
            if (o == null || !"CREATED".equals(o.getOrderStatus())) return 0;
            o.setOrderStatus("PAID");
            o.setPaymentChannel(channel);
            o.setPaidAmount(paid);
            o.setPaidAt(LocalDateTime.now());
            return 1;
        }

        @Override
        public int markFulfilled(String id) {
            LearningOrder o = byId.get(id);
            if (o == null || !"PAID".equals(o.getOrderStatus())) return 0;
            o.setOrderStatus("FULFILLED");
            o.setFulfilledAt(LocalDateTime.now());
            return 1;
        }

        @Override
        public int cancel(String id) {
            LearningOrder o = byId.get(id);
            if (o == null || !"CREATED".equals(o.getOrderStatus())) return 0;
            o.setOrderStatus("CANCELLED");
            return 1;
        }

        @Override
        public int refund(String id) {
            LearningOrder o = byId.get(id);
            if (o == null || !("PAID".equals(o.getOrderStatus())
                    || "FULFILLED".equals(o.getOrderStatus()))) return 0;
            o.setOrderStatus("REFUNDED");
            o.setRefundedAt(LocalDateTime.now());
            return 1;
        }
    }

    // ==========================================================
    // 数据构造
    // ==========================================================

    static LearningSession courseSession(String sessionId, String courseId,
                                         String price, int capacity) {
        LearningSession s = new LearningSession();
        s.setSessionId(sessionId);
        s.setTargetId(courseId);
        s.setTargetType("COURSE");
        s.setSessionNo(1);
        s.setTitle("测试场次");
        s.setStartAt(LocalDateTime.now().plusDays(2));
        s.setEndAt(LocalDateTime.now().plusDays(2).plusHours(2));
        s.setDurationMinutes(120);
        s.setIsLive(true);
        s.setSessionStatus("SCHEDULED");
        s.setCapacity(capacity);
        s.setPrice(new BigDecimal(price));
        return s;
    }

    static LearningSession eventSession(String sessionId, String eventId,
                                        String price, int capacity) {
        LearningSession s = courseSession(sessionId, eventId, price, capacity);
        s.setTargetType("EVENT");
        s.setRecordingUrl(null);   // 活动场次表无此列
        return s;
    }

    static LearningCourse publishedCourse(String courseId, String teacherId) {
        LearningCourse c = new LearningCourse();
        c.setCourseId(courseId);
        c.setTitle("测试课程");
        c.setCourseStatus("PUBLISHED");
        c.setTeacherId(teacherId);
        return c;
    }

    static LearningEvent publishedEvent(String eventId, String hostId) {
        LearningEvent e = new LearningEvent();
        e.setEventId(eventId);
        e.setTitle("测试活动");
        e.setEventStatus("PUBLISHED");
        e.setHostId(hostId);
        return e;
    }

    static LearningOrder paidOrder(String orderId, String userId, String type,
                                   String targetId, String sessionId, String price) {
        LearningOrder o = new LearningOrder();
        o.setOrderId(orderId);
        o.setOrderNo("NO_" + orderId);
        o.setUserId(userId);
        o.setOrderType(type);
        o.setTargetId(targetId);
        o.setSessionId(sessionId);
        o.setTargetTitle("测试商品");
        o.setAmount(new BigDecimal(price));
        o.setPaidAmount(BigDecimal.ZERO);
        o.setCurrency("CNY");
        o.setOrderStatus("CREATED");
        return o;
    }
}