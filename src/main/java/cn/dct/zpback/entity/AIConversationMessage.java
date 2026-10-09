package cn.dct.zpback.entity;

import lombok.Data;

import java.time.LocalDateTime;

/**
 * AI 会话消息实体
 */
@Data
public class AIConversationMessage {

    private String id;

    /** 会话ID */
    private String conversationId;

    /** 角色: user / assistant / system */
    private String role;

    /** 消息内容 */
    private String content;

    /** 消息类型: TEXT / SECTION / REPORT_RECOMMENDATION */
    private String messageType;

    /** 结构化内容 (JSON字符串) */
    private String structuredContentJson;

    /** token 使用量 */
    private Integer tokensUsed;

    /** 排序序号 */
    private Integer sortOrder;

    private LocalDateTime createdAt;
}
