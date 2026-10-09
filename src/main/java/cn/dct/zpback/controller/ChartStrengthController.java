package cn.dct.zpback.controller;

import cn.dct.zpback.dto.ApiResponse;
import cn.dct.zpback.service.CanonicalStrengthService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

/**
 * 专业数据（数据 Tab）控制器
 *
 * 提供 Shadbala / Bhavabala / Ashtakavarga 的 canonical 数据。
 * Bhavabala 与 Ashtakavarga 由本地 canonical 引擎按 PL9 口径计算
 * （第三方 API 这两项未通过 PL9 parity，不得直接透传）。
 */
@RestController
@RequestMapping("/v1/chart/strength")
@RequiredArgsConstructor
@Slf4j
public class ChartStrengthController {

    private final CanonicalStrengthService canonicalStrengthService;

    /**
     * POST /v1/chart/strength
     * 计算专业数据（Shadbala 摘要 + Bhavabala + Ashtakavarga）
     *
     * 请求体：{ name, year, month, day, hour, minute, second, place, latitude, longitude, timezone }
     */
    @PostMapping
    public ApiResponse<Map<String, Object>> getStrengthData(
            @RequestHeader(value = "Authorization", required = false) String authHeader,
            @RequestBody Map<String, Object> request) {

        try {
            Map<String, Object> data = canonicalStrengthService.computeProfessionalData(request);
            log.info("专业数据计算完成: engine={}", data.get("engineVersion"));
            return ApiResponse.success(data);
        } catch (IllegalArgumentException e) {
            return ApiResponse.error(422, e.getMessage());
        } catch (Exception e) {
            log.error("专业数据计算失败: {}", e.getMessage(), e);
            return ApiResponse.error(500, "专业数据计算失败：" + e.getMessage());
        }
    }
}
