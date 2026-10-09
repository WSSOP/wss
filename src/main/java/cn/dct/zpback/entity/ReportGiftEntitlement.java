package cn.dct.zpback.entity;

import lombok.Data;

import java.io.Serializable;
import java.time.LocalDateTime;

/**
 * 报告赠送追问权益实体
 * 每份报告赠送1次主题内追问
 */
@Data
public class ReportGiftEntitlement implements Serializable {

    private static final long serialVersionUID = 1L;

    /** ID */
    private Long id;

    /** 报告实例ID */
    private String reportInstanceId;

    /** 用户ID */
    private String userId;

    /** 权益类型（固定 FOLLOWUP_GIFT） */
    private String benefitType;

    /** 是否已使用 */
    private Boolean used;

    /** 使用时间 */
    private LocalDateTime usedAt;

    /** 关联的请求ID */
    private String usedByRequestId;

    /** 创建时间 */
    private LocalDateTime createdAt;

    /** 过期时间（null 表示随报告访问权） */
    private LocalDateTime expireAt;
}
