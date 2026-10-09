package cn.dct.zpback.controller;

import cn.dct.zpback.constant.NakshatraRelationConstants;
import cn.dct.zpback.dto.ApiResponse;
import cn.dct.zpback.service.nakshatra.NakshatraRelationException;
import cn.dct.zpback.service.nakshatra.NakshatraRelationService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;

/**
 * 星宿关系 API 控制器
 * 路径前缀：/api/v1/nakshatra-relationships（docs/05 §1）
 *
 * 计费边界（docs/05 §13）：本接口免费，不创建 AI Request，不扣任何星灵AI额度。
 */
@RestController
@RequestMapping("/v1/nakshatra-relationships")
@RequiredArgsConstructor
@Slf4j
public class NakshatraRelationController {

    private final NakshatraRelationService nakshatraRelationService;

    /**
     * POST /v1/nakshatra-relationships/resolve
     * 解析双人星宿关系
     */
    @PostMapping("/resolve")
    public ResponseEntity<ApiResponse<Map<String, Object>>> resolve(
            @RequestHeader(value = "Authorization", required = false) String authHeader,
            @RequestBody Map<String, Object> request) {

        String userId = extractUserId(authHeader);
        String profileAId = str(request.get("profile_a_id"));
        String profileBId = str(request.get("profile_b_id"));
        String locale = str(request.get("locale"));
        String contractVersion = str(request.get("contract_version"));

        log.info("解析星宿关系: userId={}, profileA={}, profileB={}, contractVersion={}",
                userId, profileAId, profileBId, contractVersion);

        try {
            Map<String, Object> result = nakshatraRelationService.resolve(
                    userId, profileAId, profileBId, locale, contractVersion);
            return ResponseEntity.ok(ApiResponse.success(result));
        } catch (NakshatraRelationException e) {
            // 稳定错误：绝不返回上一对档案的结果，也不调用 AI 兜底（docs/02 §11）
            log.warn("星宿关系解析失败: code={}, message={}", e.getErrorCode(), e.getMessage());
            return ResponseEntity.status(e.getHttpStatus())
                    .body(ApiResponse.error(e.getHttpStatus(), e.getMessage(),
                            errorBody(e.getErrorCode(), e.getMessage(), e.isRetryable())));
        } catch (Exception e) {
            log.error("星宿关系解析异常", e);
            return ResponseEntity.status(500)
                    .body(ApiResponse.error(500, "服务暂时不可用，请稍后重试。",
                            errorBody(NakshatraRelationConstants.ERR_INTERNAL_ERROR,
                                    "服务暂时不可用，请稍后重试。", true)));
        }
    }

    /** 错误体（error_response.schema.json） */
    private Map<String, Object> errorBody(String errorCode, String message, boolean retryable) {
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("request_id", "nr_" + UUID.randomUUID().toString().replace("-", ""));
        body.put("status", NakshatraRelationConstants.STATUS_ERROR);
        body.put("error_code", errorCode);
        body.put("message", message);
        body.put("retryable", retryable);
        return body;
    }

    private static String str(Object o) {
        return o == null ? null : String.valueOf(o);
    }

    /**
     * 从 Authorization header 中提取用户ID
     * 格式: token_<phone> 或 mock_token_<phone>（与既有 Controller 保持一致）
     */
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
