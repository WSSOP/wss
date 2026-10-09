package cn.dct.zpback.service;

import cn.dct.zpback.entity.*;
import cn.dct.zpback.exception.LearningValidationException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;

import static cn.dct.zpback.service.LearningTestStubs.*;
import static org.junit.jupiter.api.Assertions.*;

/**
 * 问星渡｜生命周期测试：取消报名、出勤核销、退款
 *
 * 对应文档 02_Production_Operation_Manual 提及的
 * 「订单退款」「讲师管理」「客服流程」三类运营动作。
 */
class LearningLifecycleTest {

    private LearningCourseService courseSvc;
    private LearningEventService eventSvc;
    private LearningCommerceService commerce;

    private CourseMapperStub courses;
    private EventMapperStub events;
    private SessionMapperStub sessions;
    private EntitlementMapperStub entitlements;
    private EnrollmentMapperStub enrollments;
    private OrderMapperStub orders;

    private static final String USER = "user_buyer";
    private static final String OTHER = "user_stranger";
    private static final String TEACHER = "user_teacher_lin";
    private static final String STRANGER = "user_teacher_summer";
    private static final String HOST = "user_teacher_summer";

    @BeforeEach
    void setUp() {
        courses = new CourseMapperStub();
        events = new EventMapperStub();
        sessions = new SessionMapperStub();
        enrollments = new EnrollmentMapperStub();
        sessions.bind(enrollments);
        entitlements = new EntitlementMapperStub();
        orders = new OrderMapperStub();

        // 依赖单向：Commerce -> Course/Event
        courseSvc = new LearningCourseService(courses, sessions, entitlements, enrollments);
        eventSvc = new LearningEventService(events, sessions, entitlements, enrollments);
        commerce = new LearningCommerceService(orders, sessions, entitlements, enrollments, courseSvc, eventSvc);
    }

    private void givenCourse(String id, String price, int capacity) {
        courses.byId.put(id, publishedCourse(id, TEACHER));
        sessions.put("s_" + id, courseSession("s_" + id, id, price, capacity));
    }

    private void givenEvent(String id, String price, int capacity) {
        events.byId.put(id, publishedEvent(id, HOST));
        sessions.put("ES_" + id, eventSession("ES_" + id, id, price, capacity));
    }

    /** 建一笔待支付订单（商品为课程） */
    private String givenOrder(String orderId, String type, String targetId,
                              String sessionId, String price) {
        if ("EVENT".equals(type)) {
            givenEvent(targetId, price, 10);
        } else {
            givenCourse(targetId, price, 10);
        }
        orders.byId.put(orderId, paidOrder(orderId, USER, type, targetId, sessionId, price));
        return orderId;
    }

    // ==========================================================
    // 取消报名
    // ==========================================================

    @Test
    @DisplayName("取消报名释放名额")
    void cancel_releasesSeat() {
        givenCourse("c1", "0.00", 5);
        courseSvc.enrollFree("c1", "s_c1", USER);
        assertEquals(1, sessions.courseSeatDelta("s_c1"));

        Map<String, Object> r = courseSvc.cancelEnrollment(USER, "s_c1");

        assertEquals("CANCELLED", r.get("enrollment_status"));
        assertEquals(Boolean.TRUE, r.get("seat_released"));
        assertEquals(0, sessions.currentCount("s_c1"), "取消后名额必须还回去");
    }

    @Test
    @DisplayName("活动侧取消也释放名额")
    void cancelEvent_releasesSeat() {
        givenEvent("e1", "0.00", 5);
        eventSvc.enrollFree("e1", "ES_e1", USER);

        eventSvc.cancelEnrollment(USER, "ES_e1");

        assertEquals(0, sessions.currentCount("ES_e1"));
    }

    @Test
    @DisplayName("重复取消幂等，名额不会减到负数")
    void cancel_isIdempotent() {
        givenCourse("c2", "0.00", 5);
        courseSvc.enrollFree("c2", "s_c2", USER);

        courseSvc.cancelEnrollment(USER, "s_c2");
        Map<String, Object> again = courseSvc.cancelEnrollment(USER, "s_c2");

        assertEquals("CANCELLED", again.get("enrollment_status"));
        assertEquals(Boolean.TRUE, again.get("already_cancelled"));
        assertEquals(0, sessions.currentCount("s_c2"));
        assertTrue(sessions.currentCount("s_c2") >= 0, "人数不能为负");
    }

