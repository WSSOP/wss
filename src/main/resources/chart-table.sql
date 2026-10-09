-- ============================================================
-- 星盘表（如果不存在则创建）
-- ============================================================
CREATE TABLE IF NOT EXISTS `chart` (
    `chart_id` VARCHAR(64) NOT NULL COMMENT '星盘ID',
    `user_id` VARCHAR(32) NOT NULL COMMENT '用户ID',
    `name` VARCHAR(64) NOT NULL COMMENT '姓名',
    `gender` VARCHAR(8) NOT NULL COMMENT '性别: male/female',
    `birth_date` DATE NOT NULL COMMENT '出生日期',
    `birth_time` VARCHAR(16) DEFAULT NULL COMMENT '出生时间',
    `birth_place` VARCHAR(128) DEFAULT NULL COMMENT '出生地点',
    `latitude` DECIMAL(10,6) DEFAULT NULL COMMENT '纬度',
    `longitude` DECIMAL(10,6) DEFAULT NULL COMMENT '经度',
    `timezone` VARCHAR(32) DEFAULT 'Asia/Shanghai' COMMENT '时区',
    `is_default` TINYINT(1) DEFAULT 0 COMMENT '是否为默认星盘',
    `chart_data` JSON DEFAULT NULL COMMENT '星盘计算数据',
    `created_at` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    `updated_at` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    PRIMARY KEY (`chart_id`),
    KEY `idx_user_id` (`user_id`),
    KEY `idx_user_default` (`user_id`, `is_default`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='星盘表';
