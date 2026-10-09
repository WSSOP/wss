package cn.dct.zpback.controller;

import cn.dct.zpback.dto.ApiResponse;
import cn.dct.zpback.entity.Chart;
import cn.dct.zpback.service.ChartService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;

import java.util.*;

/**
 * 占星数据 API 控制器
 */
@RestController
@RequestMapping("/v1/astro")
@Slf4j
public class AstroController {

    @Autowired
    private ChartService chartService;

    /**
     * GET /v1/astro/charts
     * 获取用户保存的星盘列表
     */
    @GetMapping("/charts")
    public ApiResponse<Map<String, Object>> getUserCharts(
            @RequestHeader(value = "Authorization", required = false) String authHeader) {

        String userId = extractUserId(authHeader);
        List<Map<String, Object>> charts = new ArrayList<>();
        try {
            for (Chart chart : chartService.getUserCharts(userId)) {
                charts.add(toChartMap(chart, false));
            }
        } catch (Exception e) {
            log.error("获取星盘列表失败: userId={}, error={}", userId, e.getMessage(), e);
        }

        Map<String, Object> result = new HashMap<>();
        result.put("items", charts);
        result.put("total", charts.size());
        return ApiResponse.success(result);
    }

    /**
     * GET /v1/astro/charts/{chartId}
     * 获取星盘详情
     */
    @GetMapping("/charts/{chartId}")
    public ApiResponse<Map<String, Object>> getChartDetail(
            @RequestHeader(value = "Authorization", required = false) String authHeader,
            @PathVariable String chartId) {

        Chart chart = chartService.getChartById(chartId);
        if (chart == null) {
            return ApiResponse.error(404, "星盘不存在");
        }
        return ApiResponse.success(toChartMap(chart, true));
    }

    private Map<String, Object> toChartMap(Chart chart, boolean withCoordinates) {
        Map<String, Object> item = new HashMap<>();
        item.put("id", chart.getChartId());
        item.put("chartId", chart.getChartId());
        item.put("name", chart.getName());
        item.put("gender", chart.getGender());
        item.put("birthDate", chart.getBirthDate());
        item.put("birthTime", chart.getBirthTime());
        item.put("birthPlace", chart.getBirthPlace());
        if (withCoordinates) {
            item.put("latitude", chart.getLatitude());
            item.put("longitude", chart.getLongitude());
            item.put("timezone", chart.getTimezone());
        }
        return item;
    }

    private String extractUserId(String authHeader) {
        if (authHeader != null && authHeader.startsWith("Bearer ")) {
            String token = authHeader.substring(7);
            if (token.startsWith("token_")) {
                return "user_" + token.substring(6);
            }
            if (token.startsWith("mock_token_")) {
                return "user_" + Math.abs(token.hashCode());
            }
        }
        return "anonymous";
    }
}
