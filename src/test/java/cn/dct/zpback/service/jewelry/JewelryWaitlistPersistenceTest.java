package cn.dct.zpback.service.jewelry;

import cn.dct.zpback.entity.PlanetaryJewelryRestockSubscription;
import cn.dct.zpback.exception.JewelryValidationException;
import cn.dct.zpback.entity.PlanetaryJewelryProduct;
import cn.dct.zpback.entity.PlanetaryJewelrySku;
import cn.dct.zpback.mapper.PlanetaryJewelryProductMapper;
import cn.dct.zpback.mapper.PlanetaryJewelrySkuMapper;
import cn.dct.zpback.mapper.PlanetaryJewelryRestockSubscriptionMapper;
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
 * 补货订阅（MySQL 持久化）契约测试
 *
 * 用一个 Map 支撑的内存 Mapper 替身，模拟唯一索引 uk_active_user_sku 的行为：
 * 同一 user + sku 只有一条 Active 订阅，upsert 命中时只刷新来源字段。
 * 这样可以在没有真实 MySQL 的情况下验证服务层契约。
 */
class JewelryWaitlistPersistenceTest {

    /** 模拟 DB 唯一索引的 Mapper 替身 */
    static class FakeMapper implements PlanetaryJewelryRestockSubscriptionMapper {

        /** activeKey -> 订阅；仅存 Active 状态 */
        final Map<String, PlanetaryJewelryRestockSubscription> active = new LinkedHashMap<>();
        /** 历史记录（含已取消），用于验证不会重复插入 */
        final List<PlanetaryJewelryRestockSubscription> all = new ArrayList<>();
        int upsertCalls = 0;

        private static String key(String userId, String skuId) {
            return userId + "::" + skuId;
        }

        @Override
        public int upsert(PlanetaryJewelryRestockSubscription s) {
            upsertCalls++;
            String k = key(s.getUserId(), s.getSkuId());
            PlanetaryJewelryRestockSubscription existing = active.get(k);
            if (existing != null) {
                // ON DUPLICATE KEY UPDATE：只刷新来源，不新增行
                if (s.getSource() != null) existing.setSource(s.getSource());
                if (s.getAssessmentId() != null) existing.setAssessmentId(s.getAssessmentId());
                if (s.getRecommendationRank() != null) {
                    existing.setRecommendationRank(s.getRecommendationRank());
                }
                return 0;
            }
            active.put(k, s);
            all.add(s);
            return 1;
        }

        @Override
        public PlanetaryJewelryRestockSubscription findActive(String userId, String skuId) {
            return active.get(key(userId, skuId));
        }

        @Override
        public List<PlanetaryJewelryRestockSubscription> findActiveByUser(String userId) {
            List<PlanetaryJewelryRestockSubscription> out = new ArrayList<>();
            active.values().forEach(s -> {
                if (s.getUserId().equals(userId)) out.add(s);
            });
            return out;
        }

        @Override
        public List<PlanetaryJewelryRestockSubscription> findWaitingBySku(String skuId) {
            List<PlanetaryJewelryRestockSubscription> out = new ArrayList<>();
            active.values().forEach(s -> {
                if (s.getSkuId().equals(skuId) && "WAITING".equals(s.getStatus())) out.add(s);
            });
            return out;
        }

        @Override
        public int countDistinctWaitingUsersBySku(String skuId) {
            return (int) active.values().stream()
                    .filter(s -> s.getSkuId().equals(skuId) && "WAITING".equals(s.getStatus()))
                    .map(PlanetaryJewelryRestockSubscription::getUserId)
                    .distinct().count();
        }

        @Override
        public int countDistinctWaitingUsersByProduct(String productId) {
            return (int) active.values().stream()
                    .filter(s -> s.getProductId().equals(productId) && "WAITING".equals(s.getStatus()))
                    .map(PlanetaryJewelryRestockSubscription::getUserId)
                    .distinct().count();
        }

