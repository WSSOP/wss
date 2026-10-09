package cn.dct.zpback.security;

import org.springframework.stereotype.Component;

import java.util.EnumSet;
import java.util.Locale;
import java.util.Set;

/**
 * 问星渡｜课程与活动 角色解析
 *
 * 决定一个 userId 拥有哪些 {@link LearningRole}，以及是否具备某个权限点。
 *
 * <b>当前实现是过渡方案</b>：user 表还没有 role 列落地，
 * 因此按「约定前缀」推断角色——这是为了让权限逻辑先可测、可跑通，
 * 而不是长期依赖字符串约定。
 *
 * 【过渡期约定】userId 形如 user_xxx 时：
 *   <ul>
 *     <li>{@code user_admin_*}      → ADMIN</li>
 *     <li>{@code user_cs_*} / user_kefu_*} → CUSTOMER_SERVICE</li>
 *     <li>{@code user_teacher_*}    → TEACHER</li>
 *     <li>{@code user_assist_*}     → ASSISTANT_TEACHER</li>
 *   </ul>
 *
 * <b>上线前必须替换</b>：user.role 落库后改为读库 + 缓存。
 * 保留这套前缀推断只会让「谁能退款」变成猜的——资金权限不能靠命名约定。
 */
@Component
public class LearningRoleResolver {

    private static final String ANONYMOUS = "anonymous";

    /** user.role 上线前，用前缀做角色推断 */
    public Set<LearningRole> rolesOf(String userId) {
        if (userId == null || userId.isBlank() || ANONYMOUS.equals(userId)) {
            return EnumSet.noneOf(LearningRole.class);
        }
        String u = userId.toLowerCase(Locale.ROOT);
        if (u.contains("admin")) return EnumSet.of(LearningRole.ADMIN);
        if (u.contains("_cs_") || u.contains("kefu") || u.contains("service")) {
            return EnumSet.of(LearningRole.CUSTOMER_SERVICE);
        }
        if (u.contains("assist")) return EnumSet.of(LearningRole.ASSISTANT_TEACHER);
        if (u.contains("teacher")) return EnumSet.of(LearningRole.TEACHER);
        // 默认无后台角色：普通用户只走 token 认证，不该碰后台接口
        return EnumSet.noneOf(LearningRole.class);
    }

    /** 是否具备指定权限（Admin 恒为 true） */
    public boolean hasPermission(String userId, String permission) {
        for (LearningRole r : rolesOf(userId)) {
            if (r.isSuperUser()) return true;
            if (r.permissions().contains(permission)) return true;
        }
        return false;
    }

    public boolean isAdmin(String userId) {
        return rolesOf(userId).contains(LearningRole.ADMIN);
    }

    /** 讲师或助教：可以查看名单、核验出勤，但受归属限制 */
    public boolean isTeachingStaff(String userId) {
        Set<LearningRole> rs = rolesOf(userId);
        return rs.contains(LearningRole.TEACHER)
                || rs.contains(LearningRole.ASSISTANT_TEACHER)
                || rs.contains(LearningRole.ADMIN);
    }

    /** 已登录的真实用户（排除 anonymous 兜底） */
    public boolean isLoggedIn(String userId) {
        return userId != null && !userId.isBlank() && !ANONYMOUS.equals(userId);
    }
}