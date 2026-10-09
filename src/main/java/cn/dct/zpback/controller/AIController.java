package cn.dct.zpback.controller;

import cn.dct.zpback.dto.ApiResponse;
import cn.dct.zpback.entity.AIConversation;
import cn.dct.zpback.entity.AIConversationMessage;
import cn.dct.zpback.service.ConversationService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.*;

import java.util.*;

/**
 * 星灵AI 会话 API 控制器
 * 路径前缀：/api/v1/ai
 */
@RestController
@RequestMapping("/v1/ai")
@RequiredArgsConstructor
@Slf4j
public class AIController {

    private final ConversationService conversationService;

    // ============================================================
    // 会话管理
    // ============================================================

    /**
     * POST /v1/ai/conversations
     * 创建新会话（首个问题会触发首次 AI 响应）
     */
    @PostMapping("/conversations")
    public ApiResponse<Map<String, Object>> createConversation(
            @RequestHeader(value = "Authorization", required = false) String authHeader,
            @RequestBody Map<String, Object> body) {

        String userId = extractUserId(authHeader);
        String mode = (String) body.getOrDefault("mode", "NATAL");
        String question = (String) body.getOrDefault("question", "");
        String responseStyle = (String) body.getOrDefault("response_style", "DIRECT");
        Object context = body.get("context");
        String sourceContext = (String) body.getOrDefault("source_context", null);

        // 创建会话
        AIConversation conv = conversationService.createConversation(
                userId, mode, question, responseStyle, context, sourceContext);

        // 如果有首个问题，触发 AI 响应
        if (question != null && !question.isEmpty()) {
            AIConversationMessage aiMsg = conversationService.chat(conv.getId(), question, responseStyle);
            Map<String, Object> result = new HashMap<>();
            result.put("conversation", conv);
            result.put("ai_response", aiMsg);
            return ApiResponse.success(result);
        }

        Map<String, Object> result = new HashMap<>();
        result.put("conversation", conv);
        result.put("ai_response", null);
        return ApiResponse.success(result);
    }

    /**
     * GET /v1/ai/conversations/{conversationId}
     * 获取会话详情（含消息列表）
     */
    @GetMapping("/conversations/{conversationId}")
    public ApiResponse<Map<String, Object>> getConversation(
            @RequestHeader(value = "Authorization", required = false) String authHeader,
            @PathVariable String conversationId) {

        String userId = extractUserId(authHeader);
        AIConversation conv = conversationService.getConversation(conversationId, userId);
        if (conv == null) {
            return ApiResponse.error(404, "会话不存在");
        }

        List<AIConversationMessage> messages = conversationService.getMessages(conversationId);

        Map<String, Object> result = new HashMap<>();
        result.put("conversation", conv);
        result.put("messages", messages);
        return ApiResponse.success(result);
    }

