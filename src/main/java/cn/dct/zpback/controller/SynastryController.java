package cn.dct.zpback.controller;

import cn.dct.zpback.dto.ApiResponse;
import cn.dct.zpback.entity.Chart;
import cn.dct.zpback.service.ChartService;
import cn.dct.zpback.service.synastry.SynastryReportService;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.*;

import java.util.*;

/**
 * 双人合盘 Skill 控制器
 * 符合 SYN-STEP 3/4/5/6 SEALED 规范
 * 路径前缀：/api/v1/synastry-report
 */
@RestController
@RequestMapping("/v1/synastry-report")
@RequiredArgsConstructor
@Slf4j
public class SynastryController {

    private final SynastryReportService synastryReportService;
    private final ChartService chartService;

    /**
     * POST /v1/synastry-report/generate
     * 生成双人关系深度报告
     *
     * 请求格式：
     * {
     *   "chart_a": { ... 人物A的星盘数据 ... },
     *   "chart_b": { ... 人物B的星盘数据 ... },
     *   "chart_a_params": { ... 人物A的出生参数 ... },
     *   "chart_b_params": { ... 人物B的出生参数 ... },
     *   "relationship_type": "romantic",
     *   "relationship_status": "STABLE",
     *   "focus_area": "我们的沟通模式有什么特点？"
     * }
     */
    @PostMapping("/generate")
    public ApiResponse<Map<String, Object>> generateReport(
            @RequestHeader(value = "Authorization", required = false) String authHeader,
            @RequestBody Map<String, Object> request) {

        String userId = extractUserId(authHeader);

        log.info("双人合盘报告生成请求: userId={}", userId);

        Map<String, Object> chartAData = (Map<String, Object>) request.getOrDefault("chart_a", Collections.emptyMap());
        Map<String, Object> chartBData = (Map<String, Object>) request.getOrDefault("chart_b", Collections.emptyMap());
        Map<String, Object> chartAParams = (Map<String, Object>) request.getOrDefault("chart_a_params", Collections.emptyMap());
        Map<String, Object> chartBParams = (Map<String, Object>) request.getOrDefault("chart_b_params", Collections.emptyMap());
        String relationshipType = (String) request.getOrDefault("relationship_type", "OTHER");
        String relationshipStatus = (String) request.getOrDefault("relationship_status", "STABLE");
        String focusArea = (String) request.getOrDefault("focus_area", "");

        // 如果传入了 chart_id，从数据库获取
        if (request.containsKey("chart_id_a")) {
            String chartIdA = (String) request.get("chart_id_a");
            Chart chartA = chartService.getChartById(chartIdA);
            if (chartA != null) {
                chartAParams = chartToParams(chartA);
                if (chartA.getChartData() != null && !chartA.getChartData().isEmpty()) {
                    try {
                        ObjectMapper mapper = new ObjectMapper();
                        chartAData = mapper.readValue(chartA.getChartData(), Map.class);
                    } catch (Exception e) {
                        log.warn("解析人物A星盘数据JSON失败: {}", e.getMessage());
                    }
                }
            }
        }

        if (request.containsKey("chart_id_b")) {
            String chartIdB = (String) request.get("chart_id_b");
            Chart chartB = chartService.getChartById(chartIdB);
            if (chartB != null) {
                chartBParams = chartToParams(chartB);
                if (chartB.getChartData() != null && !chartB.getChartData().isEmpty()) {
                    try {
                        ObjectMapper mapper = new ObjectMapper();
                        chartBData = mapper.readValue(chartB.getChartData(), Map.class);
                    } catch (Exception e) {
                        log.warn("解析人物B星盘数据JSON失败: {}", e.getMessage());
                    }
                }
            }
        }

        Map<String, Object> report = synastryReportService.generateReport(
                userId, chartAData, chartBData, chartAParams, chartBParams,
                relationshipType, relationshipStatus, focusArea);

        if ("FAILED".equals(report.get("status"))) {
            return ApiResponse.error(500, (String) report.getOrDefault("error", "报告生成失败"));
        }

        return ApiResponse.success(report);
    }

    /**
     * GET /v1/synastry-report/{reportId}
     * 获取双人关系报告详情
     */
    @GetMapping("/{reportId}")
    public ApiResponse<Map<String, Object>> getReport(
            @RequestHeader(value = "Authorization", required = false) String authHeader,
            @PathVariable String reportId) {

        // TODO: 从缓存或数据库获取报告
        Map<String, Object> report = new HashMap<>();
        report.put("report_id", reportId);
        report.put("status", "COMPLETED");
        return ApiResponse.success(report);
    }

    /**
     * GET /v1/synastry-report/list
     * 获取用户报告列表
     */
    @GetMapping("/list")
    public ApiResponse<Map<String, Object>> getReportList(
            @RequestHeader(value = "Authorization", required = false) String authHeader) {

        Map<String, Object> result = new HashMap<>();
        result.put("items", Collections.emptyList());
        result.put("total", 0);
        return ApiResponse.success(result);
    }

    /**
     * GET /v1/synastry-report/charts
     * 获取用户保存的星盘列表（供双人合盘选择）
     */
    @GetMapping("/charts")
    public ApiResponse<List<Chart>> getUserCharts(
            @RequestHeader(value = "Authorization", required = false) String authHeader) {

        String userId = extractUserId(authHeader);
        List<Chart> charts = chartService.getUserCharts(userId);
        return ApiResponse.success(charts);
    }

    private Map<String, Object> chartToParams(Chart chart) {
        Map<String, Object> params = new HashMap<>();
        params.put("name", chart.getName());
        params.put("gender", chart.getGender());
        params.put("birthDate", chart.getBirthDate());
        params.put("birthTime", chart.getBirthTime());
        params.put("birthPlace", chart.getBirthPlace());
        params.put("latitude", chart.getLatitude());
        params.put("longitude", chart.getLongitude());
        return params;
    }

    private String extractUserId(String authHeader) {
        if (authHeader != null && authHeader.startsWith("Bearer ")) {
            String token = authHeader.substring(7);
            if (token.startsWith("token_")) {
                return "user_" + token.substring(6);
            }
            return "user_" + Math.abs(token.hashCode());
        }
        return "anonymous";
    }
}
