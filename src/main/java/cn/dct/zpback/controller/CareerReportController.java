package cn.dct.zpback.controller;

import cn.dct.zpback.constant.EducationReportConstants;
import cn.dct.zpback.constant.LifePatternGrowthConstants;
import cn.dct.zpback.constant.NatalChildrenConstants;
import cn.dct.zpback.dto.ApiResponse;
import cn.dct.zpback.entity.career.CareerReportInstance;
import cn.dct.zpback.service.career.CareerReportService;
import cn.dct.zpback.service.career.CareerRoleClusterService;
import cn.dct.zpback.service.career.EducationLearningReportService;
import cn.dct.zpback.service.career.LifePatternGrowthReportService;
import cn.dct.zpback.service.career.NatalChildrenReportService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.*;

import java.util.*;

/**
 * AI深度报告 API 控制器
 * 支持多种报告类型：事业深度报告、人生课题深度报告、子女与养育深度报告等
 * 路径前缀：/api/v1/career-report
 */
@RestController
@RequestMapping("/v1/career-report")
@RequiredArgsConstructor
@Slf4j
public class CareerReportController {

    private final CareerReportService careerReportService;
    private final CareerRoleClusterService roleClusterService;
    private final LifePatternGrowthReportService lifePatternGrowthReportService;
    private final NatalChildrenReportService natalChildrenReportService;
    private final EducationLearningReportService educationLearningReportService;

    // ============================================================
    // 报告生成
    // ============================================================

    /**
     * POST /v1/career-report/generate
     * 生成AI深度报告
     * 支持报告类型：CAREER(事业深度报告), LIFE_PATTERN_GROWTH(人生课题深度报告)
     */
    @PostMapping("/generate")
    public ApiResponse<CareerReportInstance> generateReport(
            @RequestHeader(value = "Authorization", required = false) String authHeader,
            @RequestBody Map<String, Object> request) {

        String userId = extractUserId(authHeader);
        Map<String, Object> chartData = (Map<String, Object>) request.getOrDefault("chart_data", Collections.emptyMap());
        Map<String, Object> birthParams = (Map<String, Object>) request.getOrDefault("birth_params", Collections.emptyMap());
        String chartProfileId = (String) request.getOrDefault("chart_profile_id", "");

        // 根据报告类型路由到对应的服务
        String reportType = (String) request.getOrDefault("report_type", "CAREER");
        CareerReportInstance report;

        if (LifePatternGrowthConstants.REPORT_TYPE.equals(reportType)) {
            // 人生课题深度报告：支持通过 birth_params 获取真实星盘数据
            if (chartData.isEmpty() && birthParams.isEmpty()) {
                return ApiResponse.error(400, "星盘数据不能为空");
            }
            log.info("生成人生课题深度报告: userId={}, chartProfileId={}, hasBirthParams={}",
                    userId, chartProfileId, !birthParams.isEmpty());
            report = lifePatternGrowthReportService.generateReport(userId, chartData, chartProfileId, birthParams);
        } else if (NatalChildrenConstants.REPORT_TYPE.equals(reportType)) {
            // 子女与养育深度报告：需要 D1+D7，必须提供出生参数
            if (birthParams.isEmpty()) {
                return ApiResponse.error(400, "子女报告需要完整出生时间（用于计算 D7 分盘）");
            }
            log.info("生成子女与养育深度报告: userId={}, chartProfileId={}, hasBirthParams={}",
                    userId, chartProfileId, !birthParams.isEmpty());
            report = natalChildrenReportService.generateReport(userId, chartData, chartProfileId, birthParams);
        } else if (EducationReportConstants.REPORT_TYPE.equals(reportType)) {
            // 学业深度解析报告：需要 D1+D24+Dasha，必须提供出生参数
            if (birthParams.isEmpty()) {
                return ApiResponse.error(400, "学业报告需要完整出生时间（用于计算 D24 分盘与 Dasha）");
            }
            log.info("生成学业深度解析报告: userId={}, chartProfileId={}, hasBirthParams={}",
                    userId, chartProfileId, !birthParams.isEmpty());
            report = educationLearningReportService.generateReport(userId, chartData, chartProfileId, birthParams);
        } else {
            // 事业深度报告（默认）
            if (chartData.isEmpty()) {
                return ApiResponse.error(400, "星盘数据不能为空");
            }
            log.info("生成事业深度报告: userId={}", userId);
            report = careerReportService.generateReport(userId, chartData);
        }

        if ("FAILED".equals(report.getReportStatus())) {
            return ApiResponse.error(500, "报告生成失败");
        }
        // FAILED_DATA 等状态原样返回：reportContent 内含阻断原因与校正指引

        return ApiResponse.success(report);
    }

    /**
     * GET /v1/career-report/{reportId}
     * 获取报告详情
     */
    @GetMapping("/{reportId}")
    public ApiResponse<CareerReportInstance> getReport(
            @RequestHeader(value = "Authorization", required = false) String authHeader,
            @PathVariable String reportId) {

        // TODO: 从数据库获取报告
        return ApiResponse.error(404, "报告不存在");
    }

    /**
     * GET /v1/career-report/list
     * 获取用户报告列表
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
    // 角色集群与职业示例
    // ============================================================

    /**
     * GET /v1/career-report/role-clusters
     * 获取所有角色集群
     */
    @GetMapping("/role-clusters")
    public ApiResponse<Map<String, Object>> getRoleClusters() {
        Map<String, Object> result = new HashMap<>();
        result.put("items", roleClusterService.getAllRoleClusters());
        return ApiResponse.success(result);
    }

    /**
     * POST /v1/career-report/match-role-clusters
     * 根据标签匹配角色集群
     */
    @PostMapping("/match-role-clusters")
    public ApiResponse<Map<String, Object>> matchRoleClusters(@RequestBody Map<String, Object> request) {
        @SuppressWarnings("unchecked")
        List<String> tags = (List<String>) request.getOrDefault("tags", Collections.emptyList());

        Map<String, Object> result = new HashMap<>();
        result.put("items", roleClusterService.matchRoleClusters(tags));
        return ApiResponse.success(result);
    }

    /**
     * POST /v1/career-report/match-career-examples
     * 根据标签匹配职业示例
     */
    @PostMapping("/match-career-examples")
    public ApiResponse<Map<String, Object>> matchCareerExamples(@RequestBody Map<String, Object> request) {
        @SuppressWarnings("unchecked")
        List<String> tags = (List<String>) request.getOrDefault("tags", Collections.emptyList());

        Map<String, Object> result = new HashMap<>();
        result.put("items", roleClusterService.matchCareerExamples(tags));
        return ApiResponse.success(result);
    }

    // ============================================================
    // 报告配置
    // ============================================================

    /**
     * GET /v1/career-report/config
     * 获取报告配置
     */
    @GetMapping("/config")
    public ApiResponse<Map<String, Object>> getConfig() {
        Map<String, Object> config = new HashMap<>();
        config.put("report_type", "CAREER");
        config.put("skill_version", "V1");
        config.put("schema_version", "CAREER_V1");
        config.put("min_core_findings", 3);
        config.put("max_core_findings", 5);
        config.put("max_timing_windows", 5);
        config.put("max_recommendations", 5);
        config.put("timing_years_ahead", 5);

        return ApiResponse.success(config);
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
