package cn.dct.zpback.controller;

import cn.dct.zpback.dto.ApiResponse;
import cn.dct.zpback.entity.PlanetaryJewelryProduct;
import cn.dct.zpback.entity.PlanetaryJewelrySku;
import cn.dct.zpback.mapper.PlanetaryJewelryProductMapper;
import cn.dct.zpback.mapper.PlanetaryJewelrySkuMapper;
import cn.dct.zpback.service.jewelry.JewelryCatalogService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.*;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 行星珠宝建议｜运营后台 API
 * 前缀：/v1/jewelry/admin
 *
 * 对应文档：06_商品后台与补货订阅 §1、§2、§3、§8、§10、§12
 *
 * 边界（06 §12）：
 * - 只有运营角色可增删改商品和库存；本控制器是<b>接口实现</b>，
 *   权限校验由网关/拦截器按角色控制，生产环境必须保证 ANONYMOUS 角色无法访问；
 * - 用户侧只能管理自己的 Waitlist，不在本控制器范围内；
 * - 补货、换图、改价、调整同方向顺序<b>不需要发布新 Skill 版本</b>（05 §10），
 *   因为这些只写商品表，推荐排序链路不读商品表。
 *
 * 严禁：
 * - 不得提供「直接写入期待补货人数」的接口——前端人数必须来自真实去重订阅（06 §6、§12）；
 * - 不得提供物理删除商品的接口——只允许软下架/归档，保留历史订阅与推荐快照（06 §4）。
 */
@RestController
@RequestMapping("/v1/jewelry/admin")
@RequiredArgsConstructor
@Slf4j
public class JewelryCatalogAdminController {

    private final JewelryCatalogService catalogService;
    private final PlanetaryJewelryProductMapper productMapper;
    private final PlanetaryJewelrySkuMapper skuMapper;

    // ==============================================================
    // 商品（06 §2）
    // ==============================================================

    /** GET /v1/jewelry/admin/products?planet=&remedy_type=&publish_status=&source= */
    @GetMapping("/products")
    public ApiResponse<List<Map<String, Object>>> listProducts(
            @RequestParam(required = false) String planet,
            @RequestParam(name = "remedy_type", required = false) String remedyType,
            @RequestParam(name = "publish_status", required = false) String publishStatus,
            @RequestParam(required = false) String source) {
        return ApiResponse.success(
                catalogService.listProducts(planet, remedyType, publishStatus, source));
    }

    /** GET /v1/jewelry/admin/products/{productId} */
    @GetMapping("/products/{productId}")
    public ApiResponse<Map<String, Object>> getProduct(@PathVariable String productId) {
        Map<String, Object> p = catalogService.product(productId);
        if (p == null) {
            return ApiResponse.error(404, "商品不存在", Map.of("product_id", productId));
        }
        return ApiResponse.success(p);
    }

    /**
     * POST /v1/jewelry/admin/products
     *
     * 新增或全量更新商品。product_id 由运营生成，重复提交走 upsert，
     * 不会产生重复商品，也不受「商品数量设上限」约束。
     *
     * 入参按 Map 接收再手工装配：项目里不同 Jackson 配置下的
     * {@code @JsonNaming} 行为不一致，直接绑定实体不可靠。
     * 手工装配还能同时接受 camelCase 与 snake_case（文档 06 §2 用 snake_case）。
     */
    @PostMapping("/products")
    public ApiResponse<PlanetaryJewelryProduct> saveProduct(
            @RequestBody Map<String, Object> body) {
        PlanetaryJewelryProduct product = toProduct(body);
        if (product.getProductId() == null) {
            return ApiResponse.<PlanetaryJewelryProduct>error(400, "product_id 不能为空", null);
        }
        if (product.getPlanetCode() == null || product.getRemedyType() == null) {
            return ApiResponse.<PlanetaryJewelryProduct>error(
                    400, "planet_code 与 remedy_type 必填（也可用 planetCode / remedyType）", null);
        }
        return ApiResponse.success(catalogService.saveProduct(product));
    }

    /** 从原始 Map 装配商品，camelCase 优先、snake_case 兜底 */
    private PlanetaryJewelryProduct toProduct(Map<String, Object> b) {
        PlanetaryJewelryProduct p = new PlanetaryJewelryProduct();
        p.setProductId(str(pick(b, "productId", "product_id")));
        p.setTitle(str(pick(b, "title")));
        p.setSubtitle(str(pick(b, "subtitle")));
        p.setPlanetCode(str(pick(b, "planetCode", "planet_code")));
        p.setRemedyType(str(pick(b, "remedyType", "remedy_type")));
        p.setDescription(str(pick(b, "description")));
        p.setDetailContent(str(pick(b, "detailContent", "detail_content")));
        p.setCareInstructions(str(pick(b, "careInstructions", "care_instructions")));
        p.setProductForm(str(pick(b, "productForm", "product_form")));
        p.setSource(str(pick(b, "source")));
        p.setImages(jsonText(pick(b, "images")));
        p.setMaterialTags(jsonText(pick(b, "materialTags", "material_tags")));
        p.setColorTags(jsonText(pick(b, "colorTags", "color_tags")));
        p.setSupportTags(jsonText(pick(b, "supportTags", "support_tags")));
        p.setAutoRecommendable(bool(pick(b, "autoRecommendable", "auto_recommendable")));
        p.setGiftAvailable(bool(pick(b, "giftAvailable", "gift_available")));
        p.setHighThresholdGemstone(bool(pick(b, "highThresholdGemstone", "high_threshold_gemstone")));
        p.setAdminRecommendationPriority(intOf(pick(b, "adminRecommendationPriority",
                "admin_recommendation_priority")));
        p.setPublishStatus(str(pick(b, "publishStatus", "publish_status")));
        return p;
    }

