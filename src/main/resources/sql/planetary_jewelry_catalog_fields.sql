-- ============================================================
-- 行星珠宝建议｜商品与SKU 字段补齐（06_商品后台与补货订阅 §2、§3）
--
-- 已有 planetary_jewelry_catalog_tables.sql 建了主干字段，
-- 本脚本补齐 06 §2/§3 明确要求、但主干表缺少的字段。
--
-- 注意：本项目的 MySQL 版本 < 8.0.29，不支持 ADD COLUMN IF NOT EXISTS，
-- 因此每个字段用 information_schema 判断后再改，可重复执行。
-- 辅助函数 _pj_has_column(table, column) 返回 1/0。
--
-- 这些字段只影响「同一冻结方向内」的展示与订阅行为，
-- 任何一项都不得影响 Recommendation Rank（05 §5、GC14）。
-- ============================================================

DROP FUNCTION IF EXISTS `_pj_has_column`;

DELIMITER $$

CREATE FUNCTION `_pj_has_column`(p_table VARCHAR(64), p_column VARCHAR(64))
    RETURNS TINYINT
    READS SQL DATA
BEGIN
    DECLARE n INT DEFAULT 0;
    SELECT COUNT(*) INTO n
    FROM information_schema.COLUMNS
    WHERE TABLE_SCHEMA = DATABASE()
      AND TABLE_NAME = p_table
      AND COLUMN_NAME = p_column;
    RETURN IF(n > 0, 1, 0);
END$$

DELIMITER ;


-- ---------- 商品：06 §2 基础字段 ----------
SET @ddl = IF(_pj_has_column('planetary_jewelry_product','subtitle'), 'DO 0',
  'ALTER TABLE `planetary_jewelry_product` ADD COLUMN `subtitle` VARCHAR(160) DEFAULT NULL COMMENT ''副标题，可空''');
PREPARE s FROM @ddl; EXECUTE s; DEALLOCATE PREPARE s;

SET @ddl = IF(_pj_has_column('planetary_jewelry_product','detail_content'), 'DO 0',
  'ALTER TABLE `planetary_jewelry_product` ADD COLUMN `detail_content` MEDIUMTEXT DEFAULT NULL COMMENT ''详情正文''');
PREPARE s FROM @ddl; EXECUTE s; DEALLOCATE PREPARE s;

SET @ddl = IF(_pj_has_column('planetary_jewelry_product','support_tags'), 'DO 0',
  'ALTER TABLE `planetary_jewelry_product` ADD COLUMN `support_tags` JSON DEFAULT NULL COMMENT ''辅助标签数组''');
PREPARE s FROM @ddl; EXECUTE s; DEALLOCATE PREPARE s;

SET @ddl = IF(_pj_has_column('planetary_jewelry_product','high_threshold_gemstone'), 'DO 0',
  'ALTER TABLE `planetary_jewelry_product` ADD COLUMN `high_threshold_gemstone` TINYINT(1) NOT NULL DEFAULT 0 COMMENT ''高门槛宝石标记''');
PREPARE s FROM @ddl; EXECUTE s; DEALLOCATE PREPARE s;

SET @ddl = IF(_pj_has_column('planetary_jewelry_product','product_form'), 'DO 0',
  'ALTER TABLE `planetary_jewelry_product` ADD COLUMN `product_form` VARCHAR(16) DEFAULT NULL COMMENT ''NECKLACE/BRACELET/PENDANT/RING/OTHER''');
PREPARE s FROM @ddl; EXECUTE s; DEALLOCATE PREPARE s;

SET @ddl = IF(_pj_has_column('planetary_jewelry_product','material_tags'), 'DO 0',
  'ALTER TABLE `planetary_jewelry_product` ADD COLUMN `material_tags` JSON DEFAULT NULL COMMENT ''材质标签数组''');
PREPARE s FROM @ddl; EXECUTE s; DEALLOCATE PREPARE s;

SET @ddl = IF(_pj_has_column('planetary_jewelry_product','color_tags'), 'DO 0',
  'ALTER TABLE `planetary_jewelry_product` ADD COLUMN `color_tags` JSON DEFAULT NULL COMMENT ''颜色标签数组''');
PREPARE s FROM @ddl; EXECUTE s; DEALLOCATE PREPARE s;


-- ---------- SKU：06 §3 字段 ----------
SET @ddl = IF(_pj_has_column('planetary_jewelry_sku','compare_at_price'), 'DO 0',
  'ALTER TABLE `planetary_jewelry_sku` ADD COLUMN `compare_at_price` DECIMAL(12,2) DEFAULT NULL COMMENT ''划线价''');
PREPARE s FROM @ddl; EXECUTE s; DEALLOCATE PREPARE s;

SET @ddl = IF(_pj_has_column('planetary_jewelry_sku','sale_enabled'), 'DO 0',
  'ALTER TABLE `planetary_jewelry_sku` ADD COLUMN `sale_enabled` TINYINT(1) NOT NULL DEFAULT 1 COMMENT ''是否可售''');
PREPARE s FROM @ddl; EXECUTE s; DEALLOCATE PREPARE s;

SET @ddl = IF(_pj_has_column('planetary_jewelry_sku','restock_waitlist_enabled'), 'DO 0',
  'ALTER TABLE `planetary_jewelry_sku` ADD COLUMN `restock_waitlist_enabled` TINYINT(1) NOT NULL DEFAULT 1 COMMENT ''是否允许登记补货''');
PREPARE s FROM @ddl; EXECUTE s; DEALLOCATE PREPARE s;


DROP FUNCTION IF EXISTS `_pj_has_column`;
