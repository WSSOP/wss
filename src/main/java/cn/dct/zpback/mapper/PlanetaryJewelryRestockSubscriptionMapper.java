package cn.dct.zpback.mapper;

import cn.dct.zpback.entity.PlanetaryJewelryRestockSubscription;
import org.apache.ibatis.annotations.*;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

/**
 * 行星珠宝建议｜补货订阅 Mapper
 *
 * 幂等实现要点：active_key 唯一索引 + INSERT ... ON DUPLICATE KEY UPDATE。
 * 重复登记同一 user + sku 时，DB 直接把既有 Active 订阅的来源字段刷新，
 * 不会插入第二行，因此期待人数不会重复计数（catalog_contract.waitlist.unique_key）。
 */
@Mapper
public interface PlanetaryJewelryRestockSubscriptionMapper {

    String COLUMNS = "subscription_id, user_id, sku_id, product_id, planet_code, remedy_type, " +
            "status, active_key, source, assessment_id, recommendation_rank, " +
            "notify_channel, restock_event_id, notified_at, created_at, updated_at";

    /**
     * 登记订阅（幂等）
     *
     * 依赖 uk_active_user_sku：命中重复键时只更新来源与时间戳，
     * 保留原 status 与 created_at。
     */
    @Insert("INSERT INTO planetary_jewelry_restock_subscription " +
            "(subscription_id, user_id, sku_id, product_id, planet_code, remedy_type, status, " +
            " source, assessment_id, recommendation_rank) " +
            "VALUES (#{subscriptionId}, #{userId}, #{skuId}, #{productId}, #{planetCode}, #{remedyType}, " +
            " 'WAITING', #{source}, #{assessmentId}, #{recommendationRank}) " +
            "ON DUPLICATE KEY UPDATE " +
            " source = COALESCE(VALUES(source), source), " +
            " assessment_id = COALESCE(VALUES(assessment_id), assessment_id), " +
            " recommendation_rank = COALESCE(VALUES(recommendation_rank), recommendation_rank), " +
            " updated_at = CURRENT_TIMESTAMP")
    int upsert(PlanetaryJewelryRestockSubscription subscription);

    /** 查当前用户的活跃订阅 */
    @Select("SELECT " + COLUMNS + " FROM planetary_jewelry_restock_subscription " +
            "WHERE active_key = CONCAT(#{userId}, '::', #{skuId})")
    PlanetaryJewelryRestockSubscription findActive(@Param("userId") String userId,
                                                    @Param("skuId") String skuId);

    @Select("SELECT " + COLUMNS + " FROM planetary_jewelry_restock_subscription " +
            "WHERE user_id = #{userId} AND status IN ('WAITING','NOTIFIED') " +
            "ORDER BY created_at DESC")
    List<PlanetaryJewelryRestockSubscription> findActiveByUser(@Param("userId") String userId);

    /** 某 SKU 的所有 Active 订阅（补货时通知名单） */
    @Select("SELECT " + COLUMNS + " FROM planetary_jewelry_restock_subscription " +
            "WHERE sku_id = #{skuId} AND status = 'WAITING'")
    List<PlanetaryJewelryRestockSubscription> findWaitingBySku(@Param("skuId") String skuId);

    /**
     * 期待补货人数 = 去重用户数
     *
     * 契约明确要求 COUNT(DISTINCT user_id)，不是订阅条数。
     */
    @Select("SELECT COUNT(DISTINCT user_id) FROM planetary_jewelry_restock_subscription " +
            "WHERE sku_id = #{skuId} AND status = 'WAITING'")
    int countDistinctWaitingUsersBySku(@Param("skuId") String skuId);

    @Select("SELECT COUNT(DISTINCT user_id) FROM planetary_jewelry_restock_subscription " +
            "WHERE product_id = #{productId} AND status = 'WAITING'")
    int countDistinctWaitingUsersByProduct(@Param("productId") String productId);

