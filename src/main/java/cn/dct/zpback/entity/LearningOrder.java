package cn.dct.zpback.entity;

import com.fasterxml.jackson.annotation.JsonAlias;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.databind.PropertyNamingStrategies;
import com.fasterxml.jackson.databind.annotation.JsonNaming;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * 问星渡｜课程与活动订单
 *
 * 对应表：learning_order
 *
 * order_status 状态机（实现方拟定）：
 *   CREATED -> PAID -> FULFILLED
 *   CREATED -> CANCELLED
 *   FULFILLED -> REFUNDED
 *
 * PAID 与 FULFILLED 刻意分开：让「支付成功」与「权限到账」
 * 成为两个可观测、可重试的独立步骤。支付回调不得直接写 ACTIVE 权限。
 */
@Data
@JsonIgnoreProperties(ignoreUnknown = true)
@JsonNaming(PropertyNamingStrategies.SnakeCaseStrategy.class)
public class LearningOrder {

    @JsonAlias("orderId")
    private String orderId;

    @JsonAlias("orderNo")
    private String orderNo;

    @JsonAlias("userId")
    private String userId;

    /** COURSE / EVENT */
    @JsonAlias("orderType")
    private String orderType;

    @JsonAlias("targetId")
    private String targetId;

    @JsonAlias("sessionId")
    private String sessionId;

    /** 下单时标题快照：运营改价不影响历史订单展示 */
    @JsonAlias("targetTitle")
    private String targetTitle;

    @JsonAlias("amount")
    private BigDecimal amount;

    @JsonAlias("paidAmount")
    private BigDecimal paidAmount;

    @JsonAlias("currency")
    private String currency;

    /** CREATED / PAID / FULFILLED / CANCELLED / REFUNDED */
    @JsonAlias("orderStatus")
    private String orderStatus;

    /** WECHAT / ALIPAY / MANUAL */
    @JsonAlias("paymentChannel")
    private String paymentChannel;

    @JsonAlias("paidAt")
    private LocalDateTime paidAt;

    @JsonAlias("fulfilledAt")
    private LocalDateTime fulfilledAt;

    @JsonAlias("cancelledAt")
    private LocalDateTime cancelledAt;

    @JsonAlias("refundedAt")
    private LocalDateTime refundedAt;

    @JsonAlias("source")
    private String source;

    @JsonAlias("remark")
    private String remark;

    @JsonAlias("createdAt")
    private LocalDateTime createdAt;
}
