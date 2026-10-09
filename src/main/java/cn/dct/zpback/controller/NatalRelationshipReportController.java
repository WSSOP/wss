package cn.dct.zpback.controller;

import cn.dct.zpback.constant.NatalRelationshipConstants;
import cn.dct.zpback.dto.ApiResponse;
import cn.dct.zpback.entity.relationship.NatalRelationshipReportInstance;
import cn.dct.zpback.service.relationship.NatalRelationshipReportService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.*;

import java.util.*;

/**
 * 单人婚恋深度报告 API 控制器
 * 路径前缀：/api/v1/relationship-report
 */
@RestController
@RequestMapping("/v1/relationship-report")
@RequiredArgsConstructor
@Slf4j
public class NatalRelationshipReportController {

    private final NatalRelationshipReportService relationshipReportService;

    /**
     * POST /v1/relationship-report/generate
     * 生成单人婚恋深度解析报告
     */
    @PostMapping("/generate")
    public ApiResponse<NatalRelationshipReportInstance> generateReport(
            @RequestHeader(value = "Authorization", required = false) String authHeader,
            @RequestBody Map<String, Object> request) {

        String userId = extractUserId(authHeader);

        String chartProfileId = (String) request.getOrDefault("chart_profile_id", "");
        Map<String, Object> chartData = (Map<String, Object>) request.getOrDefault("chart_data", Collections.emptyMap());
        Map<String, Object> birthParams = (Map<String, Object>) request.getOrDefault("birth_params", Collections.emptyMap());
        String reportType = (String) request.getOrDefault("report_type", NatalRelationshipConstants.REPORT_TYPE);

        if (chartData == null) {
            chartData = Collections.emptyMap();
        }

        if (birthParams == null) {
            birthParams = Collections.emptyMap();
        }

        if (!NatalRelationshipConstants.REPORT_TYPE.equals(reportType)) {
            return ApiResponse.error(400, "不支持的报告类型: " + reportType);
        }

        log.info("开始生成单人婚恋深度报告: userId={}, chartProfileId={}, hasBirthParams={}", userId, chartProfileId, !birthParams.isEmpty());
        NatalRelationshipReportInstance report = relationshipReportService.generateReport(userId, chartData, chartProfileId, birthParams);

        if (NatalRelationshipConstants.STATUS_FAILED.equals(report.getReportStatus())) {
            String reason = report.getFailureReason() != null ? report.getFailureReason() : "报告生成失败，请稍后重试";
            log.warn("单人婚恋深度报告生成失败: chartProfileId={}, reason={}", chartProfileId, reason);
            return ApiResponse.error(422, reason);
        }

        log.info("单人婚恋深度报告生成完成: reportId={}", report.getReportInstanceId());
        return ApiResponse.success(report);
    }

    /**
     * GET /v1/relationship-report/{reportId}
     * 获取婚恋报告详情
     */
    @GetMapping("/{reportId}")
    public ApiResponse<NatalRelationshipReportInstance> getReport(
            @RequestHeader(value = "Authorization", required = false) String authHeader,
            @PathVariable String reportId) {

        log.info("获取婚恋报告详情: reportId={}", reportId);

        NatalRelationshipReportInstance report = relationshipReportService.getReportById(reportId);

        if (report == null) {
            return ApiResponse.error(404, "报告不存在");
        }

        return ApiResponse.success(report);
    }

    /**
     * GET /v1/relationship-report/list
     * 获取用户婚恋报告列表
     */
    @GetMapping("/list")
    public ApiResponse<Map<String, Object>> getReportList(
            @RequestHeader(value = "Authorization", required = false) String authHeader,
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "20") int limit) {

        String userId = extractUserId(authHeader);

        int offset = (page - 1) * limit;
        List<NatalRelationshipReportInstance> reports = relationshipReportService.getUserReports(userId);
        int total = relationshipReportService.getReportCount(userId);

        Map<String, Object> result = new HashMap<>();
        result.put("items", reports);
        result.put("total", total);
        result.put("page", page);
        result.put("limit", limit);

        return ApiResponse.success(result);
    }

    /**
     * 从 Authorization header 中提取用户ID
     * 格式: token_<phone> 或 mock_token_<phone>
     */
    private String extractUserId(String authHeader) {
        if (authHeader != null && authHeader.startsWith("Bearer ")) {
            String token = authHeader.substring(7);
            // 提取手机号作为稳定 userId
            if (token.startsWith("token_")) {
                return "user_" + token.substring(6);
            }
            if (token.startsWith("mock_token_")) {
                // 兼容旧格式，使用 hashCode
                return "user_" + Math.abs(token.hashCode());
            }
        }
        return "anonymous";
    }
}
