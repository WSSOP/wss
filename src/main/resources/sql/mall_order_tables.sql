-- ============================================================
-- 问星渡｜商城全链路（订单 / 地址 / 定制需求 / 结算 token）
--
-- 契约来源：《问星渡_商城HTML原型_程序员对照说明_V1》
--   §2 锁定规则、§4 程序实现要求、§5 CMS 契约（本轮只定契约）
--
-- 设计约束（不可违反）：
--
-- 1. <b>交易类型只有三类</b>（§2.2）：STANDARD / PREORDER / CUSTOM_QUOTE。
--    订单只承载前两类；CUSTOM_QUOTE 走定制需求单，永远不生成待付款订单（§2.9）。
--
-- 2. <b>现货与预售不可混结，不同预售批次不可混结</b>（§2.6）。
--    因此一单 = 一个结算分组，用 group_key 固化：
--      STANDARD        分组键 = 'STANDARD'
--      PREORDER        分组键 = 'PREORDER:' + batch（同批次才能同单）
--    提交订单时后端强校验所有行同组，跨组直接拒绝（§2.6）。
--
-- 3. <b>价格与文案都是下单快照</b>（§4 + 历史订单规则）。
--    运营改价 / 下架 / 改名都不影响已生成订单的展示与账目。
--
-- 4. <b>提交订单成功才锁库存 15 分钟</b>（§2.7）。
--    浏览 / 加购 / 打开结算一律不锁。stock_locked_until 只在订单建立时写入。
--    仅现货要锁，预售（等补货）没有可售库存可锁。
--
-- 5. <b>实体商品仅微信 / 支付宝</b>（§2.8），不出现星印支付。
--
-- 6. <b>用户主状态固定</b>（§2.10）：
--      PENDING_PAYMENT(待付款) -> PAID(待发货) -> SHIPPED(已发货) -> COMPLETED(已完成)
--    另有一个终态 CANCELLED。前台<b>无【确认收货】按钮</b>，
--    COMPLETED 由发货后到期的服务端逻辑推进，不由用户点击触发。
-- ============================================================

