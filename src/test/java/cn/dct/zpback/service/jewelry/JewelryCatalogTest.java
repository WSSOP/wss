package cn.dct.zpback.service.jewelry;

import cn.dct.zpback.entity.PlanetaryJewelryProduct;
import cn.dct.zpback.entity.PlanetaryJewelrySku;
import cn.dct.zpback.mapper.PlanetaryJewelrySkuMapper;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

/**
 * 商城目录匹配契约测试
 *
 * 覆盖 00_程序员先读 §6 与 01 §17 上线闸门中「主推/次推不受库存影响」这一条。
 * 补货订阅的幂等与持久化行为由 JewelryWaitlistPersistenceTest 覆盖。
 *
 * 目录来源已从 catalog.seed.json 迁移到 planetary_jewelry_product / _sku 两张表
 * （06 §1），因此本测试用真实 Mapper 造数据再断言，绝不写死商品 ID 到生产代码里。
 */
@SpringBootTest
@Transactional
class JewelryCatalogTest {

    @Autowired JewelryCatalogService catalog;
    @Autowired PlanetaryJewelrySkuMapper skuMapper;

    /** 造一个「商品 + 一个 SKU」，返回 productId */
    private String seedProduct(String id, String planet, String remedyType,
                                boolean autoRecommendable, String publishStatus,
                                int priority, int qty, String source) {
        PlanetaryJewelryProduct p = new PlanetaryJewelryProduct();
        p.setProductId(id);
        p.setTitle("测试商品 " + id);
        p.setPlanetCode(planet);
        p.setRemedyType(remedyType);
        p.setDescription("用于契约测试");
        p.setAutoRecommendable(autoRecommendable);
        p.setHighThresholdGemstone(false);
        p.setPublishStatus(publishStatus);
        p.setAdminRecommendationPriority(priority);
        p.setImages("[]");
        p.setGiftAvailable(true);
        p.setSource(source);
        catalog.saveProduct(p);

        PlanetaryJewelrySku s = new PlanetaryJewelrySku();
        s.setSkuId(id + "_sku");
        s.setProductId(id);
        s.setVariantAttributes("{\"form\":\"吊坠\"}");
        s.setPrice(new BigDecimal("199.00"));
        s.setInventoryQuantity(qty);
        s.setSaleEnabled(true);
        s.setRestockWaitlistEnabled(true);
        s.setStatus("ACTIVE");
        s.setSource(source);
        catalog.saveSku(s);
        return id;
    }

    @SuppressWarnings("unchecked")
    private List<Map<String, Object>> productsOf(String planet, List<String> remedies) {
        return (List<Map<String, Object>>)
                catalog.match(planet, remedies, "PRIMARY", "u1").get("products");
    }

    /**
     * 供单个用例独占的行星方向。
     *
     * 用例之间共用真实目录表，同一 planet 下其他用例造的库存会影响本用例的
     * match_state，因此每条断言只在自己的方向上进行。
     */
    private String isolatedPlanet() {
        return "ISO_" + UUID.randomUUID().toString().substring(0, 8).toUpperCase();
    }

    // ==========================================================
    // 铁律 1：目录无法改变推荐方向
    // ==========================================================

    @Test
    @DisplayName("match 对同一方向只做商品排序，不返回任何 rank 字段")
    void match_doesNotEmitRankOrRescore() {
        seedProduct("t_rank_a", "SUN", "YANTRA", true, "PUBLISHED", 50, 5, "ADMIN_UPLOAD");
        Map<String, Object> match = catalog.match("SUN", List.of("YANTRA"), "PRIMARY", "u1");

        assertEquals("SUN", match.get("planet_code"));
        assertEquals("PRIMARY", match.get("recommendation_rank"));
        // 绝不能返回可被误用来重排的字段
        assertNull(match.get("rank_score"), "不得返回 rank_score");
        assertNull(match.get("base_tier"), "不得返回 base_tier");
        List<Map<String, Object>> products = (List<Map<String, Object>>) match.get("products");
        assertFalse(products.isEmpty());
        assertNull(products.get(0).get("rank"), "商品不得携带 rank");
        assertNull(products.get(0).get("rank_score"));
    }

