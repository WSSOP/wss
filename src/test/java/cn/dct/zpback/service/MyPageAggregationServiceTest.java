package cn.dct.zpback.service;

import cn.dct.zpback.dto.mypage.MyPageBootstrap;
import cn.dct.zpback.entity.Chart;
import cn.dct.zpback.entity.LearningEnrollment;
import cn.dct.zpback.entity.User;
import cn.dct.zpback.mapper.ChartMapper;
import cn.dct.zpback.mapper.LearningEnrollmentMapper;
import cn.dct.zpback.mapper.UserMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/**
 * 问星渡｜【我的】一级页聚合 —— 锁文档第 19.1 节「页面顺序」验收项
 *
 * <b>为什么锁顺序</b>：文档第 2 节写明「结构顺序为 V1 固定顺序，
 * 不开放 CMS 拖拽」。顺序一旦被改，用户的肌肉记忆就失效了，
 * 而这种改动在代码审查里很难被一眼看出来，只能靠测试固定。
 *
 * 对应验收项：
 *   - 一级页顺序与第 2 节完全一致；
 *   - 三个核心入口顺序正确；
 *   - 八个服务入口全部显示且顺序正确；
 *   - 【平台客服】不在【更多】重复；
 *   - 【设置】是整个页面最后一项。
 */
class MyPageAggregationServiceTest {

    private MyPageAggregationService svc;
    private UserMapperStub users;
    private ChartMapperStub charts;
    private EnrollmentMapperStub enrollments;
    private MallCartServiceStub cart;

    private static final String USER = "user_test_001";

    @BeforeEach
    void setUp() {
        users = new UserMapperStub();
        charts = new ChartMapperStub();
        enrollments = new EnrollmentMapperStub();
        cart = new MallCartServiceStub();
        svc = new MyPageAggregationService(users, charts, enrollments, cart);
    }

    // ==========================================================
    // 第 19.1 节：页面顺序
    // ==========================================================

    @Test
    @DisplayName("三个核心入口顺序固定")
    void coreEntries_orderFixed() {
        List<String> names = namesOf(svc.bootstrap(USER).getCoreEntries());

        assertEquals(List.of("星盘档案", "我的AI解读", "我的真人咨询"), names);
    }

    @Test
    @DisplayName("八个服务入口全部存在且顺序固定")
    void services_orderFixed() {
        List<String> names = namesOf(svc.bootstrap(USER).getServices());

        assertEquals(List.of("我的课程", "我的活动", "我的测评", "我的练习",
                "觉察日记", "商城", "卡券", "平台客服"), names);
    }

    @Test
    @DisplayName("【更多】顺序固定，且设置永远最后")
    void more_orderFixed_settingsLast() {
        List<MyPageBootstrap.Entry> more = svc.bootstrap(USER).getMore();

        assertEquals(List.of("我的订单", "购物车", "消息通知", "外观模式", "清理缓存",
                "咨询师入驻", "给个好评", "关于问星渡", "设置"),
                namesOf(more));
        assertEquals("settings", more.get(more.size() - 1).getId(),
                "设置必须是最后一项");
    }

    @Test
    @DisplayName("【平台客服】不在【更多】重复")
    void support_notDuplicatedInMore() {
        MyPageBootstrap b = svc.bootstrap(USER);

        long inServices = b.getServices().stream()
                .filter(e -> "support".equals(e.getId())).count();
        long inMore = b.getMore().stream()
                .filter(e -> "support".equals(e.getId())).count();

        assertEquals(1, inServices, "平台客服应在服务区出现一次");
        assertEquals(0, inMore, "平台客服不得在更多里重复");
    }

    @Test
    @DisplayName("咨询师入口落在给个好评之前")
    void consultant_beforeRate() {
        List<MyPageBootstrap.Entry> more = svc.bootstrap(USER).getMore();

        int ci = indexOf(more, "consultant_entry");
        int ri = indexOf(more, "rate_us");

        assertTrue(ci >= 0, "应存在咨询师入口");
        assertTrue(ci < ri, "咨询师入口应在【给个好评】之前");
    }

