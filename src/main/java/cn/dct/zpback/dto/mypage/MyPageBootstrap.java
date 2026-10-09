package cn.dct.zpback.dto.mypage;

import lombok.Data;

import java.math.BigDecimal;
import java.util.List;

/**
 * 问星渡｜【我的】一级页聚合响应
 *
 * 对应文档《【我的】页面开发文档》STEP 7 第 5 节，
 * 路由 GET /api/v1/my-page/bootstrap。
 *
 * <b>关键约束（文档第 1 节）</b>：
 * 一级页「只保存入口、摘要、ID与状态」，
 * <b>不复制</b>报告正文、咨询聊天、课程视频、测评结果、订单或消息业务数据。
 * 因此本类每个字段都是摘要或计数，没有一个字段承载业务实例内容。
 *
 * <b>字段真实度标注</b>：{@link #dataSource} 标明哪些来自真实表、
 * 哪些因后端缺表暂时为 0。上线前必须全部为 REAL，
 * 不能让页面对着假数据验收通过。
 */
@Data
public class MyPageBootstrap {

    /** 是否已登录。未登录时 identity/wallet 为空，入口仍全部显示（文档第 8 条） */
    private boolean loggedIn;

    private Identity identity;
    private Wallet wallet;
    private Membership membership;

    /** 顶部消息图标状态：数字角标 / 橙色小点 / 无（文档第 3.2 节） */
    private Badge topBadge;

    /** 三个核心资产入口（文档第 2 节，固定顺序） */
    private List<Entry> coreEntries;

    /** 八个个人服务入口（文档第 6 节，固定顺序） */
    private List<Entry> services;

    /** 【更多】固定列表，settings 永远最后（文档第 7 节） */
    private List<Entry> more;

    /**
     * 咨询师九态（文档第 10 节）。
     * 同时给出 name/route/badge，前端不做状态判断，只渲染。
     */
    private Consultant consultant;

    /**
     * 事实源标注：REAL=读真实表；STUB=后端尚无该表，返回 0 或空。
     * 前端可用它区分「真的是 0」与「还没接」，避免把待接误当业务结论。
     */
    private String dataSource;

    // ------------------------------------------------------------
    // 身份与资产（文档第 4 节）
    // ------------------------------------------------------------

    @Data
    public static class Identity {
        private String userId;
        private String nickname;
        private String avatar;
        /** 星座／生日摘要，后端算好后传，前端不排盘 */
        private String zodiacSummary;
        private String birthSummary;
        /** NORMAL / VIP / SVIP（文档第 8 节：只改标识不隐藏入口） */
        private String levelLabel;
        /** 主星盘 ID 与名称，无星盘时为 null，前端显示创建提示 */
        private String primaryChartId;
        private String primaryChartName;
        private boolean hasChart;
    }

    @Data
    public static class Wallet {
        /** 当前星印余额（文档第 4 节） */
        private int starBalance;
        /** 点击整块直达充值页，不先经过钱包首页（文档第 4 节） */
        private String rechargeRoute;
        /** 首充是否已发放，用于前端提示，避免重复发放文案 */
        private boolean firstRechargeUsed;
    }

    @Data
    public static class Membership {
        /** NORMAL / VIP / SVIP */
        private String level;
        /** 权益大卡文案与 CTA，运营可配（文档第 2 节） */
        private String benefitTitle;
        private String benefitSubtitle;
        private String benefitCta;
        private String benefitRoute;
        /** 会员到期时间，NORMAL 为 null */
        private String expireAt;
        /** 会员是否过期：已过期仍显示入口，但权益判定为失效（文档第 8 节） */
        private boolean expired;
    }

    // ------------------------------------------------------------
    // 入口（文档第 2、6、7 节）
    // ------------------------------------------------------------

    @Data
    public static class Entry {
        /** 固定 ID，运营不可改（文档第 2 节） */
        private String id;
        private String name;
        /** emoji 或图标名 */
        private String icon;
        /** 点击目标，null 表示该功能暂未上线，前端按 disabled 渲染 */
        private String route;
        /** 仅统计本入口范围内数量，不使用全站总数（文档第 9 节） */
        private Badge badge;
        /** 弱提示文案，如「即将上线」 */
        private String hint;
        private boolean disabled;
    }

    // ------------------------------------------------------------
    // 角标（文档第 3.2、第 9 节）
    // ------------------------------------------------------------

    @Data
    public static class Badge {
        /** NONE / DOT / NUMBER */
        private String type;
        /** 已封顶：>=100 时为 "99+" */
        private String text;
        /** 未封顶原始数量，供埋点与调试 */
        private int count;

        public static Badge none() {
            Badge b = new Badge();
            b.type = "NONE";
            b.text = "";
            b.count = 0;
            return b;
        }

        /**
         * 未读 → 红色数字，封顶 99+；待处理 → 橙色小点。
         *
         * 两者同时存在时按未读优先（文档第 3.2 节给出了互斥判定，
         * 此处显式实现为「未读优先」而不是相加——
         * 相加会让用户以为有两个独立提醒，实际是同一处消息）。
         */
        public static Badge of(int unread, int pending) {
            Badge b = new Badge();
            b.count = unread > 0 ? unread : pending;
            if (unread > 0) {
                b.type = "NUMBER";
                b.text = unread > 99 ? "99+" : String.valueOf(unread);
            } else if (pending > 0) {
                b.type = "DOT";
                b.text = "";
            } else {
                b.type = "NONE";
                b.text = "";
            }
            return b;
        }
    }

    // ------------------------------------------------------------
    // 咨询师九态（文档第 10 节）
    // ------------------------------------------------------------

    /**
     * 文档第 10 节九种状态 → 入口名称与跳转：
     * <pre>
     * NOT_APPLIED       普通用户 → 咨询师入驻   /pages/consultant/apply
     * PENDING          普通用户 → 入驻审核中   /pages/consultant/apply
     * APPROVED_JOINED  已入驻   → 咨询师工作台 /pages/consultant-console
     * APPROVED_STOPPED 已入驻   → 咨询师工作台 /pages/consultant-console
     * SUSPENDED        已入驻   → 咨询师工作台 /pages/consultant-console
     * FROZEN           已入驻   → 咨询师工作台 /pages/consultant-console
     * DRAFT            普通用户 → 咨询师入驻   /pages/consultant/apply
     * EXPIRED          普通用户 → 咨询师入驻   /pages/consultant/apply
     * REJECTED         普通用户 → 咨询师入驻   /pages/consultant/apply
     * </pre>
     * 名额已满与关闭注册期间，也一律显示「咨询师入驻」，
     * 但 disabled=true，提示「暂未开放」。
     */
    @Data
    public static class Consultant {
        /** 九态之一 */
        private String state;
        /** 入驻进度百分比，NOT_APPLIED 为 0 */
        private int progressPercent;
        private String entryName;
        private String entryRoute;
        private boolean disabled;
        private String disabledReason;
        /** 当前咨询师 ID，未入驻为 null */
        private String consultantId;
    }
}