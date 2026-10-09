package cn.dct.zpback.service;

import cn.dct.zpback.entity.MallCartItem;
import cn.dct.zpback.entity.PlanetaryJewelryProduct;
import cn.dct.zpback.entity.PlanetaryJewelrySku;
import cn.dct.zpback.exception.LearningValidationException;
import cn.dct.zpback.mapper.MallCartMapper;
import cn.dct.zpback.mapper.PlanetaryJewelryProductMapper;
import cn.dct.zpback.mapper.PlanetaryJewelrySkuMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

/**
 * 问星渡｜商城购物车 —— 锁《【我的】页面开发文档》§12.1 与 §16.3
 *
 * <b>三组最容易被改坏的规则</b>：
 * <ol>
 *   <li><b>§12.1 计数口径</b>：有效可结算行 = ACTIVE + selected + quantity>0。
 *       刻意<b>不</b>剔除缺货/下架行，否则用户会看到角标与列表对不上。</li>
 *   <li><b>§16.3 所有权</b>：改数量/勾选/删除都必须带 userId 校验，
 *       且不得回显「该商品属于他人」。</li>
 *   <li><b>价格现读</b>：不存快照，列表价格必须来自 SKU。</li>
 * </ol>
 */
class MallCartServiceTest {

    private MallCartService svc;
    private CartMapperStub cart;
    private ProductMapperStub products;
    private SkuMapperStub skus;

    private static final String USER = "user_u1";
    private static final String OTHER = "user_u2";

    @BeforeEach
    void setUp() {
        cart = new CartMapperStub();
        products = new ProductMapperStub();
        skus = new SkuMapperStub();
        svc = new MallCartService(cart, products, skus);
    }

    // ==========================================================
    // §12.1 计数口径
    // ==========================================================

    @Test
    @DisplayName("只数 ACTIVE + 勾选 + 数量>0 的行")
    void count_followsDoc12_1() {
        cart.rows.add(row("s1", true, 1, "ACTIVE"));
        cart.rows.add(row("s2", true, 2, "ACTIVE"));
        cart.rows.add(row("s3", false, 1, "ACTIVE")); // 未勾选
        cart.rows.add(row("s4", true, 1, "REMOVED")); // 已软删

        assertEquals(2, svc.countSettleableRows(USER));
    }

    @Test
    @DisplayName("计数按 user 隔离，不数别人的购物车")
    void count_isPerUser() {
        cart.rows.add(row("s1", true, 1, "ACTIVE", USER));
        cart.rows.add(row("s2", true, 1, "ACTIVE", OTHER));
        cart.rows.add(row("s3", true, 1, "ACTIVE", OTHER));

        assertEquals(1, svc.countSettleableRows(USER));
        assertEquals(2, svc.countSettleableRows(OTHER));
    }

    @Test
    @DisplayName("未登录计数返回 0 而不是抛错（§8.1 入口保持可见）")
    void count_anonymousReturnsZero() {
        cart.rows.add(row("s1", true, 1, "ACTIVE"));

        assertEquals(0, svc.countSettleableRows(null));
        assertEquals(0, svc.countSettleableRows("anonymous"));
    }

    @Test
    @DisplayName("计数失败降级为 0，不阻塞整页（§19.5）")
    void count_failureDegrades() {
        cart.throwOnCount = true;

        assertEquals(0, svc.countSettleableRows(USER));
    }

    // ==========================================================
    // 列表：缺货行要显示但不计入合计
    // ==========================================================

    @Test
    @DisplayName("缺货行仍显示，但 available=false 且不计入合计")
    void list_keepsOutOfStockRow() {
        givenProduct("p1", "PUBLISHED", "月光石手链");
        givenSku("s1", "p1", "99.00", 3);
        givenSku("s2", "p2", "199.00", 0);           // 缺货
        givenProduct("p2", "PUBLISHED", "紫水晶手串");
        // product_id 必须各自对应，否则两行都命中 p1，标题会串
        cart.rows.add(row("s1", "p1", true, 2, "ACTIVE", USER));
        cart.rows.add(row("s2", "p2", true, 1, "ACTIVE", USER));

        Map<String, Object> out = svc.list(USER);
        @SuppressWarnings("unchecked")
        List<Map<String, Object>> items = (List<Map<String, Object>>) out.get("items");

        assertEquals(2, items.size(), "缺货行不能从列表消失，否则用户以为购物车丢了东西");

        Map<String, Object> oos = items.stream()
                .filter(i -> "s2".equals(i.get("sku_id"))).findFirst().orElseThrow();

        assertEquals(false, oos.get("available"));
        assertEquals("暂时缺货，可到货提醒我", oos.get("unavailable_reason"));
        assertEquals("紫水晶手串", oos.get("title"), "缺货行仍要显示商品名");

        assertEquals(1, out.get("selectedRows"), "缺货行不计入已选行数");
        assertEquals(new BigDecimal("198.00"), out.get("totalAmount"),
                "合计只算可结算行：99×2=198，不含缺货的 199");
    }

