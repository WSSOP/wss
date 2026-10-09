-- ============================================================
-- 问星渡数据库建表脚本
-- 数据库：wenxingdu
-- ============================================================

CREATE DATABASE IF NOT EXISTS wenxingdu DEFAULT CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci;
USE wenxingdu;

-- ============================================================
-- 1. 用户表
-- ============================================================
CREATE TABLE IF NOT EXISTS `user` (
    `user_id` VARCHAR(32) NOT NULL COMMENT '用户ID',
    `phone` VARCHAR(20) DEFAULT NULL COMMENT '手机号',
    `nickname` VARCHAR(64) DEFAULT NULL COMMENT '昵称',
    `avatar` VARCHAR(255) DEFAULT NULL COMMENT '头像URL',
    `gender` VARCHAR(10) DEFAULT 'male' COMMENT '性别',
    `star_balance` INT DEFAULT 0 COMMENT '星印余额',
    `level` VARCHAR(16) DEFAULT 'free' COMMENT '用户等级：free/vip/svip',
    `vip_expire_at` DATETIME DEFAULT NULL COMMENT 'VIP过期时间',
    `daily_free_remaining` INT DEFAULT 2 COMMENT '每日免费次数剩余',
    `extra_pack_remaining` INT DEFAULT 0 COMMENT '额外次数包剩余',
    `created_at` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    `updated_at` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    PRIMARY KEY (`user_id`),
    UNIQUE KEY `uk_phone` (`phone`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='用户表';

-- ============================================================
-- 2. 报告实例表
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

-- ============================================================
-- 3. 追问会话表
-- ============================================================
CREATE TABLE IF NOT EXISTS `conversation` (
    `conversation_id` VARCHAR(64) NOT NULL COMMENT '会话ID',
    `user_id` VARCHAR(32) NOT NULL COMMENT '用户ID',
    `report_instance_id` VARCHAR(64) NOT NULL COMMENT '绑定的报告实例ID',
    `status` VARCHAR(16) DEFAULT 'ACTIVE' COMMENT '状态：ACTIVE/EXPIRED/CLOSED',
    `turn_count` INT DEFAULT 0 COMMENT '追问轮数',
    `last_question` TEXT DEFAULT NULL COMMENT '最近问题',
    `last_active_at` DATETIME DEFAULT NULL COMMENT '最近活跃时间',
    `expire_at` DATETIME DEFAULT NULL COMMENT '过期时间',
    `created_at` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    `updated_at` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    PRIMARY KEY (`conversation_id`),
    KEY `idx_user_id` (`user_id`),
    KEY `idx_report_instance_id` (`report_instance_id`),
    KEY `idx_status` (`status`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='追问会话表';

-- ============================================================
-- 4. 追问请求表
-- ============================================================
CREATE TABLE IF NOT EXISTS `followup_request` (
    `request_id` VARCHAR(64) NOT NULL COMMENT '请求ID',
    `idempotency_key` VARCHAR(128) NOT NULL COMMENT '幂等键',
    `user_id` VARCHAR(32) NOT NULL COMMENT '用户ID',
    `conversation_id` VARCHAR(64) NOT NULL COMMENT '会话ID',
    `source_context` VARCHAR(16) DEFAULT 'REPORT' COMMENT '来源上下文',
    `report_instance_id` VARCHAR(64) NOT NULL COMMENT '报告实例ID',
    `question` TEXT NOT NULL COMMENT '用户问题',
    `requested_mode` VARCHAR(8) NOT NULL COMMENT '请求模式：QUICK/DEEP',
    `answer_mode` VARCHAR(16) DEFAULT NULL COMMENT '实际回答模式',
    `intent` VARCHAR(32) DEFAULT NULL COMMENT '路由意图',
    `request_status` VARCHAR(32) NOT NULL DEFAULT 'CREATED' COMMENT '请求状态',
    `answer_status` VARCHAR(32) DEFAULT NULL COMMENT '回答状态',
    `block_reason` VARCHAR(32) DEFAULT NULL COMMENT '阻塞原因',
    `route_target` VARCHAR(16) DEFAULT NULL COMMENT '路由目标',
    `quote_token` VARCHAR(128) DEFAULT NULL COMMENT '询价token',
    `answer_content` JSON DEFAULT NULL COMMENT '回答内容JSON',
    `version_info` JSON DEFAULT NULL COMMENT '版本信息JSON',
    `entitlement_source` VARCHAR(16) DEFAULT NULL COMMENT '权益来源',
    `ledger_status` VARCHAR(16) DEFAULT NULL COMMENT '权益流水状态',
    `consumed` TINYINT(1) DEFAULT 0 COMMENT '是否已核销',
    `star_cost` INT DEFAULT 0 COMMENT '消耗星印数',
    `retry_count` INT DEFAULT 0 COMMENT '重试次数',
    `client_context` JSON DEFAULT NULL COMMENT '客户端上下文JSON',
    `created_at` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    `completed_at` DATETIME DEFAULT NULL COMMENT '完成时间',
    PRIMARY KEY (`request_id`),
    UNIQUE KEY `uk_idempotency_key` (`idempotency_key`),
    KEY `idx_user_id` (`user_id`),
    KEY `idx_conversation_id` (`conversation_id`),
    KEY `idx_report_instance_id` (`report_instance_id`),
    KEY `idx_request_status` (`request_status`),
    KEY `idx_created_at` (`created_at`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='追问请求表';

-- ============================================================
-- 5. 权益流水表
-- ============================================================
CREATE TABLE IF NOT EXISTS `entitlement_ledger` (
    `id` BIGINT NOT NULL AUTO_INCREMENT COMMENT '流水ID',
    `request_id` VARCHAR(64) NOT NULL COMMENT '请求ID',
    `user_id` VARCHAR(32) NOT NULL COMMENT '用户ID',
    `report_instance_id` VARCHAR(64) DEFAULT NULL COMMENT '报告实例ID',
    `ledger_action_type` VARCHAR(16) NOT NULL COMMENT '流水动作类型',
    `entitlement_source` VARCHAR(16) DEFAULT NULL COMMENT '权益来源',
    `ledger_status` VARCHAR(16) NOT NULL COMMENT '流水状态',
    `star_amount` INT DEFAULT 0 COMMENT '星印金额',
    `balance_before` INT DEFAULT NULL COMMENT '操作前余额',
    `balance_after` INT DEFAULT NULL COMMENT '操作后余额',
    `remark` VARCHAR(255) DEFAULT NULL COMMENT '备注',
    `created_at` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    PRIMARY KEY (`id`),
    KEY `idx_request_id` (`request_id`),
    KEY `idx_user_id` (`user_id`),
    KEY `idx_ledger_action_type` (`ledger_action_type`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='权益流水表';

-- ============================================================
-- 6. 报告赠送追问权益表
-- ============================================================
CREATE TABLE IF NOT EXISTS `report_gift_entitlement` (
    `id` BIGINT NOT NULL AUTO_INCREMENT COMMENT 'ID',
    `report_instance_id` VARCHAR(64) NOT NULL COMMENT '报告实例ID',
    `user_id` VARCHAR(32) NOT NULL COMMENT '用户ID',
    `benefit_type` VARCHAR(32) DEFAULT 'FOLLOWUP_GIFT' COMMENT '权益类型',
    `used` TINYINT(1) DEFAULT 0 COMMENT '是否已使用',
    `used_at` DATETIME DEFAULT NULL COMMENT '使用时间',
    `used_by_request_id` VARCHAR(64) DEFAULT NULL COMMENT '关联请求ID',
    `created_at` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    `expire_at` DATETIME DEFAULT NULL COMMENT '过期时间',
    PRIMARY KEY (`id`),
    UNIQUE KEY `uk_report_benefit` (`report_instance_id`, `benefit_type`),
    KEY `idx_user_id` (`user_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='报告赠送追问权益表';

-- ============================================================
-- 插入测试数据
-- ============================================================

-- 测试用户
INSERT INTO `user` (`user_id`, `phone`, `nickname`, `star_balance`, `level`, `daily_free_remaining`)
VALUES ('u_001', '13800138000', '测试用户', 100, 'free', 2);

-- 测试报告实例
INSERT INTO `report_instance` (`report_instance_id`, `user_id`, `report_type`, `report_title`, `subject_label`, `report_version`, `source_skill`, `generated_at`, `status`, `followup_available`)
VALUES ('rp_life_001', 'u_001', 'LIFEPATTERN_GROWTH', '人生课题深度报告', '本人', '1.0.0', 'LIFEPATTERN_GROWTH_REPORT', '2026-08-15 14:00:00', 'COMPLETED', 1);

INSERT INTO `report_instance` (`report_instance_id`, `user_id`, `report_type`, `report_title`, `subject_label`, `report_version`, `source_skill`, `generated_at`, `status`, `followup_available`)
VALUES ('rp_career_001', 'u_001', 'CAREER', '事业深度报告', '本人', '1.0.0', 'CAREER_REPORT', '2026-08-20 10:00:00', 'COMPLETED', 1);

-- 测试赠送追问权益
INSERT INTO `report_gift_entitlement` (`report_instance_id`, `user_id`, `benefit_type`, `used`)
VALUES ('rp_life_001', 'u_001', 'FOLLOWUP_GIFT', 0);

INSERT INTO `report_gift_entitlement` (`report_instance_id`, `user_id`, `benefit_type`, `used`)
VALUES ('rp_career_001', 'u_001', 'FOLLOWUP_GIFT', 0);