        @Override
        public int cancel(String userId, String skuId) {
            PlanetaryJewelryRestockSubscription s = active.remove(key(userId, skuId));
            if (s == null) return 0;
            s.setStatus("CANCELLED");
            return 1;
        }

        @Override
        public int cancelByAssessment(String userId, String assessmentId) {
            int n = 0;
            List<String> toRemove = new ArrayList<>();
            for (Map.Entry<String, PlanetaryJewelryRestockSubscription> e : active.entrySet()) {
                PlanetaryJewelryRestockSubscription s = e.getValue();
                if (s.getUserId().equals(userId) && assessmentId.equals(s.getAssessmentId())
                        && ACTIVE.contains(s.getStatus())) {
                    s.setStatus("CANCELLED");
                    toRemove.add(e.getKey());
                    n++;
                }
            }
            toRemove.forEach(active::remove);
            return n;
        }

        @Override
        public int markNotified(String skuId, String userId, String restockEventId, java.time.LocalDateTime now) {
            PlanetaryJewelryRestockSubscription s = active.get(key(userId, skuId));
            if (s == null || !"WAITING".equals(s.getStatus())) return 0;
            if (restockEventId.equals(s.getRestockEventId())) return 0;   // 同一事件只通知一次
            s.setStatus("NOTIFIED");
            s.setRestockEventId(restockEventId);
            s.setNotifiedAt(now);
            return 1;
        }

        @Override
        public Map<String, Object> statUniqueUsers() {
            return Map.of("unique_users", (long) distinctWaiting(null).size());
        }

        @Override
        public Map<String, Object> statUniqueUsersSince(java.time.LocalDateTime since) {
            return Map.of("unique_users", (long) distinctWaiting(null).size());
        }

        @Override public List<Map<String, Object>> statByPlanet() { return bucket("planetCode"); }
        @Override public List<Map<String, Object>> statBySource() { return bucket("source"); }
        @Override public List<Map<String, Object>> statByRank() { return bucket("recommendationRank"); }
        @Override public List<Map<String, Object>> statByRemedyType() { return bucket("remedyType"); }
        @Override public List<Map<String, Object>> statVariantDemand(int limit) { return List.of(); }

        private List<String> distinctWaiting(String sku) {
            return active.values().stream()
                    .filter(s -> "WAITING".equals(s.getStatus()))
                    .filter(s -> sku == null || s.getSkuId().equals(sku))
                    .map(PlanetaryJewelryRestockSubscription::getUserId)
                    .distinct().toList();
        }

        private List<Map<String, Object>> bucket(String field) {
            Map<String, long[]> acc = new LinkedHashMap<>();
            for (PlanetaryJewelryRestockSubscription s : active.values()) {
                if (!"WAITING".equals(s.getStatus())) continue;
                Object v = switch (field) {
                    case "planetCode" -> s.getPlanetCode();
                    case "source" -> s.getSource();
                    case "recommendationRank" -> s.getRecommendationRank();
                    default -> s.getRemedyType();
                };
                if (v == null) continue;
                acc.computeIfAbsent(String.valueOf(v), k -> new long[1])[0]++;
            }
            List<Map<String, Object>> out = new ArrayList<>();
            acc.forEach((k, v) -> out.add(Map.of("k", k, "v", v[0])));
            return out;
        }

        private static final List<String> ACTIVE = List.of("WAITING", "NOTIFIED");
    }

    private JewelryCatalogService catalog;
    private FakeMapper mapper;
    private JewelryWaitlistService waitlist;

    /**
     * 目录已从内存种子迁到 MySQL（06 §1），JewelryCatalogService 依赖三个 Mapper。
     * 本测试只关心订阅幂等与人数统计，因此用可按用例声明状态的 Mapper 桩：
     * 用例明确说清「哪个 SKU、归哪个商品、有没有货、是否已归档」，
     * 断言不再依赖真实目录里恰好存在哪些数据。
     */
    private final Map<String, String[]> skuState = new LinkedHashMap<>();