    @Test
    @DisplayName("已核销出勤的场次不可取消")
    void cancel_rejectsAttended() {
        givenCourse("c3", "0.00", 5);
        courseSvc.enrollFree("c3", "s_c3", USER);
        courseSvc.checkAttendance("s_c3", USER, "ATTENDED", TEACHER, false);

        LearningValidationException ex = assertThrows(LearningValidationException.class,
                () -> courseSvc.cancelEnrollment(USER, "s_c3"));
        assertEquals(LearningCourseService.E_SESSION_CLOSED, ex.getCode());
    }

    @Test
    @DisplayName("取消后可重新报名，走 revive 而非重复插入")
    void reEnroll_afterCancel_revives() {
        givenCourse("c4", "0.00", 5);
        courseSvc.enrollFree("c4", "s_c4", USER);
        String firstId = enrollments.findExisting(USER, "s_c4").getEnrollmentId();

        courseSvc.cancelEnrollment(USER, "s_c4");
        courseSvc.enrollFree("c4", "s_c4", USER);

        assertEquals(1, enrollments.rows.size(), "重新报名应复用同一行，不新增");
        assertEquals(firstId, enrollments.findExisting(USER, "s_c4").getEnrollmentId());
        assertEquals(1, sessions.courseSeatDelta("s_c4"), "重新报名重新占一个名额");
    }

    // ==========================================================
    // 出勤核销
    // ==========================================================

    @Test
    @DisplayName("核销响应必须反映新状态，不能返回更新前的快照")
    void attendance_responseReflectsUpdate() {
        // 回归用例：Service 曾返回更新前读到的对象，
        // 结果讲师点完名看到还是「已报名」，以为没点上又点一次。
        // 桩现在返回拷贝（模拟真实 MyBatis 每次查询都是新对象），
        // 漏回填就会让这条断言失败。
        givenCourse("c20", "0.00", 5);
        courseSvc.enrollFree("c20", "s_c20", USER);

        Map<String, Object> r = courseSvc.checkAttendance("s_c20", USER, "ATTENDED", TEACHER, false);

        assertEquals("ATTENDED", r.get("enrollment_status"));
        assertEquals(Boolean.FALSE, r.get("already_checked"));
        assertNotNull(r.get("attendance_checked_at"), "响应里应带核销时间");
        assertEquals("ATTENDED", enrollments.findExisting(USER, "s_c20").getEnrollmentStatus());
    }

    @Test
    @DisplayName("活动侧核销响应同样反映新状态")
    void attendanceEvent_responseReflectsUpdate() {
        givenEvent("e20", "0.00", 5);
        eventSvc.enrollFree("e20", "ES_e20", USER);

        Map<String, Object> r = eventSvc.checkAttendance("ES_e20", USER, "ATTENDED", HOST, false);

        assertEquals("ATTENDED", r.get("enrollment_status"));
    }

    @Test
    @DisplayName("讲师核销本课出勤成功")
    void attendance_byOwnTeacher_ok() {
        givenCourse("c5", "0.00", 5);
        courseSvc.enrollFree("c5", "s_c5", USER);

        Map<String, Object> r = courseSvc.checkAttendance("s_c5", USER, "ATTENDED", TEACHER, false);

        assertEquals("ATTENDED", r.get("enrollment_status"));
        assertNotNull(enrollments.findExisting(USER, "s_c5").getAttendanceCheckedAt());
    }

    @Test
    @DisplayName("讲师不能核验别人的课")
    void attendance_rejectsOtherTeacher() {
        givenCourse("c6", "0.00", 5);
        courseSvc.enrollFree("c6", "s_c6", USER);

        LearningValidationException ex = assertThrows(LearningValidationException.class,
                () -> courseSvc.checkAttendance("s_c6", USER, "ATTENDED", STRANGER, false));
        assertEquals(LearningCourseService.E_NO_PERMISSION, ex.getCode());
        assertEquals("ENROLLED", enrollments.findExisting(USER, "s_c6").getEnrollmentStatus());
    }

