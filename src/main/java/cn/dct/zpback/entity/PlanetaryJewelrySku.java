package cn.dct.zpback.entity;

import com.fasterxml.jackson.annotation.JsonAlias;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.databind.PropertyNamingStrategies;
import com.fasterxml.jackson.databind.annotation.JsonNaming;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * 行星珠宝建议｜SKU 实体
 *
 * 对应表：planetary_jewelry_sku
 * 契约：catalog_contract.v1.json sku_required_fields / 06 §3
 *
 * inventory_status 由 DB 生成列从 inventory_quantity 推导，
 * 代码不写库存状态，只读推导结果，避免两处数据打架。
 *
 * 字段命名与商品实体一致：对外 snake_case（06 §3），
 * 入参 camelCase 由 @JsonAlias 逐字段补上。
 */
@Data
@JsonIgnoreProperties(ignoreUnknown = true)
@JsonNaming(PropertyNamingStrategies.SnakeCaseStrategy.class)
public class PlanetaryJewelrySku {

    @JsonAlias("skuId")
    private String skuId;

    @JsonAlias("productId")
    private String productId;

    /** 款式/材质等属性，结构可变，JSON 文本存储 */
    @JsonAlias("variantAttributes")
    private String variantAttributes;

    private BigDecimal price;

    /** 划线价；不参与推荐排序（GC14） */
    @JsonAlias("compareAtPrice")
    private BigDecimal compareAtPrice;

    @JsonAlias("inventoryQuantity")
    private Integer inventoryQuantity;

    /** 生成列：0=OUT_OF_STOCK，1~3=LOW_STOCK，>=4=IN_STOCK */
    @JsonAlias("inventoryStatus")
    private String inventoryStatus;

    /** ACTIVE / CANCELLED；取消为软操作，保留历史引用 */
    private String status;

    /** 是否可售；为 0 时不接受新订阅但保留历史 */
    @JsonAlias("saleEnabled")
    private Boolean saleEnabled;

    /** 是否允许登记补货；运营可单独关闭 */
    @JsonAlias("restockWaitlistEnabled")
    private Boolean restockWaitlistEnabled;

    private String source;

    @JsonAlias("createdAt")
    private LocalDateTime createdAt;

    @JsonAlias("updatedAt")
    private LocalDateTime updatedAt;
}
