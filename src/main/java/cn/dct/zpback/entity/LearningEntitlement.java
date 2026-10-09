package cn.dct.zpback.entity;

import com.fasterxml.jackson.annotation.JsonAlias;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.databind.PropertyNamingStrategies;
import com.fasterxml.jackson.databind.annotation.JsonNaming;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 问星渡｜学习权限
 *
 * 对应表：learning_entitlement
 *
 * 原始文档明确要求：<b>Payment 不直接创建业务权限</b>。
 * 支付成功只把订单推到 PAID，由 Fulfillment 环节发放 ACTIVE 权限。
 * 免费内容由系统直接发放（source=FREE_GRANT），不经过支付。
 *
 * entitlement_status 状态机（实现方拟定）：
 *   PENDING -> ACTIVE -> EXPIRED / REVOKED
 *   PENDING -> REVOKED（订单取消）
 */
@Data
@JsonIgnoreProperties(ignoreUnknown = true)
@JsonNaming(PropertyNamingStrategies.SnakeCaseStrategy.class)
public class LearningEntitlement {

    @JsonAlias("entitlementId")
    private String entitlementId;

    @JsonAlias("userId")
    private String userId;

    /** COURSE / EVENT */
    @JsonAlias("targetType")
    private String targetType;

    @JsonAlias("targetId")
    private String targetId;

    /** 限定场次；NULL = 整个课程/活动 */
    @JsonAlias("sessionId")
    private String sessionId;

    /** PENDING / ACTIVE / EXPIRED / REVOKED */
    @JsonAlias("entitlementStatus")
    private String entitlementStatus;

    /** FREE_GRANT / ORDER_PAID / GIFT / COMP */
    @JsonAlias("source")
    private String source;

    @JsonAlias("orderId")
    private String orderId;

    @JsonAlias("grantedAt")
    private LocalDateTime grantedAt;

    /** NULL = 永久 */
    @JsonAlias("expiresAt")
    private LocalDateTime expiresAt;

    @JsonAlias("createdAt")
    private LocalDateTime createdAt;
}
