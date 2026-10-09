package cn.dct.zpback.mapper;

import cn.dct.zpback.entity.LearningOrder;
import org.apache.ibatis.annotations.*;

import java.util.List;

/**
 * 问星渡｜订单 Mapper
 *
 * idx_fulfill(order_status, order_type) 支撑 Fulfillment 巡检：
 * 捞出 PAID 但未 FULFILLED 的订单补发权限。
 */
@Mapper
public interface LearningOrderMapper {

    String COLUMNS = "order_id, order_no, user_id, order_type, target_id, session_id, "
            + "target_title, amount, paid_amount, currency, order_status, payment_channel, "
            + "paid_at, fulfilled_at, cancelled_at, refunded_at, source, remark, created_at";

    @Select("SELECT " + COLUMNS + " FROM learning_order WHERE order_id = #{orderId}")
    LearningOrder findById(@Param("orderId") String orderId);

    @Select("SELECT " + COLUMNS + " FROM learning_order WHERE order_no = #{orderNo}")
    LearningOrder findByNo(@Param("orderNo") String orderNo);

    /** 同一用户对同一场次只允许一笔未关闭订单，避免重复下单 */
    @Select("SELECT " + COLUMNS + " FROM learning_order "
            + "WHERE user_id = #{userId} AND order_type = #{orderType} "
            + "  AND target_id = #{targetId} "
            + "  AND ((session_id = #{sessionId}) OR (#{sessionId} IS NULL AND session_id IS NULL)) "
            + "  AND order_status IN ('CREATED', 'PAID') LIMIT 1")
    LearningOrder findOpenOrder(@Param("userId") String userId,
                                @Param("orderType") String orderType,
                                @Param("targetId") String targetId,
                                @Param("sessionId") String sessionId);

    @Select("SELECT " + COLUMNS + " FROM learning_order WHERE user_id = #{userId} "
            + "ORDER BY created_at DESC LIMIT #{limit}")
    List<LearningOrder> findByUser(@Param("userId") String userId, @Param("limit") int limit);

    /** Fulfillment 巡检：已支付但未交付的订单 */
    @Select("SELECT " + COLUMNS + " FROM learning_order WHERE order_status = 'PAID' "
            + "ORDER BY paid_at ASC LIMIT #{limit}")
    List<LearningOrder> findPendingFulfillment(@Param("limit") int limit);

    @Insert("INSERT INTO learning_order (order_id, order_no, user_id, order_type, target_id, "
            + "session_id, target_title, amount, paid_amount, currency, order_status, "
            + "payment_channel, source, remark) VALUES "
            + "(#{orderId}, #{orderNo}, #{userId}, #{orderType}, #{targetId}, #{sessionId}, "
            + "#{targetTitle}, #{amount}, #{paidAmount}, #{currency}, #{orderStatus}, "
            + "#{paymentChannel}, #{source}, #{remark})")
    int insert(LearningOrder o);

    /**
     * 标记支付成功：CREATED -> PAID。
     *
     * 带 order_status 条件保证幂等：重复回调只生效一次。
     * 第三方支付平台会重试回调，幂等是硬要求。
     */
    @Update("UPDATE learning_order SET order_status = 'PAID', payment_channel = #{channel}, "
            + "paid_amount = #{paidAmount}, paid_at = NOW() "
            + "WHERE order_id = #{orderId} AND order_status = 'CREATED'")
    int markPaid(@Param("orderId") String orderId,
                 @Param("channel") String channel,
                 @Param("paidAmount") java.math.BigDecimal paidAmount);

    /** Fulfillment 完成：PAID -> FULFILLED（发放权限后调用） */
    @Update("UPDATE learning_order SET order_status = 'FULFILLED', fulfilled_at = NOW() "
            + "WHERE order_id = #{orderId} AND order_status = 'PAID'")
    int markFulfilled(@Param("orderId") String orderId);

    @Update("UPDATE learning_order SET order_status = 'CANCELLED', cancelled_at = NOW() "
            + "WHERE order_id = #{orderId} AND order_status = 'CREATED'")
    int cancel(@Param("orderId") String orderId);

    /** 退款：PAID 或 FULFILLED -> REFUNDED */
    @Update("UPDATE learning_order SET order_status = 'REFUNDED', refunded_at = NOW() "
            + "WHERE order_id = #{orderId} AND order_status IN ('PAID', 'FULFILLED')")
    int refund(@Param("orderId") String orderId);
}
