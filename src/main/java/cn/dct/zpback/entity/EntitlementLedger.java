package cn.dct.zpback.entity;

import lombok.Data;

import java.io.Serializable;
import java.time.LocalDateTime;

/**
 * 权益流水实体
 * 记录每次权益的预占、核销、释放/返还
 */
@Data
public class EntitlementLedger implements Serializable {

    private static final long serialVersionUID = 1L;

    /** 流水ID */
    private Long id;

    /** 请求ID */
    private String requestId;

    /** 用户ID */
    private String userId;

    /** 报告实例ID */
    private String reportInstanceId;

    /** 流水动作类型：QUOTE/RESERVE/CONSUME/RELEASE/REFUND */
    private String ledgerActionType;

    /** 权益来源 */
    private String entitlementSource;

    /** 流水状态：QUOTED/RESERVED/CONSUMED/RELEASED/REFUNDED/NOT_REQUIRED */
    private String ledgerStatus;

    /** 星印金额 */
    private Integer starAmount;

    /** 操作前余额快照 */
    private Integer balanceBefore;

    /** 操作后余额快照 */
    private Integer balanceAfter;

    /** 备注 */
    private String remark;

    /** 创建时间 */
    private LocalDateTime createdAt;
}