-- ------------------------------------------------------------
-- 订单
-- ------------------------------------------------------------
CREATE TABLE IF NOT EXISTS `mall_order` (
    `order_id` VARCHAR(64) NOT NULL COMMENT 'mo_ + 去横杠 UUID',

    `user_id` VARCHAR(64) NOT NULL COMMENT '归属用户；订单查询/支付/取消都按它做所有权校验',

    `order_no` VARCHAR(32) NOT NULL COMMENT '展示用订单号（人类可读）',

    `trade_group` VARCHAR(8) NOT NULL COMMENT 'STANDARD / PREORDER；一单只属于一个分组',
    `group_key` VARCHAR(96) NOT NULL COMMENT '分组键；PREORDER 时含 batch，见约束 2',

    `goods_amount` DECIMAL(12,2) NOT NULL COMMENT '商品金额快照 = Σ SKU价×数量（下单现读）',
    `freight_amount` DECIMAL(12,2) NOT NULL DEFAULT 0 COMMENT '运费快照；预售恒 0（包邮）',
    `discount_amount` DECIMAL(12,2) NOT NULL DEFAULT 0 COMMENT '优惠快照；V1 无优惠券恒 0，字段占位防止口径改动',
    `payable_amount` DECIMAL(12,2) NOT NULL COMMENT '应付 = goods + freight - discount',

    `address_snapshot` JSON DEFAULT NULL COMMENT '收货地址下单快照；订单不改地址（§历史订单规则）',

    `payment_channel` VARCHAR(16) DEFAULT NULL COMMENT 'WECHAT_PAY / ALIPAY；PENDING_PAYMENT 时可为空',
    `order_status` VARCHAR(16) NOT NULL DEFAULT 'PENDING_PAYMENT'
        COMMENT 'PENDING_PAYMENT/PAID/SHIPPED/COMPLETED/CANCELLED，见约束 6',

    `stock_locked_until` DATETIME DEFAULT NULL
        COMMENT '现货锁库存截止时间 = 下单时间 + 15min；预售为 NULL（无库存可锁）',
    `paid_at` DATETIME DEFAULT NULL,
    `cancelled_at` DATETIME DEFAULT NULL,
    `shipped_at` DATETIME DEFAULT NULL,
    `completed_at` DATETIME DEFAULT NULL,

    `remark` VARCHAR(255) DEFAULT NULL COMMENT '订单备注，选填',
    `source` VARCHAR(32) NOT NULL DEFAULT 'MALL' COMMENT '下单来源页',

    `created_at` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    `updated_at` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,

    PRIMARY KEY (`order_id`),
    KEY `idx_mall_order_user` (`user_id`, `order_status`, `created_at`)
        COMMENT '订单列表：当前用户、按状态过滤、按时间倒序（§我的订单 商品 tab）',
    KEY `idx_mall_order_no` (`order_no`)
        COMMENT '支付回调按 order_no 定位订单，幂等'
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4
  COMMENT='商城实体商品订单；一单=一个结算分组，价格/文案全为下单快照';

-- ------------------------------------------------------------
-- 订单行（SKU 快照）
-- ------------------------------------------------------------
CREATE TABLE IF NOT EXISTS `mall_order_item` (
    `item_id` VARCHAR(64) NOT NULL COMMENT 'oi_ + 去横杠 UUID',
    `order_id` VARCHAR(64) NOT NULL,

    `sku_id` VARCHAR(64) NOT NULL,
    `product_id` VARCHAR(64) NOT NULL,

    `product_title` VARCHAR(128) NOT NULL COMMENT '下单商品标题快照',
    `sku_variant` VARCHAR(128) DEFAULT NULL COMMENT '规格快照（如 银色/标准链）',

    `trade_group` VARCHAR(8) NOT NULL COMMENT '该行属于 STANDARD 还是 PREORDER',
    `batch` VARCHAR(32) DEFAULT NULL COMMENT '预售批次；STANDARD 恒 NULL',

    `price` DECIMAL(12,2) NOT NULL COMMENT '该 SKU 下单时现价快照',
    `quantity` INT NOT NULL,
    `subtotal` DECIMAL(12,2) NOT NULL COMMENT 'price × quantity 快照',

    PRIMARY KEY (`item_id`),
    KEY `idx_mall_order_item_order` (`order_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4
  COMMENT='订单行：SKU 快照，运营改价/下架不影响历史订单展示';

-- ------------------------------------------------------------
-- 收货地址
-- ------------------------------------------------------------
CREATE TABLE IF NOT EXISTS `mall_address` (
    `address_id` VARCHAR(64) NOT NULL COMMENT 'ad_ + 去横杠 UUID',
    `user_id` VARCHAR(64) NOT NULL COMMENT '地址归属；增删查都按它校验所有权',

    `name` VARCHAR(32) NOT NULL COMMENT '收货人',
    `phone` VARCHAR(16) NOT NULL COMMENT '手机号',
    `province` VARCHAR(32) NOT NULL,
    `city` VARCHAR(32) NOT NULL,
    `district` VARCHAR(32) DEFAULT NULL,
    `detail` VARCHAR(128) NOT NULL COMMENT '详细地址',

    `is_default` TINYINT(1) NOT NULL DEFAULT 0,

    `created_at` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    `updated_at` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,

    PRIMARY KEY (`address_id`),
    KEY `idx_mall_address_user` (`user_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4
  COMMENT='商城收货地址；下单时存快照进订单，此后改地址不影响已下订单';

-- ------------------------------------------------------------
-- 定制需求单（§2.9：只生成 custom_request_id，不生成待付款订单）
-- ------------------------------------------------------------
CREATE TABLE IF NOT EXISTS `mall_custom_request` (
    `request_id` VARCHAR(64) NOT NULL COMMENT 'cr_ + 去横杠 UUID',
    `user_id` VARCHAR(64) NOT NULL,
    `product_id` VARCHAR(64) NOT NULL COMMENT 'CUSTOM_QUOTE 商品；字段动态配置，payload 承接',

    `payload` JSON NOT NULL COMMENT '定制字段由后台动态配置，统一存 JSON，不写死固定列',
    `status` VARCHAR(16) NOT NULL DEFAULT 'SUBMITTED'
        COMMENT 'SUBMITTED(已提交) / QUOTED(已报价) / CONFIRMED(已确认进入付款)；V1 停在 SUBMITTED',

    `created_at` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    `updated_at` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,

    PRIMARY KEY (`request_id`),
    KEY `idx_mall_custom_user` (`user_id`, `created_at`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4
  COMMENT='定制需求单：提交不产生订单/待付款/锁库存/应收金额（§2.9）';

-- ------------------------------------------------------------
-- 结算 token（§4：必须由服务端签发，前端不得用商品ID/价格拼）
-- ------------------------------------------------------------
CREATE TABLE IF NOT EXISTS `mall_checkout_token` (
    `token` VARCHAR(64) NOT NULL COMMENT '随机 UUID，服务端生成',
    `user_id` VARCHAR(64) NOT NULL,

    `trade_group` VARCHAR(8) NOT NULL,
    `group_key` VARCHAR(96) NOT NULL,

    `sku_list` JSON NOT NULL COMMENT '本 token 授权覆盖的 [ {sku_id, quantity} ]；价格不在这里，结算现读',
    `expires_at` DATETIME NOT NULL COMMENT '短时效；过期即作废',

    `created_at` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,

    PRIMARY KEY (`token`),
    KEY `idx_mall_checkout_user` (`user_id`, `expires_at`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4
  COMMENT='结算授权：服务端签发的短时效凭证，仅承载『这组SKU/数量可结算』，不含价格（§4）';