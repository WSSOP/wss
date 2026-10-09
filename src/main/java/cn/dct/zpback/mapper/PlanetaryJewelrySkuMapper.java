package cn.dct.zpback.mapper;

import cn.dct.zpback.entity.PlanetaryJewelrySku;
import org.apache.ibatis.annotations.*;

import java.util.List;

/**
 * 行星珠宝建议｜SKU Mapper
 *
 * 库存必须到 SKU 级（06 §3），商品总库存由 SKU 汇总。
 * inventory_status 是 DB 生成列，代码只读不写。
 */
@Mapper
public interface PlanetaryJewelrySkuMapper {

    String COLUMNS = "sku_id, product_id, variant_attributes, price, compare_at_price, " +
            "inventory_quantity, inventory_status, status, sale_enabled, " +
            "restock_waitlist_enabled, source, created_at, updated_at";

    /** 某商品下可售 SKU，按价格升序返回（同方向内的展示顺序，不影响 rank） */
    @Select("SELECT " + COLUMNS + " FROM planetary_jewelry_sku "
            + "WHERE product_id = #{productId} AND status = 'ACTIVE' "
            + "ORDER BY price ASC, sku_id ASC")
    List<PlanetaryJewelrySku> findActiveByProduct(@Param("productId") String productId);

    /** 批量取多个商品的 SKU，避免 N+1 查询 */
    @Select("<script>"
            + "SELECT " + COLUMNS + " FROM planetary_jewelry_sku "
            + "WHERE status = 'ACTIVE' AND product_id IN "
            + "<foreach item='p' collection='productIds' open='(' separator=',' close=')'>#{p}</foreach> "
            + "ORDER BY product_id, price ASC, sku_id ASC"
            + "</script>")
    List<PlanetaryJewelrySku> findActiveByProducts(@Param("productIds") List<String> productIds);

    @Select("SELECT " + COLUMNS + " FROM planetary_jewelry_sku WHERE sku_id = #{skuId}")
    PlanetaryJewelrySku findById(@Param("skuId") String skuId);

    /**
     * 按 skuId 批量取，<b>不过滤 status</b>。
     *
     * <p>购物车要显示已停售的行。{@link #findActiveByProducts} 会把非 ACTIVE 的
     * SKU 直接滤掉，那样购物车里那些「已停售」的行会整个消失，
     * 用户会发现购物车凭空少了几行（§12.1 计数与列表必须对得上）。
     * 所以这里保留停售行，由 Service 判断并打 available=false。
     *
     * <p>避免 N+1：购物车可能有几十行，逐行查会打几十次数据库。
     */
    @Select("<script>"
            + "SELECT " + COLUMNS + " FROM planetary_jewelry_sku "
            + "WHERE sku_id IN "
            + "<foreach item='s' collection='skuIds' open='(' separator=',' close=')'>#{s}</foreach> "
            + "</script>")
    List<PlanetaryJewelrySku> findByIds(@Param("skuIds") List<String> skuIds);

    @Select("SELECT " + COLUMNS + " FROM planetary_jewelry_sku WHERE sku_id = #{skuId} FOR UPDATE")
    PlanetaryJewelrySku findByIdForUpdate(@Param("skuId") String skuId);

    @Select("<script>"
            + "SELECT " + COLUMNS + " FROM planetary_jewelry_sku "
            + "<where>"
            + " <if test='source != null'> AND source = #{source} </if>"
            + " <if test='status != null'> AND status = #{status} </if>"
            + "</where>"
            + " ORDER BY sku_id"
            + "</script>")
    List<PlanetaryJewelrySku> listForAdmin(@Param("source") String source,
                                           @Param("status") String status);

    @Insert("INSERT INTO planetary_jewelry_sku "
            + "(sku_id, product_id, variant_attributes, price, compare_at_price, "
            + " inventory_quantity, status, sale_enabled, restock_waitlist_enabled, source) "
            + "VALUES (#{skuId}, #{productId}, #{variantAttributes}, #{price}, #{compareAtPrice}, "
            + " #{inventoryQuantity}, #{status}, #{saleEnabled}, #{restockWaitlistEnabled}, #{source}) "
            + "ON DUPLICATE KEY UPDATE "
            + " variant_attributes = VALUES(variant_attributes), price = VALUES(price), "
            + " compare_at_price = VALUES(compare_at_price), "
            + " inventory_quantity = VALUES(inventory_quantity), status = VALUES(status), "
            + " sale_enabled = VALUES(sale_enabled), "
            + " restock_waitlist_enabled = VALUES(restock_waitlist_enabled), "
            + " updated_at = CURRENT_TIMESTAMP")
    int upsert(PlanetaryJewelrySku sku);

    /** 补货：只改数量，状态由生成列自动重算 */
    @Update("UPDATE planetary_jewelry_sku SET inventory_quantity = #{quantity}, "
            + " updated_at = CURRENT_TIMESTAMP WHERE sku_id = #{skuId}")
    int updateInventory(@Param("skuId") String skuId, @Param("quantity") int quantity);

    /** 调整库存并返回补货前后数量（06 §8 补货事件触发用） */
    @Update("UPDATE planetary_jewelry_sku SET inventory_quantity = #{quantity}, "
            + " updated_at = CURRENT_TIMESTAMP WHERE sku_id = #{skuId} AND inventory_quantity <> #{quantity}")
    int applyRestock(@Param("skuId") String skuId, @Param("quantity") int quantity);

    @Update("UPDATE planetary_jewelry_sku SET status = 'CANCELLED', "
            + " updated_at = CURRENT_TIMESTAMP WHERE sku_id = #{skuId}")
    int cancel(@Param("skuId") String skuId);

    @Update("UPDATE planetary_jewelry_sku SET restock_waitlist_enabled = #{enabled}, "
            + " updated_at = CURRENT_TIMESTAMP WHERE sku_id = #{skuId}")
    int updateWaitlistEnabled(@Param("skuId") String skuId, @Param("enabled") boolean enabled);

    @Select("SELECT COUNT(*) FROM planetary_jewelry_sku")
    int count();

    /**
     * 下单锁库存：条件扣减，库存不足时扣不动、返回 0。
     *
     * <p>用 {@code inventory_quantity >= #{quantity}} 前置条件做并发保护——
     * 两个请求同时买最后 1 件时，只有一个 UPDATE 的匹配行数非 0，
     * 另一个扣减失败，从根上杜绝超卖。普通 UPDATE 的 check-then-decrement
     * 在并发下会读同一份旧值，两件都"以为够"，卖成 -1。
     */
    @Update("UPDATE planetary_jewelry_sku "
            + "SET inventory_quantity = inventory_quantity - #{quantity}, "
            + " updated_at = CURRENT_TIMESTAMP "
            + "WHERE sku_id = #{skuId} AND inventory_quantity >= #{quantity}")
    int decrementInventory(@Param("skuId") String skuId, @Param("quantity") int quantity);

    /** 订单取消 / 锁库存超时：回补库存。加回而非设值，与其他扣减互不干扰 */
    @Update("UPDATE planetary_jewelry_sku "
            + "SET inventory_quantity = inventory_quantity + #{quantity}, "
            + " updated_at = CURRENT_TIMESTAMP "
            + "WHERE sku_id = #{skuId}")
    int releaseInventory(@Param("skuId") String skuId, @Param("quantity") int quantity);
}