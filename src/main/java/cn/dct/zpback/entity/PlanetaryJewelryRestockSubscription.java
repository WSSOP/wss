package cn.dct.zpback.entity;

import lombok.Data;

import java.time.LocalDateTime;

/**
 * 行星珠宝建议｜缺货补货订阅实体
 *
 * 对应表：planetary_jewelry_restock_subscription
 * 契约：catalog_contract.v1.json waitlist / 07_字段枚举与API附录 §6
 *
 * 注意：source / recommendation_rank 仅用于运营归因与埋点，
 * 任何情况下都不得参与 Recommendation Rank（01 §7、07 §7）。
 */
@Data
public class PlanetaryJewelryRestockSubscription {

    private String subscriptionId;

    private String userId;

    private String skuId;

    private String productId;

    /** 行星编码快照（冗余，仅供统计；不参与推荐判断） */
    private String planetCode;

    /** YANTRA / GEMSTONE（冗余，仅供统计） */
    private String remedyType;

    /** WAITING / NOTIFIED / CANCELLED / CONVERTED / EXPIRED */
    private String status;

    /**
     * 活跃去重键：DB 生成列，状态为 WAITING/NOTIFIED 时 = user_id::sku_id，
     * 其余为 NULL。唯一索引建在它上面，从而实现「同一用户 + 同一 SKU 只有一条
     * Active 订阅」，而取消后可以重新登记。
     */
    private String activeKey;

    /** PRIMARY_RESULT / SECONDARY_RESULT / PRODUCT_DETAIL / MALL_BROWSE */
    private String source;

    private String assessmentId;

    private String recommendationRank;

    /** IN_APP / PUSH_IF_AUTHORIZED */
    private String notifyChannel;

    /** 同一补货事件只通知一次 */
    private String restockEventId;

    private LocalDateTime notifiedAt;

    private LocalDateTime createdAt;

    private LocalDateTime updatedAt;
}
