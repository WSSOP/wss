package cn.dct.zpback.entity;

import com.fasterxml.jackson.databind.PropertyNamingStrategies;
import com.fasterxml.jackson.databind.annotation.JsonNaming;
import lombok.Data;

import java.io.Serializable;
import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * 问星渡｜商城订单
 *
 * 一单 = 一个结算分组（§2.6），价格 / 文案全为下单快照。
 * 用户主状态固定：待付款 → 待发货 → 已发货 → 已完成，前台无【确认收货】（§2.10）。
 */
@Data
@JsonNaming(PropertyNamingStrategies.SnakeCaseStrategy.class)
public class MallOrder implements Serializable {

    private static final long serialVersionUID = 1L;

    private String orderId;
    private String userId;
    private String orderNo;

    /** STANDARD / PREORDER；一单只属于一个分组 */
    private String tradeGroup;
    private String groupKey;

    private BigDecimal goodsAmount;
    private BigDecimal freightAmount;
    private BigDecimal discountAmount;
    private BigDecimal payableAmount;

    /** 收货地址下单快照（JSON） */
    private String addressSnapshot;

    /** WECHAT_PAY / ALIPAY */
    private String paymentChannel;

    /** PENDING_PAYMENT / PAID / SHIPPED / COMPLETED / CANCELLED */
    private String orderStatus;

    /** 现货锁库存截止时间；预售为 null */
    private LocalDateTime stockLockedUntil;
    private LocalDateTime paidAt;
    private LocalDateTime cancelledAt;
    private LocalDateTime shippedAt;
    private LocalDateTime completedAt;

    private String remark;
    private String source;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
}