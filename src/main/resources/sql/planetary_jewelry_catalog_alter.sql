-- ============================================================
-- 行星珠宝建议｜商品与 SKU 表增补字段（一次性迁移）
--
-- 契约来源：06_商品后台与补货订阅 §2（商品字段）、§3（SKU字段）
--
-- 背景：planetary_jewelry_catalog_tables.sql 的初版 DDL 只覆盖了
-- 05_主推次推与商城匹配 §3 的「必备标签」，缺少 06 §2/§3 要求的
-- 运营字段。此脚本补齐。
--
-- 目标库：MySQL 5.7 —— 不支持 ADD COLUMN IF NOT EXISTS（8.0.29+），
-- 因此本脚本按一次性迁移编写：重复执行会报 Duplicate column，属预期。
-- 全部新增列可空或带默认值，不影响既有数据。
-- ============================================================

-- ---------- 商品：06 §2 基础字段 ----------
ALTER TABLE `planetary_jewelry_product`
    ADD COLUMN `subtitle` VARCHAR(160) DEFAULT NULL
        COMMENT '副标题' AFTER `title`,
    ADD COLUMN `detail_content` MEDIUMTEXT DEFAULT NULL
        COMMENT '详情正文（06 §2 detail_content）' AFTER `care_instructions`,
    ADD COLUMN `support_tags` JSON DEFAULT NULL
        COMMENT '辅助标签数组（06 §2 support_tags[]）' AFTER `gift_available`,
    ADD COLUMN `high_threshold_gemstone` TINYINT(1) NOT NULL DEFAULT 0
        COMMENT '高门槛宝石款：需更高门槛才展示' AFTER `auto_recommendable`,
    ADD COLUMN `product_form` VARCHAR(16) DEFAULT NULL
        COMMENT 'NECKLACE/BRACELET/PENDANT/RING/OTHER' AFTER `high_threshold_gemstone`,
    ADD COLUMN `material_tags` JSON DEFAULT NULL
        COMMENT '材质标签数组' AFTER `product_form`,
    ADD COLUMN `color_tags` JSON DEFAULT NULL
        COMMENT '颜色标签数组' AFTER `material_tags`;

-- ---------- SKU：06 §3 字段 ----------
ALTER TABLE `planetary_jewelry_sku`
    ADD COLUMN `compare_at_price` DECIMAL(12,2) DEFAULT NULL
        COMMENT '划线价；仅展示，不参与推荐排序（GC14）' AFTER `price`,
    ADD COLUMN `sale_enabled` TINYINT(1) NOT NULL DEFAULT 1
        COMMENT '是否允许下单；缺货时仍可展示但不可下单' AFTER `inventory_quantity`,
    ADD COLUMN `restock_waitlist_enabled` TINYINT(1) NOT NULL DEFAULT 1
        COMMENT '是否允许登记补货订阅；商品已下架时置 0' AFTER `status`;