    // ==========================================================
    // 第 8 条：不隐藏功能入口
    // ==========================================================

    @Test
    @DisplayName("未登录仍返回全部入口，只清空 identity/wallet")
    void anonymous_keepsAllEntries() {
        MyPageBootstrap b = svc.bootstrap("anonymous");

        assertFalse(b.isLoggedIn());
        assertNull(b.getIdentity(), "未登录不应返回身份信息，且不能泄露上个账号");
        assertNull(b.getWallet(), "未登录不应返回余额");
        assertEquals(3, b.getCoreEntries().size());
        assertEquals(8, b.getServices().size());
        assertFalse(b.getMore().isEmpty(), "更多列表也要给，否则登录后布局突变");
    }

    @Test
    @DisplayName("已登录与未登录入口数量一致")
    void entryCountSameLoggedAndNot() {
        MyPageBootstrap anon = svc.bootstrap("anonymous");
        MyPageBootstrap auth = svc.bootstrap(USER);

        assertEquals(anon.getCoreEntries().size(), auth.getCoreEntries().size());
        assertEquals(anon.getServices().size(), auth.getServices().size());
        assertEquals(anon.getMore().size(), auth.getMore().size());
    }

    @Test
    @DisplayName("VIP 不隐藏入口，只改权益卡")
    void vip_doesNotHideEntries() {
        User u = baseUser();
        u.setLevel("VIP");
        u.setVipExpireAt(LocalDateTime.now().plusDays(30));
        users.byId.put(USER, u);

        MyPageBootstrap n = svc.bootstrap("user_normal_001");
        MyPageBootstrap v = svc.bootstrap(USER);

        assertEquals(n.getServices().size(), v.getServices().size(),
                "VIP 不得隐藏功能入口（文档第 8 条）");
        assertEquals("VIP", v.getMembership().getLevel());
        assertFalse(v.getMembership().isExpired());
        assertNotEquals(n.getMembership().getBenefitTitle(),
                v.getMembership().getBenefitTitle(), "权益卡内容应随会员变化");
    }

    @Test
    @DisplayName("SVIP 标识正确")
    void svip_level() {
        User u = baseUser();
        u.setLevel("SVIP");
        users.byId.put(USER, u);

        assertEquals("SVIP", svc.bootstrap(USER).getMembership().getLevel());
    }

    @Test
    @DisplayName("会员已过期仍显示入口，权益标记失效")
    void expiredVip_keepsEntry() {
        User u = baseUser();
        u.setLevel("VIP");
        u.setVipExpireAt(LocalDateTime.now().minusDays(1));
        users.byId.put(USER, u);

        MyPageBootstrap b = svc.bootstrap(USER);

        assertTrue(b.getMembership().isExpired());
        assertEquals(8, b.getServices().size(), "过期不该隐藏入口");
        assertEquals("续费", b.getMembership().getBenefitCta());
    }

    // ==========================================================
    // 第 4 节：身份与资产
    // ==========================================================

    @Test
    @DisplayName("星印余额读取真实值")
    void wallet_readsRealBalance() {
        User u = baseUser();
        u.setStarBalance(1234);
        users.byId.put(USER, u);

        MyPageBootstrap b = svc.bootstrap(USER);

        assertEquals(1234, b.getWallet().getStarBalance());
        assertEquals("/pages/me/recharge", b.getWallet().getRechargeRoute(),
                "点击余额必须直达充值页，不经钱包首页");
    }

    @Test
    @DisplayName("无星盘时 hasChart=false，不误绑他人星盘")
    void noChart_hasChartFalse() {
        MyPageBootstrap b = svc.bootstrap(USER);

        assertFalse(b.getIdentity().isHasChart());
        assertNull(b.getIdentity().getPrimaryChartId());
    }