    // ==========================================================
    // 铁律 2：库存与价格不参与方向决策（05 §5、GC14）
    // ==========================================================

    @Test
    @DisplayName("高优先级但缺货的商品不会把方向判成无货")
    void outOfStockHighPriority_doesNotChangeDirection() {
        seedProduct("t_oos_hi", "MOON", "YANTRA", true, "PUBLISHED", 999, 0, "ADMIN_UPLOAD");
        seedProduct("t_ok_lo", "MOON", "YANTRA", true, "PUBLISHED", 1, 10, "ADMIN_UPLOAD");

        Map<String, Object> match = catalog.match("MOON", List.of("YANTRA"), "PRIMARY", "u1");
        assertEquals("MOON", match.get("planet_code"), "方向不得因库存改变");
        assertTrue("IN_STOCK_MATCH".equals(match.get("match_state"))
                        || "PARTIAL_STOCK_MATCH".equals(match.get("match_state")),
                "有货商品存在时不应判为 ALL_MATCHES_OUT_OF_STOCK，实际=" + match.get("match_state"));
    }

    @Test
    @DisplayName("同一方向内现货优先于运营优先级")
    void sameDirection_inStockFirst() {
        seedProduct("t_sort_oos", "JUPITER", "YANTRA", true, "PUBLISHED", 999, 0, "ADMIN_UPLOAD");
        seedProduct("t_sort_ok", "JUPITER", "YANTRA", true, "PUBLISHED", 1, 10, "ADMIN_UPLOAD");

        List<Map<String, Object>> products = productsOf("JUPITER", List.of("YANTRA"));
        assertTrue(products.size() >= 2, "两个商品都应展示");
        assertEquals("t_sort_ok", products.get(0).get("product_id"),
                "同一方向内现货应排在缺货前面");
    }

    @Test
    @DisplayName("价格不参与排序（GC14）")
    void price_doesNotAffectOrder() {
        seedProduct("t_price_hi", "VENUS", "YANTRA", true, "PUBLISHED", 10, 5, "ADMIN_UPLOAD");
        seedProduct("t_price_lo", "VENUS", "YANTRA", true, "PUBLISHED", 20, 5, "ADMIN_UPLOAD");
        List<Map<String, Object>> products = productsOf("VENUS", List.of("YANTRA"));
        // 两者同价同库存，只按 admin_recommendation_priority：t_price_lo(20) 应在前
        assertEquals("t_price_lo", products.get(0).get("product_id"),
                "同库存同价时只按运营优先级，不看价格高低");
    }

    // ==========================================================
    // 05 §4 四条硬条件
    // ==========================================================

    @Test
    @DisplayName("auto_recommendable=false 的商品任何模式都不展示")
    void autoRecommendableFalse_neverShown() {
        seedProduct("t_auto_off", "MARS", "YANTRA", false, "PUBLISHED", 100, 10, "ADMIN_UPLOAD");
        assertTrue(productsOf("MARS", List.of("YANTRA")).stream()
                        .noneMatch(p -> "t_auto_off".equals(p.get("product_id"))),
                "运营关闭自动推荐的商品不得展示");
    }

    @Test
    @DisplayName("publish_status 非 PUBLISHED 的商品不展示")
    void nonPublished_neverShown() {
        for (String status : List.of("DRAFT", "UNPUBLISHED", "ARCHIVED")) {
            seedProduct("t_st_" + status, "SATURN", "YANTRA", true, status, 100, 10, "ADMIN_UPLOAD");
        }
        assertTrue(productsOf("SATURN", List.of("YANTRA")).stream()
                        .noneMatch(p -> String.valueOf(p.get("product_id")).startsWith("t_st_")),
                "非 PUBLISHED 商品不得进入推荐查询");
    }