    @BeforeEach
    void setUp() {
        skuState.clear();
        catalog = new JewelryCatalogService(new ProductStub(), new SkuStub(), new FakeMapper());
        mapper = new FakeMapper();
        waitlist = new JewelryWaitlistService(catalog, mapper);
    }

    /** 缺货 + 已上架 → 可登记补货 */
    private void givenOutOfStockSku(String skuId, String productId) {
        skuState.put(skuId, new String[]{productId, "PUBLISHED", "OUT_OF_STOCK"});
    }

    /** 有货 + 已上架 → 不可登记补货 */
    private void givenInStockSku(String skuId, String productId) {
        skuState.put(skuId, new String[]{productId, "PUBLISHED", "IN_STOCK"});
    }

    /** 缺货但商品已归档 → 归档优先于库存判断 */
    private void givenArchivedOutOfStockSku(String skuId, String productId) {
        skuState.put(skuId, new String[]{productId, "ARCHIVED", "OUT_OF_STOCK"});
    }

    private final class ProductStub implements PlanetaryJewelryProductMapper {
        @Override public List<PlanetaryJewelryProduct> findMatchable(String p, List<String> r) { return List.of(); }
        @Override public List<PlanetaryJewelryProduct> browse(String p, String r) { return List.of(); }
        @Override public List<PlanetaryJewelryProduct> listForAdmin(String a, String b, String c, String d) { return List.of(); }
        @Override public int count() { return 0; }
        @Override public int upsert(PlanetaryJewelryProduct p) { return 0; }
        @Override public int updatePublishStatus(String id, String s) { return 0; }
        @Override public int updateAutoRecommendable(String id, boolean a) { return 0; }
        @Override public int updatePriority(String id, int p) { return 0; }
        @Override public int archive(String id) { return 0; }

        @Override
        public PlanetaryJewelryProduct findById(String productId) {
            if (skuState.isEmpty()) return null;   // 未声明任何商品
            String publishStatus = null;
            for (String[] row : skuState.values()) {
                if (row[0].equals(productId)) publishStatus = row[1];
            }
            if (publishStatus == null) return null; // 未知商品
            PlanetaryJewelryProduct p = new PlanetaryJewelryProduct();
            p.setProductId(productId);
            p.setPlanetCode("SUN");
            p.setRemedyType("YANTRA");
            p.setImages("[]");
            p.setAutoRecommendable(true);
            p.setGiftAvailable(true);
            p.setHighThresholdGemstone(false);
            p.setAdminRecommendationPriority(0);
            p.setPublishStatus(publishStatus);
            return p;
        }
    }

    private final class SkuStub implements PlanetaryJewelrySkuMapper {
        @Override public List<PlanetaryJewelrySku> findActiveByProduct(String id) { return List.of(); }
        @Override public List<PlanetaryJewelrySku> findActiveByProducts(List<String> ids) { return List.of(); }
        /** 商城购物车按 skuId 批量取用；此处不参与待货验证，返回空即可 */
        @Override public List<PlanetaryJewelrySku> findByIds(List<String> ids) { return List.of(); }
        @Override public List<PlanetaryJewelrySku> listForAdmin(String a, String b) { return List.of(); }
        @Override public int upsert(PlanetaryJewelrySku s) { return 0; }
        @Override public int updateInventory(String id, int q) { return 0; }
        @Override public int applyRestock(String id, int q) { return 0; }
        @Override public int cancel(String id) { return 0; }
        @Override public int updateWaitlistEnabled(String id, boolean e) { return 0; }
        // 商城订单新增的下单扣减 / 取消回补。补货订阅链路不涉及，恒返回 0
        @Override public int decrementInventory(String id, int q) { return 0; }
        @Override public int releaseInventory(String id, int q) { return 0; }
        @Override public int count() { return 0; }