    /** 数组/对象字段序列化成 JSON 文本，便于存入 JSON 列 */
    private static String jsonText(Object v) {
        if (v == null) return null;
        if (v instanceof String s) return s;
        try {
            return new com.fasterxml.jackson.databind.ObjectMapper().writeValueAsString(v);
        } catch (Exception e) {
            return null;
        }
    }

    /**
     * PATCH /v1/jewelry/admin/products/{productId}/publish-status
     * 上下架：只改状态，不动内容（06 §4）
     */
    @PatchMapping("/products/{productId}/publish-status")
    public ApiResponse<Map<String, Object>> updatePublishStatus(
            @PathVariable String productId,
            @RequestParam String status) {
        if (!List.of("DRAFT", "PUBLISHED", "UNPUBLISHED", "ARCHIVED").contains(status)) {
            return ApiResponse.error(400, "publish_status 取值非法", Map.of("allowed",
                    List.of("DRAFT", "PUBLISHED", "UNPUBLISHED", "ARCHIVED")));
        }
        int n = productMapper.updatePublishStatus(productId, status);
        if (n == 0) {
            return ApiResponse.error(404, "商品不存在", Map.of("product_id", productId));
        }
        return ApiResponse.success(Map.of("product_id", productId, "publish_status", status));
    }

    /**
     * PATCH /v1/jewelry/admin/products/{productId}/auto-recommendable
     * 运营手动关闭/恢复自动推荐（05 §4 硬条件之一）
     */
    @PatchMapping("/products/{productId}/auto-recommendable")
    public ApiResponse<Map<String, Object>> updateAutoRecommendable(
            @PathVariable String productId,
            @RequestParam boolean enabled) {
        int n = productMapper.updateAutoRecommendable(productId, enabled);
        if (n == 0) {
            return ApiResponse.error(404, "商品不存在", Map.of("product_id", productId));
        }
        return ApiResponse.success(Map.of("product_id", productId, "auto_recommendable", enabled));
    }

    /**
     * PATCH /v1/jewelry/admin/products/{productId}/priority
     * 调整同一方向内的展示顺序（05 §5 允许；不得跨方向生效）
     */
    @PatchMapping("/products/{productId}/priority")
    public ApiResponse<Map<String, Object>> updatePriority(
            @PathVariable String productId,
            @RequestParam int priority) {
        int n = productMapper.updatePriority(productId, priority);
        if (n == 0) {
            return ApiResponse.error(404, "商品不存在", Map.of("product_id", productId));
        }
        return ApiResponse.success(Map.of("product_id", productId,
                "admin_recommendation_priority", priority));
    }

    /**
     * POST /v1/jewelry/admin/products/{productId}/archive
     * 软归档：不物理删除，历史订阅与推荐快照保留（06 §4、§12）
     */
    @PostMapping("/products/{productId}/archive")
    public ApiResponse<Map<String, Object>> archive(@PathVariable String productId) {
        int n = productMapper.archive(productId);
        if (n == 0) {
            return ApiResponse.error(404, "商品不存在", Map.of("product_id", productId));
        }
        log.info("行星珠宝商品已归档: {}", productId);
        return ApiResponse.success(Map.of("product_id", productId, "publish_status", "ARCHIVED"));
    }

    // ==============================================================
    // SKU 与库存（06 §3、§8）
    // ==============================================================

    /** POST /v1/jewelry/admin/skus */
    @PostMapping("/skus")
    public ApiResponse<PlanetaryJewelrySku> saveSku(@RequestBody Map<String, Object> body) {
        PlanetaryJewelrySku sku = new PlanetaryJewelrySku();
        sku.setSkuId(str(pick(body, "skuId", "sku_id")));
        sku.setProductId(str(pick(body, "productId", "product_id")));
        sku.setVariantAttributes(jsonText(pick(body, "variantAttributes", "variant_attributes")));
        sku.setStatus(str(pick(body, "status")));
        sku.setSource(str(pick(body, "source")));
        Object price = pick(body, "price");
        if (price != null) sku.setPrice(new java.math.BigDecimal(str(price)));
        Object compare = pick(body, "compareAtPrice", "compare_at_price");
        if (compare != null) sku.setCompareAtPrice(new java.math.BigDecimal(str(compare)));
        sku.setInventoryQuantity(intOf(pick(body, "inventoryQuantity", "inventory_quantity")));
        sku.setSaleEnabled(bool(pick(body, "saleEnabled", "sale_enabled")));
        sku.setRestockWaitlistEnabled(bool(pick(body, "restockWaitlistEnabled", "restock_waitlist_enabled")));

        if (sku.getSkuId() == null) {
            return ApiResponse.<PlanetaryJewelrySku>error(400, "sku_id 不能为空", null);
        }
        if (sku.getProductId() == null || productMapper.findById(sku.getProductId()) == null) {
            return ApiResponse.<PlanetaryJewelrySku>error(400, "product_id 不存在", null);
        }
        return ApiResponse.success(catalogService.saveSku(sku));
    }

