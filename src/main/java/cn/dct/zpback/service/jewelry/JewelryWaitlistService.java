package cn.dct.zpback.service.jewelry;

import cn.dct.zpback.entity.PlanetaryJewelryRestockSubscription;
import cn.dct.zpback.exception.JewelryValidationException;
import cn.dct.zpback.mapper.PlanetaryJewelryRestockSubscriptionMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * 行星珠宝建议｜缺货补货订阅
 *
 * 契约要点（02_双模式页面流程 §10；catalog_contract.v1.json.waitlist）：
 * <ul>
 *   <li>唯一键 {@code user_id + sku_id + active_status_group}：同一用户重复登记
 *       <b>不得</b>重复计数（00_程序员先读 §6、§17）。DB 侧由
 *       {@code uk_active_user_sku} 唯一索引保证，登记走 INSERT ... ON DUPLICATE KEY UPDATE。</li>
 *   <li>期待人数 = {@code COUNT(DISTINCT user_id) WHERE status = WAITING}，
 *       不是订阅条数。</li>
 *   <li>缺货 ≠ 下架：只有 OUT_OF_STOCK 的 SKU 可登记；归档商品不可登记。</li>
 *   <li>登记不代表预留库存，必须明示（§10）。</li>
 *   <li>再次售罄后可重新登记（{@code resubscribe_after_new_stockout}）——
 *       取消后 active_key 变 NULL，唯一索引不再拦截新行。</li>
 * </ul>
 *
 * 持久化在 MySQL（planetary_jewelry_restock_subscription），
 * 不使用内存存储：期待人数要能跨实例与重启保持一致。
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class JewelryWaitlistService {

    private final JewelryCatalogService catalog;
    private final PlanetaryJewelryRestockSubscriptionMapper mapper;

    /** 计入"期待人数"的状态 */
    private static final List<String> ACTIVE_STATUSES = List.of("WAITING", "NOTIFIED");

    // ==================================================================
    // 登记 / 取消
    // ==================================================================

    /**
     * 登记补货订阅（幂等）
     *
     * @return 订阅快照；重复提交返回既有 Active 订阅且<b>不</b>改变计数
     * @throws JewelryValidationException SKU 不存在 / 有货 / 商品已归档
     */
    @Transactional
    public Map<String, Object> subscribe(String userId, String skuId, String source,
                                         String assessmentId, String recommendationRank) {
        Map<String, Object> sku = catalog.sku(skuId);
        if (sku == null) {
            throw new JewelryValidationException(Map.of("PJA_SKU_NOT_FOUND", skuId));
        }
        Map<String, Object> product = catalog.product(String.valueOf(sku.get("product_id")));
        String publishStatus = product == null ? null : String.valueOf(product.get("publish_status"));
        if (!"PUBLISHED".equals(publishStatus)) {
            // 已归档/下架：不再接受新订阅，但历史订阅保留
            throw new JewelryValidationException(Map.of("PJA_WAITLIST_DISABLED", skuId));
        }
        if (!"OUT_OF_STOCK".equals(sku.get("inventory_status"))) {
            throw new JewelryValidationException(Map.of("PJA_SKU_NOT_OUT_OF_STOCK", skuId));
        }

        PlanetaryJewelryRestockSubscription s = new PlanetaryJewelryRestockSubscription();
        s.setSubscriptionId("pjs_" + UUID.randomUUID().toString().replace("-", "").substring(0, 20));
        s.setUserId(userId);
        s.setSkuId(skuId);
        s.setProductId(String.valueOf(sku.get("product_id")));
        s.setPlanetCode(product == null ? null : String.valueOf(product.get("planet_code")));
        s.setRemedyType(product == null ? null : String.valueOf(product.get("remedy_type")));
        s.setStatus("WAITING");
        s.setSource(source);
        s.setAssessmentId(assessmentId);
        s.setRecommendationRank(recommendationRank);

        // 唯一索引兜底幂等：重复提交只会刷新来源字段，不会插入第二行
        mapper.upsert(s);

        PlanetaryJewelryRestockSubscription saved = mapper.findActive(userId, skuId);
        log.info("补货订阅登记：user={} sku={} source={}", userId, skuId, source);
        return withCount(saved != null ? saved : s);
    }

    /** 取消订阅（幂等） */
    @Transactional
    public Map<String, Object> cancel(String userId, String skuId) {
        int changed = mapper.cancel(userId, skuId);
        Map<String, Object> out = new LinkedHashMap<>();
        out.put("sku_id", skuId);
        out.put("status", "CANCELLED");
        out.put("changed", changed > 0);
        out.put("waitlist_count", countForSku(skuId));
        return out;
    }

    /** 取消某次评估产生的全部订阅（用于「重新测试」清理） */
    @Transactional
    public int cancelByAssessment(String userId, String assessmentId) {
        return mapper.cancelByAssessment(userId, assessmentId);
    }

    // ==================================================================
    // 查询
    // ==================================================================

    /** 期待补货人数 = 去重用户数（不是订阅条数） */
    public int countForSku(String skuId) {
        return mapper.countDistinctWaitingUsersBySku(skuId);
    }

    public int countForProduct(String productId) {
        return mapper.countDistinctWaitingUsersByProduct(productId);
    }

    /** 该用户是否已登记（前端按钮态：已登记，补货后通知你） */
    public boolean isSubscribed(String userId, String skuId) {
        if (userId == null || userId.isEmpty()) return false;
        return mapper.findActive(userId, skuId) != null;
    }

    /** 当前用户全部活跃订阅 */
    public List<PlanetaryJewelryRestockSubscription> activeByUser(String userId) {
        return mapper.findActiveByUser(userId);
    }

    // ==================================================================
    // 补货通知（06_商品后台与补货订阅）
    // ==================================================================

    /**
     * 某 SKU 补货时标记通知。
     *
     * 幂等：同一 restock_event_id 重复调用不会二次通知
     * （contract.waitlist.notification_once_per_restock_event）。
     *
     * @return 实际被标记的订阅数
     */
    @Transactional
    public int notifyRestock(String skuId, String restockEventId) {
        List<PlanetaryJewelryRestockSubscription> waiting = mapper.findWaitingBySku(skuId);
        LocalDateTime now = LocalDateTime.now();
        int marked = 0;
        for (PlanetaryJewelryRestockSubscription s : waiting) {
            marked += mapper.markNotified(skuId, s.getUserId(), restockEventId, now);
        }
        log.info("补货通知：sku={} event={} 标记 {} 条", skuId, restockEventId, marked);
        return marked;
    }

    // ==================================================================
    // 运营统计
    // ==================================================================

    /** 运营统计：按 Planet / Remedy / 来源去重汇总（06_商品后台与补货订阅） */
    public Map<String, Object> demandStats() {
        Map<String, Object> out = new LinkedHashMap<>();
        out.put("active_waitlist_unique_users", toInt(mapValue(mapper.statUniqueUsers())));
        out.put("new_waitlist_7d", toInt(mapValue(mapper.statUniqueUsersSince(LocalDateTime.now().minusDays(7)))));
        out.put("new_waitlist_30d", toInt(mapValue(mapper.statUniqueUsersSince(LocalDateTime.now().minusDays(30)))));
        out.put("by_planet", bucket(mapper.statByPlanet()));
        out.put("by_source", bucket(mapper.statBySource()));
        out.put("by_recommendation_rank", bucket(mapper.statByRank()));
        out.put("by_remedy_type", bucket(mapper.statByRemedyType()));
        out.put("variant_demand", bucket(mapper.statVariantDemand(20)));
        return out;
    }

    private static Object mapValue(Map<String, Object> row) {
        return row == null ? null : row.get("unique_users");
    }

    private static int toInt(Object o) {
        return o instanceof Number n ? n.intValue() : 0;
    }

    /** MyBatis 的 GROUP BY 结果 [{k,v}] → {k: v} */
    private static Map<String, Object> bucket(List<Map<String, Object>> rows) {
        Map<String, Object> out = new LinkedHashMap<>();
        for (Map<String, Object> row : rows) {
            Object k = row.get("k");
            if (k != null) out.put(String.valueOf(k), row.get("v"));
        }
        return out;
    }

    private Map<String, Object> withCount(PlanetaryJewelryRestockSubscription s) {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("sku_id", s.getSkuId());
        m.put("product_id", s.getProductId());
        m.put("status", s.getStatus());
        m.put("source", s.getSource());
        m.put("assessment_id", s.getAssessmentId());
        m.put("recommendation_rank", s.getRecommendationRank());
        m.put("created_at", s.getCreatedAt());
        m.put("waitlist_count", countForSku(s.getSkuId()));
        m.put("reserved_stock", false);   // 明确不代表预留库存（§10）
        return m;
    }
}
