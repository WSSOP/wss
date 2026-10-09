-- ============================================================
-- 修改报告实例表的列长度（修复 schema_version 和 renderer_version 长度不足的问题）
-- ============================================================
ALTER TABLE `report_instance` MODIFY COLUMN `schema_version` VARCHAR(64) DEFAULT NULL COMMENT 'Schema版本';
ALTER TABLE `report_instance` MODIFY COLUMN `renderer_version` VARCHAR(64) DEFAULT NULL COMMENT 'Renderer版本';
