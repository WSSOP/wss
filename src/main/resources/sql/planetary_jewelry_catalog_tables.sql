-- ============================================================
-- 行星珠宝建议｜商品与SKU表
--
-- 契约来源：catalog_contract.v1.json product_required_fields / sku_required_fields
--           05_主推次推与商城匹配 §3、§4；07_字段枚举与API附录 §2、§6
--
-- 设计约束（不可违反）：
-- 1. 商品ID一律由运营生成，代码与Skill都不得写死（catalog_contract.principles）；
-- 2. 软下架 / 归档，不物理删除：历史订阅、订单与推荐快照必须保留
--    （06_商品后台：软下架不删除历史引用；07 §6 同款要求）。
--    因此 publish_status 提供 ARCHIVED 终态；
-- 3. planet_code / remedy_type 是<b>商品自身的标签</b>，供商城按冻结方向匹配使用，
--    不参与推荐排序——排序只发生在冻结方向内部（05 §5）。
-- 4. source 用于区分数据来源：
--    SEED = 随程序内置的示例商品，运营上传真实商品后自动失效；
--    ADMIN_UPLOAD = 运营后台上传的真实商品。
--    同一 planet_code + remedy_type 下只要存在任一 ADMIN_UPLOAD 商品，
--    该组合下的全部 SEED 商品即自动停止对外展示（见 combo_key 生成列）。
-- 5. inventory_status 由 inventory_quantity 推导，避免两处数据打架；
--    生成列保证二者永远一致。
-- 6. variant_attributes 用 JSON 存款式/材质等可变属性，不拆表也不写死字段。
--
-- 关于 combo_key：<b>SEED 与 ADMIN_UPLOAD 都要有值</b>。
-- 如果只给 SEED 生成 key，真实商品自己 key=NULL，
-- 「真实商品到位后 SEED 自动下架」这条比较就永远匹配不上。
-- 因此两种来源都生成同一个 planet::remedy 键，屏蔽判断才能成立。
-- ============================================================

CREATE TABLE IF NOT EXISTS `planetary_jewelry_product` (
    `product_id` VARCHAR(64) NOT NULL COMMENT '商品ID，运营生成，禁止写死在代码里',
    `title` VARCHAR(128) NOT NULL COMMENT '商品标题',
    `planet_code` VARCHAR(16) NOT NULL
        COMMENT 'SUN/MOON/MARS/MERCURY/JUPITER/VENUS/SATURN；商品标签，不参与推荐排序',
    `remedy_type` VARCHAR(16) NOT NULL COMMENT 'YANTRA / GEMSTONE',
    `description` TEXT COMMENT '商品描述',

    `auto_recommendable` TINYINT(1) NOT NULL DEFAULT 1
        COMMENT '运营可手动关闭自动推荐；为 0 时任何模式都不自动展示（05 §4）',
    `publish_status` VARCHAR(16) NOT NULL DEFAULT 'DRAFT'
        COMMENT 'DRAFT/PUBLISHED/UNPUBLISHED/ARCHIVED；ARCHIVED 为终态，不再接受新订阅',
    `admin_recommendation_priority` INT NOT NULL DEFAULT 0
        COMMENT '运营优先级；只在同一冻结方向内部排序（05 §5），不得跨方向比较',

    `images` JSON DEFAULT NULL COMMENT '图片URL数组',

    `gift_available` TINYINT(1) NOT NULL DEFAULT 1 COMMENT '是否可作赠礼',
    `care_instructions` TEXT COMMENT '保养说明',
    `detail_html` MEDIUMTEXT COMMENT '详情富文本',

    `source` VARCHAR(24) NOT NULL DEFAULT 'ADMIN_UPLOAD'
        COMMENT 'SEED=内置示例商品；ADMIN_UPLOAD=运营上传真实商品',

    `combo_key` VARCHAR(64) GENERATED ALWAYS AS (
        CONCAT(`planet_code`, '::', `remedy_type`)
    ) STORED COMMENT 'planet::remedy 组合键；SEED 与真实商品都生成，用于互相屏蔽',

    `created_at` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    `updated_at` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,

    PRIMARY KEY (`product_id`),
    KEY `idx_planet_remedy_publish` (`planet_code`, `remedy_type`, `publish_status`),
    KEY `idx_publish_auto` (`publish_status`, `auto_recommendable`),
    KEY `idx_source` (`source`),
    KEY `idx_combo` (`combo_key`),
    KEY `idx_priority` (`admin_recommendation_priority`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='行星珠宝商品表';


CREATE TABLE IF NOT EXISTS `planetary_jewelry_sku` (
    `sku_id` VARCHAR(64) NOT NULL COMMENT 'SKU ID',
    `product_id` VARCHAR(64) NOT NULL COMMENT '所属商品ID',
    `variant_attributes` JSON DEFAULT NULL COMMENT '款式/材质等属性，结构可变',
    `price` DECIMAL(12,2) NOT NULL DEFAULT 0.00 COMMENT '售价；不参与推荐排序（GC14）',
    `inventory_quantity` INT NOT NULL DEFAULT 0 COMMENT '库存数量',

    `inventory_status` VARCHAR(16) GENERATED ALWAYS AS (
        CASE
            WHEN `inventory_quantity` <= 0 THEN 'OUT_OF_STOCK'
            WHEN `inventory_quantity` <= 3 THEN 'LOW_STOCK'
            ELSE 'IN_STOCK'
        END
    ) STORED COMMENT '由库存推导：0=OUT_OF_STOCK，1~3=LOW_STOCK，>=4=IN_STOCK',

    `status` VARCHAR(16) NOT NULL DEFAULT 'ACTIVE'
        COMMENT 'ACTIVE/CANCELLED；取消为软操作，保留历史引用',
    `source` VARCHAR(24) NOT NULL DEFAULT 'ADMIN_UPLOAD' COMMENT 'SEED / ADMIN_UPLOAD',

    `created_at` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    `updated_at` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,

    PRIMARY KEY (`sku_id`),
    KEY `idx_product_status` (`product_id`, `status`),
    -- 补货触发：库存从 0 变为正数的巡检走这条索引
    KEY `idx_inventory_quantity` (`inventory_quantity`),

    CONSTRAINT `fk_pj_sku_product` FOREIGN KEY (`product_id`)
        REFERENCES `planetary_jewelry_product` (`product_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='行星珠宝SKU表';