    @Test
    @DisplayName("有星盘时带出 ID 与摘要")
    void withChart_hasChartTrue() {
        Chart c = new Chart();
        c.setChartId("chart_1");
        c.setUserId(USER);
        c.setName("本命盘");
        c.setBirthDate("1990-03-15");
        c.setBirthTime("10:30");
        c.setIsDefault(true);
        charts.defaultByUser.put(USER, c);

        MyPageBootstrap.Identity id = svc.bootstrap(USER).getIdentity();

        assertTrue(id.isHasChart());
        assertEquals("chart_1", id.getPrimaryChartId());
        assertEquals("1990-03-15 10:30", id.getBirthSummary());
    }

    // ==========================================================
    // 第 9 节 / 3.2 节：角标
    // ==========================================================

    @Test
    @DisplayName("课程角标只统计本人有效报名")
    void courseBadge_countsOwnOnly() {
        enrollments.rows.add(enrollment("e1", USER));
        enrollments.rows.add(enrollment("e2", USER));
        enrollments.rows.add(enrollment("e3", "user_other"));

        MyPageBootstrap b = svc.bootstrap(USER);

        MyPageBootstrap.Entry courses = b.getServices().get(0);
        assertEquals("my_courses", courses.getId());
        assertEquals("NUMBER", courses.getBadge().getType());
        assertEquals("2", courses.getBadge().getText(), "不应把别人的报名算进来");
    }

    @Test
    @DisplayName("数字角标封顶 99+")
    void badge_capsAt99() {
        for (int i = 0; i < 120; i++) {
            enrollments.rows.add(enrollment("e" + i, USER));
        }

        MyPageBootstrap.Badge b = svc.bootstrap(USER)
                .getServices().get(0).getBadge();

        assertEquals("NUMBER", b.getType());
        assertEquals("99+", b.getText());
        assertEquals(120, b.getCount(), "原始数量保留供埋点与调试");
    }

    @Test
    @DisplayName("无待处理时角标为 NONE")
    void badge_noneWhenZero() {
        assertEquals("NONE",
                svc.bootstrap(USER).getServices().get(0).getBadge().getType());
    }

    @Test
    @DisplayName("未登录不显示任何角标")
    void anonymous_noBadge() {
        enrollments.rows.add(enrollment("e1", USER));

        assertEquals("NONE", svc.bootstrap("anonymous").getTopBadge().getType());
        assertEquals("NONE",
                svc.bootstrap("anonymous").getServices().get(0).getBadge().getType());
    }

    @Test
    @DisplayName("未读优先于待处理，不相加")
    void badge_unreadWinsOverPending() {
        MyPageBootstrap.Badge b = MyPageBootstrap.Badge.of(3, 5);

        assertEquals("NUMBER", b.getType());
        assertEquals("3", b.getText());
        assertEquals(3, b.getCount(), "同一处消息的两种状态，相加会让用户以为有两个提醒");
    }

    @Test
    @DisplayName("仅待处理时显示橙色小点")
    void badge_dotWhenOnlyPending() {
        MyPageBootstrap.Badge b = MyPageBootstrap.Badge.of(0, 2);

        assertEquals("DOT", b.getType());
        assertEquals("", b.getText());
    }

    // ==========================================================
    // 第 1、19.5 节：不复制业务数据 + 单点失败不阻塞
    // ==========================================================

    @Test
    @DisplayName("用户表读取失败不阻塞整页")
    void userFailure_doesNotBlockPage() {
        users.throwOnSelect = true;

        MyPageBootstrap b = svc.bootstrap(USER);

        assertNotNull(b, "用户摘要失败也要返回页面");
        assertEquals(8, b.getServices().size(), "入口结构不应受影响");
        assertEquals(3, b.getCoreEntries().size());
    }

