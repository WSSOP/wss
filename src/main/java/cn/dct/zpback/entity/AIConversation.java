package cn.dct.zpback.entity;

import lombok.Data;

import java.time.LocalDateTime;

/**
 * AI 会话实体（星灵AI 6种模式会话）
 */
@Data
public class AIConversation {

    private String id;

    /** 用户ID */
    private String userId;

    /** 会话模式: NATAL / SYNASTRY / PRASHNA / EMOTIONAL / KNOWLEDGE / REPORT_FOLLOWUP */
    private String mode;

    /** 首个问题 */
    private String firstQuestion;

    /** 表达风格: DIRECT / GENTLE / PROFESSIONAL */
    private String responseStyle;

    /** 上下文数据 (JSON字符串) */
    private String contextJson;

    /** 来源上下文 */
    private String sourceContext;

    /** 状态: ACTIVE / EXPIRED / DELETED */
    private String status;

    /** 消息数量 */
    private Integer messageCount;

    /** 最后活跃时间 */
    private LocalDateTime lastActiveAt;

    private LocalDateTime createdAt;

    private LocalDateTime updatedAt;
}
