package cn.dct.zpback.security;

import cn.dct.zpback.mapper.UserRoleMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.util.List;

/**
 * 问星渡｜课程与活动 权限判定
 *
 * <b>为什么单独抽一层</b>：资金类操作（退款、支付回调、交付）
 * 一旦鉴权写错就是真实资损。这些判定必须集中在一处，
 * 不能散落在各 Controller 里各写一遍 if。
 *
 * <b>角色语义</b>（原始 skill 07_RBAC 提纲）：
 *   USER              浏览、报名、看自己的订单
 *   TEACHER           管理自己名下的课程与班级、出勤核销
 *   ASSISTANT_TEACHER 同讲师，不含课程编辑
 *   CUSTOMER_SERVICE  可查订单，不可退款
 *   ADMIN             全部权限
 *
 * <b>关键约定：退款仅 ADMIN。</b>
 * 客服能查不能退——退款会真实改变营收，口子必须最窄。
 *
 * <b>默认口径</b>：无角色记录 = USER。
 * 存量用户没有 role 行，不能因此被当成运营。
 */
@Component
@RequiredArgsConstructor
public class LearningAccessGuard {

    public static final String ROLE_USER = "USER";
    public static final String ROLE_TEACHER = "TEACHER";
    public static final String ROLE_ASSISTANT_TEACHER = "ASSISTANT_TEACHER";
    public static final String ROLE_CUSTOMER_SERVICE = "CUSTOMER_SERVICE";
    public static final String ROLE_ADMIN = "ADMIN";

    private final UserRoleMapper roleMapper;

    /** 是否登录（排除 anonymous 兜底身份） */
    public boolean isLoggedIn(String userId) {
        return userId != null && !userId.isBlank()
                && !BearerUserIdResolver.ANONYMOUS.equals(userId);
    }

    public List<String> rolesOf(String userId) {
        if (!isLoggedIn(userId)) return List.of();
        List<String> roles = roleMapper.findRoles(userId);
        // 无角色记录视为普通用户：存量数据零迁移，也不会被误判成运营
        return (roles == null || roles.isEmpty()) ? List.of(ROLE_USER) : roles;
    }

    public boolean hasRole(String userId, String roleCode) {
        return rolesOf(userId).contains(roleCode);
    }

    public boolean isAdmin(String userId) {
        return hasRole(userId, ROLE_ADMIN);
    }

    /** 客服或管理员：可查看运营侧数据 */
    public boolean isStaff(String userId) {
        return isAdmin(userId) || hasRole(userId, ROLE_CUSTOMER_SERVICE);
    }

    /** 讲师或助教：可管理班级、出勤核销 */
    public boolean isTeacher(String userId) {
        return isAdmin(userId)
                || hasRole(userId, ROLE_TEACHER)
                || hasRole(userId, ROLE_ASSISTANT_TEACHER);
    }

    /**
     * 是否可对该课程/活动执行运营操作（点名、看名单）。
     *
     * 讲师只能动自己名下的课；管理员不受归属限制。
     * 归属来自 learning_assignment，与 user_role 分开——
     * 「是什么角色」和「负责哪门课」是两个独立维度。
     */
    public boolean canManage(String userId, String targetType, String targetId) {
        if (isAdmin(userId)) return true;
        if (!isTeacher(userId)) return false;
        return roleMapper.countAssignment(targetType, targetId, userId) > 0;
    }

    /** 是否可执行资金类操作（支付回调 / 交付 / 补发） */
    public boolean canOperateMoney(String userId) {
        return isStaff(userId);
    }

    /**
     * 是否可退款。
     *
     * <b>仅 ADMIN</b>——客服可查不可退。
     */
    public boolean canRefund(String userId) {
        return isAdmin(userId);
    }
}