    @Test
    @DisplayName("星盘读取失败不影响身份摘要其他字段")
    void chartFailure_degradesGracefully() {
        users.byId.put(USER, baseUser());
        charts.throwOnSelect = true;

        MyPageBootstrap.Identity id = svc.bootstrap(USER).getIdentity();

        assertNotNull(id);
        assertFalse(id.isHasChart(), "查不到星盘应按无星盘处理，不应整页报错");
    }

    @Test
    @DisplayName("课程角标查询失败降级为无角标，不抛异常")
    void badgeFailure_degrades() {
        enrollments.throwOnFind = true;

        MyPageBootstrap b = svc.bootstrap(USER);

        assertEquals("NONE", b.getServices().get(0).getBadge().getType());
        assertEquals(8, b.getServices().size());
    }

    @Test
    @DisplayName("一级页不携带业务实例内容")
    void noBusinessInstancePayload() {
        // 文档第 1 节：只保存入口、摘要、ID与状态。
        // 断言聚合结果里没有报告正文、订单明细、消息内容等字段。
        MyPageBootstrap b = svc.bootstrap(USER);

        assertNotNull(b.getCoreEntries());
        assertNotNull(b.getServices());
        assertNotNull(b.getMore());
        // 没有出现任何承载业务对象的字段
        for (var f : MyPageBootstrap.class.getDeclaredFields()) {
            String n = f.getName().toLowerCase();
            assertFalse(n.contains("reportbody"), "不得携带报告正文");
            assertFalse(n.contains("orderlist"), "不得携带订单明细");
            assertFalse(n.contains("messagelist"), "不得携带消息内容");
        }
    }

    @Test
    @DisplayName("标注哪些事实源待接，避免把待接误当业务结论")
    void dataSource_marksPending() {
        String ds = svc.bootstrap(USER).getDataSource();

        assertTrue(ds.contains("user:REAL"));
        assertTrue(ds.contains("notification:PENDING_TABLE"),
                "通知表未落地必须标注，否则用户以为没有新消息");
        assertEquals("ANONYMOUS", svc.bootstrap("anonymous").getDataSource());
    }

    // ==========================================================
    // 桩
    // ==========================================================

    private static List<String> namesOf(List<MyPageBootstrap.Entry> list) {
        List<String> out = new ArrayList<>();
        list.forEach(e -> out.add(e.getName()));
        return out;
    }

    private static int indexOf(List<MyPageBootstrap.Entry> list, String id) {
        for (int i = 0; i < list.size(); i++) {
            if (id.equals(list.get(i).getId())) return i;
        }
        return -1;
    }

    private static User baseUser() {
        User u = new User();
        u.setUserId(USER);
        u.setNickname("测试用户");
        u.setStarBalance(0);
        u.setLevel("NORMAL");
        return u;
    }

    private static LearningEnrollment enrollment(String id, String userId) {
        LearningEnrollment e = new LearningEnrollment();
        e.setEnrollmentId(id);
        e.setUserId(userId);
        e.setSessionId("s_" + id);
        e.setTargetType("COURSE");
        e.setEnrollmentStatus("ENROLLED");
        return e;
    }

    /** §12.1：购物车角标与购物车页共用同一个计数口径 */
    @Test
    @DisplayName("购物车角标取有效可结算行数")
    void cartBadge_countsSettleableRows() {
        cart.settleable = 3;

        MyPageBootstrap.Entry e = findById(svc.bootstrap(USER).getMore(), "cart");

        assertNotNull(e);
        assertEquals("NUMBER", e.getBadge().getType());
        assertEquals("3", e.getBadge().getText());
    }

    @Test
    @DisplayName("购物车为空时不显示角标")
    void cartBadge_noneWhenEmpty() {
        cart.settleable = 0;

        assertEquals("NONE", findById(svc.bootstrap(USER).getMore(), "cart").getBadge().getType());
    }