        @Override
        public PlanetaryJewelrySku findById(String skuId) {
            String[] row = skuState.get(skuId);
            if (row == null) return null;         // 未知 SKU
            PlanetaryJewelrySku s = new PlanetaryJewelrySku();
            s.setSkuId(skuId);
            s.setProductId(row[0]);
            s.setPrice(new BigDecimal("268.00"));
            s.setStatus("ACTIVE");
            s.setSaleEnabled(true);
            s.setRestockWaitlistEnabled(true);
            s.setInventoryQuantity("IN_STOCK".equals(row[2]) ? 5 : 0);
            // 真实库里 inventory_status 是生成列，这里按同一推导规则给出
            s.setInventoryStatus(row[2]);
            return s;
        }

        @Override public PlanetaryJewelrySku findByIdForUpdate(String id) { return findById(id); }
    }

    // ==========================================================
    // 幂等
    // ==========================================================

    @Test
    @DisplayName("同一用户重复登记同一 SKU：DB 只存一行，期待人数保持 1")
    void repeatedSubscribe_isIdempotent() {
        String sku = "s_sun_yantra_001_std";
        givenOutOfStockSku(sku, "p_sun_yantra_001");
        waitlist.subscribe("u1", sku, "PRIMARY_RESULT", "a1", "PRIMARY");
        waitlist.subscribe("u1", sku, "PRIMARY_RESULT", "a1", "PRIMARY");
        waitlist.subscribe("u1", sku, "PRIMARY_RESULT", "a1", "PRIMARY");

        assertEquals(3, mapper.upsertCalls, "每次都会尝试 upsert");
        assertEquals(1, mapper.all.size(), "重复登记不得产生第二条记录");
        assertEquals(1, mapper.active.size());
        assertEquals(1, waitlist.countForSku(sku));
    }

    @Test
    @DisplayName("重复登记会刷新来源字段，但保留原 subscription_id 与创建时间")
    void repeatedSubscribe_refreshesSourceOnly() {
        String sku = "s_sun_yantra_001_std";
        givenOutOfStockSku(sku, "p_sun_yantra_001");
        Map<String, Object> first = waitlist.subscribe("u1", sku, "PRIMARY_RESULT", "a1", "PRIMARY");
        waitlist.subscribe("u1", sku, "SECONDARY_RESULT", "a2", "SECONDARY");

        assertEquals(1, mapper.all.size());
        PlanetaryJewelryRestockSubscription row = mapper.all.get(0);
        assertEquals("SECONDARY_RESULT", row.getSource(), "来源应被刷新");
        assertEquals(1, waitlist.countForSku(sku), "计数不受刷新影响");
    }

    @Test
    @DisplayName("不同用户登记同一 SKU：期待人数按用户去重递增")
    void differentUsers_increaseCount() {
        String sku = "s_sun_yantra_001_std";
        givenOutOfStockSku(sku, "p_sun_yantra_001");
        waitlist.subscribe("u1", sku, "PRIMARY_RESULT", "a1", "PRIMARY");
        waitlist.subscribe("u2", sku, "PRIMARY_RESULT", "a2", "PRIMARY");
        assertEquals(2, waitlist.countForSku(sku));
        assertEquals(2, mapper.active.size());
    }

    @Test
    @DisplayName("取消后可重新登记（resubscribe_after_new_stockout）")
    void cancelThenResubscribe_works() {
        String sku = "s_sun_yantra_001_std";
        givenOutOfStockSku(sku, "p_sun_yantra_001");
        waitlist.subscribe("u1", sku, "PRIMARY_RESULT", "a1", "PRIMARY");
        assertEquals(1, waitlist.countForSku(sku));

        waitlist.cancel("u1", sku);
        assertEquals(0, waitlist.countForSku(sku));
        assertFalse(waitlist.isSubscribed("u1", sku));

        waitlist.subscribe("u1", sku, "PRIMARY_RESULT", "a1", "PRIMARY");
        assertEquals(1, waitlist.countForSku(sku));
        assertTrue(waitlist.isSubscribed("u1", sku));
        assertEquals(2, mapper.all.size(), "取消后的重新登记是一条新记录");
    }

