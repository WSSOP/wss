package cn.dct.zpback.controller;

import cn.dct.zpback.constant.HealthReportConstants;
import cn.dct.zpback.dto.ApiResponse;
import cn.dct.zpback.entity.health.HealthReportInstance;
import cn.dct.zpback.service.health.HealthReportService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.*;

import java.util.*;

/**
 * 健康深度报告 API 控制器
 * 路径前缀：/api/v1/health-report
 */
@RestController
@RequestMapping("/v1/health-report")
@RequiredArgsConstructor
@Slf4j
public class HealthReportController {

    private final HealthReportService healthReportService;

    // ============================================================
    // 报告生成
    // ============================================================

    /**
     * POST /v1/health-report/generate
     * 生成健康深度解析报告
     *
     * 请求体:
     * {
     *   "chart_profile_id": "chart_default_001",
     *   "chart_data": {
     *     "D1": { "ascendant": {...}, "planets": {...}, "houses": {...} },
     *     "D9": { "planets": {...} },
     *     "D27": { "planets": {...} },
     *     "D30": { "planets": {...} },
     *     "Shadbala": { "SUN": {...}, ... },
     *     "Nakshatra": { "SUN": {...}, ... },
     *     "Dasha": { "periods": [...] },
     *     "HealthYoga": { "yogas": [...] }
     *   },
     *   "report_type": "HEALTH_WELLBEING_REPORT"
     * }
     */
    @PostMapping("/generate")
    public ApiResponse<HealthReportInstance> generateReport(
            @RequestHeader(value = "Authorization", required = false) String authHeader,
            @RequestBody Map<String, Object> request) {

        String userId = extractUserId(authHeader);

        // 1. 提取并验证参数
        String chartProfileId = (String) request.getOrDefault("chart_profile_id", "");
        Map<String, Object> chartData = (Map<String, Object>) request.getOrDefault("chart_data", Collections.emptyMap());
        String reportType = (String) request.getOrDefault("report_type", HealthReportConstants.REPORT_TYPE);

        if (chartData.isEmpty()) {
            return ApiResponse.error(400, "星盘数据不能为空");
        }

        // 2. 验证报告类型
        if (!HealthReportConstants.REPORT_TYPE.equals(reportType)) {
            return ApiResponse.error(400, "不支持的报告类型: " + reportType);
        }

        // 3. 生成报告
        log.info("开始生成健康深度报告: userId={}, chartProfileId={}", userId, chartProfileId);
        HealthReportInstance report = healthReportService.generateReport(userId, chartData, chartProfileId);

        // 4. 检查结果
        if (HealthReportConstants.STATUS_FAILED.equals(report.getReportStatus())) {
            return ApiResponse.error(500, "报告生成失败");
        }

        log.info("健康深度报告生成完成: reportId={}", report.getReportInstanceId());
        return ApiResponse.success(report);
    }

    /**
     * GET /v1/health-report/{reportId}
     * 获取健康报告详情
     */
    @GetMapping("/{reportId}")
    public ApiResponse<HealthReportInstance> getReport(
            @RequestHeader(value = "Authorization", required = false) String authHeader,
            @PathVariable String reportId) {

        // TODO: 从数据库获取报告
        return ApiResponse.error(404, "报告不存在");
    }

    /**
     * GET /v1/health-report/list
     * 获取用户健康报告列表
     */
    @GetMapping("/list")
    public ApiResponse<Map<String, Object>> getReportList(
            @RequestHeader(value = "Authorization", required = false) String authHeader,
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "20") int limit) {

        String userId = extractUserId(authHeader);

        // TODO: 从数据库获取报告列表
        Map<String, Object> result = new HashMap<>();
        result.put("items", Collections.emptyList());
        result.put("total", 0);
        result.put("page", page);
        result.put("limit", limit);

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
            return "user_" + authHeader.hashCode();
        }
        return "anonymous";
    }
}
