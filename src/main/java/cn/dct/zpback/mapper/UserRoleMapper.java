package cn.dct.zpback.mapper;

import org.apache.ibatis.annotations.*;

import java.util.List;

/**
 * 问星渡｜角色与讲师归属 Mapper
 *
 * <b>为什么需要</b>：原始 skill 目录要求 Admin / Customer Service /
 * Teacher / Assistant Teacher 四种角色，但既有的 user 表没有 role 列。
 * 本 Mapper 补齐角色查询，使资金类接口能做真实鉴权，而不是
 * 只判断「是否登录」。
 *
 * <b>默认口径</b>：查不到任何角色记录的用户，一律视为 USER。
 * 这样存量用户（没有 role 行）不会因为没配置而被误判成运营。
 */
@Mapper
public interface UserRoleMapper {

    /** 取某人的全部角色；无记录时返回空列表，由调用方按 USER 处理 */
    @Select("SELECT role_code FROM user_role WHERE user_id = #{userId}")
    List<String> findRoles(@Param("userId") String userId);

    /** 批量授予角色（幂等：IGNORE 掉重复） */
    @Insert("INSERT IGNORE INTO user_role (user_id, role_code, granted_by) "
            + "VALUES (#{userId}, #{roleCode}, #{grantedBy})")
    int grant(@Param("userId") String userId,
              @Param("roleCode") String roleCode,
              @Param("grantedBy") String grantedBy);

    @Delete("DELETE FROM user_role WHERE user_id = #{userId} AND role_code = #{roleCode}")
    int revoke(@Param("userId") String userId, @Param("roleCode") String roleCode);

    // ============================================================
    // 讲师归属
    // ============================================================

    /**
     * 某人是否为某课程/活动的负责人或助教。
     *
     * 出勤核销、班级名单都依赖它——讲师只能动自己名下的课。
     */
    @Select("SELECT COUNT(*) FROM learning_assignment "
            + "WHERE target_type = #{targetType} AND target_id = #{targetId} "
            + "  AND user_id = #{userId} AND assignment_role IN ('OWNER', 'ASSISTANT')")
    int countAssignment(@Param("targetType") String targetType,
                        @Param("targetId") String targetId,
                        @Param("userId") String userId);

    /** 某课程/活动的全部归属人（讲师+助教） */
    @Select("SELECT user_id FROM learning_assignment "
            + "WHERE target_type = #{targetType} AND target_id = #{targetId}")
    List<String> findAssignees(@Param("targetType") String targetType,
                               @Param("targetId") String targetId);

    /** 该讲师名下的全部课程 ID */
    @Select("SELECT target_id FROM learning_assignment "
            + "WHERE user_id = #{userId} AND target_type = 'COURSE' "
            + "ORDER BY target_id")
    List<String> findMyCourseIds(@Param("userId") String userId);

    /** 该讲师名下的全部活动 ID */
    @Select("SELECT target_id FROM learning_assignment "
            + "WHERE user_id = #{userId} AND target_type = 'EVENT' "
            + "ORDER BY target_id")
    List<String> findMyEventIds(@Param("userId") String userId);

    @Insert("INSERT INTO learning_assignment (target_type, target_id, user_id, assignment_role) "
            + "VALUES (#{targetType}, #{targetId}, #{userId}, #{assignmentRole}) "
            + "ON DUPLICATE KEY UPDATE assignment_role = VALUES(assignment_role)")
    int assign(@Param("targetType") String targetType,
               @Param("targetId") String targetId,
               @Param("userId") String userId,
               @Param("assignmentRole") String assignmentRole);
}