    /**
     * PATCH /v1/jewelry/admin/skus/{skuId}/inventory
     *
     * 库存必须到 SKU 级（06 §3）；inventory_status 由 DB 生成列推导，接口不接收状态。
     */
    @PatchMapping("/skus/{skuId}/inventory")
    public ApiResponse<Map<String, Object>> updateInventory(
            @PathVariable String skuId,
            @RequestParam int quantity) {
        if (quantity < 0) {
            return ApiResponse.error(400, "库存不能为负", Map.of());
        }
        if (skuMapper.findById(skuId) == null) {
            return ApiResponse.error(404, "SKU 不存在", Map.of("sku_id", skuId));
        }
        skuMapper.updateInventory(skuId, quantity);
        return ApiResponse.success(catalogService.sku(skuId));
    }

    /**
     * POST /v1/jewelry/admin/skus/{skuId}/restock
     *
     * 补货（06 §8）。仅当 inventory_quantity 从 0 变为正数时生成补货事件；
     * 同一事件重复处理不会重复通知（GC18）。
     */
    @PostMapping("/skus/{skuId}/restock")
    public ApiResponse<Map<String, Object>> restock(
            @PathVariable String skuId,
            @RequestParam int quantity) {
        if (quantity < 0) {
            return ApiResponse.error(400, "库存不能为负", Map.of());
        }
        Map<String, Object> result = catalogService.restock(skuId, quantity);
        if (result.containsKey("error_code")) {
            return ApiResponse.error(404, "SKU 不存在", result);
        }
        return ApiResponse.success(result);
    }

    /** PATCH /v1/jewelry/admin/skus/{skuId}/restock-waitlist —— 关闭/开启补货订阅 */
    @PatchMapping("/skus/{skuId}/restock-waitlist")
    public ApiResponse<Map<String, Object>> toggleWaitlist(
            @PathVariable String skuId,
            @RequestParam boolean enabled) {
        if (skuMapper.findById(skuId) == null) {
            return ApiResponse.error(404, "SKU 不存在", Map.of("sku_id", skuId));
        }
        skuMapper.updateWaitlistEnabled(skuId, enabled);
        return ApiResponse.success(catalogService.sku(skuId));
    }

    /** POST /v1/jewelry/admin/skus/{skuId}/cancel —— 软取消，保留历史引用 */
    @PostMapping("/skus/{skuId}/cancel")
    public ApiResponse<Map<String, Object>> cancelSku(@PathVariable String skuId) {
        if (skuMapper.findById(skuId) == null) {
            return ApiResponse.error(404, "SKU 不存在", Map.of("sku_id", skuId));
        }
        skuMapper.cancel(skuId);
        return ApiResponse.success(Map.of("sku_id", skuId, "status", "CANCELLED"));
    }

    // ==============================================================
    // 看板（06 §10）
    // ==============================================================

    /** GET /v1/jewelry/admin/restock-board —— 补货需求看板，数据来自真实去重订阅 */
    @GetMapping("/restock-board")
    public ApiResponse<Map<String, Object>> restockBoard() {
        Map<String, Object> data = new LinkedHashMap<>();
        data.put("total_products", catalogService.catalogSize());
        data.put("total_skus", catalogService.skuCount());
        return ApiResponse.success(data);
    }

    // ==============================================================
    // 入参装配工具
    // ==============================================================

    /** 取字段，依次尝试多个候选名（camelCase 优先、snake_case 兜底） */
    private static Object pick(Map<String, Object> b, String... names) {
        for (String n : names) {
            Object v = b.get(n);
            if (v != null) return v;
        }
        return null;
    }

    private static String str(Object o) {
        if (o == null) return null;
        String s = String.valueOf(o);
        return s.isBlank() ? null : s;
    }

    private static Boolean bool(Object o) {
        if (o == null) return null;
        if (o instanceof Boolean b) return b;
        return Boolean.parseBoolean(String.valueOf(o));
    }

    private static int intOf(Object o) {
        if (o instanceof Number n) return n.intValue();
        if (o == null) return 0;
        try {
            return Integer.parseInt(String.valueOf(o));
        } catch (NumberFormatException e) {
            return 0;
        }
    }
}