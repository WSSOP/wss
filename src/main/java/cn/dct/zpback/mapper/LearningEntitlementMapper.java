package cn.dct.zpback.mapper;

import cn.dct.zpback.entity.LearningEntitlement;
import org.apache.ibatis.annotations.*;

import java.util.List;

/**
 * 问星渡｜学习权限 Mapper
 *
 * 只有 entitlement_status='ACTIVE' 才算可用权限；
 * PENDING（已下单未支付）/ EXPIRED / REVOKED 一律不可用。
 */
@Mapper
public interface LearningEntitlementMapper {

    String COLUMNS = "entitlement_id, user_id, target_type, target_id, session_id, "
            + "entitlement_status, source, order_id, granted_at, expires_at, created_at";

    /**
     * 是否拥有可用权限。
     *
     * sessionId 为空时查「整门课程/活动」的权限；
     * 指定场次时优先匹配场次级权限，其次回落到课程级权限
     * （买了整门课就不该还要逐场次再买一次）。
     */
    @Select("<script>"
            + "SELECT " + COLUMNS + " FROM learning_entitlement "
            + "WHERE user_id = #{userId} AND target_type = #{targetType} AND target_id = #{targetId} "
            + "  AND entitlement_status = 'ACTIVE' "
            + "  AND (expires_at IS NULL OR expires_at &gt; NOW()) "
            + " <choose>"
            + "   <when test='sessionId != null'>"
            + "     AND (session_id = #{sessionId} OR session_id IS NULL)"
            + "   </when>"
            + "   <otherwise> AND session_id IS NULL </otherwise>"
            + " </choose>"
            + " ORDER BY session_id IS NULL ASC LIMIT 1"
            + "</script>")
    LearningEntitlement findActive(@Param("userId") String userId,
                                   @Param("targetType") String targetType,
                                   @Param("targetId") String targetId,
                                   @Param("sessionId") String sessionId);

    /** 用户全部有效权限（我的学习 / 我的活动） */
    @Select("SELECT " + COLUMNS + " FROM learning_entitlement "
            + "WHERE user_id = #{userId} AND entitlement_status = 'ACTIVE' "
            + "  AND (expires_at IS NULL OR expires_at > NOW()) "
            + "ORDER BY granted_at DESC")
    List<LearningEntitlement> findActiveByUser(@Param("userId") String userId);

    /** 按来源订单查权限：Fulfillment 幂等发放的依据 */
    @Select("SELECT " + COLUMNS + " FROM learning_entitlement WHERE order_id = #{orderId}")
    LearningEntitlement findByOrderId(@Param("orderId") String orderId);

    /**
     * 退款后撤销该订单发放的所有权限。
     *
     * 记录不删除——只改状态。财务对账与客服查询都需要看到
     * 「这笔订单曾经发过权限，后来因为退款被收回了」。
     *
     * 返回 0 表示该订单本来就没发过权限（未 FULFILLED 就退款），
     * 这是合法情形，不应视为错误。
     */
    @Update("UPDATE learning_entitlement SET entitlement_status = 'REVOKED' "
            + "WHERE order_id = #{orderId} AND entitlement_status = 'ACTIVE'")
    int revokeByOrderId(@Param("orderId") String orderId);

    /** 到期扫描：expires_at 已过但仍是 ACTIVE 的权限 */
    @Update("UPDATE learning_entitlement SET entitlement_status = 'EXPIRED' "
            + "WHERE entitlement_status = 'ACTIVE' AND expires_at IS NOT NULL "
            + "  AND expires_at <= NOW()")
    int expireOverdue();

    @Insert("INSERT INTO learning_entitlement (entitlement_id, user_id, target_type, target_id, "
            + "session_id, entitlement_status, source, order_id, granted_at, expires_at) VALUES "
            + "(#{entitlementId}, #{userId}, #{targetType}, #{targetId}, #{sessionId}, "
            + "#{entitlementStatus}, #{source}, #{orderId}, #{grantedAt}, #{expiresAt})")
    int insert(LearningEntitlement e);

    /** 已存在的权限改状态：免费课 PENDING -> ACTIVE，或退款后 REVOKED */
    @Update("UPDATE learning_entitlement SET entitlement_status = #{status}, granted_at = #{grantedAt} "
            + "WHERE entitlement_id = #{entitlementId}")
    int updateStatus(@Param("entitlementId") String entitlementId,
                     @Param("status") String status,
                     @Param("grantedAt") java.time.LocalDateTime grantedAt);
}
