package cn.dct.zpback.security;

import java.util.Arrays;
import java.util.EnumSet;
import java.util.HashSet;
import java.util.Set;

/**
 * 问星渡｜课程与活动 角色与权限
 *
 * 对应文档 16_Backend_Menu_Mapping 的角色定义：
 * Admin             全部管理权限
 * Customer Service  订单、退款、用户支持
 * Teacher           被分配课程和班期
 * Assistant Teacher 辅助学习运营
 *
 * <b>实现说明</b>：当前 user 表还没有 role 列落地，本枚举先固定权限矩阵语义，
 * 由 {@link LearningRoleResolver} 决定一个 userId 拥有哪些角色。
 * 待 user.role 上线后把 resolver 换成读库即可，权限判断代码不用改。
 */
public enum LearningRole {

    ADMIN,
    CUSTOMER_SERVICE,
    TEACHER,
    ASSISTANT_TEACHER;

    // ----------------------------------------------------------
    // 权限点
    // ----------------------------------------------------------

    public static final String P_VIEW_ALL_CONTENT = "content:view:all";
    public static final String P_CONTENT_EDIT = "content:edit";
    public static final String P_SESSION_MANAGE = "session:manage";
    public static final String P_ORDER_VIEW = "order:view";
    public static final String P_REFUND = "order:refund";
    public static final String P_PAYMENT_CONFIRM = "payment:confirm";
    public static final String P_FULFILL = "fulfill:execute";
    public static final String P_ROSTER_VIEW = "roster:view";
    public static final String P_ATTENDANCE = "attendance:check";
    public static final String P_USER_SUPPORT = "user:support";

    // ----------------------------------------------------------
    // 角色 -> 权限
    // ----------------------------------------------------------

    /**
     * 权限集合用 EnumSet&lt;String&gt; 而非 Set.of。
     *
     * EnumSet 要求元素是枚举类型，这里元素是权限点字符串，
     * 所以只能用 HashSet；用 {@code Set<String>} 声明类型保持可读性。
     */
    private static final Set<String> ADMIN_PERMS = new HashSet<>(Arrays.asList(
            P_VIEW_ALL_CONTENT, P_CONTENT_EDIT, P_SESSION_MANAGE, P_ORDER_VIEW,
            P_REFUND, P_PAYMENT_CONFIRM, P_FULFILL, P_ROSTER_VIEW, P_ATTENDANCE, P_USER_SUPPORT));

    /** 客服：订单、退款、用户支持。不含内容编辑与内容总览 */
    private static final Set<String> CS_PERMS = new HashSet<>(Arrays.asList(
            P_ORDER_VIEW, P_REFUND, P_PAYMENT_CONFIRM,
            P_FULFILL, P_ROSTER_VIEW, P_USER_SUPPORT));

    /** 讲师：只管自己被分配的课程和班期 */
    private static final Set<String> TEACHER_PERMS = new HashSet<>(Arrays.asList(
            P_ROSTER_VIEW, P_ATTENDANCE));

    /** 助教：辅助学习运营，权限与讲师同级 */
    private static final Set<String> ASSISTANT_PERMS = new HashSet<>(Arrays.asList(
            P_ROSTER_VIEW, P_ATTENDANCE));

    public Set<String> permissions() {
        switch (this) {
            case ADMIN:
                return ADMIN_PERMS;
            case CUSTOMER_SERVICE:
                return CS_PERMS;
            case TEACHER:
                return TEACHER_PERMS;
            case ASSISTANT_TEACHER:
            default:
                return ASSISTANT_PERMS;
        }
    }

    /**
     * Admin 拥有全部权限，包括将来新增的权限点。
     *
     * 用 ADMIN_PERMS 全量列举有个隐患：将来加了新权限而忘了补进来，
     * Admin 就会莫名其妙地缺权限。所以判断时单独短路一次。
     */
    public boolean isSuperUser() {
        return this == ADMIN;
    }
}