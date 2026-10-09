-- ============================================================
-- 心理测评中心 V1 表结构
--
-- 设计原则：
-- 1. assessment / question / result / answer 四表分离，各自独立扩展；
-- 2. 量表不可删除，只能归档（ARCHIVED）；
-- 3. 用户结果只读，不允许多次重复完成同一量表（V1 约束，V2 放开）；
-- 4. 选项分数为 int，支持负分（反向题场景）。
-- ============================================================

-- ------------------------------------------------------------
-- 1. 测评目录
-- ------------------------------------------------------------
CREATE TABLE IF NOT EXISTS `psychological_assessment` (
    `assessment_id`   VARCHAR(64)  NOT NULL COMMENT '测评ID（UUID）',
    `slug`            VARCHAR(64)  NOT NULL COMMENT '唯一英文标识，如 gads-7',
    `title`           VARCHAR(256) NOT NULL COMMENT '标题',
    `subtitle`        VARCHAR(512) DEFAULT NULL COMMENT '副标题/一句话描述',
    `category`        VARCHAR(64)  NOT NULL DEFAULT 'MOOD' COMMENT '分类：MOOD/STRESS/RELATIONSHIP/PERSONALITY/OTHER',
    `icon`            VARCHAR(32)  DEFAULT NULL COMMENT 'emoji 图标',
    `color`           VARCHAR(16)  DEFAULT '#b3763f' COMMENT '主色，用于前端展示',
    `question_count`  INT          NOT NULL DEFAULT 0 COMMENT '总题数',
    `estimated_minutes` INT       NOT NULL DEFAULT 5 COMMENT '预计用时（分钟）',
    `min_score`       INT          NOT NULL DEFAULT 0 COMMENT '最低总分',
    `max_score`       INT          NOT NULL DEFAULT 100 COMMENT '最高总分',
    `description`     TEXT         DEFAULT NULL COMMENT '详细说明（用于「关于本测评」抽屉）',
    `disclaimer`      VARCHAR(512) DEFAULT '本测评结果仅供参考，不构成医学诊断。如有疑问请咨询专业心理工作者。' COMMENT '免责声明',
    `publish_status`  VARCHAR(16)  NOT NULL DEFAULT 'DRAFT'
                      COMMENT 'DRAFT / PUBLISHED / ARCHIVED',
    `sort_order`      INT          NOT NULL DEFAULT 0 COMMENT '前端展示排序',
    `created_at`      DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP,
    `updated_at`      DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,

    PRIMARY KEY (`assessment_id`),
    UNIQUE KEY `uk_slug` (`slug`),
    KEY `idx_category_status` (`category`, `publish_status`),
    KEY `idx_status_sort` (`publish_status`, `sort_order`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='心理测评目录';

-- ------------------------------------------------------------
-- 2. 题目
-- ------------------------------------------------------------
CREATE TABLE IF NOT EXISTS `psychological_question` (
    `question_id`   VARCHAR(64)  NOT NULL COMMENT '题目ID',
    `assessment_id` VARCHAR(64)  NOT NULL COMMENT '所属测评',
    `text`          VARCHAR(512) NOT NULL COMMENT '题干',
    `order_index`   INT          NOT NULL DEFAULT 0 COMMENT '顺序（同 assessment 内递增）',
    `reverse_score` TINYINT(1)   NOT NULL DEFAULT 0 COMMENT '是否反向计分（1=是）',
    `created_at`    DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP,

    PRIMARY KEY (`question_id`),
    KEY `idx_assessment_order` (`assessment_id`, `order_index`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='心理测评题目';

CREATE TABLE IF NOT EXISTS `psychological_option` (
    `option_id`     VARCHAR(64)  NOT NULL COMMENT '选项ID',
    `question_id`   VARCHAR(64)  NOT NULL COMMENT '所属题目',
    `text`          VARCHAR(256) NOT NULL COMMENT '选项文案',
    `score`         INT          NOT NULL COMMENT '该选项对应的原始分值',
    `order_index`   INT          NOT NULL DEFAULT 0 COMMENT '选项顺序',
    `created_at`    DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP,

    PRIMARY KEY (`option_id`),
    KEY `idx_question_order` (`question_id`, `order_index`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='心理测评选项';

-- ------------------------------------------------------------
-- 3. 用户结果（一次作答产生一条）
-- ------------------------------------------------------------
CREATE TABLE IF NOT EXISTS `psychological_user_result` (
    `result_id`       VARCHAR(64)  NOT NULL COMMENT '结果ID',
    `user_id`         VARCHAR(32)  NOT NULL COMMENT '用户ID',
    `assessment_id`   VARCHAR(64)  NOT NULL COMMENT '测评ID',
    `total_score`     INT          NOT NULL DEFAULT 0 COMMENT '原始总分',
    `level`           VARCHAR(32)  NOT NULL COMMENT '结果等级：NONE/LOW/MEDIUM/HIGH/SEVERE',
    `level_label`     VARCHAR(64)  NOT NULL COMMENT '等级文案快照（运营可随时改，历史结果不变）',
    `level_color`     VARCHAR(16)  NOT NULL DEFAULT '#8a7a6a' COMMENT '等级色快照',
    `advice_text`     TEXT         DEFAULT NULL COMMENT '解读与建议（运营配置，后端计算填入）',
    `finish_reason`   VARCHAR(64)  DEFAULT 'COMPLETED' COMMENT 'COMPLETED/DROPPED',
    `started_at`      DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '开始答题时间',
    `finished_at`     DATETIME     DEFAULT NULL COMMENT '完成时间',
    `created_at`      DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP,
    `updated_at`      DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,

    PRIMARY KEY (`result_id`),
    UNIQUE KEY `uk_user_assessment` (`user_id`, `assessment_id`),
    KEY `idx_user_id` (`user_id`),
    KEY `idx_assessment_id` (`assessment_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='心理测评用户结果';

CREATE TABLE IF NOT EXISTS `psychological_user_answer` (
    `answer_id`     VARCHAR(64)  NOT NULL COMMENT '答题ID',
    `result_id`     VARCHAR(64)  NOT NULL COMMENT '所属结果',
    `question_id`   VARCHAR(64)  NOT NULL COMMENT '题目ID',
    `option_id`     VARCHAR(64)  NOT NULL COMMENT '用户所选选项',
    `score`         INT          NOT NULL COMMENT '实际得分（含反向处理后的值）',
    `created_at`    DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP,

    PRIMARY KEY (`answer_id`),
    KEY `idx_result_id` (`result_id`),
    KEY `idx_question_id` (`question_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='心理测评用户答题';
