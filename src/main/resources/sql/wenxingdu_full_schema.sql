-- ============================================================================
-- 问星渡（wenxingdu）完整数据库脚本
-- ============================================================================
-- 数据库   : MySQL 5.7+ / 8.0+（含 JSON 类型支持）
-- 字符集   : utf8mb4 / utf8mb4_unicode_ci
-- 内容     : 建库 + 全部 17 张表 + 索引 + 测试数据
-- 用法     : mysql -uroot -p < wenxingdu_full_schema.sql
-- 说明     : 全部使用 CREATE TABLE IF NOT EXISTS，可重复执行；
--            与 application.yml 的 sql.init（schema.sql / chart-table.sql /
--            relationship-report-table.sql / alter-report-instance.sql /
--            sql/career_report_tables.sql）以及注解 Mapper（ai_conversation、
--            ai_conversation_message）完全对应。
-- ============================================================================

CREATE DATABASE IF NOT EXISTS wenxingdu DEFAULT CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci;
USE wenxingdu;

-- ============================================================================
-- 一、核心业务表
-- ============================================================================

-- ----------------------------------------------------------------------------
-- 1. 用户表
-- ----------------------------------------------------------------------------
CREATE TABLE IF NOT EXISTS `user` (
    `user_id` VARCHAR(32) NOT NULL COMMENT '用户ID',
    `phone` VARCHAR(20) DEFAULT NULL COMMENT '手机号',
    `nickname` VARCHAR(64) DEFAULT NULL COMMENT '昵称',
    `avatar` VARCHAR(255) DEFAULT NULL COMMENT '头像URL',
    `gender` VARCHAR(10) DEFAULT 'male' COMMENT '性别：male/female/others',
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

-- ----------------------------------------------------------------------------
-- 2. 星盘表
-- ----------------------------------------------------------------------------
CREATE TABLE IF NOT EXISTS `chart` (
    `chart_id` VARCHAR(64) NOT NULL COMMENT '星盘ID',
    `user_id` VARCHAR(32) NOT NULL COMMENT '用户ID',
    `name` VARCHAR(64) NOT NULL COMMENT '姓名',
    `gender` VARCHAR(8) NOT NULL COMMENT '性别：male/female',
    `birth_date` DATE NOT NULL COMMENT '出生日期',
    `birth_time` VARCHAR(16) DEFAULT NULL COMMENT '出生时间',
    `birth_place` VARCHAR(128) DEFAULT NULL COMMENT '出生地点',
    `latitude` DECIMAL(10,6) DEFAULT NULL COMMENT '纬度',
    `longitude` DECIMAL(10,6) DEFAULT NULL COMMENT '经度',
    `timezone` VARCHAR(32) DEFAULT 'Asia/Shanghai' COMMENT '时区',
    `is_default` TINYINT(1) DEFAULT 0 COMMENT '是否为默认星盘',
    `chart_data` JSON DEFAULT NULL COMMENT '星盘计算数据',
    `created_at` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    `updated_at` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    PRIMARY KEY (`chart_id`),
    KEY `idx_user_id` (`user_id`),
    KEY `idx_user_default` (`user_id`, `is_default`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='星盘表';

-- ----------------------------------------------------------------------------
-- 3. 报告实例表（人生课题/学业/子女/婚恋/健康/合盘等统一实例表）
--    注意：schema_version / renderer_version 已按 alter-report-instance.sql
--    直接建为 VARCHAR(64)
-- ----------------------------------------------------------------------------
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
    `status` VARCHAR(16) DEFAULT 'COMPLETED' COMMENT '状态：COMPLETED/GENERATING/FAILED',
    `followup_available` TINYINT(1) DEFAULT 1 COMMENT '是否可追问',
    `followup_config` JSON DEFAULT NULL COMMENT '追问配置JSON',
    `final_payload` JSON DEFAULT NULL COMMENT '最终报告JSON',
    `created_at` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    `updated_at` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    PRIMARY KEY (`report_instance_id`),
    KEY `idx_user_id` (`user_id`),
    KEY `idx_report_type` (`report_type`),
    KEY `idx_generated_at` (`generated_at`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='报告实例表';

-- ----------------------------------------------------------------------------
-- 4. 报告追问会话表（报告实例绑定的追问会话）
-- ----------------------------------------------------------------------------
CREATE TABLE IF NOT EXISTS `conversation` (
    `conversation_id` VARCHAR(64) NOT NULL COMMENT '会话ID',
    `user_id` VARCHAR(32) NOT NULL COMMENT '用户ID',
    `report_instance_id` VARCHAR(64) NOT NULL COMMENT '绑定的报告实例ID',
    `status` VARCHAR(16) DEFAULT 'ACTIVE' COMMENT '状态：ACTIVE/EXPIRED/CLOSED',
    `turn_count` INT DEFAULT 0 COMMENT '追问轮数',
    `last_question` TEXT COMMENT '最近问题',
    `last_active_at` DATETIME DEFAULT NULL COMMENT '最近活跃时间',
    `expire_at` DATETIME DEFAULT NULL COMMENT '过期时间',
    `created_at` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    `updated_at` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    PRIMARY KEY (`conversation_id`),
    KEY `idx_user_id` (`user_id`),
    KEY `idx_report_instance_id` (`report_instance_id`),
    KEY `idx_status` (`status`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='追问会话表';

-- ----------------------------------------------------------------------------
-- 5. 追问请求表（每次追问的完整生命周期）
-- ----------------------------------------------------------------------------
CREATE TABLE IF NOT EXISTS `followup_request` (
    `request_id` VARCHAR(64) NOT NULL COMMENT '请求ID',
    `idempotency_key` VARCHAR(128) NOT NULL COMMENT '幂等键',
    `user_id` VARCHAR(32) NOT NULL COMMENT '用户ID',
    `conversation_id` VARCHAR(64) NOT NULL COMMENT '会话ID',
    `source_context` VARCHAR(16) DEFAULT 'REPORT' COMMENT '来源上下文：REPORT/STAR_AI/HISTORY',
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
    `created_at` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    `completed_at` DATETIME DEFAULT NULL COMMENT '完成时间',
    PRIMARY KEY (`request_id`),
    UNIQUE KEY `uk_idempotency_key` (`idempotency_key`),
    KEY `idx_user_id` (`user_id`),
    KEY `idx_conversation_id` (`conversation_id`),
    KEY `idx_report_instance_id` (`report_instance_id`),
    KEY `idx_request_status` (`request_status`),
    KEY `idx_created_at` (`created_at`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='追问请求表';

-- ----------------------------------------------------------------------------
-- 6. 权益流水表（星印扣减/赠送核销等资金类流水）
-- ----------------------------------------------------------------------------
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
    `created_at` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    PRIMARY KEY (`id`),
    KEY `idx_request_id` (`request_id`),
    KEY `idx_user_id` (`user_id`),
    KEY `idx_ledger_action_type` (`ledger_action_type`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='权益流水表';

-- ----------------------------------------------------------------------------
-- 7. 报告赠送追问权益表（每份报告赠送 1 次追问）
-- ----------------------------------------------------------------------------
CREATE TABLE IF NOT EXISTS `report_gift_entitlement` (
    `id` BIGINT NOT NULL AUTO_INCREMENT COMMENT 'ID',
    `report_instance_id` VARCHAR(64) NOT NULL COMMENT '报告实例ID',
    `user_id` VARCHAR(32) NOT NULL COMMENT '用户ID',
    `benefit_type` VARCHAR(32) DEFAULT 'FOLLOWUP_GIFT' COMMENT '权益类型',
    `used` TINYINT(1) DEFAULT 0 COMMENT '是否已使用',
    `used_at` DATETIME DEFAULT NULL COMMENT '使用时间',
    `used_by_request_id` VARCHAR(64) DEFAULT NULL COMMENT '关联请求ID',
    `created_at` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    `expire_at` DATETIME DEFAULT NULL COMMENT '过期时间',
    PRIMARY KEY (`id`),
    UNIQUE KEY `uk_report_benefit` (`report_instance_id`, `benefit_type`),
    KEY `idx_user_id` (`user_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='报告赠送追问权益表';

-- ============================================================================
-- 二、星灵AI 会话表（6 种模式：NATAL/SYNASTRY/PRASHNA/EMOTIONAL/KNOWLEDGE/
--     REPORT_FOLLOWUP，注解 Mapper：AIConversationMapper /
--     AIConversationMessageMapper）
-- ============================================================================

-- ----------------------------------------------------------------------------
-- 8. AI 会话表
-- ----------------------------------------------------------------------------
CREATE TABLE IF NOT EXISTS `ai_conversation` (
    `id` VARCHAR(64) NOT NULL COMMENT '会话ID',
    `user_id` VARCHAR(32) NOT NULL COMMENT '用户ID',
    `mode` VARCHAR(32) NOT NULL COMMENT '会话模式：NATAL/SYNASTRY/PRASHNA/EMOTIONAL/KNOWLEDGE/REPORT_FOLLOWUP',
    `first_question` TEXT COMMENT '首个问题',
    `response_style` VARCHAR(16) DEFAULT 'DIRECT' COMMENT '表达风格：DIRECT/GENTLE/PROFESSIONAL',
    `context_json` MEDIUMTEXT COMMENT '上下文数据JSON',
    `source_context` VARCHAR(16) DEFAULT NULL COMMENT '来源上下文',
    `status` VARCHAR(16) DEFAULT 'ACTIVE' COMMENT '状态：ACTIVE/EXPIRED/DELETED',
    `message_count` INT DEFAULT 0 COMMENT '消息数量',
    `last_active_at` DATETIME DEFAULT NULL COMMENT '最后活跃时间',
    `created_at` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    `updated_at` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    PRIMARY KEY (`id`),
    KEY `idx_user_status` (`user_id`, `status`, `last_active_at`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='星灵AI会话表';

-- ----------------------------------------------------------------------------
-- 9. AI 会话消息表
-- ----------------------------------------------------------------------------
CREATE TABLE IF NOT EXISTS `ai_conversation_message` (
    `id` VARCHAR(64) NOT NULL COMMENT '消息ID',
    `conversation_id` VARCHAR(64) NOT NULL COMMENT '会话ID',
    `role` VARCHAR(16) NOT NULL COMMENT '角色：user/assistant/system',
    `content` MEDIUMTEXT COMMENT '消息内容',
    `message_type` VARCHAR(32) DEFAULT 'TEXT' COMMENT '消息类型：TEXT/SECTION/REPORT_RECOMMENDATION',
    `structured_content_json` MEDIUMTEXT COMMENT '结构化内容JSON',
    `tokens_used` INT DEFAULT 0 COMMENT 'token使用量',
    `sort_order` INT DEFAULT 0 COMMENT '排序序号',
    `created_at` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    PRIMARY KEY (`id`),
    KEY `idx_conversation_order` (`conversation_id`, `sort_order`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='星灵AI会话消息表';

-- ============================================================================
-- 三、事业深度报告表（结构化存储：Instance / Section / Claim / Evidence /
--     Timing / Recommendation / ClaimRelation / Condition）
-- ============================================================================

-- ----------------------------------------------------------------------------
-- 10. 事业报告实例表
-- ----------------------------------------------------------------------------
CREATE TABLE IF NOT EXISTS `career_report_instance` (
    `report_instance_id` VARCHAR(64) NOT NULL COMMENT '报告实例ID',
    `report_type` VARCHAR(32) NOT NULL DEFAULT 'CAREER' COMMENT '报告类型',
    `report_status` VARCHAR(16) NOT NULL DEFAULT 'GENERATING' COMMENT '报告状态：GENERATING/COMPLETED/FAILED',
    `report_content_version` VARCHAR(16) DEFAULT 'V1' COMMENT '内容版本',
    `report_schema_version` VARCHAR(16) DEFAULT 'CAREER_V1' COMMENT 'Schema版本',
    `generated_at` DATETIME DEFAULT NULL COMMENT '生成时间',
    `user_id` VARCHAR(64) NOT NULL COMMENT '用户ID',
    `primary_chart_id` VARCHAR(64) DEFAULT NULL COMMENT '主星盘ID',
    `chart_snapshot_id` VARCHAR(64) DEFAULT NULL COMMENT '星盘快照ID',
    `dasha_snapshot_id` VARCHAR(64) DEFAULT NULL COMMENT 'Dasha快照ID',
    `user_context_snapshot_id` VARCHAR(64) DEFAULT NULL COMMENT '用户上下文快照ID',
    `source_skill` VARCHAR(64) DEFAULT 'CAREER_REPORT_SKILL' COMMENT '来源Skill',
    `source_skill_version` VARCHAR(16) DEFAULT 'V1' COMMENT 'Skill版本',
    `analysis_engine_version` VARCHAR(16) DEFAULT 'V1' COMMENT '分析引擎版本',
    `renderer_version` VARCHAR(16) DEFAULT 'V1' COMMENT '渲染器版本',
    `title` VARCHAR(255) DEFAULT NULL COMMENT '标题',
    `subtitle` VARCHAR(500) DEFAULT NULL COMMENT '副标题',
    `subject_display_name` VARCHAR(100) DEFAULT NULL COMMENT '报告对象显示名',
    `cover_summary` TEXT COMMENT '封面摘要',
    `pdf_export_status` VARCHAR(16) DEFAULT 'NOT_EXPORTED' COMMENT 'PDF导出状态',
    `pdf_file_id` VARCHAR(64) DEFAULT NULL COMMENT 'PDF文件ID',
    `pdf_generated_at` DATETIME DEFAULT NULL COMMENT 'PDF生成时间',
    `derived_from_report_instance_id` VARCHAR(64) DEFAULT NULL COMMENT '派生来源报告ID',
    `created_at` DATETIME DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    `updated_at` DATETIME DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    PRIMARY KEY (`report_instance_id`),
    INDEX `idx_user_status` (`user_id`, `report_status`),
    INDEX `idx_generated_at` (`generated_at` DESC)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='事业深度报告实例表';

-- ----------------------------------------------------------------------------
-- 11. 事业报告章节表
-- ----------------------------------------------------------------------------
CREATE TABLE IF NOT EXISTS `career_report_section` (
    `section_id` VARCHAR(64) NOT NULL COMMENT '章节ID',
    `report_instance_id` VARCHAR(64) NOT NULL COMMENT '报告实例ID',
    `section_type` VARCHAR(32) NOT NULL COMMENT '章节类型',
    `section_key` VARCHAR(64) NOT NULL COMMENT '章节键',
    `title` VARCHAR(255) NOT NULL COMMENT '标题',
    `order_num` INT DEFAULT 0 COMMENT '排序号',
    `summary` TEXT COMMENT '摘要',
    `body` LONGTEXT COMMENT '正文',
    `is_visible` TINYINT(1) DEFAULT 1 COMMENT '是否可见',
    `created_at` DATETIME DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    `updated_at` DATETIME DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    PRIMARY KEY (`section_id`),
    INDEX `idx_report_order` (`report_instance_id`, `order_num`),
    CONSTRAINT `fk_crs_instance` FOREIGN KEY (`report_instance_id`) REFERENCES `career_report_instance`(`report_instance_id`) ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='事业报告章节表';

-- ----------------------------------------------------------------------------
-- 12. 事业报告Claim表
-- ----------------------------------------------------------------------------
CREATE TABLE IF NOT EXISTS `career_report_claim` (
    `claim_id` VARCHAR(64) NOT NULL COMMENT 'ClaimID',
    `report_instance_id` VARCHAR(64) NOT NULL COMMENT '报告实例ID',
    `section_id` VARCHAR(64) DEFAULT NULL COMMENT '章节ID',
    `claim_type` VARCHAR(64) DEFAULT NULL COMMENT 'Claim类型',
    `claim_scope` VARCHAR(32) DEFAULT NULL COMMENT 'Claim范围',
    `statement` TEXT COMMENT '陈述',
    `claim_strength` VARCHAR(16) DEFAULT NULL COMMENT '强度',
    `importance` VARCHAR(16) DEFAULT NULL COMMENT '重要性',
    `cross_system_state` VARCHAR(64) DEFAULT NULL COMMENT '跨系统状态',
    `manifestation_capacity` VARCHAR(16) DEFAULT NULL COMMENT '显化容量',
    `friction_level` VARCHAR(16) DEFAULT NULL COMMENT '摩擦等级',
    `is_core` TINYINT(1) DEFAULT 0 COMMENT '是否核心',
    `is_user_visible` TINYINT(1) DEFAULT 1 COMMENT '是否用户可见',
    `suppression_reason` VARCHAR(64) DEFAULT NULL COMMENT '抑制原因',
    `resolver_notes` TEXT COMMENT '解析备注',
    `created_at` DATETIME DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    `updated_at` DATETIME DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    PRIMARY KEY (`claim_id`),
    INDEX `idx_report` (`report_instance_id`),
    INDEX `idx_section` (`section_id`),
    INDEX `idx_core` (`report_instance_id`, `is_core`),
    CONSTRAINT `fk_crc_instance` FOREIGN KEY (`report_instance_id`) REFERENCES `career_report_instance`(`report_instance_id`) ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='事业报告Claim表';

-- ----------------------------------------------------------------------------
-- 13. 事业报告证据表
-- ----------------------------------------------------------------------------
CREATE TABLE IF NOT EXISTS `career_report_evidence` (
    `evidence_id` VARCHAR(64) NOT NULL COMMENT '证据ID',
    `report_instance_id` VARCHAR(64) NOT NULL COMMENT '报告实例ID',
    `astrological_fact_id` VARCHAR(64) DEFAULT NULL COMMENT '占星事实ID',
    `dedup_group_id` VARCHAR(64) DEFAULT NULL COMMENT '去重组ID',
    `evidence_family` VARCHAR(32) DEFAULT NULL COMMENT '证据族',
    `evidence_priority` VARCHAR(16) DEFAULT NULL COMMENT '证据优先级',
    `evidence_type` VARCHAR(64) DEFAULT NULL COMMENT '证据类型',
    `source_chart` VARCHAR(16) DEFAULT NULL COMMENT '来源星盘：D1/D10等',
    `source_system` VARCHAR(64) DEFAULT NULL COMMENT '来源体系',
    `source_object` VARCHAR(255) DEFAULT NULL COMMENT '来源对象',
    `raw_configuration` TEXT COMMENT '原始配置',
    `interpreted_meaning` TEXT COMMENT '解读含义',
    `support_direction` VARCHAR(16) DEFAULT NULL COMMENT '支持方向',
    `support_strength` VARCHAR(16) DEFAULT NULL COMMENT '支持强度',
    `rule_id` VARCHAR(64) DEFAULT NULL COMMENT '规则ID',
    `source_skill` VARCHAR(64) DEFAULT NULL COMMENT '来源Skill',
    `is_user_visible` TINYINT(1) DEFAULT 1 COMMENT '是否用户可见',
    `user_facing_summary` TEXT COMMENT '用户可见摘要',
    `independent_for_claim` TINYINT(1) DEFAULT 1 COMMENT '是否独立证据',
    `created_at` DATETIME DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    `updated_at` DATETIME DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    PRIMARY KEY (`evidence_id`),
    INDEX `idx_report` (`report_instance_id`),
    INDEX `idx_family` (`report_instance_id`, `evidence_family`),
    CONSTRAINT `fk_cre_instance` FOREIGN KEY (`report_instance_id`) REFERENCES `career_report_instance`(`report_instance_id`) ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='事业报告证据表';

-- ----------------------------------------------------------------------------
-- 14. 事业报告时间窗口表
-- ----------------------------------------------------------------------------
CREATE TABLE IF NOT EXISTS `career_report_timing` (
    `timing_id` VARCHAR(64) NOT NULL COMMENT '时间窗口ID',
    `report_instance_id` VARCHAR(64) NOT NULL COMMENT '报告实例ID',
    `section_id` VARCHAR(64) DEFAULT NULL COMMENT '章节ID',
    `start_date` DATE DEFAULT NULL COMMENT '开始日期',
    `end_date` DATE DEFAULT NULL COMMENT '结束日期',
    `timing_type` VARCHAR(32) DEFAULT 'DASHA_PHASE' COMMENT '类型：DASHA_PHASE/TRANSIT等',
    `mahadasa_lord` VARCHAR(16) DEFAULT NULL COMMENT '大运主星',
    `antardasha_lord` VARCHAR(16) DEFAULT NULL COMMENT '中运主星',
    `stage_type` VARCHAR(32) DEFAULT NULL COMMENT '阶段类型',
    `theme` VARCHAR(255) DEFAULT NULL COMMENT '主题',
    `direction` VARCHAR(16) DEFAULT NULL COMMENT '方向',
    `intensity` VARCHAR(16) DEFAULT NULL COMMENT '强度',
    `activation_strength` VARCHAR(16) DEFAULT NULL COMMENT '激活强度',
    `manifestation_capacity` VARCHAR(16) DEFAULT NULL COMMENT '显化容量',
    `friction_level` VARCHAR(16) DEFAULT NULL COMMENT '摩擦等级',
    `user_facing_title` VARCHAR(255) DEFAULT NULL COMMENT '用户可见标题',
    `user_facing_summary` TEXT COMMENT '用户可见摘要',
    `created_at` DATETIME DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    `updated_at` DATETIME DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    PRIMARY KEY (`timing_id`),
    INDEX `idx_report` (`report_instance_id`),
    INDEX `idx_dates` (`start_date`, `end_date`),
    CONSTRAINT `fk_crt_instance` FOREIGN KEY (`report_instance_id`) REFERENCES `career_report_instance`(`report_instance_id`) ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='事业报告时间窗口表';

-- ----------------------------------------------------------------------------
-- 15. 事业报告建议表
-- ----------------------------------------------------------------------------
CREATE TABLE IF NOT EXISTS `career_report_recommendation` (
    `recommendation_id` VARCHAR(64) NOT NULL COMMENT '建议ID',
    `report_instance_id` VARCHAR(64) NOT NULL COMMENT '报告实例ID',
    `section_id` VARCHAR(64) DEFAULT NULL COMMENT '章节ID',
    `recommendation_type` VARCHAR(16) DEFAULT NULL COMMENT '建议类型',
    `recommendation_mode` VARCHAR(16) DEFAULT NULL COMMENT '建议模式',
    `action_risk_level` VARCHAR(16) DEFAULT NULL COMMENT '行动风险等级',
    `specificity_level` VARCHAR(16) DEFAULT NULL COMMENT '具体程度',
    `statement` TEXT COMMENT '建议陈述',
    `rationale` TEXT COMMENT '理由',
    `priority` VARCHAR(16) DEFAULT NULL COMMENT '优先级',
    `is_user_visible` TINYINT(1) DEFAULT 1 COMMENT '是否用户可见',
    `created_at` DATETIME DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    `updated_at` DATETIME DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    PRIMARY KEY (`recommendation_id`),
    INDEX `idx_report` (`report_instance_id`),
    INDEX `idx_priority` (`report_instance_id`, `priority`),
    CONSTRAINT `fk_crr_instance` FOREIGN KEY (`report_instance_id`) REFERENCES `career_report_instance`(`report_instance_id`) ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='事业报告建议表';

-- ----------------------------------------------------------------------------
-- 16. 事业报告Claim关联表
--     relation_type: SUPPORTING_EVIDENCE / COUNTER_EVIDENCE / RELATED_CLAIM /
--                    RELATED_TIMING / RELATED_YOGA
-- ----------------------------------------------------------------------------
CREATE TABLE IF NOT EXISTS `career_report_claim_relation` (
    `id` BIGINT NOT NULL AUTO_INCREMENT COMMENT 'ID',
    `claim_id` VARCHAR(64) NOT NULL COMMENT 'ClaimID',
    `relation_type` VARCHAR(32) NOT NULL COMMENT '关联类型',
    `related_id` VARCHAR(64) NOT NULL COMMENT '关联对象ID',
    `created_at` DATETIME DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    PRIMARY KEY (`id`),
    INDEX `idx_claim` (`claim_id`),
    INDEX `idx_related` (`related_id`),
    CONSTRAINT `fk_crcr_claim` FOREIGN KEY (`claim_id`) REFERENCES `career_report_claim`(`claim_id`) ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='事业报告Claim关联表';

-- ----------------------------------------------------------------------------
-- 17. 事业报告Condition表
--     condition_type: CONDITION_FLAG / REQUIRED_CONDITION / COUNTER_CONDITION
-- ----------------------------------------------------------------------------
CREATE TABLE IF NOT EXISTS `career_report_condition` (
    `id` BIGINT NOT NULL AUTO_INCREMENT COMMENT 'ID',
    `claim_id` VARCHAR(64) NOT NULL COMMENT 'ClaimID',
    `condition_type` VARCHAR(32) NOT NULL COMMENT '条件类型',
    `condition_value` VARCHAR(255) NOT NULL COMMENT '条件值',
    `created_at` DATETIME DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    PRIMARY KEY (`id`),
    INDEX `idx_claim` (`claim_id`),
    CONSTRAINT `fk_crcond_claim` FOREIGN KEY (`claim_id`) REFERENCES `career_report_claim`(`claim_id`) ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='事业报告Condition表';

-- ============================================================================
-- 四、测试数据
-- ============================================================================

-- 测试用户
INSERT INTO `user` (`user_id`, `phone`, `nickname`, `gender`, `star_balance`, `level`, `daily_free_remaining`)
VALUES ('u_001', '13800138000', '测试用户', 'female', 100, 'free', 2);

-- 测试星盘
INSERT INTO `chart` (`chart_id`, `user_id`, `name`, `gender`, `birth_date`, `birth_time`, `birth_place`, `latitude`, `longitude`, `timezone`, `is_default`)
VALUES ('c_001', 'u_001', '测试用户', 'female', '1995-06-15', '08:30', '北京', 39.904200, 116.407400, 'Asia/Shanghai', 1);

-- 测试报告实例（人生课题）
INSERT INTO `report_instance` (`report_instance_id`, `user_id`, `report_type`, `report_title`, `subject_label`, `report_version`, `source_skill`, `chart_id`, `generated_at`, `status`, `followup_available`)
VALUES ('rp_life_001', 'u_001', 'LIFEPATTERN_GROWTH', '人生课题深度报告', '本人', '1.0.0', 'LIFEPATTERN_GROWTH_REPORT', 'c_001', '2026-08-15 14:00:00', 'COMPLETED', 1);

-- 测试报告实例（事业）
INSERT INTO `report_instance` (`report_instance_id`, `user_id`, `report_type`, `report_title`, `subject_label`, `report_version`, `source_skill`, `chart_id`, `generated_at`, `status`, `followup_available`)
VALUES ('rp_career_001', 'u_001', 'CAREER', '事业深度报告', '本人', '1.0.0', 'CAREER_REPORT', 'c_001', '2026-08-20 10:00:00', 'COMPLETED', 1);

-- 测试报告实例（学业）
INSERT INTO `report_instance` (`report_instance_id`, `user_id`, `report_type`, `report_title`, `subject_label`, `report_version`, `source_skill`, `chart_id`, `generated_at`, `status`, `followup_available`)
VALUES ('rp_edu_001', 'u_001', 'EDUCATION', '学业深度解析报告', '本人', '1.0.0', 'EDUCATION_REPORT', 'c_001', '2026-08-22 09:30:00', 'COMPLETED', 1);

-- 测试报告实例（子女与养育）
INSERT INTO `report_instance` (`report_instance_id`, `user_id`, `report_type`, `report_title`, `subject_label`, `report_version`, `source_skill`, `chart_id`, `generated_at`, `status`, `followup_available`)
VALUES ('rp_child_001', 'u_001', 'CHILDREN', '子女与养育深度报告', '本人', '1.0.0', 'NATAL_CHILDREN_REPORT', 'c_001', '2026-08-25 16:00:00', 'COMPLETED', 1);

-- 测试报告实例（健康）
INSERT INTO `report_instance` (`report_instance_id`, `user_id`, `report_type`, `report_title`, `subject_label`, `report_version`, `source_skill`, `chart_id`, `generated_at`, `status`, `followup_available`)
VALUES ('rp_health_001', 'u_001', 'HEALTH', '健康与活力深度报告', '本人', '1.0.0', 'HEALTH_REPORT', 'c_001', '2026-09-01 11:00:00', 'COMPLETED', 1);

-- 测试赠送追问权益
INSERT INTO `report_gift_entitlement` (`report_instance_id`, `user_id`, `benefit_type`, `used`)
VALUES ('rp_life_001', 'u_001', 'FOLLOWUP_GIFT', 0),
       ('rp_career_001', 'u_001', 'FOLLOWUP_GIFT', 0),
       ('rp_edu_001', 'u_001', 'FOLLOWUP_GIFT', 0),
       ('rp_child_001', 'u_001', 'FOLLOWUP_GIFT', 0),
       ('rp_health_001', 'u_001', 'FOLLOWUP_GIFT', 0);

-- 测试星灵AI会话
INSERT INTO `ai_conversation` (`id`, `user_id`, `mode`, `first_question`, `response_style`, `source_context`, `status`, `message_count`, `last_active_at`)
VALUES ('conv_001', 'u_001', 'NATAL', '我的上升星座对性格的影响是什么？', 'GENTLE', 'STAR_AI', 'ACTIVE', 2, '2026-09-10 20:00:00');

INSERT INTO `ai_conversation_message` (`id`, `conversation_id`, `role`, `content`, `message_type`, `sort_order`)
VALUES ('msg_001', 'conv_001', 'user', '我的上升星座对性格的影响是什么？', 'TEXT', 1),
       ('msg_002', 'conv_001', 'assistant', '从你的星盘来看，上升星座决定了你面对世界的第一反应模式……', 'TEXT', 2);

-- ============================================================================
-- 结束
-- ============================================================================