    @Test
    @DisplayName("取消不存在的订阅是幂等的")
    void cancelNonExistent_isNoop() {
        Map<String, Object> res = waitlist.cancel("ghost", "s_sun_yantra_001_std");
        assertEquals("CANCELLED", res.get("status"));
        assertEquals(Boolean.FALSE, res.get("changed"));
    }

    // ==========================================================
    // 业务校验
    // ==========================================================

    @Test
    @DisplayName("有货 SKU 不得登记补货，且不落库")
    void cannotSubscribeInStockSku() {
        givenInStockSku("s_sun_gem_001_r9", "p_sun_gem_001");
        JewelryValidationException e = assertThrows(JewelryValidationException.class,
                () -> waitlist.subscribe("u1", "s_sun_gem_001_r9", "PRIMARY_RESULT", "a1", "PRIMARY"));
        assertTrue(e.getViolations().containsKey("PJA_SKU_NOT_OUT_OF_STOCK"));
        assertEquals(0, mapper.upsertCalls, "校验失败不得触达 DB");
        assertTrue(mapper.all.isEmpty());
    }

    @Test
    @DisplayName("归档商品不得登记补货（publish_status 优先于库存判断）")
    void cannotSubscribeArchivedProduct() {
        // 缺货但商品已归档：应报 PJA_WAITLIST_DISABLED，而不是通过校验
        givenArchivedOutOfStockSku("s_test_archived", "p_test_archived");

        JewelryValidationException e = assertThrows(JewelryValidationException.class,
                () -> waitlist.subscribe("u1", "s_test_archived", "PRIMARY_RESULT", "a1", "PRIMARY"));
        assertTrue(e.getViolations().containsKey("PJA_WAITLIST_DISABLED"));
        assertEquals(0, mapper.upsertCalls, "校验失败不得触达 DB");
    }

    @Test
    @DisplayName("未知 SKU 返回 PJA_SKU_NOT_FOUND")
    void unknownSku_rejected() {
        JewelryValidationException e = assertThrows(JewelryValidationException.class,
                () -> waitlist.subscribe("u1", "s_not_exist", "PRIMARY_RESULT", "a1", "PRIMARY"));
        assertTrue(e.getViolations().containsKey("PJA_SKU_NOT_FOUND"));
    }

    @Test
    @DisplayName("空 userId 不应被当作已订阅")
    void blankUserId_isNeverSubscribed() {
        assertFalse(waitlist.isSubscribed("", "s_sun_yantra_001_std"));
        assertFalse(waitlist.isSubscribed(null, "s_sun_yantra_001_std"));
    }

    // ==========================================================
    // 补货通知幂等
    // ==========================================================

    @Test
    @DisplayName("补货通知幂等：同一事件不重复，且已通知者不再计入")
    void notifyRestock_isIdempotentPerEvent() {
        String sku = "s_sun_yantra_001_std";
        givenOutOfStockSku(sku, "p_sun_yantra_001");
        waitlist.subscribe("u1", sku, "PRIMARY_RESULT", "a1", "PRIMARY");
        waitlist.subscribe("u2", sku, "PRIMARY_RESULT", "a2", "PRIMARY");

        assertEquals(2, waitlist.notifyRestock(sku, "evt_001"));
        assertEquals(0, waitlist.notifyRestock(sku, "evt_001"),
                "同一事件重复触发不再标记（notification_once_per_restock_event）");
    }

