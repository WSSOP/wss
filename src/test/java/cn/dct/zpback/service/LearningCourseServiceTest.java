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

class LearningCourseServiceTest {

    private LearningCourseService courseSvc;
    private LearningCommerceService commerce;
    private LearningEventService eventSvc;

    private CourseMapperStub courses;
    private EventMapperStub events;
    private SessionMapperStub sessions;
    private EntitlementMapperStub entitlements;
    private EnrollmentMapperStub enrollments;
    private OrderMapperStub orders;

    private static final String USER = "user_001";
    private static final String TEACHER = "user_teacher_lin";

    @BeforeEach
    void setUp() {
        courses = new CourseMapperStub();
        events = new EventMapperStub();
        sessions = new SessionMapperStub();
        enrollments = new EnrollmentMapperStub();
        sessions.bind(enrollments);
        entitlements = new EntitlementMapperStub();
        orders = new OrderMapperStub();

        // 依赖方向是单向的：Commerce -> Course/Event，Course 不反向依赖 Commerce。
        // 否则 Spring 会因循环依赖直接启动失败。
        courseSvc = new LearningCourseService(courses, sessions, entitlements, enrollments);
        eventSvc = new LearningEventService(events, sessions, entitlements, enrollments);
        commerce = new LearningCommerceService(orders, sessions, entitlements, enrollments, courseSvc, eventSvc);
    }

    private void givenCourse(String id, String price, int capacity) {
        courses.byId.put(id, publishedCourse(id, TEACHER));
        sessions.put("s_" + id, courseSession("s_" + id, id, price, capacity));
    }

    // ==========================================================
    // 核心边界：Payment 不直接创建业务权限
    // ==========================================================

    @Test
    @DisplayName("支付成功后不发放权限，必须再走 Fulfillment")
    void payCallback_doesNotGrantEntitlement() {
        givenCourse("c1", "880.00", 30);
        Map<String, Object> order = commerce.createOrder("COURSE", "c1", "s_c1", USER, "COURSE_DETAIL");
        String orderId = String.valueOf(order.get("order_id"));

        Map<String, Object> paid = commerce.markPaid(orderId, "MANUAL", new BigDecimal("880.00"));

        assertEquals("PAID", paid.get("order_status"));
        assertEquals(0, entitlements.all.size(),
                "支付回调不得直接创建学习权限（原始文档硬要求）");

        Map<String, Object> fulfilled = commerce.fulfillOrder(orderId);
        assertEquals("FULFILLED", fulfilled.get("order_status"));
        assertEquals(1, entitlements.all.size(), "Fulfillment 之后才应有权限");
        assertEquals("ORDER_PAID", entitlements.all.values().iterator().next().getSource());
    }

    @Test
    @DisplayName("Fulfillment 幂等：重复交付不重复发权限")
    void fulfill_isIdempotent() {
        givenCourse("c2", "880.00", 30);
        String orderId = String.valueOf(
                commerce.createOrder("COURSE", "c2", "s_c2", USER, "COURSE_DETAIL").get("order_id"));
        commerce.markPaid(orderId, "MANUAL", new BigDecimal("880.00"));

        commerce.fulfillOrder(orderId);
        commerce.fulfillOrder(orderId);
        commerce.fulfillOrder(orderId);

        assertEquals(1, entitlements.all.size(), "重复交付只发一份权限");
    }

    @Test
    @DisplayName("支付回调幂等：重复回调不重复发货")
    void payCallback_isIdempotent() {
        givenCourse("c3", "880.00", 30);
        String orderId = String.valueOf(
                commerce.createOrder("COURSE", "c3", "s_c3", USER, "COURSE_DETAIL").get("order_id"));

        commerce.markPaid(orderId, "MANUAL", new BigDecimal("880.00"));
        commerce.markPaid(orderId, "MANUAL", new BigDecimal("880.00"));

        assertEquals("PAID", orders.byId.get(orderId).getOrderStatus());
        assertTrue(entitlements.all.isEmpty());
    }