    @Test
    @DisplayName("助教也能核验（文档：辅助学习运营）")
    void attendance_assistantAllowed() {
        givenCourse("c6b", "0.00", 5);
        LearningCourse c = courses.byId.get("c6b");
        c.setTeacherId("user_teacher_x");
        c.setAssistantTeacherId(STRANGER);
        courseSvc.enrollFree("c6b", "s_c6b", USER);

        Map<String, Object> r = courseSvc.checkAttendance("s_c6b", USER, "ATTENDED", STRANGER, false);
        assertEquals("ATTENDED", r.get("enrollment_status"));
    }

    @Test
    @DisplayName("Admin 可核验任意课程，不受归属限制")
    void attendance_adminBypassesOwnership() {
        givenCourse("c7", "0.00", 5);
        courseSvc.enrollFree("c7", "s_c7", USER);

        Map<String, Object> r = courseSvc.checkAttendance("s_c7", USER, "ATTENDED", STRANGER, true);
        assertEquals("ATTENDED", r.get("enrollment_status"));
    }

    @Test
    @DisplayName("重复核销幂等")
    void attendance_isIdempotent() {
        givenCourse("c8", "0.00", 5);
        courseSvc.enrollFree("c8", "s_c8", USER);

        courseSvc.checkAttendance("s_c8", USER, "ATTENDED", TEACHER, false);
        Map<String, Object> again = courseSvc.checkAttendance("s_c8", USER, "ATTENDED", TEACHER, false);

        assertEquals(Boolean.TRUE, again.get("already_checked"));
    }

    @Test
    @DisplayName("未报名的人不能被核销出勤")
    void attendance_rejectsNonEnrolled() {
        givenCourse("c9", "0.00", 5);
        LearningValidationException ex = assertThrows(LearningValidationException.class,
                () -> courseSvc.checkAttendance("s_c9", OTHER, "ATTENDED", TEACHER, false));
        assertEquals(LearningCourseService.E_SESSION_NOT_FOUND, ex.getCode());
    }

    @Test
    @DisplayName("非法出勤状态被拒")
    void attendance_rejectsBadStatus() {
        givenCourse("c10", "0.00", 5);
        courseSvc.enrollFree("c10", "s_c10", USER);
        assertThrows(LearningValidationException.class,
                () -> courseSvc.checkAttendance("s_c10", USER, "MAYBE", TEACHER, false));
    }

    @Test
    @DisplayName("主持人只能核验自己主持的活动")
    void attendanceEvent_hostOwnership() {
        givenEvent("e2", "0.00", 5);
        eventSvc.enrollFree("e2", "ES_e2", USER);

        assertThrows(LearningValidationException.class,
                () -> eventSvc.checkAttendance("ES_e2", USER, "ATTENDED", "user_stranger_host", false));

        Map<String, Object> ok = eventSvc.checkAttendance("ES_e2", USER, "ATTENDED", HOST, false);
        assertEquals("ATTENDED", ok.get("enrollment_status"));
    }

    @Test
    @DisplayName("讲师端能看到自己的课")
    void myCourses_listsOwned() {
        courses.byId.put("c_own", publishedCourse("c_own", TEACHER));
        courses.byId.put("c_other", publishedCourse("c_other", STRANGER));

        List<LearningCourse> mine = courseSvc.listMyCourses(TEACHER);

        assertEquals(1, mine.size());
        assertEquals("c_own", mine.get(0).getCourseId());
    }

    // ==========================================================
    // 退款
    // ==========================================================

    @Test
    @DisplayName("交付必须同时建报名，否则用户付了钱打不开直播间")
    void fulfill_alsoEnrolls() {
        // 回归用例：曾只发权限不建报名，
        // 而 /sessions/{id}/live 校验的是报名关系，
        // 结果用户付了钱仍然拿不到直播地址。
        String orderId = givenOrder("o10", "COURSE", "c30", "s_c30", "880.00");
        commerce.markPaid(orderId, "WECHAT", new BigDecimal("880.00"));

        commerce.fulfillOrder(orderId);

        LearningEnrollment e = enrollments.findExisting(USER, "s_c30");
        assertNotNull(e, "交付后应有报名记录");
        assertEquals("ENROLLED", e.getEnrollmentStatus());
        assertEquals("COURSE", e.getTargetType());
        assertEquals(1, sessions.courseSeatDelta("s_c30"), "报名应占住名额");
        assertTrue(service_canSeeLive(USER, "s_c30"), "交付后应能看到直播地址");
    }

