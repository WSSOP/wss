package cn.dct.zpback.service;

import cn.dct.zpback.config.AIConfig;
import cn.dct.zpback.exception.AIUnavailableException;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.web.reactive.function.client.ClientResponse;
import org.springframework.web.reactive.function.client.ExchangeFunction;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.core.publisher.Mono;

import static org.junit.jupiter.api.Assertions.*;

/**
 * AIService 失败语义测试
 *
 * 回归背景：旧实现在任何异常时都 return 一段中文提示语，
 * 调用方无法区分「模型返回了这段话」与「调用失败」，
 * 失败文案会被当成报告正文写进数据库。
 */
class AIServiceTest {

    private static final String OK_JSON = """
            {"id":"chatcmpl-1","object":"chat.completion","created":1,"model":"gpt-4o-mini",
             "choices":[{"index":0,"message":{"role":"assistant","content":"你好"},"finish_reason":"stop"}],
             "usage":{"prompt_tokens":1,"completion_tokens":1,"total_tokens":2}}
            """;

    private static WebClient stub(String body) {
        ExchangeFunction fn = req -> Mono.just(ClientResponse.create(HttpStatus.OK)
                .header("Content-Type", "application/json")
                .body(body)
                .build());
        return WebClient.builder().exchangeFunction(fn).build();
    }

    private static AIService service(AIConfig cfg, WebClient client) {
        return new AIService(cfg, client, new ObjectMapper());
    }

    private static AIConfig config(String key) {
        AIConfig c = new AIConfig();
        c.setApiKey(key);
        c.setTimeout(5);
        return c;
    }

    @Test
    void missingApiKey_failsFastWithoutNetworkCall() {
        // 用一个「被调用就炸」的 client，证明未配置 Key 时根本不会发请求
        WebClient boom = WebClient.builder().exchangeFunction(req -> {
            throw new AssertionError("未配置 Key 时不应发起网络请求");
        }).build();
        AIService svc = service(config(""), boom);

        assertFalse(svc.isAvailable());
        AIUnavailableException e = assertThrows(AIUnavailableException.class,
                () -> svc.ask("sys", "hi"));
        assertFalse(e.isRetryable(), "未配置 Key 重试无意义");
    }

    @Test
    void emptyChoices_throwsInsteadOfReturningErrorText() {
        AIService svc = service(config("k"), stub("{\"choices\":[]}"));
        assertThrows(AIUnavailableException.class, () -> svc.ask("sys", "hi"));
    }

    @Test
    void blankContent_throws() {
        AIService svc = service(config("k"), stub(
                "{\"choices\":[{\"index\":0,\"message\":{\"role\":\"assistant\",\"content\":\"  \"},\"finish_reason\":\"stop\"}]}"));
        assertThrows(AIUnavailableException.class, () -> svc.ask("sys", "hi"));
    }

    @Test
    void success_returnsContent() {
        AIService svc = service(config("k"), stub(OK_JSON));
        assertEquals("你好", svc.ask("sys", "hi"));
    }

    @Test
    void httpError_isWrappedAndNonRetryableForAuth() {
        ExchangeFunction fn = req -> Mono.just(ClientResponse.create(HttpStatus.UNAUTHORIZED)
                .body("{\"error\":\"bad key\"}")
                .build());
        AIService svc = service(config("k"), WebClient.builder().exchangeFunction(fn).build());

        AIUnavailableException e = assertThrows(AIUnavailableException.class, () -> svc.ask("sys", "hi"));
        assertFalse(e.isRetryable(), "401 重试无意义");
    }

    @Test
    void httpError_isRetryableForServerError() {
        ExchangeFunction fn = req -> Mono.just(ClientResponse.create(HttpStatus.BAD_GATEWAY)
                .body("upstream down")
                .build());
        AIService svc = service(config("k"), WebClient.builder().exchangeFunction(fn).build());

        AIUnavailableException e = assertThrows(AIUnavailableException.class, () -> svc.ask("sys", "hi"));
        assertTrue(e.isRetryable(), "5xx 值得重试");
    }
}
