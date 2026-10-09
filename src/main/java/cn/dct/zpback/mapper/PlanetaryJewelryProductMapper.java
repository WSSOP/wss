package cn.dct.zpback.mapper;

import cn.dct.zpback.entity.PlanetaryJewelryProduct;
import org.apache.ibatis.annotations.*;

import java.util.List;

/**
 * 行星珠宝建议｜商品 Mapper
 *
 * 05 §4 的四条硬条件全部下推到 SQL：
 *   1. planet_code 相同；
 *   2. remedy_type 在允许集合内；
 *   3. auto_recommendable = 1；
 *   4. publish_status = 'PUBLISHED'。
 *
 * 排序只在<b>同一方向内</b>生效（现货优先 → 运营优先级 → 稳定 ID），
 * SQL 里没有任何跨方向比较，也不含价格/毛利（GC14）。
 */
@Mapper
public interface PlanetaryJewelryProductMapper {

    String COLUMNS = "product_id, title, subtitle, planet_code, remedy_type, description, " +
            "detail_content, care_instructions, auto_recommendable, publish_status, " +
            "admin_recommendation_priority, images, gift_available, high_threshold_gemstone, " +
            "product_form, material_tags, color_tags, support_tags, source, " +
            "combo_key, created_at, updated_at";

    /**
     * 按冻结方向查询可自动推荐的商品。
     *
     * 过滤条件即 05 §4：
     * - publish_status = PUBLISHED（ARCHIVED/UNPUBLISHED/DRAFT 都不展示）；
     * - auto_recommendable = 1；
     * - remedy_type 命中 allowed；
     * - source = ADMIN_UPLOAD，或该 planet::remedy 组合尚无真实商品（SEED 兜底）。
     *
     * SEED 屏蔽语义对应表里的 combo_key 生成列（SEED 与真实商品都生成该键）：
     * 只要同组合下存在 ADMIN_UPLOAD 商品，该组合的 SEED 商品即停止对外展示。
     */
    @Select("<script>"
            + "SELECT " + COLUMNS + " FROM planetary_jewelry_product WHERE planet_code = #{planet} "
            + " AND publish_status = 'PUBLISHED' AND auto_recommendable = 1 "
            + " AND remedy_type IN <foreach item='r' collection='remedies' open='(' separator=',' close=')'>#{r}</foreach> "
            + " AND (source = 'ADMIN_UPLOAD' "
            + "      OR NOT EXISTS ( "
            + "          SELECT 1 FROM planetary_jewelry_product real_p "
            + "          WHERE real_p.source = 'ADMIN_UPLOAD' "
            + "            AND real_p.combo_key = planetary_jewelry_product.combo_key)) "
            + " ORDER BY CASE ("
            + "    SELECT COUNT(*) FROM planetary_jewelry_sku s "
            + "    WHERE s.product_id = planetary_jewelry_product.product_id "
            + "      AND s.status = 'ACTIVE' AND s.inventory_quantity > 0) "
            + " WHEN 0 THEN 2 "
            + "    WHEN 1 THEN 0 "
            + "    ELSE 1 END ASC, "
            + " admin_recommendation_priority DESC, product_id ASC"
            + "</script>")
    List<PlanetaryJewelryProduct> findMatchable(@Param("planet") String planet,
                                                @Param("remedies") List<String> remedies);

    /**
     * 全目录浏览查询（用户主动逛商城，不在推荐流程内）。
     *
     * 与 {@link #findMatchable} 的区别：
     * <ul>
     *   <li><b>不按 planet_code 过滤</b>——浏览页要看到所有行星的商品；</li>
     *   <li><b>不过滤 auto_recommendable</b>——该字段管的是「是否自动推荐」，
     *       用户主动浏览时不该被它挡住；</li>
     *   <li>仍然要求 publish_status='PUBLISHED'，已归档/草稿不出现在任何前台页面；</li>
     *   <li>仍然跳过被真实商品顶替的 SEED 示例。</li>
     * </ul>
     *
     * 边界：返回结果只用于陈列，<b>不得</b>参与 Rank 计算，
     * 也无法改变已冻结的推荐方向（05 §1「推荐方向先锁定」）。
     */
    @Select("<script>"
            + "SELECT " + COLUMNS + " FROM planetary_jewelry_product WHERE publish_status = 'PUBLISHED' "
            + " <if test='planet != null'> AND planet_code = #{planet} </if>"
            + " <if test='remedyType != null'> AND remedy_type = #{remedyType} </if>"
            + " AND (source = 'ADMIN_UPLOAD' "
            + "      OR NOT EXISTS ( "
            + "          SELECT 1 FROM planetary_jewelry_product real_p "
            + "          WHERE real_p.source = 'ADMIN_UPLOAD' "
            + "            AND real_p.combo_key = planetary_jewelry_product.combo_key)) "
            + " ORDER BY CASE ("
            + "    SELECT COUNT(*) FROM planetary_jewelry_sku s "
            + "    WHERE s.product_id = planetary_jewelry_product.product_id "
            + "      AND s.status = 'ACTIVE' AND s.inventory_quantity > 0) "
            + " WHEN 0 THEN 1 ELSE 0 END ASC, planet_code, remedy_type, product_id"
            + "</script>")
    List<PlanetaryJewelryProduct> browse(@Param("planet") String planet,
                                         @Param("remedyType") String remedyType);

