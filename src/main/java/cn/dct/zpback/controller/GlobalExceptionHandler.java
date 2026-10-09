package cn.dct.zpback.controller;

import cn.dct.zpback.dto.ApiResponse;
import cn.dct.zpback.exception.AIUnavailableException;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * 全局异常处理
 *
 * 此前项目没有 @RestControllerAdvice，AI 调用抛出的任何异常都会变成裸 500 +
 * Spring 默认错误页（对前端不可用，也容易把内部信息泄漏出去）。
 *
 * 这里只兜 AI 域的异常：刻意不接管 Exception，
 * 避免把 Spring 自身的参数校验错误（400）改写成 500。
 */
@RestControllerAdvice
@Slf4j
public class GlobalExceptionHandler {

    /**
     * AI 服务不可用 → 503 + retryable 标记
     *
     * 前端据此提示「稍后重试」；报告类接口在 service 内已优先降级到确定性内容，
     * 走到这里说明连确定性降级也没兜住，属于真故障。
     */
    @ExceptionHandler(AIUnavailableException.class)
    public ResponseEntity<ApiResponse<Map<String, Object>>> handleAIUnavailable(AIUnavailableException e) {
        log.error("AI 服务不可用: {}", e.getMessage());
        Map<String, Object> data = new LinkedHashMap<>();
        data.put("error_code", "AI_UNAVAILABLE");
        data.put("retryable", e.isRetryable());
        return ResponseEntity.status(HttpStatus.SERVICE_UNAVAILABLE)
                .body(ApiResponse.error(503, "AI 服务暂时不可用，请稍后重试", data));
    }
}