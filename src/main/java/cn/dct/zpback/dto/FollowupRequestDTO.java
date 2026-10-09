package cn.dct.zpback.dto;

import lombok.Data;

import java.io.Serializable;

/**
 * 追问请求 DTO
 * 对应前端 POST /v1/report-followup/requests 的请求体
 */
@Data
public class FollowupRequestDTO implements Serializable {

    private static final long serialVersionUID = 1L;

    /** 请求ID（前端生成） */
    private String requestId;

    /** 幂等键（前端生成） */
    private String idempotencyKey;

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

    /** 询价token */
    private String quoteToken;

    /** 客户端上下文 */
    private ClientContext clientContext;

    @Data
    public static class ClientContext implements Serializable {
        /** 入口：STAR_AI/REPORT_DETAIL/HISTORY */
        private String entry;
        /** 语言 */
        private String locale;
        /** 时区 */
        private String timezone;
    }
}
