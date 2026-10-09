package cn.dct.zpback.entity;

import lombok.Data;

import java.time.LocalDateTime;

/**
 * 报告追问会话实体
 */
@Data
public class Conversation {

    private String conversationId;

    /** 用户ID */
    private String userId;

    /** 报告实例ID */
    private String reportInstanceId;

    /** 状态: ACTIVE / EXPIRED / CLOSED */
    private String status;

    /** 追问轮数 */
    private Integer turnCount;

    /** 最后追问 */
    private String lastQuestion;

    /** 最后活跃时间 */
    private LocalDateTime lastActiveAt;

    /** 过期时间 */
    private LocalDateTime expireAt;

    private LocalDateTime createdAt;
}