    @Test
    @DisplayName("商品归档的行保留占位标题")
    void list_archivedProductKeepsRow() {
        givenProduct("p1", "PUBLISHED", "月光石手链");
        givenSku("s1", "p1", "99.00", 5);
        cart.rows.add(row("s1", true, 1, "ACTIVE"));
        // browse() 只返回 PUBLISHED，商品归档后查不到
        products.browseResult.clear();

        Map<String, Object> out = svc.list(USER);
        @SuppressWarnings("unchecked")
        List<Map<String, Object>> items = (List<Map<String, Object>>) out.get("items");

        assertEquals(1, items.size());
        assertEquals("商品已下架", items.get(0).get("title"));
        assertEquals(false, items.get(0).get("available"));
        assertEquals(BigDecimal.ZERO, out.get("totalAmount"));
    }

    @Test
    @DisplayName("价格来自 SKU 现读，不是行上的快照")
    void list_priceFromSku() {
        givenProduct("p1", "PUBLISHED", "月光石手链");
        givenSku("s1", "p1", "128.00", 5);
        cart.rows.add(row("s1", true, 2, "ACTIVE"));

        Map<String, Object> out = svc.list(USER);
        @SuppressWarnings("unchecked")
        List<Map<String, Object>> items = (List<Map<String, Object>>) out.get("items");

        assertEquals(new BigDecimal("128.00"), items.get(0).get("price"));
        assertEquals(new BigDecimal("256.00"), items.get(0).get("amount"));
    }

    @Test
    @DisplayName("空购物车返回结构完整的空结果")
    void list_empty() {
        Map<String, Object> out = svc.list(USER);

        assertEquals(0, out.get("totalRows"));
        assertEquals(0, out.get("selectedRows"));
        assertEquals(BigDecimal.ZERO, out.get("totalAmount"));
        assertTrue(((List<?>) out.get("items")).isEmpty());
    }

    @Test
    @DisplayName("查询不产生 N+1：SKU 一次性批量取回")
    void list_batchesSkuLookup() {
        givenProduct("p1", "PUBLISHED", "手链");
        for (int i = 1; i <= 20; i++) {
            givenSku("s" + i, "p1", "10.00", 5);
            cart.rows.add(row("s" + i, true, 1, "ACTIVE"));
        }

        svc.list(USER);

        assertEquals(1, skus.findByIdsCalls, "20 行应只查一次 SKU，而不是 20 次");
    }

    // ==========================================================
    // §16.3 所有权与鉴权
    // ==========================================================

    @Test
    @DisplayName("改数量前校验行归属，越权时拒绝且不泄露")
    void updateQuantity_rejectsOtherUsersRow() {
        givenProduct("p1", "PUBLISHED", "手链");
        givenSku("s1", "p1", "99.00", 5);
        cart.rows.add(row("s1", true, 1, "ACTIVE", OTHER));

        LearningValidationException ex = assertThrows(
                LearningValidationException.class,
                () -> svc.updateQuantity(USER, "s1", 5));

        assertEquals("MALL_ITEM_NOT_FOUND", ex.getCode());
        assertFalse(ex.getMessage().contains(OTHER),
                "不得回显他人 userId，否则泄露他人购物车（§16.3）");
        assertEquals(1, cart.rows.get(0).getQuantity(), "被拒绝时不得改动数据");
    }

    @Test
    @DisplayName("删除与勾选同样校验归属")
    void removeAndSelect_rejectOtherUsersRow() {
        cart.rows.add(row("s1", true, 1, "ACTIVE", OTHER));

        assertEquals("MALL_ITEM_NOT_FOUND", assertThrows(LearningValidationException.class,
                () -> svc.remove(USER, "s1")).getCode());
        assertEquals("MALL_ITEM_NOT_FOUND", assertThrows(LearningValidationException.class,
                () -> svc.setSelected(USER, "s1", false)).getCode());
    }

    @Test
    @DisplayName("未登录不能读写购物车")
    void anonymous_cannotOperate() {
        assertEquals("MALL_LOGIN_REQUIRED", assertThrows(LearningValidationException.class,
                () -> svc.list("anonymous")).getCode());
        assertEquals("MALL_LOGIN_REQUIRED", assertThrows(LearningValidationException.class,
                () -> svc.add("anonymous", "s1", 1)).getCode());
    }

