package cn.dct.zpback.mapper;

import cn.dct.zpback.entity.MallOrder;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.util.List;

/** 问星渡｜商城订单 Mapper */
@Mapper
public interface MallOrderMapper {

    int insert(MallOrder order);

    MallOrder findById(@Param("orderId") String orderId);

    /** 支付回调按订单号定位（幂等） */
    MallOrder findByOrderNo(@Param("orderNo") String orderNo);

    /** 商品订单列表；status 为 null 时查全部，按下单时间倒序 */
    List<MallOrder> listByUser(@Param("userId") String userId,
                               @Param("status") String status);

    /** PENDING_PAYMENT -> PAID；只改状态，不做别的 */
    int markPaid(@Param("orderId") String orderId,
                 @Param("paymentChannel") String paymentChannel);

    /** PENDING_PAYMENT -> CANCELLED */
    int cancel(@Param("orderId") String orderId);

    /** 发货：PAID -> SHIPPED（服务端/运营推进，非用户点击） */
    int markShipped(@Param("orderId") String orderId);

    /** 完成：SHIPPED -> COMPLETED */
    int markCompleted(@Param("orderId") String orderId);
}