-- ============================================================
-- 商城｜购物车表
--
-- 契约来源：《【我的】页面开发文档》STEP 7
--   §6  商城入口 /mall；§7 购物车 /mall/cart
--   §12.1 购物车数量事实源 = <b>有效可结算 SKU 行</b>
--   §19.5 深链对象必须做所有权校验
--
-- 设计约束（不可违反）：
--
-- 1. <b>购物车数量不是行数，是「有效可结算 SKU 行」</b>（§12.1）。
--    什么叫有效可结算，这里给出可判定的定义，避免前端自己猜：
--      · item_status = ACTIVE（用户未删除）
--      · 行 selected = 1（用户勾选参与结算）
--      · 该行数量 > 0
--    注意<b>不含</b>库存与上架状态判断：
--    下架或缺货的行仍应让用户看见并自己取消，
--    若把它们从计数里剔掉，用户会遇到「购物车角标有 3，去了一行没了」，
--    以为是数据丢了。真正结算时（§16.3 服务端鉴权）才做校验并报错。
--    这是「计数口径」与「结算口径」刻意分离，不是遗漏。
--
-- 2. 同一 user + sku 只保留一行（唯一键），重复加购累加 quantity。
--    不保留多行，否则同一商品会出现三条，结算时还要前端去重。
--
-- 3. <b>不物理删除</b>，用 item_status 软删。
--    与 planetary_jewelry_product 的 ARCHIVED 终态一致：
--    商品快照与历史引用必须保留，否则订单无法回溯。
--
-- 4. 加购时快照 sku_id → product_id，
--    商品被归档后购物车仍能显示「已下架」而不是变成空白行。
--
-- 5. 不存商品标题与价格。<b>价格永远从 SKU 现读</b>，
--    存快照价格必然出现「购物车显示 99、下单变 109」，
--    而购物车到结算之间用户可能停留几天。
-- ============================================================

CREATE TABLE IF NOT EXISTS `mall_cart_item` (
    `cart_item_id` VARCHAR(64) NOT NULL COMMENT '行ID',
    `user_id` VARCHAR(64) NOT NULL COMMENT '归属用户；§16.3 要求深链做所有权校验',

    `sku_id` VARCHAR(64) NOT NULL COMMENT 'SKU，结算时按它现读价格与库存',
    `product_id` VARCHAR(64) NOT NULL COMMENT '冗余快照：商品归档后仍能显示占位',

    `quantity` INT NOT NULL DEFAULT 1 COMMENT '数量；>0 且 item_status=ACTIVE 才计入 §12.1 计数',
    `selected` TINYINT(1) NOT NULL DEFAULT 1
        COMMENT '是否参与结算；§12.1 计数口径的一部分',

    `item_status` VARCHAR(16) NOT NULL DEFAULT 'ACTIVE'
        COMMENT 'ACTIVE / REMOVED；软删，不物理删除（约束 3）',

    `created_at` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    `updated_at` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,

    PRIMARY KEY (`cart_item_id`),

    UNIQUE KEY `uk_mall_cart_user_sku` (`user_id`, `sku_id`)
        COMMENT '同一 SKU 只保留一行（约束 2），MyBatis 用 ON DUPLICATE KEY UPDATE 累加',

    KEY `idx_mall_cart_user_status` (`user_id`, `item_status`, `selected`)
        COMMENT '§12.1 计数走这条索引：只扫当前用户的有效行'
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4
  COMMENT='商城购物车；计数口径见《【我的】页面开发文档》§12.1';