    /**
     * 搜索 / 列表查询（原型 /mall/search 与 /mall/list 的数据来源）。
     *
     * <p>与 {@link #browse} 共用同一套<b>可见性口径</b>：只 PUBLISHED、跳过被真实商品顶替的 SEED。
     * 两套口径一旦分叉，同一个商品会在首页能搜到、在列表里消失，用户无法解释。
     *
     * <p>排序口径：
     * <ul>
     *   <li>{@code PRICE_ASC / PRICE_DESC} 按 ACTIVE SKU 的最低价，
     *       无 SKU 的商品排在最后而不是最前——没有报价的商品参与价格排序没有意义；</li>
     *   <li>{@code NEWEST} 按 created_at 倒序；</li>
     *   <li>其余（含 null）沿用 browse 的「有货优先」默认序。</li>
     * </ul>
     */
    @Select("<script>"
            + "SELECT " + COLUMNS + " FROM planetary_jewelry_product WHERE publish_status = 'PUBLISHED' "
            + " <if test='keyword != null and keyword != \"\"'>"
            + "   AND (title LIKE CONCAT('%', #{keyword}, '%') "
            + "        OR subtitle LIKE CONCAT('%', #{keyword}, '%') "
            + "        OR planet_code LIKE CONCAT('%', #{keyword}, '%') "
            + "        OR remedy_type LIKE CONCAT('%', #{keyword}, '%'))"
            + " </if>"
            + " <if test='planet != null'> AND planet_code = #{planet} </if>"
            + " <if test='remedyType != null'> AND remedy_type = #{remedyType} </if>"
            // stock=IN_STOCK 要求至少一个 ACTIVE SKU 有货；OUT_OF_STOCK 反之
            + " <if test='stock == \"IN_STOCK\"'>"
            + "   AND EXISTS (SELECT 1 FROM planetary_jewelry_sku s "
            + "     WHERE s.product_id = planetary_jewelry_product.product_id "
            + "       AND s.status = 'ACTIVE' AND s.inventory_quantity &gt; 0) "
            + " </if>"
            + " <if test='stock == \"OUT_OF_STOCK\"'>"
            + "   AND NOT EXISTS (SELECT 1 FROM planetary_jewelry_sku s "
            + "     WHERE s.product_id = planetary_jewelry_product.product_id "
            + "       AND s.status = 'ACTIVE' AND s.inventory_quantity &gt; 0) "
            + " </if>"
            + " AND (source = 'ADMIN_UPLOAD' "
            + "      OR NOT EXISTS ( "
            + "          SELECT 1 FROM planetary_jewelry_product real_p "
            + "          WHERE real_p.source = 'ADMIN_UPLOAD' "
            + "            AND real_p.combo_key = planetary_jewelry_product.combo_key)) "
            + " <choose>"
            + "   <when test=\"sort == 'PRICE_ASC'\">"
            + "     ORDER BY (SELECT MIN(s.price) FROM planetary_jewelry_sku s "
            + "        WHERE s.product_id = planetary_jewelry_product.product_id "
            + "          AND s.status = 'ACTIVE') IS NULL ASC, "
            + "       (SELECT MIN(s.price) FROM planetary_jewelry_sku s "
            + "        WHERE s.product_id = planetary_jewelry_product.product_id "
            + "          AND s.status = 'ACTIVE') ASC"
            + "   </when>"
            + "   <when test=\"sort == 'PRICE_DESC'\">"
            + "     ORDER BY (SELECT MIN(s.price) FROM planetary_jewelry_sku s "
            + "        WHERE s.product_id = planetary_jewelry_product.product_id "
            + "          AND s.status = 'ACTIVE') IS NULL ASC, "
            + "       (SELECT MIN(s.price) FROM planetary_jewelry_sku s "
            + "        WHERE s.product_id = planetary_jewelry_product.product_id "
            + "          AND s.status = 'ACTIVE') DESC"
            + "   </when>"
            + "   <when test=\"sort == 'NEWEST'\">"
            + "     ORDER BY created_at DESC, product_id"
            + "   </when>"
            + "   <otherwise>"
            + "     ORDER BY CASE ("
            + "       SELECT COUNT(*) FROM planetary_jewelry_sku s "
            + "       WHERE s.product_id = planetary_jewelry_product.product_id "
            + "         AND s.status = 'ACTIVE' AND s.inventory_quantity &gt; 0) "
            + "     WHEN 0 THEN 1 ELSE 0 END ASC, planet_code, remedy_type, product_id"
            + "   </otherwise>"
            + " </choose>"
            + "</script>")
    List<PlanetaryJewelryProduct> search(@Param("keyword") String keyword,
                                         @Param("planet") String planet,
                                         @Param("remedyType") String remedyType,
                                         @Param("stock") String stock,
                                         @Param("sort") String sort);