    @Test
    @DisplayName("未支付订单不能发权限")
    void fulfill_rejectsUnpaidOrder() {
        givenCourse("c4", "880.00", 30);
        String orderId = String.valueOf(
                commerce.createOrder("COURSE", "c4", "s_c4", USER, "COURSE_DETAIL").get("order_id"));

        LearningValidationException ex = assertThrows(LearningValidationException.class,
                () -> commerce.fulfillOrder(orderId));
        assertEquals(LearningCommerceService.E_ORDER_STATE, ex.getCode());
        assertTrue(entitlements.all.isEmpty());
    }

    @Test
    @DisplayName("补发巡检只处理已支付未交付的订单")
    void reFulfill_onlyTouchesPaidNotFulfilled() {
        givenCourse("c5", "880.00", 30);
        String orderId = String.valueOf(
                commerce.createOrder("COURSE", "c5", "s_c5", USER, "COURSE_DETAIL").get("order_id"));
        commerce.markPaid(orderId, "MANUAL", new BigDecimal("880.00"));

        int n = commerce.reFulfillPending(10);

        assertEquals(1, n);
        assertEquals("FULFILLED", orders.byId.get(orderId).getOrderStatus());
        assertEquals(1, entitlements.all.size());
    }

    // ==========================================================
    // 免费路径
    // ==========================================================

    @Test
    @DisplayName("免费场次报名：直接发权限并占名额")
    void enrollFree_grantsEntitlementDirectly() {
        givenCourse("c6", "0.00", 10);
        Map<String, Object> r = courseSvc.enrollFree("c6", "s_c6", USER);

        assertEquals("ENROLLED", r.get("enrollment_status"));
        assertEquals("FREE_GRANT", r.get("entitlement_source"));
        assertEquals(1, entitlements.all.size());
        assertEquals(1, sessions.courseSeatDelta("s_c6"));
    }

    @Test
    @DisplayName("付费场次不能用免费报名")
    void enrollFree_rejectsPaidSession() {
        givenCourse("c7", "880.00", 30);
        LearningValidationException ex = assertThrows(LearningValidationException.class,
                () -> courseSvc.enrollFree("c7", "s_c7", USER));
        assertEquals(LearningCourseService.E_REQUIRES_ORDER, ex.getCode());
        assertTrue(entitlements.all.isEmpty(), "被拒绝的请求不得留下权限");
    }

    @Test
    @DisplayName("免费内容不能下单")
    void createOrder_rejectsFreeSession() {
        givenCourse("c8", "0.00", 10);
        LearningValidationException ex = assertThrows(LearningValidationException.class,
                () -> commerce.createOrder("COURSE", "c8", "s_c8", USER, "COURSE_LIST"));
        assertEquals(LearningCommerceService.E_FREE_ITEM, ex.getCode());
        assertTrue(orders.byId.isEmpty());
    }

    // ==========================================================
    // 名额与幂等
    // ==========================================================

    @Test
    @DisplayName("名额已满时不产生报名记录")
    void seatFull_doesNotCreateEnrollment() {
        courses.byId.put("c9", publishedCourse("c9", TEACHER));
        sessions.put("s_c9", courseSession("s_c9", "c9", "0.00", 1));
        sessions.enrolledCount.put("s_c9", 1);   // 名额 1，已满

        LearningValidationException ex = assertThrows(LearningValidationException.class,
                () -> courseSvc.enrollFree("c9", "s_c9", USER));

        assertEquals(LearningCourseService.E_SEAT_FULL, ex.getCode());
        assertTrue(enrollments.rows.isEmpty(), "名额没占到就不该留下报名记录");
    }

    @Test
    @DisplayName("重复报名被拒，名额不会重复扣")
    void duplicateEnroll_rejectedAndSeatNotDoubleCounted() {
        givenCourse("c10", "0.00", 10);
        courseSvc.enrollFree("c10", "s_c10", USER);
        assertThrows(LearningValidationException.class,
                () -> courseSvc.enrollFree("c10", "s_c10", USER));
        assertEquals(1, sessions.courseSeatDelta("s_c10"), "重复报名不应再扣名额");
    }