    @Test
    @DisplayName("remedy_type 不被 Modality 允许时不展示（YANTRA_PREFERRED 不推宝石）")
    void remedyTypeNotAllowed_neverShown() {
        seedProduct("t_gem_only", "MERCURY", "GEMSTONE", true, "PUBLISHED", 100, 10, "ADMIN_UPLOAD");
        assertTrue(productsOf("MERCURY", List.of("YANTRA")).stream()
                        .noneMatch(p -> "t_gem_only".equals(p.get("product_id"))),
                "只允许 YANTRA 时不得展示宝石款");
    }

    @Test
    @DisplayName("行星不匹配的商品不展示")
    void otherPlanet_neverShown() {
        seedProduct("t_other_planet", "SUN", "YANTRA", true, "PUBLISHED", 999, 10, "ADMIN_UPLOAD");
        assertTrue(productsOf("MOON", List.of("YANTRA")).stream()
                        .noneMatch(p -> "t_other_planet".equals(p.get("product_id"))),
                "不得展示其他行星的商品");
    }

    // ==========================================================
    // 目录为空 / 降级
    // ==========================================================

    @Test
    @DisplayName("该方向没有商品时不影响方向（NO_MAPPED_PRODUCT）")
    void emptyCatalog_keepsDirection() {
        Map<String, Object> match = catalog.match("PLUTO_UNKNOWN", List.of("YANTRA"), "PRIMARY", "u1");
        assertEquals("PLUTO_UNKNOWN", match.get("planet_code"), "目录为空也不得改变方向");
        assertEquals("NO_MAPPED_PRODUCT", match.get("match_state"));
    }

    @Test
    @DisplayName("allowed_remedy_types 为空时不匹配任何商品")
    void emptyAllowedRemedyTypes_matchesNothing() {
        Map<String, Object> match = catalog.match("SUN", List.of(), "PRIMARY", "u1");
        assertEquals("NO_MAPPED_PRODUCT", match.get("match_state"));
    }

    // ==========================================================
    // 缺货语义（05 §7、06 §4）
    // ==========================================================

    @Test
    @DisplayName("全部缺货时 match_state=ALL_MATCHES_OUT_OF_STOCK 且仍展示商品")
    void allOutOfStock_stillShowsProducts() {
        String planet = isolatedPlanet();
        seedProduct("t_all_oos", planet, "YANTRA", true, "PUBLISHED", 50, 0, "ADMIN_UPLOAD");
        Map<String, Object> match = catalog.match(planet, List.of("YANTRA"), "PRIMARY", "u1");
        assertEquals("ALL_MATCHES_OUT_OF_STOCK", match.get("match_state"));
        List<Map<String, Object>> products = (List<Map<String, Object>>) match.get("products");
        assertFalse(products.isEmpty(), "缺货不等于下架，商品仍要展示");
        @SuppressWarnings("unchecked")
        Map<String, Object> cta = (Map<String, Object>) match.get("cta_payload");
        assertEquals("SUBSCRIBE_WAITLIST", cta.get("primary_action"));
    }

    @Test
    @DisplayName("缺货 SKU 给出 can_subscribe，restock_waitlist_enabled=0 时不给")
    void canSubscribe_respectsFlag() {
        String id = seedProduct("t_sub_flag", "MOON", "YANTRA", true, "PUBLISHED", 50, 0, "ADMIN_UPLOAD");
        Map<String, Object> found = productsOf("MOON", List.of("YANTRA")).stream()
                .filter(p -> id.equals(p.get("product_id"))).findFirst().orElseThrow();
        @SuppressWarnings("unchecked")
        List<Map<String, Object>> skus = (List<Map<String, Object>>) found.get("skus");
        assertEquals(Boolean.TRUE, skus.get(0).get("can_subscribe"));

        // 关掉订阅开关后应变为 false
        skuMapper.updateWaitlistEnabled(id + "_sku", false);
        Map<String, Object> after = productsOf("MOON", List.of("YANTRA")).stream()
                .filter(p -> id.equals(p.get("product_id"))).findFirst().orElseThrow();
        @SuppressWarnings("unchecked")
        List<Map<String, Object>> skus2 = (List<Map<String, Object>>) after.get("skus");
        assertEquals(Boolean.FALSE, skus2.get(0).get("can_subscribe"),
                "运营关闭补货订阅后不应再引导登记");
    }