    @Test
    @DisplayName("数量上限 99，防止误触连点")
    void quantity_capped() {
        givenProduct("p1", "PUBLISHED", "手链");
        givenSku("s1", "p1", "99.00", 500);

        assertEquals("MALL_QTY_TOO_LARGE", assertThrows(LearningValidationException.class,
                () -> svc.add(USER, "s1", 100)).getCode());
    }

    @Test
    @DisplayName("数量必须为正")
    void quantity_mustBePositive() {
        assertEquals("MALL_QTY_INVALID", assertThrows(LearningValidationException.class,
                () -> svc.add(USER, "s1", 0)).getCode());
    }

    // ==========================================================
    // 加购校验
    // ==========================================================

    @Test
    @DisplayName("缺货仍可加购，交给结算环节报错")
    void add_allowsOutOfStock() {
        givenProduct("p1", "PUBLISHED", "手链");
        givenSku("s1", "p1", "99.00", 0);

        MallCartItem r = svc.add(USER, "s1", 1);

        assertEquals("s1", r.getSkuId());
        assertEquals("p1", r.getProductId());
        assertTrue(r.getSelected(), "新加购默认勾选，否则用户会以为没加进去");
    }

    @Test
    @DisplayName("停售 SKU 不可加购")
    void add_rejectsCancelledSku() {
        givenProduct("p1", "PUBLISHED", "手链");
        givenSku("s1", "p1", "99.00", 5);
        skus.byId.get("s1").setStatus("CANCELLED");

        assertEquals("MALL_SKU_UNAVAILABLE", assertThrows(LearningValidationException.class,
                () -> svc.add(USER, "s1", 1)).getCode());
    }

    @Test
    @DisplayName("已下架商品不可加购")
    void add_rejectsArchivedProduct() {
        givenProduct("p1", "ARCHIVED", "手链");
        givenSku("s1", "p1", "99.00", 5);

        assertEquals("MALL_PRODUCT_UNAVAILABLE", assertThrows(LearningValidationException.class,
                () -> svc.add(USER, "s1", 1)).getCode());
    }

    @Test
    @DisplayName("重复加购同一 SKU 走 upsert 累加，不产生新行")
    void add_usesUpsert() {
        givenProduct("p1", "PUBLISHED", "手链");
        givenSku("s1", "p1", "99.00", 5);

        svc.add(USER, "s1", 2);
        svc.add(USER, "s1", 3);

        assertEquals(2, cart.upsertCalls);
        assertEquals(0, cart.insertCalls,
                "必须靠唯一键 upsert，不能先查后插——并发加购会出现两行");
    }

    // ==========================================================
    // 桩
    // ==========================================================

    private void givenProduct(String id, String status, String title) {
        PlanetaryJewelryProduct p = new PlanetaryJewelryProduct();
        p.setProductId(id);
        p.setTitle(title);
        p.setSubtitle("副标题");
        p.setPublishStatus(status);
        p.setImages("[\"https://cdn/x.jpg\"]");
        products.all.put(id, p);
        if ("PUBLISHED".equals(status)) {
            products.browseResult.add(p);
        }
    }

    private void givenSku(String skuId, String productId, String price, int stock) {
        PlanetaryJewelrySku s = new PlanetaryJewelrySku();
        s.setSkuId(skuId);
        s.setProductId(productId);
        s.setPrice(new BigDecimal(price));
        s.setInventoryQuantity(stock);
        s.setStatus("ACTIVE");
        skus.byId.put(skuId, s);
    }

    private static MallCartItem row(String skuId, boolean selected, int qty, String status) {
        return row(skuId, "p1", selected, qty, status, USER);
    }

    private static MallCartItem row(String skuId, boolean selected, int qty, String status, String userId) {
        return row(skuId, "p1", selected, qty, status, userId);
    }

    private static MallCartItem row(String skuId, String productId, boolean selected, int qty,
                                    String status, String userId) {
        MallCartItem r = new MallCartItem();
        r.setCartItemId("ci_" + skuId);
        r.setUserId(userId);
        r.setSkuId(skuId);
        r.setProductId(productId);
        r.setQuantity(qty);
        r.setSelected(selected);
        r.setItemStatus(status);
        return r;
    }

    static final class CartMapperStub implements MallCartMapper {
        final List<MallCartItem> rows = new ArrayList<>();
        int upsertCalls;
        int insertCalls;
        boolean throwOnCount;