    /** 模拟已报名者调 /sessions/{id}/live 是否拿得到地址 */
    private boolean service_canSeeLive(String userId, String sessionId) {
        try {
            courseSvc.enrolledSession(sessionId, userId);
            return true;
        } catch (LearningValidationException ex) {
            return false;
        }
    }

    @Test
    @DisplayName("活动侧交付同样建报名")
    void fulfillEvent_alsoEnrolls() {
        givenEvent("e30", "880.00", 5);
        LearningOrder o = paidOrder("o11", USER, "EVENT", "e30", "ES_e30", "880.00");
        orders.byId.put("o11", o);
        commerce.markPaid("o11", "WECHAT", new BigDecimal("880.00"));

        commerce.fulfillOrder("o11");

        LearningEnrollment e = enrollments.findExisting(USER, "ES_e30");
        assertNotNull(e, "活动交付后也应有报名记录");
        assertEquals("EVENT", e.getTargetType());
    }

    @Test
    @DisplayName("名额已满时仍交付权限，只是不占位")
    void fulfill_seatFullStillGrantsEntitlement() {
        // 用户已付钱，权限必须给；占不到位由运营人工安排
        String orderId = givenOrder("o12", "COURSE", "c31", "s_c31", "880.00");
        sessions.enrolledCount.put("s_c31", 5);   // capacity=10，模拟占满
        sessions.byId.get("s_c31").setCapacity(5);
        commerce.markPaid(orderId, "WECHAT", new BigDecimal("880.00"));

        commerce.fulfillOrder(orderId);

        assertEquals("FULFILLED", orders.byId.get(orderId).getOrderStatus());
        assertNotNull(entitlementMapper_findByOrder(orderId), "名额满也必须已发权限");
        assertNull(enrollments.findExisting(USER, "s_c31"), "名额满时不应有报名记录");
    }

    private LearningEntitlement entitlementMapper_findByOrder(String orderId) {
        for (LearningEntitlement e : entitlements.all.values()) {
            if (orderId.equals(e.getOrderId())) return e;
        }
        return null;
    }

    @Test
    @DisplayName("重复交付不会重复占名额")
    void fulfill_repeatDoesNotDoubleEnroll() {
        String orderId = givenOrder("o13", "COURSE", "c32", "s_c32", "880.00");
        commerce.markPaid(orderId, "WECHAT", new BigDecimal("880.00"));
        commerce.fulfillOrder(orderId);
        commerce.fulfillOrder(orderId);

        assertEquals(1, enrollments.rows.size());
        assertEquals(1, sessions.courseSeatDelta("s_c32"));
    }

    @Test
    @DisplayName("退款必须取消报名，否则用户退完还能进直播间")
    void refund_alsoCancelsEnrollment() {
        // 回归用例：直播入口 JOIN 的是 learning_enrollment，
        // 只撤 entitlement 的话退款后仍能拿直播地址。
        String orderId = givenOrder("o20", "COURSE", "c40", "s_c40", "880.00");
        commerce.markPaid(orderId, "WECHAT", new BigDecimal("880.00"));
        commerce.fulfillOrder(orderId);
        assertNotNull(enrollments.findExisting(USER, "s_c40"), "交付后应有报名");

        commerce.refundOrder(orderId, "退");

        LearningEnrollment e = enrollments.findExisting(USER, "s_c40");
        assertEquals("CANCELLED", e.getEnrollmentStatus(), "退款后报名应被取消");
        assertEquals(0, sessions.currentCount("s_c40"), "名额应已释放");
        assertFalse(service_canSeeLive(USER, "s_c40"), "退款后不应还能看直播");
    }

