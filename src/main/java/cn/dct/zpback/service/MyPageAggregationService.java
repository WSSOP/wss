package cn.dct.zpback.service;

import cn.dct.zpback.dto.mypage.MyPageBootstrap;
import cn.dct.zpback.entity.Chart;
import cn.dct.zpback.entity.User;
import cn.dct.zpback.mapper.ChartMapper;
import cn.dct.zpback.mapper.LearningEnrollmentMapper;
import cn.dct.zpback.mapper.UserMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

/**
 * 问星渡｜【我的】一级页聚合服务
 *
 * 对应文档 STEP 7 第 18 节后端拆分建议 MyPageAggregationService。
 *
 * <b>核心约束（文档第 1、18 节）</b>：
 * 聚合服务<b>只编排读取</b>，业务事实仍归原领域对象。
 * <b>禁止建立「我的页业务总表」复制所有领域状态</b>——
 * 那样一份数据要两处维护，字段一改就漏同步。
 *
 * <b>未登录策略（文档第 8、15 节）</b>：
 * 入口<b>照常返回</b>，只把 identity/wallet 置空。
 * 前端靠 loggedIn 决定显示「点击登录」还是真实昵称。
 * 若登录前隐去入口，用户登录后布局会突然多出一堆东西。
 *
 * <b>数据缺失策略</b>：单个摘要读取失败<b>不阻塞整页</b>
 * （文档 19.5「单个摘要失败不阻塞整个页面」）。
 * 每个子查询独立 try，失败降级为 null／0 并记日志。
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class MyPageAggregationService {

    private final UserMapper userMapper;
    private final ChartMapper chartMapper;
    private final LearningEnrollmentMapper enrollmentMapper;
    private final MallCartService cartService;

    /** 未登录占位 userId，与 BearerUserIdResolver 的 ANONYMOUS 一致 */
    private static final String ANONYMOUS = "anonymous";

    public MyPageBootstrap bootstrap(String userId) {
        boolean logged = userId != null && !userId.isBlank() && !ANONYMOUS.equals(userId);

        // 咨询师入口要先算出来：它要注入【更多】列表，
        // 顺序落在【给个好评】之前（文档第 7 节）。
        MyPageBootstrap.Consultant consultant = consultantEntry(logged);

        MyPageBootstrap out = new MyPageBootstrap();
        out.setLoggedIn(logged);
        out.setConsultant(consultant);
        out.setCoreEntries(coreEntries());
        out.setServices(services(logged ? userId : null));
        out.setMore(moreEntries(consultant, logged ? userId : null));
        out.setTopBadge(topBadge(logged ? userId : null));

        if (!logged) {
            out.setIdentity(null);
            out.setWallet(null);
            out.setMembership(membershipOf(null));
            out.setDataSource("ANONYMOUS");
            return out;
        }

        out.setIdentity(identityOf(userId));
        out.setWallet(walletOf(userId));
        out.setMembership(membershipOf(userId));
        out.setDataSource(dataSource());
        return out;
    }

    // ==============================================================
    // 身份与资产
    // ==============================================================

    /**
     * 身份摘要。
     *
     * 星座／生日摘要从<b>已存的星盘</b>读，不在这里排盘：
     * 文档第 1 节禁止一级页重新计算星盘。
     *
     * userMapper 只查一次：identity 和 membership 都要读 user，
     * 各查一次等于同一行读两遍，字段改动时也容易只改一处。
     */
    private MyPageBootstrap.Identity identityOf(String userId) {
        MyPageBootstrap.Identity id = new MyPageBootstrap.Identity();
        id.setUserId(userId);

        try {
            User u = userMapper.selectById(userId);
            if (u != null) {
                id.setNickname(u.getNickname());
                id.setAvatar(u.getAvatar());
            }
        } catch (RuntimeException ex) {
            log.warn("[我的] 用户摘要读取失败：{}", ex.getMessage());
        }

        try {
            Chart chart = chartMapper.selectDefaultByUserId(userId);
            if (chart != null) {
                id.setHasChart(true);
                id.setPrimaryChartId(chart.getChartId());
                id.setPrimaryChartName(chart.getName());
                String date = chart.getBirthDate() == null ? "" : chart.getBirthDate();
                String time = chart.getBirthTime() == null ? "" : " " + chart.getBirthTime();
                id.setBirthSummary((date + time).trim());
                // 星座由生日推算属展示层职责，后端只给生日摘要；
                // 此处填假星座会与星盘页不一致，比留空更糟。
                id.setZodiacSummary(date);
            }
        } catch (RuntimeException ex) {
            log.warn("[我的] 星盘摘要读取失败：{}", ex.getMessage());
        }
        return id;
    }

    /** 星印余额。文档第 4 节：点击整块直达充值页，不先经钱包首页 */
    private MyPageBootstrap.Wallet walletOf(String userId) {
        MyPageBootstrap.Wallet w = new MyPageBootstrap.Wallet();
        w.setRechargeRoute("/pages/me/recharge");
        try {
            User u = userMapper.selectById(userId);
            if (u != null && u.getStarBalance() != null) {
                w.setStarBalance(u.getStarBalance());
            }
        } catch (RuntimeException ex) {
            log.warn("[我的] 星印余额读取失败：{}", ex.getMessage());
        }
        return w;
    }

    /**
     * 会员权益。
     *
     * 文档第 8 条：普通／VIP／SVIP <b>不隐藏功能入口</b>，
     * 只改标识与权益卡内容。所以这里只填卡片文案，绝不返回 disabled。
     *
     * level 为空时按 NORMAL 处理，
     * 避免用户表 level 没落值就整页拿不到会员信息。
     */
    private MyPageBootstrap.Membership membershipOf(String userId) {
        MyPageBootstrap.Membership m = new MyPageBootstrap.Membership();
        m.setLevel("NORMAL");
        m.setBenefitTitle("开通会员，解锁完整解读");
        m.setBenefitSubtitle("会员可享专属额度与优惠");
        m.setBenefitCta("了解会员");
        m.setBenefitRoute("/pages/me/membership");
        if (userId == null) return m;

        try {
            User u = userMapper.selectById(userId);
            if (u == null || u.getLevel() == null) return m;
            String lv = u.getLevel().toUpperCase();
            if (!"VIP".equals(lv) && !"SVIP".equals(lv)) return m;

            m.setLevel(lv);
            boolean vip = "VIP".equals(lv);
            m.setBenefitTitle(vip ? "VIP 会员权益" : "SVIP 会员权益");
            m.setBenefitSubtitle(vip
                    ? "专属解读额度 · 咨询优惠 · 内容优先"
                    : "全部 VIP 权益 · 真人咨询特权");
            m.setBenefitCta("查看我的权益");
            m.setBenefitRoute("/pages/me/membership");

            if (u.getVipExpireAt() != null) {
                m.setExpireAt(u.getVipExpireAt().toLocalDate().toString());
                // 已过期仍显示入口，但权益判定失效——
                // 直接降级为 NORMAL 会让用户以为会员被回收了
                m.setExpired(u.getVipExpireAt().isBefore(LocalDateTime.now()));
                if (m.isExpired()) {
                    m.setBenefitSubtitle("会员已到期，续费后可继续享受权益");
                    m.setBenefitCta("续费");
                }
            }
        } catch (RuntimeException ex) {
            log.warn("[我的] 会员权益读取失败：{}", ex.getMessage());
        }
        return m;
    }

    // ==============================================================
    // 角标
    // ==============================================================

    /**
     * 顶部消息角标（文档第 3.2 节）。
     *
     * <b>未读／待处理</b>两路计数后端都还没有表，
     * 这里返回 NONE 并在 dataSource 标注，
     * 不填假数字——假数字会让前端角标逻辑通过验收却对不上真实数据。
     */
    private MyPageBootstrap.Badge topBadge(String userId) {
        if (userId == null) return MyPageBootstrap.Badge.none();
        try {
            // TODO(接通知表后启用)：unread / pending 各走一次 count 查询
            return MyPageBootstrap.Badge.none();
        } catch (RuntimeException ex) {
            log.warn("[我的] 消息角标读取失败：{}", ex.getMessage());
            return MyPageBootstrap.Badge.none();
        }
    }

    /** 按入口范围计数（文档第 9 节：各入口只显示自身范围角标） */
    private MyPageBootstrap.Badge courseBadge(String userId) {
        if (userId == null) return MyPageBootstrap.Badge.none();
        try {
            List<?> rows = enrollmentMapper.findActiveByUser(userId);
            return MyPageBootstrap.Badge.of(rows.size(), 0);
        } catch (RuntimeException ex) {
            log.warn("[我的] 课程角标读取失败：{}", ex.getMessage());
            return MyPageBootstrap.Badge.none();
        }
    }

    // ==============================================================
    // 入口结构（文档第 2、6、7 节）
    // ==============================================================

    /**
     * 三个核心资产入口。
     *
     * 顺序固定：星盘档案 → 我的AI解读 → 我的真人咨询。
     * 文档第 5.1 节要求 VIP 卡面积明显大于这三张小卡，
     * 那是样式职责，不靠这里的数据。
     */
    private List<MyPageBootstrap.Entry> coreEntries() {
        List<MyPageBootstrap.Entry> l = new ArrayList<>();
        l.add(entry("star_charts", "星盘档案", "📄", "/pages/me/charts", null));
        l.add(entry("ai_insights", "我的AI解读", "🔮", "/pages/me/ai-insights", null));
        l.add(entry("consultations", "我的真人咨询", "💬", "/pages/me/consultations", null));
        return l;
    }

    /**
     * 八个个人服务入口，顺序固定（文档第 6 节）。
     *
     * 【平台客服】只在这里出现，
     * 【更多】里不得重复（文档 19.1 验收项）。
     */
    private List<MyPageBootstrap.Entry> services(String userId) {
        List<MyPageBootstrap.Entry> l = new ArrayList<>();
        l.add(entry("my_courses", "我的课程", "📘", "/pages/me/courses", courseBadge(userId)));
        l.add(entry("my_events", "我的活动", "🌙", "/pages/me/events", null));
        l.add(entry("my_assessments", "我的测评", "🧪", "/pages/me/assessments", null));
        l.add(entry("my_practices", "我的练习", "🧘", "/pages/me/practices", null));
        l.add(entry("journal", "觉察日记", "📔", "/pages/me/journal", null));
        l.add(entry("mall", "商城", "🛍️", "/pages/mall/index", null));
        l.add(entry("coupons", "卡券", "🎫", "/pages/me/coupons", null));
        l.add(entry("support", "平台客服", "🛡️", "/pages/support/index", null));
        return l;
    }

    /**
     * 【更多】固定列表，【设置】永远最后（文档第 7 节）。
     *
     * 咨询师入口在此处<b>动态注入</b>，
     * 顺序落在【给个好评】之前，与文档第 7 节一致。
     */
    private List<MyPageBootstrap.Entry> moreEntries(MyPageBootstrap.Consultant consultant,
                                                  String userIdForBadge) {
        List<MyPageBootstrap.Entry> l = new ArrayList<>();
        l.add(entry("orders", "我的订单", "🧾", "/pages/me/orders", null));
        // §12.1：购物车角标只取「有效可结算 SKU 行」，
        // 且必须与购物车页用同一个 service 算，否则两处口径会漂移。
        l.add(entry("cart", "购物车", "🛒", "/pages/mall/cart", cartBadge(userIdForBadge)));
        l.add(entry("notifications", "消息通知", "🔔", "/pages/notifications/index", null));
        // 外观模式与清理缓存是本页内动作，不跳二级页，route 置 null 由前端当页弹层
        l.add(entry("appearance", "外观模式", "🌓", null, null));
        l.add(entry("clear_cache", "清理缓存", "🧹", null, null));
        if (consultant != null) {
            MyPageBootstrap.Entry c = new MyPageBootstrap.Entry();
            c.setId("consultant_entry");
            c.setName(consultant.getEntryName());
            c.setIcon("👨‍🏫");
            c.setRoute(consultant.getEntryRoute());
            c.setBadge(MyPageBootstrap.Badge.none());
            c.setDisabled(consultant.isDisabled());
            c.setHint(consultant.getDisabledReason());
            l.add(c);
        }
        l.add(entry("rate_us", "给个好评", "⭐", null, null));
        l.add(entry("about", "关于问星渡", "ℹ️", "/pages/about/index", null));
        l.add(entry("settings", "设置", "⚙️", "/pages/me/settings", null));
        return l;
    }

    /**
     * 购物车角标（文档 §12.1：有效可结算 SKU 行）。
     *
     * <p>口径<b>必须</b>与购物车页一致，所以复用
     * {@link MallCartService#countSettleableRows} 而不是另写一遍查询。
     * 两处各写一份，早晚会在「要不要剔掉缺货行」上分叉。
     *
     * <p>未登录返回 NONE：§8.1 要求一级页入口保持可见，
     * 没登录本来就没有购物车，不该显示角标。
     */
    private MyPageBootstrap.Badge cartBadge(String userId) {
        if (userId == null) return MyPageBootstrap.Badge.none();
        try {
            int n = cartService.countSettleableRows(userId);
            return n <= 0 ? MyPageBootstrap.Badge.none() : MyPageBootstrap.Badge.of(n, 0);
        } catch (RuntimeException ex) {
            // §19.5：单个摘要失败不阻塞整页
            log.warn("[我的] 购物车角标读取失败：{}", ex.getMessage());
            return MyPageBootstrap.Badge.none();
        }
    }

    /**
     * 咨询师九态 → 入口名称与跳转（文档第 10 节）。
     *
     * <b>后端尚无 consultant 表</b>，
     * 这里统一返回 NOT_APPLIED（普通用户 → 咨询师入驻）。
     * 待 consultant 表落地后，把 state 改为读取真实状态即可，
     * 前端只渲染 entryName/entryRoute，不需要再改。
     */
    private MyPageBootstrap.Consultant consultantEntry(boolean loggedIn) {
        MyPageBootstrap.Consultant c = new MyPageBootstrap.Consultant();
        c.setState("NOT_APPLIED");
        c.setProgressPercent(0);
        c.setEntryName("咨询师入驻");
        c.setEntryRoute("/pages/consultant/apply");
        c.setDisabled(false);
        return c;
    }

    // ==============================================================
    // 工具
    // ==============================================================

    private MyPageBootstrap.Entry entry(String id, String name, String icon,
                                       String route, MyPageBootstrap.Badge badge) {
        MyPageBootstrap.Entry e = new MyPageBootstrap.Entry();
        e.setId(id);
        e.setName(name);
        e.setIcon(icon);
        e.setRoute(route);
        e.setBadge(badge == null ? MyPageBootstrap.Badge.none() : badge);
        e.setDisabled(route == null);
        if (route == null) e.setHint("即将上线");
        return e;
    }

    /**
     * 标注哪些事实源已接真实表、哪些待接。
     *
     * 前端可用它区分「真的是 0」与「还没接」，
     * 避免把待接误当业务结论（比如以为用户真的没课程）。
     */
    private String dataSource() {
        return "user:REAL;chart:REAL;course_badge:REAL;membership:REAL;"
                + "notification:PENDING_TABLE;mall:PENDING_TABLE;coupon:PENDING_TABLE;"
                + "assessment:REAL;practice:PENDING_TABLE;journal:PENDING_TABLE;"
                + "consultant_state:PENDING_TABLE;cart:REAL;order_count:PENDING_TABLE";
    }
}