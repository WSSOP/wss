package cn.dct.zpback.controller;

import cn.dct.zpback.dto.ApiResponse;
import cn.dct.zpback.exception.JewelryValidationException;
import cn.dct.zpback.exception.LearningValidationException;
import cn.dct.zpback.security.BearerUserIdResolver;
import cn.dct.zpback.service.jewelry.JewelryAdvisorService;
import cn.dct.zpback.service.jewelry.JewelryCatalogService;
import cn.dct.zpback.service.jewelry.JewelryChartSelectionService;
import cn.dct.zpback.service.jewelry.JewelryWaitlistService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.*;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 行星珠宝建议 API
 * 前缀：/v1/jewelry
 *
 * 契约要点：
 * - 问卷由后端下发，前端不得复制一份硬编码题库（§03_1）；
 * - 答案一律用 Option ID 提交，后端按 JSON 配置计分（§03_8）；
 * - Resolve 幂等：同一 answers + 同一 chart snapshot 得到相同排序；
 * - 校验失败返回 400，不生成 Completed 结果（§16）。
 */
@RestController
@RequestMapping("/v1/jewelry")
@RequiredArgsConstructor
@Slf4j
public class JewelryAdvisorController {

    private final JewelryAdvisorService advisorService;
    private final JewelryCatalogService catalogService;
    private final JewelryWaitlistService waitlistService;
    private final JewelryChartSelectionService chartSelectionService;
    private final cn.dct.zpback.security.BearerUserIdResolver bearerUserIdResolver;

    /**
     * GET /v1/jewelry/questionnaire?mode=SELF_ASSESSMENT
     * 下发问卷（主干 + 分支 + 必答标记 + 多选上限 + 分支触发关系）
     */
    @GetMapping("/questionnaire")
    public ApiResponse<Map<String, Object>> questionnaire(
            @RequestParam(defaultValue = "SELF_ASSESSMENT") String mode) {
        return ApiResponse.success(advisorService.buildQuestionnaire(mode));
    }

    /**
     * POST /v1/jewelry/validate
     * 仅校验不解析，用于前端在提交前自检
     */
    @PostMapping("/validate")
    public ApiResponse<Map<String, Object>> validate(
            @RequestBody Map<String, Object> request) {
        String mode = str(request, "assessment_mode", JewelryAdvisorService.MODE_SELF);
        Map<String, List<String>> answers = answersOf(request);
        Map<String, String> errors = advisorService.validateAnswers(mode, answers);

        Map<String, Object> data = new LinkedHashMap<>();
        data.put("valid", errors.isEmpty());
        data.put("violations", errors);
        // 前端取消某个目标时，据此同步移除失效的分支答案（§03_6）
        data.put("active_branches",
                advisorService.resolveActiveBranches(mode, answers.getOrDefault(
                        JewelryAdvisorService.MODE_SELF.equals(mode) ? "S01" : "G01", List.of())));
        return ApiResponse.success(data);
    }

    /**
     * POST /v1/jewelry/resolve
     * 全链路解析：ChartAdapter → Scorer → Gate → Modality → Ranker → 冻结结果
     */
    @PostMapping("/resolve")
    public ApiResponse<Map<String, Object>> resolve(
            @RequestBody Map<String, Object> request,
            @RequestHeader(value = "Authorization", required = false) String authHeader) {
        try {
            String mode = str(request, "assessment_mode", JewelryAdvisorService.MODE_SELF);
            Map<String, List<String>> answers = answersOf(request);
            String chartId = str(request, "chart_id", null);
            // 身份一律以 token 为准；body 里的 user_id 只在无 token 时兜底，
            // 否则前端可伪造他人 userId 订阅别人的缺货提醒
            String bodyUserId = str(request, "user_id", null);
            String tokenUserId = userIdOf(authHeader);
            boolean anon = tokenUserId == null || BearerUserIdResolver.ANONYMOUS.equals(tokenUserId);
            String userId = (anon && bodyUserId != null && !bodyUserId.isBlank()) ? bodyUserId : tokenUserId;
            String assessmentId = str(request, "assessment_id", null);

            Map<String, Object> result;
            if (chartId != null && !chartId.isBlank()) {
                // 主路径：出生资料由后端从 chart 表读取，前端不接触完整出生信息（01 §16）
                result = advisorService.resolveByChartId(
                        mode, answers, chartId, str(request, "subject_name", null), userId, assessmentId);
            } else {
                @SuppressWarnings("unchecked")
                Map<String, Object> birthParams =
                        (Map<String, Object>) request.getOrDefault("birth", Map.of());
                @SuppressWarnings("unchecked")
                Map<String, String> chartContext =
                        (Map<String, String>) request.getOrDefault("chart", Map.of());
                result = advisorService.resolve(mode, answers, birthParams, chartContext, userId, assessmentId);
            }
            return ApiResponse.success(result);
        } catch (JewelryValidationException e) {
            log.warn("行星珠宝参数校验失败: {}", e.getViolations());
            Map<String, Object> data = new LinkedHashMap<>();
            data.put("error_code", "ASSESSMENT_INVALID");
            data.put("violations", e.getViolations());
            return ApiResponse.error(400, "问卷未完成或答案不合法", data);
        }
    }