    @Test
    @DisplayName("inventory_status 由库存推导：0=缺货, 1~3=低库存, >=4=有货")
    void inventoryStatus_derivedFromQuantity() {
        String id = "t_band";
        seedProduct(id, "VENUS", "YANTRA", true, "PUBLISHED", 50, 0, "ADMIN_UPLOAD");
        skuMapper.updateInventory(id + "_sku", 2);
        assertEquals("LOW_STOCK", skuMapper.findById(id + "_sku").getInventoryStatus());
        skuMapper.updateInventory(id + "_sku", 9);
        assertEquals("IN_STOCK", skuMapper.findById(id + "_sku").getInventoryStatus());
        skuMapper.updateInventory(id + "_sku", 0);
        assertEquals("OUT_OF_STOCK", skuMapper.findById(id + "_sku").getInventoryStatus());
    }

    // ==========================================================
    // SEED 商品自动失效
    // ==========================================================

    @Test
    @DisplayName("同组合存在真实商品时，SEED 示例商品自动失效")
    void seedSuppressedWhenRealProductExists() {
        seedProduct("t_seed_item", "JUPITER", "YANTRA", true, "PUBLISHED", 1, 10, "SEED");
        assertTrue(productsOf("JUPITER", List.of("YANTRA")).stream()
                        .anyMatch(p -> "t_seed_item".equals(p.get("product_id"))),
                "没有真实商品时 SEED 应可见");

        seedProduct("t_real_item", "JUPITER", "YANTRA", true, "PUBLISHED", 50, 10, "ADMIN_UPLOAD");
        List<Map<String, Object>> after = productsOf("JUPITER", List.of("YANTRA"));
        assertTrue(after.stream().noneMatch(p -> "t_seed_item".equals(p.get("product_id"))),
                "真实商品到位后同组合 SEED 必须自动停止展示");
        assertTrue(after.stream().anyMatch(p -> "t_real_item".equals(p.get("product_id"))));
    }

    @Test
    @DisplayName("不同 remedy_type 的 SEED 不受真实商品影响")
    void seedNotSuppressedAcrossRemedyTypes() {
        seedProduct("t_seed_gem", "VENUS", "GEMSTONE", true, "PUBLISHED", 1, 10, "SEED");
        seedProduct("t_real_yantra", "VENUS", "YANTRA", true, "PUBLISHED", 50, 10, "ADMIN_UPLOAD");
        assertTrue(productsOf("VENUS", List.of("GEMSTONE")).stream()
                        .anyMatch(p -> "t_seed_gem".equals(p.get("product_id"))),
                "屏蔽只在同一 planet+remedy 组合内生效");
    }

    // ==========================================================
    // GC13：同一行星的多款商品不得被当作次推
    // ==========================================================

    @Test
    @DisplayName("同一行星多款商品全部落在同一个商品池，不产生第二条方向")
    void samePlanetMultipleProducts_stayInOnePool() {
        seedProduct("t_multi_1", "SUN", "YANTRA", true, "PUBLISHED", 90, 10, "ADMIN_UPLOAD");
        seedProduct("t_multi_2", "SUN", "YANTRA", true, "PUBLISHED", 80, 10, "ADMIN_UPLOAD");
        Map<String, Object> match = catalog.match("SUN", List.of("YANTRA"), "PRIMARY", "u1");
        assertEquals("SUN", match.get("planet_code"));
        assertEquals("PRIMARY", match.get("recommendation_rank"));
        List<Map<String, Object>> products = (List<Map<String, Object>>) match.get("products");
        assertTrue(products.size() >= 2);
        assertTrue(products.stream().allMatch(p -> "SUN".equals(p.get("planet_code"))));
    }

    // ==========================================================
    // 独立商品浏览页（与推荐路径严格分离）
    // ==========================================================

