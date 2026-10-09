package cn.dct.zpback.dto;

import lombok.Data;

import java.io.Serializable;

/**
 * 询价请求 DTO
 * 对应前端 POST /v1/report-followup/quotes 的请求体
 */
@Data
public class QuoteRequestDTO implements Serializable {

    private static final long serialVersionUID = 1L;

    /** 会话ID */
    private String conversationId;

    /** 报告实例ID */
    private String reportInstanceId;

    /** 用户问题 */
    private String question;

    /** 请求模式：QUICK/DEEP */
    private String requestedMode;
}