    @Select("SELECT " + COLUMNS + " FROM planetary_jewelry_product WHERE product_id = #{productId}")
    PlanetaryJewelryProduct findById(@Param("productId") String productId);

    @Select("<script>"
            + "SELECT " + COLUMNS + " FROM planetary_jewelry_product "
            + "<where>"
            + " <if test='planet != null'> AND planet_code = #{planet} </if>"
            + " <if test='remedyType != null'> AND remedy_type = #{remedyType} </if>"
            + " <if test='publishStatus != null'> AND publish_status = #{publishStatus} </if>"
            + " <if test='source != null'> AND source = #{source} </if>"
            + "</where>"
            + " ORDER BY planet_code, remedy_type, product_id"
            + "</script>")
    List<PlanetaryJewelryProduct> listForAdmin(@Param("planet") String planet,
                                                @Param("remedyType") String remedyType,
                                                @Param("publishStatus") String publishStatus,
                                                @Param("source") String source);

    @Select("SELECT COUNT(*) FROM planetary_jewelry_product")
    int count();

    /**
     * 新增 / 全量更新商品。
     *
     * 用 ON DUPLICATE KEY UPDATE 而不是先查后写：运营重复提交同一 product_id
     * 时不会插出第二行，也不会产生"商品数量不设上限"的脏数据。
     */
    @Insert("INSERT INTO planetary_jewelry_product "
            + "(product_id, title, subtitle, planet_code, remedy_type, description, "
            + " detail_content, care_instructions, auto_recommendable, publish_status, "
            + " admin_recommendation_priority, images, gift_available, high_threshold_gemstone, "
            + " product_form, material_tags, color_tags, support_tags, source) "
            + "VALUES (#{productId}, #{title}, #{subtitle}, #{planetCode}, #{remedyType}, #{description}, "
            + " #{detailContent}, #{careInstructions}, #{autoRecommendable}, #{publishStatus}, "
            + " #{adminRecommendationPriority}, #{images}, #{giftAvailable}, #{highThresholdGemstone}, "
            + " #{productForm}, #{materialTags}, #{colorTags}, #{supportTags}, #{source}) "
            + "ON DUPLICATE KEY UPDATE "
            + " title = VALUES(title), subtitle = VALUES(subtitle), planet_code = VALUES(planet_code), "
            + " remedy_type = VALUES(remedy_type), description = VALUES(description), "
            + " detail_content = VALUES(detail_content), care_instructions = VALUES(care_instructions), "
            + " auto_recommendable = VALUES(auto_recommendable), publish_status = VALUES(publish_status), "
            + " admin_recommendation_priority = VALUES(admin_recommendation_priority), "
            + " images = VALUES(images), gift_available = VALUES(gift_available), "
            + " high_threshold_gemstone = VALUES(high_threshold_gemstone), "
            + " product_form = VALUES(product_form), material_tags = VALUES(material_tags), "
            + " color_tags = VALUES(color_tags), support_tags = VALUES(support_tags), "
            + " updated_at = CURRENT_TIMESTAMP")
    int upsert(PlanetaryJewelryProduct product);

    /** 上下架 / 关闭自动推荐：只改状态，不动内容（06 §4） */
    @Update("UPDATE planetary_jewelry_product SET publish_status = #{status}, "
            + " updated_at = CURRENT_TIMESTAMP WHERE product_id = #{productId}")
    int updatePublishStatus(@Param("productId") String productId, @Param("status") String status);

    @Update("UPDATE planetary_jewelry_product SET auto_recommendable = #{auto}, "
            + " updated_at = CURRENT_TIMESTAMP WHERE product_id = #{productId}")
    int updateAutoRecommendable(@Param("productId") String productId, @Param("auto") boolean auto);

    @Update("UPDATE planetary_jewelry_product SET admin_recommendation_priority = #{priority}, "
            + " updated_at = CURRENT_TIMESTAMP WHERE product_id = #{productId}")
    int updatePriority(@Param("productId") String productId, @Param("priority") int priority);

    /**
     * 软归档，不物理删除。
     *
     * 历史订阅、推荐快照与订单必须保留（06 §4、07 §6），
     * 因此只有 ARCHIVED 是终态，没有 DELETE 路径。
     */
    @Update("UPDATE planetary_jewelry_product SET publish_status = 'ARCHIVED', "
            + " auto_recommendable = 0, updated_at = CURRENT_TIMESTAMP WHERE product_id = #{productId}")
    int archive(@Param("productId") String productId);
}
