package cn.dct.zpback.controller;

import cn.dct.zpback.dto.ApiResponse;
import cn.dct.zpback.entity.Chart;
import cn.dct.zpback.service.ChartService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

/**
 * 星盘控制器
 */
@RestController
@RequestMapping("/v1/chart")
@RequiredArgsConstructor
@Slf4j
public class ChartController {

    private final ChartService chartService;

    /**
     * POST /v1/chart
     * 创建星盘
     */
    @PostMapping
    public ApiResponse<Chart> createChart(
            @RequestHeader(value = "Authorization", required = false) String authHeader,
            @RequestBody Map<String, Object> request) {

        String userId = extractUserId(authHeader);
        String name = (String) request.getOrDefault("name", "");
        String gender = (String) request.getOrDefault("gender", "male");
        String birthDate = (String) request.getOrDefault("birthDate", "");
        String birthTime = (String) request.getOrDefault("birthTime", "");
        String birthPlace = (String) request.getOrDefault("birthPlace", "");

        Double latitude = null;
        Double longitude = null;
        if (request.get("latitude") instanceof Number) {
            latitude = ((Number) request.get("latitude")).doubleValue();
        }
        if (request.get("longitude") instanceof Number) {
            longitude = ((Number) request.get("longitude")).doubleValue();
        }

        log.info("创建星盘: userId={}, name={}, gender={}, birthDate={}, birthTime={}, birthPlace={}, lat={}, lng={}",
                userId, name, gender, birthDate, birthTime, birthPlace, latitude, longitude);

        if (name.isEmpty() || birthDate.isEmpty()) {
            return ApiResponse.error(400, "姓名和出生日期不能为空");
        }

        Chart chart = chartService.createChart(userId, name, gender, birthDate, birthTime, birthPlace, latitude, longitude);
        return ApiResponse.success(chart);
    }

    /**
     * GET /v1/chart/list
     * 获取用户的所有星盘
     */
    @GetMapping("/list")
    public ApiResponse<List<Chart>> getChartList(
            @RequestHeader(value = "Authorization", required = false) String authHeader) {

        String userId = extractUserId(authHeader);
        List<Chart> charts = chartService.getUserCharts(userId);
        log.info("获取星盘列表: userId={}, count={}", userId, charts.size());
        return ApiResponse.success(charts);
    }

    /**
     * GET /v1/chart/default
     * 获取用户的默认星盘
     */
    @GetMapping("/default")
    public ApiResponse<Chart> getDefaultChart(
            @RequestHeader(value = "Authorization", required = false) String authHeader) {

        String userId = extractUserId(authHeader);
        Chart chart = chartService.getDefaultChart(userId);
        return ApiResponse.success(chart);
    }

    /**
     * GET /v1/chart/{chartId}
     * 获取星盘详情
     */
    @GetMapping("/{chartId}")
    public ApiResponse<Chart> getChart(
            @RequestHeader(value = "Authorization", required = false) String authHeader,
            @PathVariable String chartId) {

        Chart chart = chartService.getChartById(chartId);
        if (chart == null) {
            return ApiResponse.error(404, "星盘不存在");
        }
        return ApiResponse.success(chart);
    }

    /**
     * PUT /v1/chart/{chartId}
     * 更新星盘
     */
    @PutMapping("/{chartId}")
    public ApiResponse<Chart> updateChart(
            @RequestHeader(value = "Authorization", required = false) String authHeader,
            @PathVariable String chartId,
            @RequestBody Map<String, Object> request) {

        Chart chart = chartService.getChartById(chartId);
        if (chart == null) {
            return ApiResponse.error(404, "星盘不存在");
        }

        if (request.containsKey("name")) chart.setName((String) request.get("name"));
        if (request.containsKey("gender")) chart.setGender((String) request.get("gender"));
        if (request.containsKey("birthDate")) chart.setBirthDate((String) request.get("birthDate"));
        if (request.containsKey("birthTime")) chart.setBirthTime((String) request.get("birthTime"));
        if (request.containsKey("birthPlace")) chart.setBirthPlace((String) request.get("birthPlace"));
        if (request.containsKey("latitude") && request.get("latitude") instanceof Number) {
            chart.setLatitude(((Number) request.get("latitude")).doubleValue());
        }
        if (request.containsKey("longitude") && request.get("longitude") instanceof Number) {
            chart.setLongitude(((Number) request.get("longitude")).doubleValue());
        }

        chartService.updateChart(chart);
        return ApiResponse.success(chart);
    }

    /**
     * DELETE /v1/chart/{chartId}
     * 删除星盘
     */
    @DeleteMapping("/{chartId}")
    public ApiResponse<Boolean> deleteChart(
            @RequestHeader(value = "Authorization", required = false) String authHeader,
            @PathVariable String chartId) {

        boolean deleted = chartService.deleteChart(chartId);
        if (!deleted) {
            return ApiResponse.error(404, "星盘不存在");
        }
        return ApiResponse.success(true);
    }

    /**
     * POST /v1/chart/{chartId}/default
     * 设置默认星盘
     */
    @PostMapping("/{chartId}/default")
    public ApiResponse<Boolean> setDefaultChart(
            @RequestHeader(value = "Authorization", required = false) String authHeader,
            @PathVariable String chartId) {

        String userId = extractUserId(authHeader);
        chartService.setDefaultChart(chartId, userId);
        return ApiResponse.success(true);
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
