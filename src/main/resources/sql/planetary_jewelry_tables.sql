-- ============================================================
-- 行星珠宝建议｜补货订阅表
--
-- 契约来源：catalog_contract.v1.json waitlist / 07_字段枚举与API附录 §6
--
-- 关键约束：
-- 1. 期待人数 = COUNT(DISTINCT user_id) WHERE status='WAITING'；
-- 2. 唯一约束 user_id + sku_id + active_key，保证同一用户重复登记
--    不会产生第二条 Active 订阅（幂等）；
-- 3. active_key 在非 Active 状态下为 NULL —— MySQL 唯一索引允许多个 NULL，
--    这样 CANCELLED 之后可以重新登记（resubscribe_after_new_stockout），
--    同时不需要部分索引（MySQL 不支持）；
-- 4. 商品与 SKU 不在此表外键约束：商城由运营后台动态管理，
--    且历史订阅必须保留（06_商品后台：软下架不删除历史引用）。
-- ============================================================

CREATE TABLE IF NOT EXISTS `planetary_jewelry_restock_subscription` (
    `subscription_id` VARCHAR(64) NOT NULL COMMENT '订阅ID',
    `user_id` VARCHAR(32) NOT NULL COMMENT '用户ID',
    `sku_id` VARCHAR(64) NOT NULL COMMENT 'SKU ID',
    `product_id` VARCHAR(64) NOT NULL COMMENT '商品ID（冗余，便于运营统计）',
    `planet_code` VARCHAR(16) DEFAULT NULL COMMENT '行星编码（冗余快照，不参与推荐）',
    `remedy_type` VARCHAR(16) DEFAULT NULL COMMENT 'YANTRA/GEMSTONE（冗余快照）',
    `status` VARCHAR(16) NOT NULL DEFAULT 'WAITING'
        COMMENT 'WAITING/NOTIFIED/CANCELLED/CONVERTED/EXPIRED',
    `active_key` VARCHAR(128) GENERATED ALWAYS AS (
        CASE WHEN `status` IN ('WAITING', 'NOTIFIED')
             THEN CONCAT(`user_id`, '::', `sku_id`)
             ELSE NULL END
    ) STORED COMMENT '活跃去重键；非活跃为 NULL 以允许重新登记',

    `source` VARCHAR(32) DEFAULT NULL COMMENT 'PRIMARY_RESULT/SECONDARY_RESULT/PRODUCT_DETAIL/MALL_BROWSE',
    `assessment_id` VARCHAR(64) DEFAULT NULL COMMENT '来源评估ID（运营归因）',
    `recommendation_rank` VARCHAR(16) DEFAULT NULL COMMENT 'PRIMARY/SECONDARY（埋点，不参与推荐排序）',

    `notify_channel` VARCHAR(16) DEFAULT NULL COMMENT 'IN_APP/PUSH_IF_AUTHORIZED',
    `restock_event_id` VARCHAR(64) DEFAULT NULL COMMENT '补货事件ID；同一事件只通知一次',
    `notified_at` DATETIME DEFAULT NULL COMMENT '通知发送时间',

    `created_at` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    `updated_at` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,

    PRIMARY KEY (`subscription_id`),
    UNIQUE KEY `uk_active_user_sku` (`active_key`),
    KEY `idx_sku_status` (`sku_id`, `status`),
    KEY `idx_user_id` (`user_id`),
    KEY `idx_product_status` (`product_id`, `status`),
    KEY `idx_planet_status` (`planet_code`, `status`),
    KEY `idx_source_created` (`source`, `created_at`),
    KEY `idx_assessment_id` (`assessment_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='行星珠宝缺货补货订阅表';