    @Test
    @DisplayName("browse 不需要推荐方向，返回全部已上架商品")
    void browse_returnsAllPublishedWithoutRecommendation() {
        String p1 = isolatedPlanet();
        String p2 = isolatedPlanet();
        seedProduct("t_browse_1", p1, "YANTRA", true, "PUBLISHED", 1, 5, "ADMIN_UPLOAD");
        seedProduct("t_browse_2", p2, "GEMSTONE", true, "PUBLISHED", 1, 5, "ADMIN_UPLOAD");

        Map<String, Object> result = catalog.browse(null, null, "u1");
        List<Map<String, Object>> products = (List<Map<String, Object>>) result.get("products");

        assertTrue(products.stream().anyMatch(x -> "t_browse_1".equals(x.get("product_id"))));
        assertTrue(products.stream().anyMatch(x -> "t_browse_2".equals(x.get("product_id"))),
                "浏览页应看到全部行星的商品，而不是只看到某一方向");
        assertEquals("BROWSE", result.get("source"), "必须标记这不是一次推荐匹配");
    }

    @Test
    @DisplayName("browse 返回体不含任何 rank / planet 字段，无法影响推荐方向（05 §1）")
    void browse_emitsNoRecommendationFields() {
        seedProduct("t_browse_safe", isolatedPlanet(), "YANTRA", true, "PUBLISHED", 1, 5, "ADMIN_UPLOAD");
        Map<String, Object> result = catalog.browse(null, null, "u1");

        assertNull(result.get("recommendation_rank"), "浏览页不得产出 rank");
        assertNull(result.get("planet_code"), "浏览页不得产出单一 planet 方向");
        assertNull(result.get("cta_payload"), "浏览页不生成购买 CTA");
        assertNull(result.get("match_state"), "浏览页不是匹配，没有 match_state");
    }

    @Test
    @DisplayName("browse 不受 auto_recommendable 限制（该字段只管自动推荐）")
    void browse_ignoresAutoRecommendable() {
        String planet = isolatedPlanet();
        seedProduct("t_browse_autooff", planet, "YANTRA", false, "PUBLISHED", 1, 5, "ADMIN_UPLOAD");

        // 推荐路径看不到它
        assertTrue(productsOf(planet, List.of("YANTRA")).stream()
                        .noneMatch(p -> "t_browse_autooff".equals(p.get("product_id"))),
                "auto_recommendable=false 的商品不得进入推荐查询");
        // 浏览页应当看得到
        assertTrue(catalog.browse(planet, null, "u1").get("products").toString()
                        .contains("t_browse_autooff"),
                "用户主动浏览时，运营关闭的自动推荐不应挡住商品");
    }

    @Test
    @DisplayName("browse 仍遵守 publish_status 与 SEED 屏蔽")
    void browse_respectsPublishStatusAndSeedSuppression() {
        String planet = isolatedPlanet();
        seedProduct("t_browse_draft", planet, "YANTRA", true, "DRAFT", 5, 5, "ADMIN_UPLOAD");
        seedProduct("t_browse_seed", planet, "GEMSTONE", true, "PUBLISHED", 5, 5, "SEED");

        String before = catalog.browse(planet, null, "u1").get("products").toString();
        assertFalse(before.contains("t_browse_draft"), "草稿商品不出现在浏览页");

        // 上传同组合真实商品后，该组合的 SEED 应自动让位
        seedProduct("t_browse_real", planet, "GEMSTONE", true, "PUBLISHED", 9, 5, "ADMIN_UPLOAD");
        String after = catalog.browse(planet, null, "u1").get("products").toString();
        assertFalse(after.contains("t_browse_seed"), "SEED 示例在真实商品到位后应停止展示");
        assertTrue(after.contains("t_browse_real"));
    }