    @Test
    @DisplayName("活动侧退款同样取消报名")
    void refundEvent_alsoCancelsEnrollment() {
        givenEvent("e40", "880.00", 5);
        orders.byId.put("o21", paidOrder("o21", USER, "EVENT", "e40", "ES_e40", "880.00"));
        commerce.markPaid("o21", "WECHAT", new BigDecimal("880.00"));
        commerce.fulfillOrder("o21");

        commerce.refundOrder("o21", "退");

        assertEquals("CANCELLED", enrollments.findExisting(USER, "ES_e40").getEnrollmentStatus());
        assertEquals(0, sessions.currentCount("ES_e40"));
    }

    @Test
    @DisplayName("退款撤销权限")
    void refund_revokesEntitlement() {
        String orderId = givenOrder("o1", "COURSE", "c11", "s_c11", "880.00");
        commerce.markPaid(orderId, "WECHAT", new BigDecimal("880.00"));
        commerce.fulfillOrder(orderId);
        assertEquals(1, entitlements.all.size());

        Map<String, Object> r = commerce.refundOrder(orderId, "用户申请");

        assertEquals("REFUNDED", r.get("order_status"));
        assertEquals(1, r.get("entitlements_revoked"));
        assertEquals("REVOKED", entitlements.all.values().iterator().next().getEntitlementStatus(),
                "退款后权限必须失效，否则用户退款还能继续学");
        assertEquals(1, entitlements.all.size(), "权限记录不能删除，要留作对账凭证");
    }

    @Test
    @DisplayName("退款幂等")
    void refund_isIdempotent() {
        String orderId = givenOrder("o2", "COURSE", "c12", "s_c12", "880.00");
        commerce.markPaid(orderId, "WECHAT", new BigDecimal("880.00"));
        commerce.fulfillOrder(orderId);

        commerce.refundOrder(orderId, "第一次");
        Map<String, Object> again = commerce.refundOrder(orderId, "第二次");

        assertEquals("REFUNDED", again.get("order_status"));
        assertEquals(Boolean.TRUE, again.get("already_refunded"));
    }

    @Test
    @DisplayName("未支付订单不能退款")
    void refund_rejectsUnpaid() {
        String orderId = givenOrder("o3", "COURSE", "c13", "s_c13", "880.00");
        LearningValidationException ex = assertThrows(LearningValidationException.class,
                () -> commerce.refundOrder(orderId, "试试"));
        assertEquals(LearningCommerceService.E_ORDER_STATE, ex.getCode());
    }

    @Test
    @DisplayName("退款不影响其他订单的权限")
    void refund_onlyAffectsItsOwnOrder() {
        String o1 = givenOrder("o4", "COURSE", "c14", "s_c14", "880.00");
        String o2 = givenOrder("o5", "COURSE", "c15", "s_c15", "380.00");
        commerce.markPaid(o1, "WECHAT", new BigDecimal("880.00"));
        commerce.markPaid(o2, "WECHAT", new BigDecimal("380.00"));
        commerce.fulfillOrder(o1);
        commerce.fulfillOrder(o2);
        assertEquals(2, entitlements.all.size());

        commerce.refundOrder(o1, "退第一笔");

        assertEquals(1, entitlements.activeCount(), "只该撤销退款那一笔的权限");
    }

    @Test
    @DisplayName("未支付订单可取消，已支付不可")
    void cancelOrder_stateMachine() {
        String orderId = givenOrder("o6", "COURSE", "c16", "s_c16", "880.00");
        commerce.cancelOrder(orderId, USER);
        assertEquals("CANCELLED", orders.byId.get(orderId).getOrderStatus());

        String o2 = givenOrder("o7", "COURSE", "c17", "s_c17", "880.00");
        commerce.markPaid(o2, "WECHAT", new BigDecimal("880.00"));
        assertThrows(LearningValidationException.class,
                () -> commerce.cancelOrder(o2, USER));
    }

    @Test
    @DisplayName("不能取消别人的订单")
    void cancelOrder_rejectsOtherUser() {
        String orderId = givenOrder("o8", "COURSE", "c18", "s_c18", "880.00");
        LearningValidationException ex = assertThrows(LearningValidationException.class,
                () -> commerce.cancelOrder(orderId, "user_hacker"));
        assertEquals(LearningCommerceService.E_NO_PERMISSION, ex.getCode());
    }
}