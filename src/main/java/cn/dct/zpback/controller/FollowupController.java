package cn.dct.zpback.controller;

import cn.dct.zpback.dto.ApiResponse;
import cn.dct.zpback.dto.FollowupRequestDTO;
import cn.dct.zpback.dto.QuoteRequestDTO;
import cn.dct.zpback.entity.ReportInstance;
import cn.dct.zpback.mapper.ReportInstanceMapper;
import cn.dct.zpback.service.FollowupService;
import cn.dct.zpback.service.RedisService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.*;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * 报告追问 REST API 控制器
 * 路径前缀：/api/v1/report-followup
 */
@RestController
@RequestMapping("/v1/report-followup")
@RequiredArgsConstructor
@Slf4j
public class FollowupController {

    private final FollowupService followupService;
    private final ReportInstanceMapper reportInstanceMapper;
    private final RedisService redisService;

    // ============================================================
    // 报告选择相关
    // ============================================================

    /**
     * GET /v1/report-followup/reports
     * 获取当前用户可追问的报告列表
     */
    @GetMapping("/reports")
    public ApiResponse<Map<String, Object>> getFollowupReports(
            @RequestHeader("Authorization") String authHeader,
            @RequestParam(required = false) String keyword,
            @RequestParam(defaultValue = "20") int limit) {

        String userId = extractUserId(authHeader);

        // 先查缓存（缓存失败不影响主流程）
        String cacheKey = RedisService.KEY_USER_INFO + userId + ":reports";
        try {
            List<ReportInstance> cached = (List<ReportInstance>) redisService.getObject(cacheKey);
            if (cached != null && !cached.isEmpty()) {
                Map<String, Object> cachedResult = new HashMap<>();
                cachedResult.put("items", cached);
                cachedResult.put("nextCursor", null);
                return ApiResponse.success(cachedResult);
            }
        } catch (Exception e) {
            log.warn("Redis缓存读取失败，跳过缓存: {}", e.getMessage());
        }

        // 查数据库
        List<ReportInstance> reports = reportInstanceMapper.selectFollowupAvailable(userId);

        // 缓存结果（5分钟），缓存失败不影响响应
        try {
            redisService.setObject(cacheKey, reports, 5, java.util.concurrent.TimeUnit.MINUTES);
        } catch (Exception e) {
            log.warn("Redis缓存写入失败: {}", e.getMessage());
        }

        Map<String, Object> result = new HashMap<>();
        result.put("items", reports);
        result.put("nextCursor", null);
        return ApiResponse.success(result);
    }

    /**
     * GET /v1/report-followup/reports/{report_instance_id}/suggestions
     * 获取推荐问题
     */
    @GetMapping("/reports/{report_instance_id}/suggestions")
    public ApiResponse<Map<String, Object>> getSuggestions(
            @RequestHeader("Authorization") String authHeader,
            @PathVariable("report_instance_id") String reportInstanceId) {

        String userId = extractUserId(authHeader);

        // 检查权限
        ReportInstance report = reportInstanceMapper.selectById(reportInstanceId);
        if (report == null || !userId.equals(report.getUserId())) {
            return ApiResponse.error(403, "REPORT_ACCESS_DENIED");
        }

        // 推荐问题（可从缓存获取）
        List<String> suggestions = List.of(
                "报告里的核心拉扯，在生活中会怎样出现？",
                "哪一条成长建议最适合我先开始？",
                "重复模式被触发时，我可以怎样识别？"
        );

        Map<String, Object> suggestionsResult = new HashMap<>();
        suggestionsResult.put("suggestions", suggestions);
        return ApiResponse.success(suggestionsResult);
    }

    // ============================================================
    // 会话相关
    // ============================================================

    /**
     * POST /v1/report-followup/conversations
     * 创建或恢复会话
     */
    @PostMapping("/conversations")
    public ApiResponse<Map<String, Object>> createOrResumeConversation(
            @RequestHeader("Authorization") String authHeader,
            @RequestBody Map<String, Object> body) {

        String userId = extractUserId(authHeader);
        String reportInstanceId = (String) body.get("report_instance_id");
        Boolean resumeIfActive = (Boolean) body.getOrDefault("resume_if_active", true);

        // 检查报告权限
        ReportInstance report = reportInstanceMapper.selectById(reportInstanceId);
        if (report == null || !userId.equals(report.getUserId())) {
            return ApiResponse.error(403, "REPORT_ACCESS_DENIED");
        }

        // 返回会话信息
        Map<String, Object> result = new HashMap<>();
        result.put("conversation_id", "conv_rf_" + System.currentTimeMillis());
        result.put("report_instance_id", reportInstanceId);
        result.put("status", "ACTIVE");
        result.put("gifted_followup_remaining", 1);

        return ApiResponse.success(result);
    }