    // ============================================================
    // 商城匹配与补货订阅（05_主推次推与商城匹配 §4、§7；06_商品后台）
    // ============================================================

    /**
     * POST /v1/jewelry/catalog/match
     *
     * 只接受<b>已冻结</b>的推荐查询标签，不接受用户原始答案重算（07 §3）。
     * 库存与价格只影响同一方向内的商品顺序，不改变 planet rank。
     */
    @PostMapping("/catalog/match")
    public ApiResponse<Map<String, Object>> catalogMatch(
            @RequestBody Map<String, Object> request,
            @RequestHeader(value = "Authorization", required = false) String authHeader) {
        @SuppressWarnings("unchecked")
        Map<String, Object> queryTags = (Map<String, Object>) request.get("recommendation");
        if (queryTags == null) {
            return ApiResponse.error(400, "缺少已冻结的推荐方向",
                    Map.of("error_code", "PJA_RESULT_ALREADY_FROZEN"));
        }
        String planet = str(queryTags, "planet_code", null);
        if (planet == null) {
            return ApiResponse.error(400, "推荐方向缺少 planet_code",
                    Map.of("error_code", "PJA_RESULT_ALREADY_FROZEN"));
        }
        @SuppressWarnings("unchecked")
        List<String> allowed = (List<String>) queryTags.getOrDefault(
                "allowed_remedy_types", List.of());
        String rank = str(queryTags, "rank", "PRIMARY");
        Map<String, Object> match = catalogService.match(
                planet, allowed, rank, userIdOf(authHeader));
        match.put("cta_payload",
                catalogService.ctaForMode(rank, String.valueOf(match.get("match_state")),
                        str(request, "assessment_mode", JewelryAdvisorService.MODE_SELF)));
        return ApiResponse.success(match);
    }

    /**
     * GET /v1/jewelry/catalog/browse?planet=&remedy_type=
     *
     * <b>商品浏览页</b>：用户主动逛商城，不需要先做问卷、也不需要任何推荐方向。
     *
     * 与 {@link #catalogMatch} 的区别（务必分清，05 §1、§4、§5）：
     * <ul>
     *   <li>match 只接受<b>已冻结</b>的推荐标签，planet 必须命中；</li>
     *   <li>browse 不产出 rank / planet / recommendation 任何字段，
     *       返回体带 source=BROWSE 标记，无法被误当成一次推荐匹配；</li>
     *   <li>浏览页<b>不能</b>影响推荐方向，也就不会违反「推荐方向先锁定」。</li>
     * </ul>
     *
     * 仍然只返回 publish_status=PUBLISHED 的商品，且跳过被真实商品顶替的 SEED 示例。
     */
    @GetMapping("/catalog/browse")
    public ApiResponse<Map<String, Object>> catalogBrowse(
            @RequestParam(required = false) String planet,
            @RequestParam(name = "remedy_type", required = false) String remedyType,
            @RequestHeader(value = "Authorization", required = false) String authHeader) {
        return ApiResponse.success(catalogService.browse(
                blankToNull(planet), blankToNull(remedyType), userIdOf(authHeader)));
    }

    /**
     * GET /v1/jewelry/catalog/search 商品搜索 / 列表。
     *
     * <p>参数全部可选：keyword 为空即退化为列表页。
     * 返回结构与 browse 一致，前端可复用同一套字段映射。
     */
    @GetMapping("/catalog/search")
    public ApiResponse<Map<String, Object>> catalogSearch(
            @RequestParam(required = false) String keyword,
            @RequestParam(required = false) String planet,
            @RequestParam(name = "remedy_type", required = false) String remedyType,
            @RequestParam(required = false) String stock,
            @RequestParam(required = false) String sort,
            @RequestHeader(value = "Authorization", required = false) String authHeader) {
        return ApiResponse.success(catalogService.search(
                keyword, planet, remedyType, stock, sort, userIdOf(authHeader)));
    }

    /**
     * GET /v1/jewelry/catalog/products/{productId} 商品详情。
     *
     * <p>返回含 §5 CMS 契约字段 gallery_images[] / detail_blocks[]，
     * 商品介绍按后台结构化内容渲染，不写死在页面（§5.3）。
     */
    @GetMapping("/catalog/products/{productId}")
    public ApiResponse<Map<String, Object>> catalogProductDetail(
            @PathVariable String productId,
            @RequestHeader(value = "Authorization", required = false) String authHeader) {
        try {
            return ApiResponse.success(
                    catalogService.detail(productId, userIdOf(authHeader)));
        } catch (LearningValidationException e) {
            return ApiResponse.error(404, e.getMessage(),
                    Map.of("error_code", e.getCode()));
        }
    }