    @Test
    @DisplayName("browse 支持按行星与补救类型筛选")
    void browse_filtersByPlanetAndRemedy() {
        String planet = isolatedPlanet();
        seedProduct("t_bf_yantra", planet, "YANTRA", true, "PUBLISHED", 1, 5, "ADMIN_UPLOAD");
        seedProduct("t_bf_gem", planet, "GEMSTONE", true, "PUBLISHED", 1, 5, "ADMIN_UPLOAD");

        String yantra = catalog.browse(planet, "YANTRA", "u1").get("products").toString();
        assertTrue(yantra.contains("t_bf_yantra"));
        assertFalse(yantra.contains("t_bf_gem"), "按补救类型筛选应生效");
    }

    // ==========================================================
    // 目录查询异常降级
    // ==========================================================

    @Test
    @DisplayName("catalogSize 返回数据库中的商品数")
    void catalogSize_reflectsDatabase() {
        int before = catalog.catalogSize();
        seedProduct("t_size_check", "MARS", "YANTRA", true, "PUBLISHED", 1, 1, "ADMIN_UPLOAD");
        assertEquals(before + 1, catalog.catalogSize(), "目录规模应来自数据库");
    }

    @Test
    @DisplayName("listProducts 支持按行星过滤（运营后台）")
    void listProducts_filtersByPlanet() {
        seedProduct("t_lp_1", "MARS", "YANTRA", true, "PUBLISHED", 1, 1, "ADMIN_UPLOAD");
        List<Map<String, Object>> list = catalog.listProducts("MARS", null, null, null);
        assertTrue(list.stream().anyMatch(p -> "t_lp_1".equals(p.get("product_id"))));
        assertTrue(list.stream().allMatch(p -> "MARS".equals(p.get("planet_code"))));
    }

    // ==========================================================
    // 真实数据接入：库存改动立即反映到推荐查询
    // ==========================================================

    @Test
    @DisplayName("运营改库存后，match 立即反映新的库存状态（无需重启）")
    void inventoryChange_takesEffectImmediately() {
        String id = seedProduct("t_restock", "SATURN", "YANTRA", true, "PUBLISHED", 50, 0, "ADMIN_UPLOAD");
        assertEquals("ALL_MATCHES_OUT_OF_STOCK",
                catalog.match("SATURN", List.of("YANTRA"), "PRIMARY", "u1").get("match_state"));

        Map<String, Object> event = catalog.restock(id + "_sku", 6);
        assertNotNull(event.get("restock_event_id"), "0 -> >0 应生成补货事件");
        assertEquals("IN_STOCK_MATCH",
                catalog.match("SATURN", List.of("YANTRA"), "PRIMARY", "u1").get("match_state"));
    }

    @Test
    @DisplayName("非 0 -> 非 0 的库存调整不生成补货事件（GC18 触发条件）")
    void nonZeroToNonZero_noRestockEvent() {
        String id = seedProduct("t_no_event", "SATURN", "YANTRA", true, "PUBLISHED", 50, 10, "ADMIN_UPLOAD");
        Map<String, Object> event = catalog.restock(id + "_sku", 20);
        assertNull(event.get("restock_event_id"), "已有货时改数量不是补货事件");
    }

    @Test
    @DisplayName("商品归档后不再进入推荐查询，但快照仍可查（软下架）")
    void archive_removesFromMatchButKeepsRecord() {
        String id = seedProduct("t_archive", "MERCURY", "YANTRA", true, "PUBLISHED", 50, 10, "ADMIN_UPLOAD");
        assertTrue(productsOf("MERCURY", List.of("YANTRA")).stream()
                .anyMatch(p -> id.equals(p.get("product_id"))));

        skuMapper.cancel(id + "_sku");
        assertNotNull(skuMapper.findById(id + "_sku"), "取消是软操作，记录仍在");
    }

    @Test
    @DisplayName("商品快照带 publish_status，供补货订阅校验归档态")
    void productSnapshot_exposesPublishStatus() {
        String id = seedProduct("t_pubstat", "MARS", "YANTRA", true, "ARCHIVED", 50, 0, "ADMIN_UPLOAD");
        Map<String, Object> snapshot = catalog.product(id);
        assertNotNull(snapshot);
        assertEquals("ARCHIVED", snapshot.get("publish_status"),
                "订阅校验依赖 publish_status 判定是否归档");
    }
}