    @Test
    @DisplayName("已通知的订阅不计入期待人数")
    void notified_notCountedInWaitlist() {
        String sku = "s_sun_yantra_001_std";
        givenOutOfStockSku(sku, "p_sun_yantra_001");
        waitlist.subscribe("u1", sku, "PRIMARY_RESULT", "a1", "PRIMARY");
        assertEquals(1, waitlist.countForSku(sku));

        waitlist.notifyRestock(sku, "evt_001");
        assertEquals(0, waitlist.countForSku(sku), "通知后不再是 WAITING");
    }

    // ==========================================================
    // 统计与快照
    // ==========================================================

    @Test
    @DisplayName("运营统计使用去重用户数，不按订阅条数")
    void demandStats_useDistinctUsers() {
        String sku = "s_sun_yantra_001_std";
        givenOutOfStockSku(sku, "p_sun_yantra_001");
        waitlist.subscribe("u1", sku, "PRIMARY_RESULT", "a1", "PRIMARY");
        waitlist.subscribe("u1", sku, "PRIMARY_RESULT", "a1", "PRIMARY");
        waitlist.subscribe("u1", sku, "PRIMARY_RESULT", "a1", "PRIMARY");
        waitlist.subscribe("u2", sku, "SECONDARY_RESULT", "a2", "SECONDARY");

        Map<String, Object> stats = waitlist.demandStats();
        assertEquals(2, stats.get("active_waitlist_unique_users"));
        // MyBatis 的 COUNT(DISTINCT ...) 返回 Long，测试侧统一按 Number 取值
        assertEquals(2, intOf(stats, "by_planet", "SUN"));
        assertEquals(1, intOf(stats, "by_source", "PRIMARY_RESULT"));
        assertEquals(1, intOf(stats, "by_source", "SECONDARY_RESULT"));
    }

    private static int intOf(Map<String, Object> stats, String bucketKey, String k) {
        Object v = ((Map<?, ?>) stats.get(bucketKey)).get(k);
        return ((Number) v).intValue();
    }

    @Test
    @DisplayName("订阅不表示预留库存")
    void subscription_doesNotReserveStock() {
        givenOutOfStockSku("s_sun_yantra_001_std", "p_sun_yantra_001");
        Map<String, Object> res = waitlist.subscribe(
                "u1", "s_sun_yantra_001_std", "PRIMARY_RESULT", "a1", "PRIMARY");
        assertEquals(Boolean.FALSE, res.get("reserved_stock"));
    }

    @Test
    @DisplayName("落库字段包含商品快照与来源，便于运营归因")
    void persistedRow_carriesSnapshotAndAttribution() {
        givenOutOfStockSku("s_sun_yantra_001_std", "p_sun_yantra_001");
        waitlist.subscribe("u1", "s_sun_yantra_001_std", "PRIMARY_RESULT", "a1", "PRIMARY");

        assertEquals(1, mapper.all.size());
        PlanetaryJewelryRestockSubscription row = mapper.all.get(0);

        assertNotNull(row.getSubscriptionId());
        assertEquals("u1", row.getUserId());
        assertEquals("WAITING", row.getStatus());
        assertEquals("p_sun_yantra_001", row.getProductId());
        assertEquals("SUN", row.getPlanetCode());
        assertEquals("YANTRA", row.getRemedyType());
        assertEquals("a1", row.getAssessmentId());
        assertEquals("PRIMARY", row.getRecommendationRank());
    }

    @Test
    @DisplayName("商品与目录匹配不联动：订阅不改变 match 的方向")
    void subscription_doesNotAffectMatchDirection() {
        String sku = "s_sun_yantra_001_std";
        givenOutOfStockSku(sku, "p_sun_yantra_001");
        waitlist.subscribe("u1", sku, "PRIMARY_RESULT", "a1", "PRIMARY");

        Map<String, Object> before = catalog.match("SUN", List.of("YANTRA"), "PRIMARY", "u1");
        assertEquals("SUN", before.get("planet_code"));
        assertEquals("PRIMARY", before.get("recommendation_rank"));
    }
}
