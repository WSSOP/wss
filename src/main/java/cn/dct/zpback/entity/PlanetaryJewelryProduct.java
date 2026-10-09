package cn.dct.zpback.entity;

import com.fasterxml.jackson.annotation.JsonAlias;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.databind.PropertyNamingStrategies;
import com.fasterxml.jackson.databind.annotation.JsonNaming;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 行星珠宝建议｜商品实体
 *
 * 对应表：planetary_jewelry_product
 * 契约：catalog_contract.v1.json product_required_fields / 06 §2
 *
 * 边界（05 §5、GC14）：
 * planet_code / remedy_type 是<b>商品自身标签</b>，只用于按冻结方向做商城匹配，
 * 绝不参与推荐排序；admin_recommendation_priority 也只在<b>同一方向内</b>排序，
 * 不得跨方向比较，更不得让高毛利商品改变 planet rank。
 *
 * <b>字段命名</b>：对外序列化用 snake_case（文档 06 §2 写法）。
 * 反序列化时 snake_case 由 @JsonNaming 负责，camelCase 由每个字段的
 * {@link JsonAlias} 显式补上——实测 @JsonNaming 本身不会同时接受两种写法，
 * 运营两种写法都要能用，因此别名必须逐字段声明。
 */
@Data
@JsonIgnoreProperties(ignoreUnknown = true)
@JsonNaming(PropertyNamingStrategies.SnakeCaseStrategy.class)
public class PlanetaryJewelryProduct {

    @JsonAlias("productId")
    private String productId;

    private String title;

    @JsonAlias("subtitle")
    private String subtitle;

    /** SUN/MOON/MARS/MERCURY/JUPITER/VENUS/SATURN */
    @JsonAlias("planetCode")
    private String planetCode;

    /** YANTRA / GEMSTONE */
    @JsonAlias("remedyType")
    private String remedyType;

    private String description;

    @JsonAlias("detailContent")
    private String detailContent;

    @JsonAlias("careInstructions")
    private String careInstructions;

    /** 运营可手动关闭自动推荐；为 0 时任何模式都不自动展示（05 §4） */
    @JsonAlias("autoRecommendable")
    private Boolean autoRecommendable;

    /** DRAFT / PUBLISHED / UNPUBLISHED / ARCHIVED */
    @JsonAlias("publishStatus")
    private String publishStatus;

    /** 只在同一冻结方向内部排序 */
    @JsonAlias("adminRecommendationPriority")
    private Integer adminRecommendationPriority;

    /** 图片URL数组（JSON 文本） */
    private String images;

    @JsonAlias("giftAvailable")
    private Boolean giftAvailable;

    /** 高门槛宝石标记：仅 GEMSTONE_ELIGIBLE 方向可见 */
    @JsonAlias("highThresholdGemstone")
    private Boolean highThresholdGemstone;

    /** NECKLACE/BRACELET/PENDANT/RING/OTHER */
    @JsonAlias("productForm")
    private String productForm;

    @JsonAlias("materialTags")
    private String materialTags;

    @JsonAlias("colorTags")
    private String colorTags;

    @JsonAlias("supportTags")
    private String supportTags;

    /** SEED = 内置示例；ADMIN_UPLOAD = 运营上传真实商品 */
    private String source;

    /** planet::remedy 组合键（生成列）；SEED 与真实商品都生成，用于互相屏蔽 */
    @JsonAlias("comboKey")
    private String comboKey;

    @JsonAlias("createdAt")
    private LocalDateTime createdAt;

    @JsonAlias("updatedAt")
    private LocalDateTime updatedAt;
}