    /**
     * GET /v1/ai/conversations
     * 获取用户会话列表
     */
    @GetMapping("/conversations")
    public ApiResponse<Map<String, Object>> getConversations(
            @RequestHeader(value = "Authorization", required = false) String authHeader,
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "20") int limit,
            @RequestParam(required = false) String mode) {

        String userId = extractUserId(authHeader);
        List<AIConversation> conversations = conversationService.getConversations(userId, page, limit, mode);
        int total = conversationService.getConversationCount(userId);

        Map<String, Object> result = new HashMap<>();
        result.put("items", conversations);
        result.put("total", total);
        result.put("page", page);
        result.put("limit", limit);
        return ApiResponse.success(result);
    }

    /**
     * DELETE /v1/ai/conversations/{conversationId}
     * 删除会话
     */
    @DeleteMapping("/conversations/{conversationId}")
    public ApiResponse<Void> deleteConversation(
            @RequestHeader(value = "Authorization", required = false) String authHeader,
            @PathVariable String conversationId) {

        String userId = extractUserId(authHeader);
        conversationService.deleteConversation(conversationId, userId);
        return ApiResponse.success(null);
    }

    // ============================================================
    // 消息管理
    // ============================================================

    /**
     * POST /v1/ai/conversations/{conversationId}/messages
     * 发送消息（继续追问）
     */
    @PostMapping("/conversations/{conversationId}/messages")
    public ApiResponse<AIConversationMessage> sendMessage(
            @RequestHeader(value = "Authorization", required = false) String authHeader,
            @PathVariable String conversationId,
            @RequestBody Map<String, Object> body) {

        String userId = extractUserId(authHeader);
        String content = (String) body.getOrDefault("content", "");
        String responseStyle = (String) body.getOrDefault("response_style", null);

        // 验证会话
        AIConversation conv = conversationService.getConversation(conversationId, userId);
        if (conv == null) {
            return ApiResponse.error(404, "会话不存在");
        }

        // 执行对话
        AIConversationMessage aiMsg = conversationService.chat(conversationId, content, responseStyle);
        if (aiMsg == null) {
            return ApiResponse.error(500, "AI 响应失败");
        }

        return ApiResponse.success(aiMsg);
    }

    /**
     * PUT /v1/ai/conversations/{conversationId}/style
     * 切换表达风格
     */
    @PutMapping("/conversations/{conversationId}/style")
    public ApiResponse<Void> updateStyle(
            @RequestHeader(value = "Authorization", required = false) String authHeader,
            @PathVariable String conversationId,
            @RequestBody Map<String, Object> body) {

        String userId = extractUserId(authHeader);
        String style = (String) body.getOrDefault("response_style", "DIRECT");

        conversationService.updateStyle(conversationId, userId, style);
        return ApiResponse.success(null);
    }

    // ============================================================
    // 推荐问题
    // ============================================================

    /**
     * GET /v1/ai/suggestions
     * 获取推荐问题
     */
    @GetMapping("/suggestions")
    public ApiResponse<Map<String, Object>> getSuggestions(
            @RequestParam String mode,
            @RequestParam(required = false) Map<String, Object> context) {

        List<String> questions = conversationService.getSuggestedQuestions(mode);

        Map<String, Object> result = new HashMap<>();
        result.put("questions", questions);
        result.put("mode", mode);
        return ApiResponse.success(result);
    }

    // ============================================================
    // AI 配额
    // ============================================================

    /**
     * GET /v1/ai/quota
     * 获取用户 AI 配额
     */
    @GetMapping("/quota")
    public ApiResponse<Map<String, Object>> getQuota(
            @RequestHeader(value = "Authorization", required = false) String authHeader) {

        String userId = extractUserId(authHeader);

        // TODO: 接入真实配额系统（VIP / 免费次数）
        Map<String, Object> result = new HashMap<>();
        result.put("daily_free_limit", 5);
        result.put("daily_free_remaining", 3);
        result.put("has_vip", false);
        result.put("vip_daily_limit", 50);
        return ApiResponse.success(result);
    }

    // ============================================================
    // 报告推荐
    // ============================================================

    /**
     * GET /v1/ai/conversations/{conversationId}/report-recommendation
     * 获取深度报告推荐
     */
    @GetMapping("/conversations/{conversationId}/report-recommendation")
    public ApiResponse<Map<String, Object>> getReportRecommendation(
            @RequestHeader(value = "Authorization", required = false) String authHeader,
            @PathVariable String conversationId) {

        String userId = extractUserId(authHeader);

        // 根据会话模式推荐报告
        AIConversation conv = conversationService.getConversation(conversationId, userId);
        String reportType = "CAREER";
        if (conv != null) {
            reportType = switch (conv.getMode()) {
                case "NATAL" -> "LIFEPATTERN_GROWTH";
                case "SYNASTRY" -> "SYNASTRY_RELATIONSHIP";
                case "PRASHNA" -> "CAREER";
                default -> "LIFEPATTERN_GROWTH";
            };
        }

        Map<String, Object> result = new HashMap<>();
        result.put("recommended", true);
        result.put("report_type", reportType);
        result.put("reason", "基于你的星盘解读，这份深度报告将为你提供更全面的分析。");
        result.put("price", 99);
        return ApiResponse.success(result);
    }

    // ============================================================
    // 工具方法
    // ============================================================

    /**
     * 从 Authorization header 提取用户ID
     * TODO: 接入真实 JWT 解析
     */
    private String extractUserId(String authHeader) {
        if (authHeader != null && authHeader.startsWith("Bearer ")) {
            // 临时实现：从 token 解析或使用默认值
            // TODO: 使用 JWT 解析真实用户ID
            return "user_" + authHeader.hashCode();
        }
        return "anonymous";
    }
}
