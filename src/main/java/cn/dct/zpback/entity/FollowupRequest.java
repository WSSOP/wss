package cn.dct.zpback.entity;

import lombok.Data;

import java.io.Serializable;
import java.time.LocalDateTime;

/**
 * 追问请求实体
 * 记录每次追问的完整生命周期
 */
@Data
public class FollowupRequest implements Serializable {

    private static final long serialVersionUID = 1L;

    /** 请求ID */
    private String requestId;

    /** 幂等键 */
    private String idempotencyKey;

    /** 用户ID */
    private String userId;

    /** 会话ID */
    private String conversationId;

    /** 来源上下文（REPORT/STAR_AI/HISTORY） */
    private String sourceContext;

    /** 报告实例ID */
    private String reportInstanceId;

    /** 用户问题 */
    private String question;

    /** 请求模式：QUICK/DEEP */
    private String requestedMode;

    /** 实际回答模式：QUICK/DEEP/NO_CHARGE_TRANSFER */
    private String answerMode;

    /** 路由意图：REPORT_EXPLANATION/REPORT_EVIDENCE/DEEP_ANALYSIS/OUT_OF_REPORT_SCOPE */
    private String intent;

    /** 请求状态 */
    private String requestStatus;

    /** 回答状态 */
    private String answerStatus;

    /** 阻塞原因 */
    private String blockReason;

    /** 路由目标 */
    private String routeTarget;

    /** 询价token */
    private String quoteToken;

    /** 回答内容（JSON：sections/source_refs/answer_claims等） */
    private String answerContent;

    /** 版本信息（JSON） */
    private String versionInfo;

    /** 权益来源 */
    private String entitlementSource;

    /** 权益流水状态 */
    private String ledgerStatus;

    /** 是否已核销 */
    private Boolean consumed;

    /** 消耗星印数 */
    private Integer starCost;

    /** 重试次数 */
    private Integer retryCount;

    /** 客户端上下文（JSON） */
    private String clientContext;

    /** 创建时间 */
    private LocalDateTime createdAt;

    /** 完成时间 */
    private LocalDateTime completedAt;
}