    // ============================================================
    // 询价
    // ============================================================

    /**
     * POST /v1/report-followup/quotes
     * 询价：确定权益来源和价格
     */
    @PostMapping("/quotes")
    public ApiResponse<Map<String, Object>> requestQuote(
            @RequestHeader("Authorization") String authHeader,
            @RequestBody QuoteRequestDTO dto) {

        String userId = extractUserId(authHeader);

        // Precheck
        String blockReason = followupService.precheck(dto, userId);
        if (blockReason != null) {
            Map<String, Object> blockResp = new HashMap<>();
            blockResp.put("passed", false);
            blockResp.put("request_status", "BLOCKED_PRECHARGE");
            blockResp.put("block_reason", blockReason);
            blockResp.put("message", getBlockMessage(blockReason));
            Map<String, Object> blockEntitlement = new HashMap<>();
            blockEntitlement.put("source", "NONE");
            blockEntitlement.put("ledger_status", "NOT_REQUIRED");
            blockEntitlement.put("consumed", false);
            blockEntitlement.put("star_cost", 0);
            blockResp.put("entitlement", blockEntitlement);
            return ApiResponse.success(blockResp);
        }

        // 询价
        Map<String, Object> quote = followupService.quote(dto, userId);
        return ApiResponse.success(quote);
    }

    // ============================================================
    // 提交追问请求
    // ============================================================

    /**
     * POST /v1/report-followup/requests
     * 提交正式追问请求
     */
    @PostMapping("/requests")
    public ApiResponse<Map<String, Object>> submitFollowupRequest(
            @RequestHeader("Authorization") String authHeader,
            @RequestBody FollowupRequestDTO dto) {

        String userId = extractUserId(authHeader);

        try {
            Map<String, Object> result = followupService.submit(dto, userId);
            return ApiResponse.success(result);
        } catch (IllegalArgumentException e) {
            return ApiResponse.error(400, e.getMessage());
        } catch (Exception e) {
            log.error("提交追问请求失败", e);
            return ApiResponse.error(500, "INTERNAL_ERROR");
        }
    }

    /**
     * GET /v1/report-followup/requests/{request_id}
     * 查询请求状态（用于轮询/恢复断线）
     */
    @GetMapping("/requests/{request_id}")
    public ApiResponse<Map<String, Object>> getRequestStatus(
            @RequestHeader("Authorization") String authHeader,
            @PathVariable("request_id") String requestId) {

        String userId = extractUserId(authHeader);

        // 先查缓存（缓存失败不影响主流程）
        String cacheKey = RedisService.KEY_FOLLOWUP_REQUEST + requestId;
        try {
            Map<String, Object> cached = (Map<String, Object>) redisService.getObject(cacheKey);
            if (cached != null) {
                return ApiResponse.success(cached);
            }
        } catch (Exception e) {
            log.warn("Redis缓存读取失败，跳过缓存: {}", e.getMessage());
        }

        return ApiResponse.error(404, "REQUEST_NOT_FOUND");
    }

    // ============================================================
    // 工具方法
    // ============================================================

    /**
     * 从 Authorization header 提取用户ID
     * 实际项目中应解析 JWT token
     */
    private String extractUserId(String authHeader) {
        // 简化实现：直接返回测试用户
        // 实际项目中应解析 JWT token 获取 userId
        if (authHeader != null && authHeader.startsWith("Bearer ")) {
            // TODO: 解析 JWT
            return "u_001";
        }
        return "u_001"; // 默认测试用户
    }

    /**
     * 获取阻塞原因对应的用户文案
     */
    private String getBlockMessage(String reason) {
        Map<String, String> messages = new HashMap<>();
        messages.put("REPORT_REQUIRED", "请先选择一份要追问的报告");
        messages.put("REPORT_ACCESS_DENIED", "你没有权限访问该报告");
        messages.put("REPORT_UNAVAILABLE", "该报告暂不可用");
        messages.put("QUESTION_REQUIRED", "请输入你的问题");
        messages.put("MULTI_QUESTION_OVERLOAD", "这次包含了多个独立问题，请先选择最想问的一个");
        messages.put("SESSION_REPORT_MISMATCH", "当前会话绑定的报告与请求不一致");
        messages.put("ENTITLEMENT_INSUFFICIENT", "当前次数或星印不足，请选择可用权益后再试");
        return messages.getOrDefault(reason, "请求未通过校验");
    }
}
