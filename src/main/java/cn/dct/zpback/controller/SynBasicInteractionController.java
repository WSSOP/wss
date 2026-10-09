package cn.dct.zpback.controller;

import cn.dct.zpback.constant.SynBasicConstants;
import cn.dct.zpback.dto.ApiResponse;
import cn.dct.zpback.engine.synbasic.SynBasicAssets;
import cn.dct.zpback.service.synbasic.SynBasicInteractionService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.*;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * 双人合盘基础互动解析控制器
 *
 * POST /v1/synastry/basic-interaction
 *
 * 四模块（月亮/金星/火星/水星）基础互动，消费 156 Canonical Library；
 * 与 A×B 星宿关系相互独立，星宿关系继续走各自接口。
 */
@RestController
@RequestMapping("/v1/synastry/basic-interaction")
@RequiredArgsConstructor
@Slf4j
@SuppressWarnings("unchecked")
public class SynBasicInteractionController {

    private final SynBasicInteractionService service;
    private final SynBasicAssets assets;

    /**
     * POST /v1/synastry/basic-interaction/resolve
     *
     * 请求体：
     * {
     *   "person_a": { "subject_id": "A", "display_name": "若岚",
     *                 "moon": {"sign": "SIGN_LEO", "nakshatra": "NAK_11_PURVA_PHALGUNI"}, ... },
     *   "person_b": { ... },
     *   "relationship_type": "romantic" | null,
     *   "viewer_perspective": "VIEWER_IS_A" | "VIEWER_IS_B" | "OBSERVER"
     * }
     */
    @PostMapping("/resolve")
    public ApiResponse<Map<String, Object>> resolve(
            @RequestHeader(value = "Authorization", required = false) String authHeader,
            @RequestBody Map<String, Object> request) {

        Map<String, Object> personA = (Map<String, Object>) request.get("person_a");
        Map<String, Object> personB = (Map<String, Object>) request.get("person_b");
        if (personA == null || personB == null) {
            return ApiResponse.error(400, "缺少 person_a / person_b");
        }

        String relationshipType = (String) request.get("relationship_type");
        String viewer = (String) request.get("viewer_perspective");

        try {
            Map<String, Object> data = service.resolve(personA, personB, relationshipType, viewer);
            return ApiResponse.success(data);
        } catch (Exception e) {
            log.error("基础互动解析失败: {}", e.getMessage(), e);
            return ApiResponse.error(500, "基础互动解析失败：" + e.getMessage());
        }
    }

    /**
     * GET /v1/synastry/basic-interaction/evidence-hints
     *
     * Evidence Hint 是 UI 静态绑定（A1.4）：前端按 planet + source_mode 读取固定文案。
     * 这里直接回传同一份 Lookup Index 内的映射，避免前端再维护一份副本。
     */
    @GetMapping("/evidence-hints")
    public ApiResponse<Map<String, Object>> evidenceHints() {
        Map<String, Object> data = new LinkedHashMap<>();
        data.put("schema_version", SynBasicConstants.SCHEMA_VERSION);
        data.put("content_library_version", assets.contentLibraryVersion());
        data.put("resolver_version", SynBasicConstants.RESOLVER_VERSION);
        data.put("render_profile_version", SynBasicConstants.RENDER_PROFILE_VERSION);
        data.put("scope_note", SynBasicConstants.SCOPE_NOTE);
        data.put("hints", assets.evidenceHints());
        return ApiResponse.success(data);
    }
}