    @Test
    @DisplayName("未登录购物车不显示角标")
    void cartBadge_noneWhenAnonymous() {
        cart.settleable = 5;

        assertEquals("NONE",
                findById(svc.bootstrap("anonymous").getMore(), "cart").getBadge().getType());
    }

    @Test
    @DisplayName("购物车角标失败不阻塞整页（§19.5）")
    void cartBadge_degrades() {
        cart.throwOnCount = true;

        MyPageBootstrap b = svc.bootstrap(USER);

        assertEquals("NONE", findById(b.getMore(), "cart").getBadge().getType());
        assertEquals(8, b.getServices().size());
        assertEquals(3, b.getCoreEntries().size());
    }

    private static MyPageBootstrap.Entry findById(List<MyPageBootstrap.Entry> list, String id) {
        for (MyPageBootstrap.Entry e : list) {
            if (id.equals(e.getId())) return e;
        }
        throw new AssertionError("未找到入口: " + id);
    }

    /** 只覆写被聚合服务用到的那一个方法 */
    static final class MallCartServiceStub extends MallCartService {
        int settleable;
        boolean throwOnCount;

        MallCartServiceStub() {
            super(null, null, null);
        }

        @Override
        public int countSettleableRows(String userId) {
            if (throwOnCount) throw new RuntimeException("模拟购物车表不可用");
            return userId == null ? 0 : settleable;
        }
    }

    static final class UserMapperStub implements UserMapper {
        final java.util.Map<String, User> byId = new java.util.HashMap<>();
        boolean throwOnSelect;

        @Override
        public User selectById(String userId) {
            if (throwOnSelect) throw new RuntimeException("模拟用户表不可用");
            return byId.get(userId);
        }

        @Override public User selectByPhone(String phone) { return null; }
        @Override public int insert(User user) { return 0; }
        @Override public int update(User user) { return 0; }
        @Override public int updateStarBalance(String userId, int delta) { return 0; }
        @Override public int updateDailyFree(String userId, int remaining) { return 0; }
    }

    static final class ChartMapperStub implements ChartMapper {
        final java.util.Map<String, Chart> defaultByUser = new java.util.HashMap<>();
        boolean throwOnSelect;

        @Override
        public Chart selectDefaultByUserId(String userId) {
            if (throwOnSelect) throw new RuntimeException("模拟星盘表不可用");
            return defaultByUser.get(userId);
        }

        @Override public Chart selectById(String chartId) { return null; }
        @Override public List<Chart> selectByUserId(String userId) { return List.of(); }
        @Override public int insert(Chart chart) { return 0; }
        @Override public int update(Chart chart) { return 0; }
        @Override public int deleteById(String chartId) { return 0; }
        @Override public int clearDefaultByUserId(String userId) { return 0; }
        @Override public int setDefault(String chartId, String userId) { return 0; }
    }

    static final class EnrollmentMapperStub implements LearningEnrollmentMapper {
        final List<LearningEnrollment> rows = new ArrayList<>();
        boolean throwOnFind;

        @Override
        public List<LearningEnrollment> findActiveByUser(String userId) {
            if (throwOnFind) throw new RuntimeException("模拟报名表不可用");
            List<LearningEnrollment> out = new ArrayList<>();
            for (LearningEnrollment e : rows) {
                if (userId.equals(e.getUserId())
                        && ("ENROLLED".equals(e.getEnrollmentStatus())
                        || "ATTENDED".equals(e.getEnrollmentStatus()))) {
                    out.add(e);
                }
            }
            return out;
        }

        @Override public LearningEnrollment findExisting(String userId, String sessionId) { return null; }
        @Override public List<LearningEnrollment> findBySession(String sessionId) { return List.of(); }
        @Override public int insert(LearningEnrollment e) { return 0; }
        @Override public int revive(String id, String t) { return 0; }
        @Override public int cancel(String userId, String sessionId) { return 0; }
        @Override public int checkAttendance(String u, String s, String st) { return 0; }
    }
}