    /** 取消订阅（幂等：不存在或已取消时返回 0） */
    @Update("UPDATE planetary_jewelry_restock_subscription " +
            "SET status = 'CANCELLED', updated_at = CURRENT_TIMESTAMP " +
            "WHERE active_key = CONCAT(#{userId}, '::', #{skuId})")
    int cancel(@Param("userId") String userId, @Param("skuId") String skuId);

    /** 取消某次评估产生的全部订阅（重新测试时清理） */
    @Update("UPDATE planetary_jewelry_restock_subscription " +
            "SET status = 'CANCELLED', updated_at = CURRENT_TIMESTAMP " +
            "WHERE user_id = #{userId} AND assessment_id = #{assessmentId} " +
            "AND status IN ('WAITING','NOTIFIED')")
    int cancelByAssessment(@Param("userId") String userId, @Param("assessmentId") String assessmentId);

    /**
     * 标记某次补货事件已通知
     *
     * 依赖 (restock_event_id, user_id, sku_id) 的幂等语义：
     * 已通知过的行 restock_event_id 不为空，WHERE 条件不命中，因此不会重复通知。
     */
    @Update("UPDATE planetary_jewelry_restock_subscription " +
            "SET status = 'NOTIFIED', restock_event_id = #{restockEventId}, " +
            "    notified_at = #{now}, updated_at = CURRENT_TIMESTAMP " +
            "WHERE sku_id = #{skuId} AND user_id = #{userId} AND status = 'WAITING' " +
            "  AND (restock_event_id IS NULL OR restock_event_id <> #{restockEventId})")
    int markNotified(@Param("skuId") String skuId,
                     @Param("userId") String userId,
                     @Param("restockEventId") String restockEventId,
                     @Param("now") LocalDateTime now);

    // ==============================================================
    // 运营统计（06_商品后台与补货订阅）
    // ==============================================================

    @Select("SELECT COUNT(DISTINCT user_id) AS unique_users " +
            "FROM planetary_jewelry_restock_subscription WHERE status = 'WAITING'")
    Map<String, Object> statUniqueUsers();

    @Select("SELECT COUNT(DISTINCT user_id) AS unique_users " +
            "FROM planetary_jewelry_restock_subscription " +
            "WHERE status = 'WAITING' AND created_at >= #{since}")
    Map<String, Object> statUniqueUsersSince(@Param("since") LocalDateTime since);

    @Select("SELECT planet_code AS k, COUNT(DISTINCT user_id) AS v " +
            "FROM planetary_jewelry_restock_subscription " +
            "WHERE status = 'WAITING' AND planet_code IS NOT NULL GROUP BY planet_code")
    List<Map<String, Object>> statByPlanet();

    @Select("SELECT source AS k, COUNT(DISTINCT user_id) AS v " +
            "FROM planetary_jewelry_restock_subscription " +
            "WHERE status = 'WAITING' AND source IS NOT NULL GROUP BY source")
    List<Map<String, Object>> statBySource();

    @Select("SELECT recommendation_rank AS k, COUNT(DISTINCT user_id) AS v " +
            "FROM planetary_jewelry_restock_subscription " +
            "WHERE status = 'WAITING' AND recommendation_rank IS NOT NULL " +
            "GROUP BY recommendation_rank")
    List<Map<String, Object>> statByRank();

    @Select("SELECT remedy_type AS k, COUNT(DISTINCT user_id) AS v " +
            "FROM planetary_jewelry_restock_subscription " +
            "WHERE status = 'WAITING' AND remedy_type IS NOT NULL GROUP BY remedy_type")
    List<Map<String, Object>> statByRemedyType();

    /** 变体需求：哪个款式/材质最常被登记补货 */
    @Select("SELECT sku_id AS k, COUNT(DISTINCT user_id) AS v " +
            "FROM planetary_jewelry_restock_subscription " +
            "WHERE status = 'WAITING' GROUP BY sku_id ORDER BY v DESC LIMIT #{limit}")
    List<Map<String, Object>> statVariantDemand(@Param("limit") int limit);
}
