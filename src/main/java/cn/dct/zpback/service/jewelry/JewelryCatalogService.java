package cn.dct.zpback.service.jewelry;

import cn.dct.zpback.entity.PlanetaryJewelryProduct;
import cn.dct.zpback.entity.PlanetaryJewelrySku;
import cn.dct.zpback.exception.LearningValidationException;
import cn.dct.zpback.mapper.PlanetaryJewelryProductMapper;
import cn.dct.zpback.mapper.PlanetaryJewelrySkuMapper;
import cn.dct.zpback.mapper.PlanetaryJewelryRestockSubscriptionMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 行星珠宝建议｜商城匹配（Commerce Adapter）
 *
 * 铁律（05_主推次推与商城匹配 §1、§5；catalog_contract.v1.json）：
 * <ol>
 *   <li>本类只在 Resolver 锁定推荐方向之后被调用；它<b>没有</b>任何修改 rank 的能力——
 *       入参 planet/modality 不可变，返回值也不参与排序。</li>
 *   <li>商品 ID 一律不写死：只按 planet_code + remedy_type + auto_recommendable
 *       + publish_status 过滤（§4）。</li>
 *   <li>库存/价格/运营优先级只影响<b>同一方向内部</b>的商品展示顺序，
 *       不得把次推升为主推，也不得恢复被 Block 的行星（§5）。</li>
 *   <li>缺货 ≠ 下架：OUT_OF_STOCK 仍要展示并允许登记补货（§7、contract.no_match_behavior）。</li>
 * </ol>
 *
 * <b>数据来源</b>：MySQL 的 planetary_jewelry_product / planetary_jewelry_sku，
 * 由运营后台动态维护（06）。内置 seed 只在启动时导入一次，
 * 之后商品、价格、库存一律以 DB 为准，重启即生效，无需改代码。
 *
 * <b>为什么仍然能改价格而不重新发布</b>（05 §10）：运营变更只写 DB，
 * 推荐排序链路不读商品表，因此「改价 / 补货 / 换图 / 调整同方向顺序」
 * 永远不需要发布新 Skill 版本；而「改变 Gemstone Gate」属于规则变更，
 * 必须改 09_CONFIG 里的 policy 并升版本号。
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class JewelryCatalogService {

    private final PlanetaryJewelryProductMapper productMapper;
    private final PlanetaryJewelrySkuMapper skuMapper;
    private final PlanetaryJewelryRestockSubscriptionMapper waitlistMapper;

    // ==================================================================
    // 对外：按冻结推荐方向做匹配
    // ==================================================================

    /**
     * 匹配单个推荐方向。
     *
     * @param planet            冻结的行星（不可被商品侧影响）
     * @param allowedRemedyTypes 该 Modality 允许的补救类型（GEMSTONE_ELIGIBLE 才含 GEMSTONE）
     * @param rank              PRIMARY / SECONDARY，仅用于回显与埋点
     * @param userId            用于回显该用户的补货订阅状态
     */
    public Map<String, Object> match(String planet, List<String> allowedRemedyTypes,
                                     String rank, String userId) {
        Map<String, Object> out = matchRaw(planet, allowedRemedyTypes, rank, userId);
        // 期待补货人数必须来自真实去重订阅（06 §6、§12），不能用示例值或点击次数
        return decorateWaitlist(out,
                (skuId, productId) -> waitlistMapper.countDistinctWaitingUsersBySku(skuId),
                (skuId, unused) -> waitlistMapper.findActive(userId, skuId) != null);
    }

    /** 不带订阅统计的匹配，供只需要商品本身的场景使用 */
    public Map<String, Object> matchRaw(String planet, List<String> allowedRemedyTypes,
                                        String rank, String userId) {
        Map<String, Object> out = new LinkedHashMap<>();
        out.put("recommendation_rank", rank);
        out.put("planet_code", planet);
        out.put("allowed_remedy_types", allowedRemedyTypes);

        // NO_AUTOMATIC_RECOMMENDATION：没有自动商品池（§6）
        if (planet == null || allowedRemedyTypes == null || allowedRemedyTypes.isEmpty()) {
            return emptyMatch(out, "NO_MAPPED_PRODUCT");
        }

        List<PlanetaryJewelryProduct> matched =
                productMapper.findMatchable(planet, allowedRemedyTypes);

        // 一次批量取回全部 SKU，避免逐商品查询
        List<String> productIds = new ArrayList<>();
        for (PlanetaryJewelryProduct p : matched) productIds.add(p.getProductId());
        Map<String, List<PlanetaryJewelrySku>> skuByProduct =
                productIds.isEmpty() ? Map.of()
                        : skuMapper.findActiveByProducts(productIds).stream()
                                .collect(java.util.stream.Collectors.groupingBy(
                                        PlanetaryJewelrySku::getProductId,
                                        java.util.LinkedHashMap::new,
                                        java.util.stream.Collectors.toList()));

        List<Map<String, Object>> productViews = new ArrayList<>();
        int inStock = 0;
        int lowStock = 0;
        int outOfStock = 0;
        int waitlistCount = 0;

        for (PlanetaryJewelryProduct p : matched) {
            Map<String, Object> view = productView(p, skuByProduct.getOrDefault(p.getProductId(), List.of()));
            productViews.add(view);
            waitlistCount += intOf(view.get("waitlist_count"));
            switch (String.valueOf(view.get("inventory_summary_state"))) {
                case "IN_STOCK" -> inStock++;
                case "LOW_STOCK" -> lowStock++;
                default -> outOfStock++;
            }
        }

        String state;
        if (matched.isEmpty()) {
            state = "NO_MAPPED_PRODUCT";              // 保留推荐结论，不改 Planet（§7）
        } else if (inStock + lowStock > 0) {
            state = outOfStock > 0 ? "PARTIAL_STOCK_MATCH" : "IN_STOCK_MATCH";
        } else {
            state = "ALL_MATCHES_OUT_OF_STOCK";
        }

        out.put("match_state", state);
        out.put("products", productViews);
        out.put("inventory_summary", new LinkedHashMap<>(Map.of(
                "in_stock_products", inStock,
                "low_stock_products", lowStock,
                "out_of_stock_products", outOfStock)));
        out.put("waitlist_count", waitlistCount);
        out.put("user_waitlist_status", Map.of());
        out.put("cta_payload", cta(rank, state, null));
        return out;
    }

    // ==================================================================
    // 用户主动浏览（不在推荐流程内）
    // ==================================================================

    /**
     * 全目录浏览：用户主动逛商城，不依赖任何冻结的推荐方向。
     *
     * <b>与 {@link #match} 的边界</b>（05 §1、§4、§5）：
     * <ul>
     *   <li>本方法<b>不产出</b>任何 rank / planet / recommendation 字段，
     *       调用方拿到的只是陈列列表，没有能力用它改推荐方向；</li>
     *   <li>05 §4 的四条硬条件是<b>推荐路径</b>的要求：Planet 必须相同、
     *       remedy_type 必须被 Modality 允许、auto_recommendable 必须为真。
     *       浏览页不受这三条约束——用户想看什么就看什么，
     *       但它和推荐路径完全是两条互不影响的链路；</li>
     *   <li>排序只按「现货优先 + 行星/类型稳定序」，纯陈列用；
     *       不涉及 admin_recommendation_priority，避免让人误以为这是推荐序。</li>
     * </ul>
     *
     * @param planet     可选，按行星筛选；null = 全部
     * @param remedyType 可选，YANTRA/GEMSTONE；null = 全部
     */
    public Map<String, Object> browse(String planet, String remedyType, String userId) {
        Map<String, Object> out = new LinkedHashMap<>();
        out.put("source", "BROWSE");   // 显式标记：这不是一次推荐匹配

        List<PlanetaryJewelryProduct> matched = productMapper.browse(planet, remedyType);

        List<String> productIds = new ArrayList<>();
        for (PlanetaryJewelryProduct p : matched) productIds.add(p.getProductId());
        Map<String, List<PlanetaryJewelrySku>> skuByProduct =
                productIds.isEmpty() ? Map.of()
                        : skuMapper.findActiveByProducts(productIds).stream()
                                .collect(java.util.stream.Collectors.groupingBy(
                                        PlanetaryJewelrySku::getProductId,
                                        java.util.LinkedHashMap::new,
                                        java.util.stream.Collectors.toList()));

        List<Map<String, Object>> views = new ArrayList<>();
        int inStock = 0;
        for (PlanetaryJewelryProduct p : matched) {
            Map<String, Object> view =
                    productView(p, skuByProduct.getOrDefault(p.getProductId(), List.of()));
            views.add(view);
            if ("IN_STOCK".equals(view.get("inventory_summary_state"))
                    || "LOW_STOCK".equals(view.get("inventory_summary_state"))) {
                inStock++;
            }
        }

        out.put("products", views);
        out.put("total", views.size());
        out.put("in_stock_total", inStock);
        out.put("filters", Map.of(
                "planet_code", planet == null ? "" : planet,
                "remedy_type", remedyType == null ? "" : remedyType));
        return decorateWaitlist(out,
                (skuId, productId) -> waitlistMapper.countDistinctWaitingUsersBySku(skuId),
                (skuId, unused) -> waitlistMapper.findActive(userId, skuId) != null);
    }

    /**
     * 商品详情。除业务字段外，额外返回 §5 约定的两个 CMS 结构字段。
     *
     * <p><b>CMS 契约（本轮只定契约，不做后台编辑）</b>：
     * <pre>
     * gallery_images[] = { image_url, sort_order, is_cover, alt_text, enabled }
     * detail_blocks[]  = { block_id, type, sort_order, enabled, content }
     *                   type ∈ RICH_TEXT | IMAGE | IMAGE_TEXT | SPEC_GROUP | NOTICE
     * </pre>
     *
     * <p>本轮后台还没有商品详情维护表，因此：
     * <ul>
     *   <li>{@code gallery_images} 先由商品已有的 {@code images} 字段派生，
     *       保证详情页顶部轮播现在就有内容，且形状已符合契约；</li>
     *   <li>{@code detail_blocks} 暂为空数组——<b>前端只按它渲染</b>，
     *       后台补上内容维护后无需改前端即自动展示。</li>
     * </ul>
     * 商品介绍文案与图片<b>不写死在 Vue 页面</b>（§5.3）。
     */
    public Map<String, Object> detail(String productId, String userId) {
        PlanetaryJewelryProduct p = productMapper.findById(productId);
        if (p == null || !"PUBLISHED".equals(p.getPublishStatus())) {
            throw new LearningValidationException("MALL_PRODUCT_UNAVAILABLE", "商品不存在或已下架");
        }
        List<PlanetaryJewelrySku> skus =
                skuMapper.findActiveByProducts(List.of(productId));

        Map<String, Object> view =
                productView(p, skus);

        // ---- §5.1 顶部商品图库 ----
        List<String> images = parseJsonArray(p.getImages());
        List<Map<String, Object>> gallery = new ArrayList<>();
        for (int i = 0; i < images.size(); i++) {
            Map<String, Object> g = new LinkedHashMap<>();
            g.put("image_url", images.get(i));
            g.put("sort_order", i);
            g.put("is_cover", i == 0);   // 第一张作为主图
            g.put("alt_text", p.getTitle());
            g.put("enabled", true);
            gallery.add(g);
        }
        view.put("gallery_images", gallery);

        // ---- §5.2 正文图文详情（后台未维护时为空数组）----
        view.put("detail_blocks", new ArrayList<Map<String, Object>>());

        // decorateWaitlist 的契约是 { products: [ {product_id, skus:[...] } ] }，
        // 它只认这个形状；单个商品详情也要包一层，否则里面遍历 products 会 NPE。
        Map<String, Object> wrapper = new LinkedHashMap<>();
        wrapper.put("products", List.of(view));
        decorateWaitlist(wrapper,
                (skuId, pid) -> waitlistMapper.countDistinctWaitingUsersBySku(skuId),
                (skuId, unused) -> waitlistMapper.findActive(userId, skuId) != null);

        return view;
    }

    /**
     * 搜索 / 列表查询。
     *
     * <p>与 {@link #browse} 返回<b>同一形状</b>（products[] 里同样是 productView 结构），
     * 前端才可以用同一个 flattenProducts 渲染两处，不必维护两套字段映射。
     *
     * <p>{@code keyword} 为空时等价于列表页：不带关键词只按筛选与排序。
     */
    public Map<String, Object> search(String keyword, String planet, String remedyType,
                                     String stock, String sort, String userId) {
        Map<String, Object> out = new LinkedHashMap<>();
        out.put("source", "SEARCH");

        List<PlanetaryJewelryProduct> matched = productMapper.search(
                blankToNull(keyword), blankToNull(planet),
                blankToNull(remedyType), normalizeStock(stock), blankToNull(sort));

        out.put("products", viewsWithSkus(matched));
        out.put("total", matched.size());
        out.put("filters", Map.of(
                "keyword", keyword == null ? "" : keyword,
                "planet_code", planet == null ? "" : planet,
                "remedy_type", remedyType == null ? "" : remedyType,
                "stock", stock == null ? "" : stock,
                "sort", sort == null ? "" : sort));
        return decorateWaitlist(out,
                (skuId, productId) -> waitlistMapper.countDistinctWaitingUsersBySku(skuId),
                (skuId, unused) -> waitlistMapper.findActive(userId, skuId) != null);
    }

    /** 把商品列表装配成 productView，并按 SKU 是否命中补齐 min_price 与库存状态 */
    private List<Map<String, Object>> viewsWithSkus(List<PlanetaryJewelryProduct> matched) {
        List<String> productIds = new ArrayList<>();
        for (PlanetaryJewelryProduct p : matched) productIds.add(p.getProductId());
        Map<String, List<PlanetaryJewelrySku>> skuByProduct =
                productIds.isEmpty() ? Map.of()
                        : skuMapper.findActiveByProducts(productIds).stream()
                                .collect(java.util.stream.Collectors.groupingBy(
                                        PlanetaryJewelrySku::getProductId,
                                        java.util.LinkedHashMap::new,
                                        java.util.stream.Collectors.toList()));

        List<Map<String, Object>> views = new ArrayList<>();
        for (PlanetaryJewelryProduct p : matched) {
            views.add(productView(p, skuByProduct.getOrDefault(p.getProductId(), List.of())));
        }
        return views;
    }

    /**
     * 库存筛选只接受两种取值，其它一律当「不筛选」。
     *
     * <p>故意不抛异常：这是可选筛选条件，传错一个值就整页报错不值得；
     * 与其报错让用户无所适从，不如退化成「全部」，结果依然正确。
     */
    private String normalizeStock(String stock) {
        if ("IN_STOCK".equals(stock) || "OUT_OF_STOCK".equals(stock)) return stock;
        return null;
    }

    private static String blankToNull(String s) {
        return (s == null || s.isBlank()) ? null : s;
    }

    private Map<String, Object> emptyMatch(Map<String, Object> out, String state) {
        out.put("match_state", state);
        out.put("products", List.of());
        out.put("inventory_summary", new LinkedHashMap<>());
        out.put("waitlist_count", 0);
        out.put("user_waitlist_status", Map.of());
        out.put("cta_payload", cta(null, state, null));
        return out;
    }

    // ==================================================================
    // 运营后台：商品与库存维护（06_商品后台与补货订阅 §1、§12）
    // ==================================================================

    /**
     * 运营写入商品。
     *
     * 只允许运营角色调用（06 §12）；商品 ID 由运营生成，
     * 重复提交同一 ID 走 upsert，不会产生重复商品。
     */
    @Transactional
    public PlanetaryJewelryProduct saveProduct(PlanetaryJewelryProduct product) {
        normalize(product);
        productMapper.upsert(product);
        log.info("行星珠宝商品已保存: {}", product.getProductId());
        return productMapper.findById(product.getProductId());
    }

    /** 运营写入 SKU；库存变化由生成列推导状态，代码不写状态 */
    @Transactional
    public PlanetaryJewelrySku saveSku(PlanetaryJewelrySku sku) {
        if (sku.getStatus() == null) sku.setStatus("ACTIVE");
        if (sku.getSaleEnabled() == null) sku.setSaleEnabled(true);
        if (sku.getRestockWaitlistEnabled() == null) sku.setRestockWaitlistEnabled(true);
        if (sku.getSource() == null) sku.setSource("ADMIN_UPLOAD");
        if (sku.getInventoryQuantity() == null) sku.setInventoryQuantity(0);
        if (sku.getPrice() == null) sku.setPrice(BigDecimal.ZERO);
        skuMapper.upsert(sku);
        log.info("行星珠宝SKU已保存: {}", sku.getSkuId());
        return skuMapper.findById(sku.getSkuId());
    }

    /**
     * 补货（06 §8）。
     *
     * 触发条件是 inventory_quantity 从 0 变为正数；
     * 重复调用不会重复通知，因为补货事件 ID 幂等（GC18）。
     * 返回本次实际生成的补货事件。
     */
    @Transactional
    public Map<String, Object> restock(String skuId, int newQuantity) {
        PlanetaryJewelrySku before = skuMapper.findByIdForUpdate(skuId);
        if (before == null) {
            Map<String, Object> m = new LinkedHashMap<>();
            m.put("error_code", "PJA_SKU_NOT_FOUND");
            return m;
        }
        int from = before.getInventoryQuantity() == null ? 0 : before.getInventoryQuantity();
        skuMapper.applyRestock(skuId, newQuantity);

        // 只有 0 -> >0 才算一次补货事件
        if (from <= 0 && newQuantity > 0) {
            String eventId = "re_" + skuId + "_" + newQuantity + "_" + from;
            int notified = waitlistMapper.findWaitingBySku(skuId).size();
            Map<String, Object> m = new LinkedHashMap<>();
            m.put("restock_event_id", eventId);
            m.put("sku_id", skuId);
            m.put("from_quantity", from);
            m.put("to_quantity", newQuantity);
            m.put("waiting_users", notified);
            m.put("notified_count", 0);   // 站内信发送由后续任务消费，本方法不直接推送
            log.info("行星珠宝补货事件: sku={} {}->{} 等待人数={}", skuId, from, newQuantity, notified);
            return m;
        }
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("restock_event_id", null);
        m.put("sku_id", skuId);
        m.put("from_quantity", from);
        m.put("to_quantity", newQuantity);
        m.put("notified_count", 0);
        return m;
    }

    /**
     * 商品快照，供补货订阅校验使用。
     *
     * 必须带 publish_status：订阅校验要判断「已归档/下架则不接受新订阅，
     * 但历史订阅保留」（06 §4）。
     */
    public Map<String, Object> product(String productId) {
        PlanetaryJewelryProduct p = productMapper.findById(productId);
        if (p == null) return null;
        Map<String, Object> m = productView(p, skuMapper.findActiveByProduct(productId));
        m.put("publish_status", p.getPublishStatus());
        m.put("auto_recommendable", p.getAutoRecommendable());
        return m;
    }

    public Map<String, Object> sku(String skuId) {
        PlanetaryJewelrySku s = skuMapper.findById(skuId);
        if (s == null) return null;
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("sku_id", s.getSkuId());
        m.put("product_id", s.getProductId());
        m.put("inventory_status", s.getInventoryStatus());
        m.put("status", s.getStatus());
        m.put("sale_enabled", s.getSaleEnabled());
        m.put("restock_waitlist_enabled", s.getRestockWaitlistEnabled());
        return m;
    }

    /** 商品列表（运营后台）：返回展示视图，前端直接可用 */
    public List<Map<String, Object>> listProducts(String planet, String remedyType,
                                                  String publishStatus, String source) {
        return productMapper.listForAdmin(planet, remedyType, publishStatus, source).stream()
                .map(p -> productView(p, skuMapper.findActiveByProduct(p.getProductId())))
                .toList();
    }

    /**
     * 目录规模（商品数）。
     *
     * 05 §3「商品数量不设上限」：本方法只是给运营看板看总量，
     * 任何推荐决策都不读它。
     */
    public int catalogSize() {
        return productMapper.count();
    }

    public int skuCount() {
        return skuMapper.count();
    }

    private void normalize(PlanetaryJewelryProduct p) {
        if (p.getPublishStatus() == null) p.setPublishStatus("DRAFT");
        if (p.getAutoRecommendable() == null) p.setAutoRecommendable(true);
        if (p.getGiftAvailable() == null) p.setGiftAvailable(true);
        if (p.getHighThresholdGemstone() == null) p.setHighThresholdGemstone(false);
        if (p.getAdminRecommendationPriority() == null) p.setAdminRecommendationPriority(0);
        // 默认就是运营上传；只有内部种子导入才显式传 SEED
        if (p.getSource() == null) p.setSource("ADMIN_UPLOAD");
    }

    // ==================================================================
    // 展示视图
    // ==================================================================

    private Map<String, Object> productView(PlanetaryJewelryProduct product,
                                            List<PlanetaryJewelrySku> skus) {
        Map<String, Object> view = new LinkedHashMap<>();
        view.put("product_id", product.getProductId());
        view.put("title", product.getTitle());
        view.put("subtitle", product.getSubtitle());
        view.put("planet_code", product.getPlanetCode());
        view.put("remedy_type", product.getRemedyType());
        view.put("images", parseJsonArray(product.getImages()));
        view.put("description", product.getDescription());
        view.put("gift_available", product.getGiftAvailable());
        view.put("product_form", product.getProductForm());
        view.put("high_threshold_gemstone", product.getHighThresholdGemstone());

        List<Map<String, Object>> skuViews = new ArrayList<>();
        BigDecimal minPrice = null;
        boolean anyStock = false;
        boolean anyLow = false;
        for (PlanetaryJewelrySku sku : skus) {
            Map<String, Object> sv = new LinkedHashMap<>();
            sv.put("sku_id", sku.getSkuId());
            sv.put("variant_attributes", parseJsonObject(sku.getVariantAttributes()));
            sv.put("price", sku.getPrice());
            sv.put("compare_at_price", sku.getCompareAtPrice());
            sv.put("inventory_status", sku.getInventoryStatus());
            sv.put("inventory_quantity", sku.getInventoryQuantity());
            sv.put("waitlist_count", 0);            // 由 WaitlistService 回填
            sv.put("user_subscribed", false);
            // 只有「缺货 + 可售 + 允许登记补货」三者同时成立才允许订阅
            sv.put("can_subscribe", "OUT_OF_STOCK".equals(sku.getInventoryStatus())
                    && !Boolean.FALSE.equals(sku.getSaleEnabled())
                    && !Boolean.FALSE.equals(sku.getRestockWaitlistEnabled()));
            skuViews.add(sv);
            if (minPrice == null || sku.getPrice().compareTo(minPrice) < 0) {
                minPrice = sku.getPrice();
            }
            anyStock |= "IN_STOCK".equals(sku.getInventoryStatus());
            anyLow |= "LOW_STOCK".equals(sku.getInventoryStatus());
        }
        view.put("skus", skuViews);
        view.put("min_price", minPrice);
        view.put("inventory_summary_state",
                anyStock ? "IN_STOCK" : anyLow ? "LOW_STOCK" : "OUT_OF_STOCK");
        view.put("waitlist_count", 0);
        return view;
    }

    /** JSON 数组列 -> List<String>；解析失败时返回空列表而不是抛异常 */
    private List<String> parseJsonArray(String json) {
        if (json == null || json.isBlank()) return List.of();
        try {
            List<String> out = new ArrayList<>();
            com.fasterxml.jackson.databind.JsonNode n =
                    new com.fasterxml.jackson.databind.ObjectMapper().readTree(json);
            if (n.isArray()) n.forEach(x -> out.add(x.asText()));
            return out;
        } catch (Exception e) {
            log.warn("商品图片列解析失败，按空处理", e);
            return List.of();
        }
    }

    /** JSON 对象列 -> Map；解析失败时返回空 Map */
    private Map<String, Object> parseJsonObject(String json) {
        if (json == null || json.isBlank()) return Map.of();
        try {
            return new com.fasterxml.jackson.databind.ObjectMapper()
                    .readValue(json, Map.class);
        } catch (Exception e) {
            log.warn("SKU variant_attributes 解析失败，按空处理", e);
            return Map.of();
        }
    }

    // ==================================================================
    // 固定底部 CTA（02_双模式页面流程 §8）
    // ==================================================================

    /**
     * CTA 完全由「冻结结果 + 实时目录状态」推导，Renderer 不得自由生成购买路径（§9）。
     */
    private Map<String, Object> cta(String rank, String matchState, String forcedMode) {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("mode", forcedMode);
        if (rank == null || "NO_MAPPED_PRODUCT".equals(matchState)) {
            m.put("primary_label", "相关饰品暂未上架");
            m.put("primary_action", "NONE");
            m.put("secondary_label", null);
            m.put("secondary_action", "NONE");
            return m;
        }
        if ("ALL_MATCHES_OUT_OF_STOCK".equals(matchState)) {
            m.put("primary_label", "补货后通知我");
            m.put("primary_action", "SUBSCRIBE_WAITLIST");
            if ("SECONDARY".equals(rank)) {
                m.put("secondary_label", null);
                m.put("secondary_action", "NONE");
            } else {
                m.put("secondary_label", "查看次推饰品");
                m.put("secondary_action", "VIEW_SECONDARY");
            }
            return m;
        }
        m.put("primary_label", "PRIMARY".equals(rank) ? "查看主推饰品" : "查看次推饰品");
        m.put("primary_action", "VIEW_PRODUCTS");
        m.put("secondary_label", "SECONDARY".equals(rank) ? null : "查看次推饰品");
        m.put("secondary_action", "SECONDARY".equals(rank) ? "NONE" : "VIEW_SECONDARY");
        return m;
    }

    /** 赠礼模式主 CTA 文案不同（02 §8） */
    public Map<String, Object> ctaForMode(String rank, String matchState, String assessmentMode) {
        Map<String, Object> c = cta(rank, matchState, null);
        if (c == null) return null;
        c.put("mode", assessmentMode);
        if (!"PRIMARY".equals(rank) || !"VIEW_PRODUCTS".equals(c.get("primary_action"))) return c;
        c.put("primary_label",
                "GIFT_BLESSING".equals(assessmentMode) ? "为TA挑选礼物" : "查看主推饰品");
        return c;
    }

    // ==================================================================

    /**
     * 回填 SKU 维度期待人数与用户订阅状态（WaitlistService 调用）。
     *
     * 人数按 06 §6 必须是 COUNT(DISTINCT user_id) 且只算 status='WAITING'，
     * 不能用点击次数，也不能把已通知的人继续计入当前等待人数。
     */
    public Map<String, Object> decorateWaitlist(Map<String, Object> matchResult,
                                                java.util.function.BiFunction<String, String, Integer> waitlistCountFn,
                                                java.util.function.BiFunction<String, String, Boolean> subscribedFn) {
        for (Object po : (List<?>) matchResult.get("products")) {
            Map<String, Object> p = cast(po);
            int productTotal = 0;
            for (Object so : (List<?>) p.get("skus")) {
                Map<String, Object> s = cast(so);
                String skuId = String.valueOf(s.get("sku_id"));
                int c = waitlistCountFn.apply(skuId, String.valueOf(p.get("product_id")));
                s.put("waitlist_count", c);
                s.put("user_subscribed", Boolean.TRUE.equals(subscribedFn.apply(skuId, "")));
                productTotal += c;
            }
            p.put("waitlist_count", productTotal);
        }
        if (matchResult.get("inventory_summary") instanceof Map<?, ?> sum) {
            int total = 0;
            for (Object po : (List<?>) matchResult.get("products")) {
                total += intOf(cast(po).get("waitlist_count"));
            }
            ((Map<String, Object>) sum).put("waitlist_unique_users", total);
        }
        matchResult.put("waitlist_count", intOf(matchResult.get("inventory_summary")
                instanceof Map<?, ?> s ? s.get("waitlist_unique_users") : 0));
        return matchResult;
    }

    @SuppressWarnings("unchecked")
    private static Map<String, Object> cast(Object o) {
        return (Map<String, Object>) o;
    }

    private static int intOf(Object o) {
        return o instanceof Number n ? n.intValue() : 0;
    }
}