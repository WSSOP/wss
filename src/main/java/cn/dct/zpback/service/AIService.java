package cn.dct.zpback.service;

import cn.dct.zpback.config.AIConfig;
import cn.dct.zpback.exception.AIUnavailableException;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.Data;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Service;
import org.springframework.web.reactive.function.client.WebClient;
import org.springframework.web.reactive.function.client.WebClientResponseException;
import reactor.core.publisher.Mono;

import java.time.Duration;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * AI 服务 - OpenAI 兼容格式
 * 支持 OpenAI / Claude (via OpenAI compat) / DeepSeek / 通义千问 等
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class AIService {

    private final AIConfig aiConfig;
    private final WebClient aiWebClient;
    private final ObjectMapper objectMapper;

    // ============================================================
    // 消息对象
    // ============================================================

    @Data
    public static class AIMessage {
        private String role;    // system / user / assistant
        private String content;

        public AIMessage() {}

        public AIMessage(String role, String content) {
            this.role = role;
            this.content = content;
        }
    }

    @Data
    public static class ChatCompletionRequest {
        private String model;
        private List<AIMessage> messages;
        @JsonProperty("max_tokens")
        private Integer maxTokens;
        private Double temperature;
        private Boolean stream;
    }

    @Data
    public static class ChatCompletionChoice {
        private Integer index;
        private AIMessage message;
        @JsonProperty("finish_reason")
        private String finishReason;
    }

    @Data
    public static class ChatCompletionUsage {
        @JsonProperty("prompt_tokens")
        private Integer promptTokens;
        @JsonProperty("completion_tokens")
        private Integer completionTokens;
        @JsonProperty("total_tokens")
        private Integer totalTokens;
    }

    @Data
    public static class ChatCompletionResponse {
        private String id;
        private String object;
        private Long created;
        private String model;
        private List<ChatCompletionChoice> choices;
        private ChatCompletionUsage usage;
    }

    // ============================================================
    // 核心调用方法
    // ============================================================

    /**
     * AI 是否可用（是否配置了 API Key）
     *
     * 未配置时不要发起注定失败的请求：直接失败比等一次网络往返更快也更可控。
     */
    public boolean isAvailable() {
        return aiConfig.getApiKey() != null && !aiConfig.getApiKey().isBlank();
    }

    /**
     * 发送聊天补全请求（非流式）
     */
    public String chatCompletion(List<AIMessage> messages) {
        return chatCompletion(messages, aiConfig.getModel(), aiConfig.getTemperature(), aiConfig.getMaxTokens());
    }

    /**
     * 发送聊天补全请求（带参数）
     *
     * @throws AIUnavailableException 未配置 / 超时 / 网络异常 / 返回空结果
     */
    public String chatCompletion(List<AIMessage> messages, String model, double temperature, int maxTokens) {
        if (!isAvailable()) {
            throw new AIUnavailableException("AI 服务未配置 API Key（ai.api-key）", false, null);
        }

        String url = aiConfig.getBaseUrl() + "/chat/completions";

        ChatCompletionRequest request = new ChatCompletionRequest();
        request.setModel(model);
        request.setMessages(messages);
        request.setMaxTokens(maxTokens);
        request.setTemperature(temperature);
        request.setStream(false);

        try {
            log.info("AI 请求: model={}, messages={}", model, messages.size());

            ChatCompletionResponse response = aiWebClient.post()
                    .uri(url)
                    .header("Authorization", "Bearer " + aiConfig.getApiKey())
                    .contentType(MediaType.APPLICATION_JSON)
                    .bodyValue(request)
                    .retrieve()
                    .bodyToMono(ChatCompletionResponse.class)
                    // 配置里的 timeout 此前从未生效，模型挂起会永久占用请求线程
                    .timeout(Duration.ofSeconds(Math.max(1, aiConfig.getTimeout())))
                    .block();

            if (response == null || response.getChoices() == null || response.getChoices().isEmpty()) {
                throw new AIUnavailableException("AI 返回空结果");
            }

            ChatCompletionChoice first = response.getChoices().get(0);
            String content = first.getMessage() == null ? null : first.getMessage().getContent();
            if (content == null || content.isBlank()) {
                throw new AIUnavailableException("AI 返回内容为空");
            }

            log.info("AI 响应: tokens={}",
                    response.getUsage() != null ? response.getUsage().getTotalTokens() : "unknown");
            return content;
        } catch (AIUnavailableException e) {
            throw e;
        } catch (Exception e) {
            log.error("AI 调用失败: {}", e.getMessage(), e);
            // 鉴权 / 参数类错误重试没有意义
            boolean retryable = !(e instanceof WebClientResponseException wr
                    && (wr.getStatusCode().value() == 401
                        || wr.getStatusCode().value() == 403
                        || wr.getStatusCode().value() == 400));
            throw new AIUnavailableException("AI 调用失败: " + e.getMessage(), retryable, e);
        }
    }

    /**
     * 简单问答（快捷方法）
     */
    public String ask(String systemPrompt, String userQuestion) {
        List<AIMessage> messages = new ArrayList<>();
        messages.add(new AIMessage("system", systemPrompt));
        messages.add(new AIMessage("user", userQuestion));
        return chatCompletion(messages);
    }

    /**
     * 生成长文本报告（使用更高的 token 限制）
     * @param systemPrompt 系统提示词
     * @param userPrompt 用户提示词
     * @param maxTokens 最大 token 数（建议 6000-8000 用于生成长报告）
     * @return AI 生成的报告内容
     */
    public String generateLongReport(String systemPrompt, String userPrompt, int maxTokens) {
        List<AIMessage> messages = new ArrayList<>();
        messages.add(new AIMessage("system", systemPrompt));
        messages.add(new AIMessage("user", userPrompt));
        return chatCompletion(messages, aiConfig.getModel(), aiConfig.getTemperature(), maxTokens);
    }

    /**
     * 生成人生课题深度报告（默认 8000 token）
     */
    public String generateLifePatternReport(String systemPrompt, String userPrompt) {
        return generateLongReport(systemPrompt, userPrompt, 8000);
    }

    /**
     * 带上下文的问答
     */
    public String askWithContext(String systemPrompt, String context, String userQuestion) {
        List<AIMessage> messages = new ArrayList<>();
        messages.add(new AIMessage("system", systemPrompt + "\n\n=== 上下文信息 ===\n" + context));
        messages.add(new AIMessage("user", userQuestion));
        return chatCompletion(messages);
    }

    /**
     * 多轮对话
     */
    public String chatWithHistory(String systemPrompt, List<AIMessage> history, String newQuestion) {
        List<AIMessage> messages = new ArrayList<>();
        messages.add(new AIMessage("system", systemPrompt));
        if (history != null) {
            messages.addAll(history);
        }
        messages.add(new AIMessage("user", newQuestion));
        return chatCompletion(messages);
    }
}