    @Test
    @DisplayName("重复下单返回既有订单，不产生第二笔")
    void duplicateOrder_returnsExisting() {
        givenCourse("c11", "880.00", 30);
        Map<String, Object> first = commerce.createOrder("COURSE", "c11", "s_c11", USER, "COURSE_DETAIL");
        Map<String, Object> second = commerce.createOrder("COURSE", "c11", "s_c11", USER, "COURSE_DETAIL");

        assertEquals(first.get("order_id"), second.get("order_id"));
        assertEquals(Boolean.TRUE, second.get("reused"));
        assertEquals(1, orders.byId.size());
    }

    @Test
    @DisplayName("场次与目标不匹配时拒绝，防止越权报名别的课")
    void sessionMustBelongToTarget() {
        courses.byId.put("c_mine", publishedCourse("c_mine", TEACHER));
        courses.byId.put("c_other", publishedCourse("c_other", TEACHER));
        sessions.put("s_x", courseSession("s_x", "c_other", "0.00", 10));

        LearningValidationException ex = assertThrows(LearningValidationException.class,
                () -> courseSvc.enrollFree("c_mine", "s_x", USER));
        assertEquals(LearningCourseService.E_SESSION_NOT_FOUND, ex.getCode());
    }

    // ==========================================================
    // 直播地址可见性
    // ==========================================================

    @Test
    @DisplayName("未报名者拿不到直播地址")
    void liveSession_deniedForNonEnrolled() {
        givenCourse("c12", "0.00", 10);
        sessions.liveUrl.put("s_c12", "https://live.example.com/secret");

        LearningValidationException ex = assertThrows(LearningValidationException.class,
                () -> courseSvc.enrolledSession("s_c12", "user_other"));
        assertEquals(LearningCourseService.E_NO_PERMISSION, ex.getCode());
    }

    @Test
    @DisplayName("已报名者拿得到直播地址")
    void liveSession_allowedForEnrolled() {
        givenCourse("c13", "0.00", 10);
        sessions.liveUrl.put("s_c13", "https://live.example.com/secret");
        courseSvc.enrollFree("c13", "s_c13", USER);

        LearningSession s = (LearningSession) courseSvc.enrolledSession("s_c13", USER).get("session");
        assertEquals("https://live.example.com/secret", s.getLiveUrl());
    }

    @Test
    @DisplayName("列表与详情不含直播地址")
    void listAndDetail_doNotLeakLiveUrl() {
        givenCourse("c14", "0.00", 10);
        sessions.liveUrl.put("s_c14", "https://live.example.com/secret");

        for (LearningSession s : sessions.findCourseSessions("c14")) {
            assertNull(s.getLiveUrl(), "未报名者的场次列表不得含直播地址");
            assertNull(s.getRecordingUrl());
        }
    }

    @Test
    @DisplayName("取消报名后拿不到直播地址")
    void live_deniedAfterCancel() {
        givenCourse("c15", "0.00", 10);
        sessions.liveUrl.put("s_c15", "https://live.example.com/secret");
        courseSvc.enrollFree("c15", "s_c15", USER);

        courseSvc.cancelEnrollment(USER, "s_c15");

        assertThrows(LearningValidationException.class,
                () -> courseSvc.enrolledSession("s_c15", USER));
    }

    // ==========================================================
    // 发布状态过滤
    // ==========================================================

    @Test
    @DisplayName("未发布课程不出现在列表，但详情可查到以便提示下架")
    void draftCourse_hiddenInList() {
        LearningCourse draft = publishedCourse("c_draft", TEACHER);
        draft.setCourseStatus("DRAFT");
        courses.published.add(publishedCourse("c_pub", TEACHER));
        courses.byId.put("c_draft", draft);
        courses.byId.put("c_pub", publishedCourse("c_pub", TEACHER));

        List<LearningCourse> list = courseSvc.listCourses(null, null, USER);

        assertEquals(1, list.size());
        assertEquals("c_pub", list.get(0).getCourseId());

        LearningValidationException ex = assertThrows(LearningValidationException.class,
                () -> courseSvc.courseDetail("c_draft", USER));
        assertEquals(LearningCourseService.E_NOT_PUBLISHED, ex.getCode());
    }
}