        @Override
        public int countSettleableRows(String userId) {
            if (throwOnCount) throw new RuntimeException("模拟购物车表不可用");
            int n = 0;
            for (MallCartItem r : rows) {
                if (userId.equals(r.getUserId())
                        && "ACTIVE".equals(r.getItemStatus())
                        && Boolean.TRUE.equals(r.getSelected())
                        && r.getQuantity() != null && r.getQuantity() > 0) {
                    n++;
                }
            }
            return n;
        }

        @Override
        public int countActiveRows(String userId) { return rows.size(); }

        @Override
        public List<MallCartItem> findActiveByUser(String userId) {
            List<MallCartItem> out = new ArrayList<>();
            for (MallCartItem r : rows) {
                if (userId.equals(r.getUserId()) && "ACTIVE".equals(r.getItemStatus())) {
                    out.add(r);
                }
            }
            return out;
        }

        @Override
        public MallCartItem findByUserAndSku(String userId, String skuId) {
            for (MallCartItem r : rows) {
                if (userId.equals(r.getUserId()) && skuId.equals(r.getSkuId())
                        && "ACTIVE".equals(r.getItemStatus())) {
                    return r;
                }
            }
            return null;
        }

        @Override
        public int upsertIncrement(MallCartItem item) {
            upsertCalls++;
            for (MallCartItem r : rows) {
                if (item.getUserId().equals(r.getUserId())
                        && item.getSkuId().equals(r.getSkuId())) {
                    r.setQuantity(r.getQuantity() + item.getQuantity());
                    return 1;
                }
            }
            rows.add(item);
            return 1;
        }

        @Override public int updateQuantity(String u, String s, int q) { return 1; }
        @Override public int updateSelected(String u, String s, boolean sel) { return 1; }
        @Override public int updateSelectedAll(String u, boolean sel) { return 1; }
        @Override public int softRemove(String u, String s) { return 1; }
        @Override public int softRemoveSelected(String u) { return 1; }
    }

    static final class ProductMapperStub implements PlanetaryJewelryProductMapper {
        final Map<String, PlanetaryJewelryProduct> all = new LinkedHashMap<>();
        final List<PlanetaryJewelryProduct> browseResult = new ArrayList<>();

        @Override
        public List<PlanetaryJewelryProduct> browse(String planet, String remedyType) {
            return browseResult;
        }

        @Override
        public PlanetaryJewelryProduct findById(String productId) {
            return all.get(productId);
        }

        @Override public List<PlanetaryJewelryProduct> findMatchable(
                String p, List<String> m) { return List.of(); }
        @Override public List<PlanetaryJewelryProduct> listForAdmin(
                String p, String r, String s, String src) { return List.of(); }
        @Override public int count() { return all.size(); }
        @Override public int upsert(PlanetaryJewelryProduct p) { return 0; }
        @Override public int updatePublishStatus(String id, String s) { return 0; }
        @Override public int updateAutoRecommendable(String id, boolean a) { return 0; }
        @Override public int updatePriority(String id, int p) { return 0; }
        @Override public int archive(String id) { return 0; }
    }

    static final class SkuMapperStub implements PlanetaryJewelrySkuMapper {
        final Map<String, PlanetaryJewelrySku> byId = new LinkedHashMap<>();
        int findByIdsCalls;

        @Override
        public PlanetaryJewelrySku findById(String skuId) {
            return byId.get(skuId);
        }

        @Override
        public List<PlanetaryJewelrySku> findByIds(List<String> skuIds) {
            findByIdsCalls++;
            List<PlanetaryJewelrySku> out = new ArrayList<>();
            for (String id : skuIds) {
                if (byId.containsKey(id)) out.add(byId.get(id));
            }
            return out;
        }

        @Override public List<PlanetaryJewelrySku> findActiveByProduct(String p) { return List.of(); }
        @Override public List<PlanetaryJewelrySku> findActiveByProducts(List<String> p) { return List.of(); }
        @Override public PlanetaryJewelrySku findByIdForUpdate(String s) { return byId.get(s); }
        @Override public List<PlanetaryJewelrySku> listForAdmin(String p, String s) { return List.of(); }
        @Override public int upsert(PlanetaryJewelrySku s) { return 0; }
        @Override public int updateInventory(String s, int q) { return 0; }
        @Override public int applyRestock(String s, int q) { return 0; }
        @Override public int cancel(String s) { return 0; }
        @Override public int updateWaitlistEnabled(String s, boolean e) { return 0; }
        // 商城订单新增的下单扣减 / 取消回补。购物车链路不涉及，恒返回 0
        @Override public int decrementInventory(String s, int q) { return 0; }
        @Override public int releaseInventory(String s, int q) { return 0; }
        @Override public int count() { return byId.size(); }
    }
}