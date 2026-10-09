package cn.dct.zpback.controller;

import cn.dct.zpback.dto.ApiResponse;
import cn.dct.zpback.security.BearerUserIdResolver;
import cn.dct.zpback.service.PsychologicalAssessmentService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

/**
 * 问星渡｜心理测评中心
 *
 * 路径前缀：/api/v1/psychological（context-path 已含 /api，实际对外为 /v1/psychological）
 *
 * 接口清单：
 * - GET  /catalog              测评目录列表（可选 category 过滤）
 * - GET  /catalog/{slug}       单题测评详情（含题目+选项，一次返回）
 * - POST /start/{slug}         开始答题（创建结果记录，返回 result_id）
 * - POST /submit/{resultId}    提交答案
 * - GET  /my/results           我的测评历史
 * - GET  /my/results/{resultId} 单次结果详情
 */
@RestController
@RequestMapping("/v1/psychological")
@RequiredArgsConstructor
@Slf4j
public class PsychologicalAssessmentController {

    private final PsychologicalAssessmentService service;
    private final BearerUserIdResolver bearer;

    /**
     * 测评目录：所有已发布的测评，可按 category 过滤。
     *
     * 未登录也可访问——测评本身不需要登录。
     */
    @GetMapping("/catalog")
    public ApiResponse<List<Map<String, Object>>> listCatalog(
            @RequestParam(required = false) String category,
            @RequestHeader(value = "Authorization", required = false) String auth) {
        return ApiResponse.success(service.listCatalog(category));
    }

    /**
     * 单题测评详情（题目 + 选项）。
     *
     * 前端在用户进入测评详情页时调用，一次性返回全部题目与选项，避免逐题请求。
     */
    @GetMapping("/catalog/{slug}")
    public ApiResponse<Map<String, Object>> getDetail(
            @PathVariable("slug") String slug,
            @RequestHeader(value = "Authorization", required = false) String auth) {
        return ApiResponse.success(service.getDetail(slug));
    }

    /**
     * 开始答题：创建一条空结果记录，返回 result_id。
     *
     * V1 允许未登录用户开始答题（匿名作答），但只有登录后提交才会绑定到用户。
     */
    @PostMapping("/start/{slug}")
    public ApiResponse<Map<String, Object>> startAssessment(
            @PathVariable("slug") String slug,
            @RequestHeader(value = "Authorization", required = false) String auth) {
        String userId = bearer.resolve(auth);
        return ApiResponse.success(service.startAssessment(userId, slug));
    }

    /**
     * 提交答案。
     *
     * 请求体：{ "answers": [{ "question_id": "...", "option_id": "..." }] }
     */
    @PostMapping("/submit/{resultId}")
    public ApiResponse<Map<String, Object>> submitAnswers(
            @PathVariable("resultId") String resultId,
            @RequestBody Map<String, Object> body,
            @RequestHeader(value = "Authorization", required = false) String auth) {
        String userId = bearer.resolve(auth);
        @SuppressWarnings("unchecked")
        List<Map<String, String>> answers = (List<Map<String, String>>) body.get("answers");
        return ApiResponse.success(service.submitAnswers(resultId, userId, answers));
    }

    /**
     * 我的测评历史（仅登录用户可见）。
     *
     * 未登录时 bearer.resolve 返回 anonymous，service 会降级为空列表。
     */
    @GetMapping("/my/results")
    public ApiResponse<List<Map<String, Object>>> listMyResults(
            @RequestHeader(value = "Authorization", required = false) String auth) {
        String userId = bearer.resolve(auth);
        return ApiResponse.success(service.listMyResults(userId));
    }

    /**
     * 单次结果详情（含逐题作答）。
     *
     * 仅本人可查看，越权直接拒绝。
     */
    @GetMapping("/my/results/{resultId}")
    public ApiResponse<Map<String, Object>> getResultDetail(
            @PathVariable("resultId") String resultId,
            @RequestHeader(value = "Authorization", required = false) String auth) {
        String userId = bearer.resolve(auth);
        return ApiResponse.success(service.getResultDetail(resultId, userId));
    }
}
