-- ============================================================
-- 报告实例表（如果不存在则创建）
-- ============================================================
CREATE TABLE IF NOT EXISTS `report_instance` (
    `report_instance_id` VARCHAR(64) NOT NULL COMMENT '报告实例ID',
    `user_id` VARCHAR(32) NOT NULL COMMENT '用户ID',
    `report_type` VARCHAR(32) NOT NULL COMMENT '报告类型',
    `report_title` VARCHAR(128) NOT NULL COMMENT '报告名称',
    `subject_label` VARCHAR(128) DEFAULT NULL COMMENT '报告对象标签',
    `report_version` VARCHAR(16) DEFAULT '1.0.0' COMMENT '报告版本',
    `source_skill` VARCHAR(64) DEFAULT NULL COMMENT '来源Skill',
    `skill_version` VARCHAR(16) DEFAULT NULL COMMENT 'Skill版本',
    `schema_version` VARCHAR(64) DEFAULT NULL COMMENT 'Schema版本',
    `renderer_version` VARCHAR(64) DEFAULT NULL COMMENT 'Renderer版本',
    `data_snapshot_id` VARCHAR(64) DEFAULT NULL COMMENT '数据快照ID',
    `chart_id` VARCHAR(64) DEFAULT NULL COMMENT '星盘ID',
    `secondary_chart_id` VARCHAR(64) DEFAULT NULL COMMENT '第二人星盘ID',
    `generated_at` DATETIME NOT NULL COMMENT '生成时间',
    `status` VARCHAR(16) DEFAULT 'COMPLETED' COMMENT '状态',
    `followup_available` TINYINT(1) DEFAULT 1 COMMENT '是否可追问',
    `followup_config` JSON DEFAULT NULL COMMENT '追问配置JSON',
    `final_payload` JSON DEFAULT NULL COMMENT '最终报告JSON',
    `created_at` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    `updated_at` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    PRIMARY KEY (`report_instance_id`),
    KEY `idx_user_id` (`user_id`),
    KEY `idx_report_type` (`report_type`),
    KEY `idx_generated_at` (`generated_at`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='报告实例表';