    private static String blankToNull(String s) {
        return (s == null || s.isBlank()) ? null : s;
    }

    /**
     * POST /v1/jewelry/catalog/skus/{skuId}/restock-subscriptions
     *
     * 幂等：同一 user + sku 重复提交返回既有 Active 订阅，期待人数不重复计入。
     */
    @PostMapping("/catalog/skus/{skuId}/restock-subscriptions")
    public ApiResponse<Map<String, Object>> subscribeRestock(
            @PathVariable String skuId,
            @RequestBody Map<String, Object> request,
            @RequestHeader(value = "Authorization", required = false) String authHeader) {
        try {
            Map<String, Object> result = waitlistService.subscribe(
                    userIdOf(authHeader),
                    skuId,
                    str(request, "source", "PRIMARY_RESULT"),
                    str(request, "assessment_id", null),
                    str(request, "recommendation_rank", null));
            return ApiResponse.success(result);
        } catch (JewelryValidationException e) {
            return ApiResponse.error(400, restockMessage(e), Map.of("violations", e.getViolations()));
        }
    }

    /** DELETE /v1/jewelry/catalog/skus/{skuId}/restock-subscriptions/me */
    @DeleteMapping("/catalog/skus/{skuId}/restock-subscriptions/me")
    public ApiResponse<Map<String, Object>> cancelRestock(
            @PathVariable String skuId,
            @RequestHeader(value = "Authorization", required = false) String authHeader) {
        return ApiResponse.success(waitlistService.cancel(userIdOf(authHeader), skuId));
    }

    // ============================================================
    // 星盘选择（02_双模式页面流程 §3）
    // ============================================================

    /**
     * GET /v1/jewelry/charts?mode=&current_chart_id=
     *
     * 出生信息脱敏与"自测模式下主体不是本人"的警告都在服务端完成，
     * 前端不拿到完整出生资料，也不自己推导该用哪种措辞（§3、01 §16）。
     */
    @GetMapping("/charts")
    public ApiResponse<Map<String, Object>> selectableCharts(
            @RequestParam(defaultValue = "SELF_ASSESSMENT") String mode,
            @RequestParam(required = false) String current_chart_id,
            @RequestHeader(value = "Authorization", required = false) String authHeader) {
        return ApiResponse.success(
                chartSelectionService.listSelectableCharts(mode, userIdOf(authHeader), current_chart_id));
    }

    /**
     * POST /v1/jewelry/chart-selection/validate
     *
     * 切换星盘后校验：自测模式下若所选不是本人星盘，返回 self_chart_warning，
     * 前端必须引导切换到赠礼模式或确认关系，不得继续用自测措辞。
     */
    @PostMapping("/chart-selection/validate")
    public ApiResponse<Map<String, Object>> validateChartSelection(
            @RequestBody Map<String, Object> request,
            @RequestHeader(value = "Authorization", required = false) String authHeader) {
        String mode = str(request, "assessment_mode", JewelryAdvisorService.MODE_SELF);
        return ApiResponse.success(
                chartSelectionService.validateSelection(mode, userIdOf(authHeader),
                        str(request, "chart_id", null)));
    }

    private String userIdOf(String authHeader) {
        // 统一走 BearerUserIdResolver：口径与 ChartController 一致，
        // 否则同一份星盘在两个接口下会对应不同 userId，导致选盘列表为空
        return bearerUserIdResolver.resolve(authHeader);
    }

    /** 运营统计：按 Planet / Remedy / 来源去重汇总（06_商品后台） */
    @GetMapping("/catalog/restock-demand")
    public ApiResponse<Map<String, Object>> restockDemand() {
        return ApiResponse.success(waitlistService.demandStats());
    }

    private String restockMessage(JewelryValidationException e) {
        if (e.getViolations().containsKey("PJA_SKU_NOT_FOUND")) return "未找到该商品规格";
        if (e.getViolations().containsKey("PJA_SKU_NOT_OUT_OF_STOCK")) return "该商品当前有货，无需登记补货";
        if (e.getViolations().containsKey("PJA_WAITLIST_DISABLED")) return "该商品已下架，暂不支持补货通知";
        return "补货订阅操作失败，请重试";
    }

    @SuppressWarnings("unchecked")
    private Map<String, List<String>> answersOf(Map<String, Object> request) {
        Object raw = request.get("answers");
        Map<String, List<String>> out = new LinkedHashMap<>();
        if (raw instanceof Map<?, ?> m) {
            for (Map.Entry<?, ?> e : m.entrySet()) {
                Object v = e.getValue();
                if (v instanceof List<?> list) {
                    List<String> ids = new java.util.ArrayList<>();
                    for (Object o : list) ids.add(String.valueOf(o));
                    out.put(String.valueOf(e.getKey()), ids);
                }
            }
        }
        return out;
    }

    private String str(Map<String, Object> map, String key, String fallback) {
        Object v = map.get(key);
        return v == null ? fallback : String.valueOf(v);
    }
}