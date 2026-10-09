package cn.dct.zpback.service;

import cn.dct.zpback.entity.MyLearningRow;
import cn.dct.zpback.mapper.LearningOrderMapper;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

import static cn.dct.zpback.service.LearningTestStubs.*;
import static org.junit.jupiter.api.Assertions.*;

/**
 * 问星渡｜我的学习 列表合并与排序
 *
 * 锁的是三条容易被改坏的性质：
 *   1. 课程与活动合并成一张列表（两条独立查询，Java 侧合并，
 *      <b>不在 SQL 里跨 Domain JOIN</b>——文档要求 Domain 隔离）；
 *   2. 最近的场次排在最前；
 *   3. 未登录返回空列表，不抛异常。
 *
 * 这三条直接影响「我的学习」页能不能正常渲染。
 */
class MyLearningQueryTest {

    private final List<MyLearningRow> courseRows = new ArrayList<>();
    private final List<MyLearningRow> eventRows = new ArrayList<>();

    /**
     * myLearning 放在 Commerce Service：它是跨 Domain 的聚合查询，
     * 而 Commerce Core 按文档定位就是「统一处理跨域编排」。
     */
    private LearningCommerceService service() {
        SessionMapperStub sessions = new SessionMapperStub();
        sessions.myCourseRows = courseRows;
        sessions.myEventRows = eventRows;

        LearningCourseService courseSvc = new LearningCourseService(
                new CourseMapperStub(), new SessionMapperStub(),
                new EntitlementMapperStub(), new EnrollmentMapperStub());
        LearningEventService eventSvc = new LearningEventService(
                new EventMapperStub(), new SessionMapperStub(),
                new EntitlementMapperStub(), new EnrollmentMapperStub());
        return new LearningCommerceService(
                new NoopOrderMapper(), sessions,
                new EntitlementMapperStub(), new EnrollmentMapperStub(),
                courseSvc, eventSvc);
    }

    private MyLearningRow row(String type, LocalDateTime start) {
        MyLearningRow r = new MyLearningRow();
        r.setTargetType(type);
        r.setStartAt(start);
        return r;
    }

    @Test
    @DisplayName("课程与活动合并为一张列表")
    void myLearning_mergesCourseAndEvent() {
        courseRows.add(row("COURSE", LocalDateTime.now().plusDays(1)));
        eventRows.add(row("EVENT", LocalDateTime.now().plusDays(2)));

        List<MyLearningRow> out = service().myLearning("user_x");

        assertEquals(2, out.size());
        assertTrue(out.stream().anyMatch(r -> "COURSE".equals(r.getTargetType())));
        assertTrue(out.stream().anyMatch(r -> "EVENT".equals(r.getTargetType())));
    }

    @Test
    @DisplayName("最近的场次排在最前")
    void myLearning_sortsByStartDesc() {
        LocalDateTime soon = LocalDateTime.now().plusDays(1);
        LocalDateTime mid = LocalDateTime.now().plusDays(3);
        LocalDateTime later = LocalDateTime.now().plusDays(5);
        courseRows.add(row("COURSE", later));
        courseRows.add(row("COURSE", soon));
        eventRows.add(row("EVENT", mid));

        List<MyLearningRow> out = service().myLearning("user_x");

        assertEquals(3, out.size());
        assertTrue(out.get(0).getStartAt().isAfter(out.get(1).getStartAt()), "第 1 条应晚于第 2 条");
        assertTrue(out.get(1).getStartAt().isAfter(out.get(2).getStartAt()), "第 2 条应晚于第 3 条");
    }

    @Test
    @DisplayName("排序对 null 开始时间稳定，不抛 NPE")
    void myLearning_toleratesNullStart() {
        courseRows.add(row("COURSE", null));
        eventRows.add(row("EVENT", LocalDateTime.now().plusDays(1)));

        List<MyLearningRow> out = service().myLearning("user_x");

        assertEquals(2, out.size());
        assertNotNull(out.get(0).getStartAt(), "有时间的应排在无时间之前");
    }

    @Test
    @DisplayName("未登录返回空列表，不抛异常")
    void myLearning_anonymousReturnsEmpty() {
        courseRows.add(row("COURSE", LocalDateTime.now()));
        assertTrue(service().myLearning(null).isEmpty());
        assertTrue(service().myLearning("").isEmpty());
        assertTrue(service().myLearning("anonymous").isEmpty());
    }

    @Test
    @DisplayName("没有任何报名时返回空列表")
    void myLearning_noEnrollmentsEmpty() {
        assertTrue(service().myLearning("user_x").isEmpty());
    }

    private static final class NoopOrderMapper implements LearningOrderMapper {
        @Override public cn.dct.zpback.entity.LearningOrder findById(String id) { return null; }
        @Override public cn.dct.zpback.entity.LearningOrder findByNo(String no) { return null; }
        @Override public cn.dct.zpback.entity.LearningOrder findOpenOrder(String u, String t, String g, String s) { return null; }
        @Override public List<cn.dct.zpback.entity.LearningOrder> findByUser(String u, int l) { return List.of(); }
        @Override public List<cn.dct.zpback.entity.LearningOrder> findPendingFulfillment(int l) { return List.of(); }
        @Override public int insert(cn.dct.zpback.entity.LearningOrder o) { return 0; }
        @Override public int markPaid(String id, String c, BigDecimal p) { return 0; }
        @Override public int markFulfilled(String id) { return 0; }
        @Override public int cancel(String id) { return 0; }
        @Override public int refund(String id) { return 0; }
    }
}