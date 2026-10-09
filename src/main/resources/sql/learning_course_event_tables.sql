-- ============================================================
-- 问星渡｜学习课程 与 主题活动
--
-- 规格来源说明（重要）：
--   原始 skill 目录（首页｜【学习课程】与【主题活动】）只有 13 个
--   205~469 字节的提纲文件，没有字段定义、状态机、schema 与 API 契约。
--   本文件的每一个设计决策均为实现方所拟，不是原始规格，
--   因此下面逐条标注【设计假设】，需求确认后需要逐项复核。
--
-- 已从原始文档中确证的约束（少数几条，全部遵守）：
--   1. 业务 Domain 隔离：Course / Event 各自独立，不互相依赖；
--   2. Commerce Core 统一处理交易；
--   3. Payment 不直接创建业务权限 —— 支付成功后由 Fulfillment 交付；
--   4. 角色划分 Admin / Customer Service / Teacher / Assistant Teacher。
--
-- 【设计假设 A1】课程与活动都是「场次售卖」模型
--   course_session / learning_event_session 承载时间、名额、票价。
-- 【设计假设 A2】免费与付费并存
--   price = 0 走免费报名；price > 0 需要订单支付后才发放权限。
--   首页文案「入门进阶 · 系统成长」偏免费入门，文档又要求 Commerce Core，
--   两者都保留：免费路径可完整走通，付费路径表结构就绪。
-- 【设计假设 A3】权限（Entitlement）与参与记录（Enrollment）分离
--   支付成功 -> order PAID -> Fulfillment -> entitlement ACTIVE -> 才能报名/学习。
--   免费课由系统直接发放 entitlement，不经过支付。
--
-- 建表顺序有硬约束，不可调换：
--   learning_entitlement 用 order_id 外键指向 learning_order，
--   而 MySQL 不允许外键指向尚未创建的表，因此 order 必须排在 entitlement 之前。
-- ============================================================

-- ------------------------------------------------------------
-- 1. 课程
-- ------------------------------------------------------------

CREATE TABLE IF NOT EXISTS `learning_course` (
    `course_id` VARCHAR(64) NOT NULL COMMENT '课程ID，运营生成，禁止写死',
    `title` VARCHAR(128) NOT NULL COMMENT '课程标题',
    `subtitle` VARCHAR(255) DEFAULT NULL COMMENT '副标题',
    `description` TEXT COMMENT '课程简介',
    `cover_url` VARCHAR(512) DEFAULT NULL COMMENT '封面图URL',

    `category` VARCHAR(32) NOT NULL DEFAULT 'ASTROLOGY'
        COMMENT 'ASTROLOGY 占星基础 / VEDIC 吠陀体系 / INTERPRETATION 解读方法 / MEDITATION 冥想修习',
    `level` VARCHAR(16) NOT NULL DEFAULT 'BEGINNER' COMMENT 'BEGINNER/INTERMEDIATE/ADVANCED',
    `course_status` VARCHAR(16) NOT NULL DEFAULT 'DRAFT'
        COMMENT 'DRAFT/PUBLISHED/UNPUBLISHED/ARCHIVED；ARCHIVED 终态，保留历史订单与学习记录',

    `teacher_id` VARCHAR(64) DEFAULT NULL COMMENT '主讲教师ID，对应 role=TEACHER',
    `assistant_teacher_id` VARCHAR(64) DEFAULT NULL COMMENT '助教ID，对应 role=ASSISTANT_TEACHER',
    `session_count` INT NOT NULL DEFAULT 0 COMMENT '场次数',
    `total_minutes` INT NOT NULL DEFAULT 0 COMMENT '总时长（分钟）',
    `lesson_count` INT NOT NULL DEFAULT 0 COMMENT '课时数',

    `outline` JSON DEFAULT NULL COMMENT '课程大纲 [{title, minutes}]',
    `tags` JSON DEFAULT NULL COMMENT '标签数组',

    `created_at` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    `updated_at` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,

    PRIMARY KEY (`course_id`),
    KEY `idx_status_category` (`course_status`, `category`),
    KEY `idx_teacher` (`teacher_id`),
    KEY `idx_created` (`created_at`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='学习课程表';


-- ------------------------------------------------------------
-- 2. 活动
-- ------------------------------------------------------------

CREATE TABLE IF NOT EXISTS `learning_event` (
    `event_id` VARCHAR(64) NOT NULL COMMENT '活动ID，运营生成，禁止写死',
    `title` VARCHAR(128) NOT NULL COMMENT '活动标题',
    `subtitle` VARCHAR(255) DEFAULT NULL COMMENT '副标题',
    `description` TEXT COMMENT '活动介绍',
    `cover_url` VARCHAR(512) DEFAULT NULL COMMENT '封面图URL',

    `event_type` VARCHAR(32) NOT NULL DEFAULT 'TALK'
        COMMENT 'TALK 讲座 / WORKSHOP 工作坊 / MEDITATION 共修 / RETREAT 静修 / QA 答疑',
    `event_status` VARCHAR(16) NOT NULL DEFAULT 'DRAFT'
        COMMENT 'DRAFT/PUBLISHED/UNPUBLISHED/ARCHIVED；ARCHIVED 终态',

    `host_id` VARCHAR(64) DEFAULT NULL COMMENT '主持人ID',
    `venue_name` VARCHAR(128) DEFAULT NULL COMMENT '线上平台名或线下场地名',
    `venue_address` VARCHAR(255) DEFAULT NULL COMMENT '线下详细地址',
    `is_online` TINYINT(1) NOT NULL DEFAULT 0 COMMENT '是否线上',
    -- live_url 只在已报名接口返回给本人，绝不在列表/详情接口外泄
    `live_url` VARCHAR(512) DEFAULT NULL COMMENT '直播地址；仅对已报名者可见',

    `duration_minutes` INT NOT NULL DEFAULT 0 COMMENT '时长（分钟）',
    `agenda` JSON DEFAULT NULL COMMENT '流程 [{time, title}]',

    `created_at` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    `updated_at` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,

    PRIMARY KEY (`event_id`),
    KEY `idx_status_type` (`event_status`, `event_type`),
    KEY `idx_host` (`host_id`),
    KEY `idx_created` (`created_at`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='主题活动表';


-- ------------------------------------------------------------
-- 3. 场次（课程与活动各自一张表，保持 Domain 隔离）
-- ------------------------------------------------------------

CREATE TABLE IF NOT EXISTS `course_session` (
    `session_id` VARCHAR(64) NOT NULL COMMENT '场次ID',
    `course_id` VARCHAR(64) NOT NULL,
    `session_no` INT NOT NULL DEFAULT 1 COMMENT '第几讲',
    `title` VARCHAR(128) DEFAULT NULL COMMENT '场次标题',
    `start_at` DATETIME NOT NULL,
    `end_at` DATETIME NOT NULL,
    `duration_minutes` INT NOT NULL DEFAULT 0,
    `is_live` TINYINT(1) NOT NULL DEFAULT 1 COMMENT '是否直播',
    `live_url` VARCHAR(512) DEFAULT NULL COMMENT '直播地址；仅对已报名者可见',
    `recording_url` VARCHAR(512) DEFAULT NULL COMMENT '回放地址；课后可见',
    `session_status` VARCHAR(16) NOT NULL DEFAULT 'SCHEDULED'
        COMMENT 'SCHEDULED/ONGOING/DONE/CANCELLED；CANCELLED 保留已报名记录',

    `capacity` INT NOT NULL DEFAULT 0 COMMENT '名额，0 = 不限',
    `price` DECIMAL(12,2) NOT NULL DEFAULT 0.00 COMMENT '场次价格；0 = 免费',
    `enrolled_count` INT NOT NULL DEFAULT 0 COMMENT '已报名人数，由报名动作维护',

    `created_at` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    `updated_at` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,

    PRIMARY KEY (`session_id`),
    UNIQUE KEY `uk_course_no` (`course_id`, `session_no`),
    KEY `idx_course_start` (`course_id`, `start_at`),
    CONSTRAINT `fk_cs_course` FOREIGN KEY (`course_id`)
        REFERENCES `learning_course` (`course_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='课程场次表';


CREATE TABLE IF NOT EXISTS `learning_event_session` (
    `session_id` VARCHAR(64) NOT NULL COMMENT '场次ID',
    `event_id` VARCHAR(64) NOT NULL,
    `session_no` INT NOT NULL DEFAULT 1,
    `title` VARCHAR(128) DEFAULT NULL,
    `start_at` DATETIME NOT NULL,
    `end_at` DATETIME NOT NULL,
    `duration_minutes` INT NOT NULL DEFAULT 0,
    `is_live` TINYINT(1) NOT NULL DEFAULT 1,
    `live_url` VARCHAR(512) DEFAULT NULL COMMENT '直播地址；仅对已报名者可见',
    `session_status` VARCHAR(16) NOT NULL DEFAULT 'SCHEDULED'
        COMMENT 'SCHEDULED/ONGOING/DONE/CANCELLED',
    `capacity` INT NOT NULL DEFAULT 0 COMMENT '名额，0 = 不限',
    `price` DECIMAL(12,2) NOT NULL DEFAULT 0.00,
    `registered_count` INT NOT NULL DEFAULT 0,

    `created_at` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    `updated_at` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,

    PRIMARY KEY (`session_id`),
    UNIQUE KEY `uk_es_event_no` (`event_id`, `session_no`),
    KEY `idx_event_start` (`event_id`, `start_at`),
    CONSTRAINT `fk_es_event` FOREIGN KEY (`event_id`)
        REFERENCES `learning_event` (`event_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='活动场次表';


-- ------------------------------------------------------------
-- 4. 订单（必须先于 entitlement 建表）
--
-- order_status 状态机（实现方拟定）：
--   CREATED   -> 已创建，未支付
--   PAID      -> 支付成功，等待 Fulfillment 发放权限
--   FULFILLED -> 权限已发放，交易闭环
--   CANCELLED -> 用户取消或超时未支付关闭
--   REFUNDED  -> 已退款
--
-- PAID 与 FULFILLED 分开，是为了让「支付成功」和「权限到账」
-- 成为两个可观测、可重试的独立步骤。
-- 支付回调<b>不得</b>直接写 ACTIVE 权限（原始文档明确要求）。
-- ------------------------------------------------------------

CREATE TABLE IF NOT EXISTS `learning_order` (
    `order_id` VARCHAR(64) NOT NULL,
    `order_no` VARCHAR(64) NOT NULL COMMENT '对外订单号',
    `user_id` VARCHAR(64) NOT NULL,

    `order_type` VARCHAR(16) NOT NULL COMMENT 'COURSE / EVENT',
    `target_id` VARCHAR(64) NOT NULL COMMENT '课程或活动ID',
    `session_id` VARCHAR(64) DEFAULT NULL COMMENT '场次；NULL = 购买整个课程',
    `target_title` VARCHAR(128) DEFAULT NULL COMMENT '下单时标题快照，商品改价不影响历史订单',

    `amount` DECIMAL(12,2) NOT NULL DEFAULT 0.00 COMMENT '应付金额',
    `paid_amount` DECIMAL(12,2) NOT NULL DEFAULT 0.00 COMMENT '实付金额',
    `currency` CHAR(3) NOT NULL DEFAULT 'CNY',

    `order_status` VARCHAR(16) NOT NULL DEFAULT 'CREATED'
        COMMENT 'CREATED/PAID/FULFILLED/CANCELLED/REFUNDED',
    `payment_channel` VARCHAR(24) DEFAULT NULL
        COMMENT 'WECHAT/ALIPAY/MANUAL；MANUAL=后台手动确认，支付未接入前的过渡手段',
    `paid_at` DATETIME DEFAULT NULL,
    `fulfilled_at` DATETIME DEFAULT NULL COMMENT '权限发放完成时间',
    `cancelled_at` DATETIME DEFAULT NULL,
    `refunded_at` DATETIME DEFAULT NULL,

    `source` VARCHAR(24) NOT NULL DEFAULT 'COURSE_LIST'
        COMMENT 'COURSE_LIST/EVENT_LIST/COURSE_DETAIL/EVENT_DETAIL',
    `remark` VARCHAR(255) DEFAULT NULL,

    `created_at` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    `updated_at` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,

    PRIMARY KEY (`order_id`),
    UNIQUE KEY `uk_order_no` (`order_no`),
    KEY `idx_user_status` (`user_id`, `order_status`),
    KEY `idx_status_created` (`order_status`, `created_at`),
    -- Fulfillment 巡检：捞 PAID 未交付的订单补发权限
    KEY `idx_fulfill` (`order_status`, `order_type`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='课程与活动订单表';


-- ------------------------------------------------------------
-- 5. 学习权限（Entitlement）
--
-- 【设计假设 A3】Payment 不直接创建权限。
--   支付成功只把订单推到 PAID，由 Fulfillment 环节发放 entitlement。
--   免费课程/活动由系统直接发放，来源标记为 FREE_GRANT。
--
-- entitlement_status 状态机（实现方拟定）：
--   PENDING -> 已下单未支付（占位，不可用）
--   ACTIVE  -> 可用
--   EXPIRED -> 已过期
--   REVOKED -> 已撤销（退款后）
-- ------------------------------------------------------------

CREATE TABLE IF NOT EXISTS `learning_entitlement` (
    `entitlement_id` VARCHAR(64) NOT NULL,
    `user_id` VARCHAR(64) NOT NULL COMMENT '归属用户；一切以 token 解析结果为准',
    `target_type` VARCHAR(16) NOT NULL COMMENT 'COURSE / EVENT',
    `target_id` VARCHAR(64) NOT NULL COMMENT 'learning_course.course_id 或 learning_event.event_id',
    `session_id` VARCHAR(64) DEFAULT NULL COMMENT '限定场次；NULL = 整个课程/活动',

    `entitlement_status` VARCHAR(16) NOT NULL DEFAULT 'PENDING'
        COMMENT 'PENDING/ACTIVE/EXPIRED/REVOKED',
    `source` VARCHAR(24) NOT NULL DEFAULT 'FREE_GRANT'
        COMMENT 'FREE_GRANT 免费发放 / ORDER_PAID 订单支付交付 / GIFT 赠送 / COMP 赠送补偿',
    `order_id` VARCHAR(64) DEFAULT NULL COMMENT '来源订单；FREE_GRANT 为 NULL',

    `granted_at` DATETIME DEFAULT NULL COMMENT '权限生效时间',
    `expires_at` DATETIME DEFAULT NULL COMMENT '过期时间；NULL = 永久',

    `created_at` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    `updated_at` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,

    PRIMARY KEY (`entitlement_id`),
    UNIQUE KEY `uk_user_target_session`
        (`user_id`, `target_type`, `target_id`, `session_id`),
    KEY `idx_user_status` (`user_id`, `entitlement_status`),
    KEY `idx_target` (`target_type`, `target_id`),
    KEY `idx_order` (`order_id`),
    CONSTRAINT `fk_le_order` FOREIGN KEY (`order_id`)
        REFERENCES `learning_order` (`order_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='学习权限表';


-- ------------------------------------------------------------
-- 6. 报名 / 参与记录
--
-- 【设计假设 A4】报名与权限分离
--   有 entitlement 才能报名；报名成功才占名额。
--   幂等：同一 user + 同一 session 重复报名返回既有记录，名额不重复扣减。
-- ------------------------------------------------------------

CREATE TABLE IF NOT EXISTS `learning_enrollment` (
    `enrollment_id` VARCHAR(64) NOT NULL,
    `user_id` VARCHAR(64) NOT NULL,
    `session_id` VARCHAR(64) NOT NULL COMMENT 'course_session 或 learning_event_session',
    `target_type` VARCHAR(16) NOT NULL COMMENT 'COURSE / EVENT',

    `enrollment_status` VARCHAR(16) NOT NULL DEFAULT 'ENROLLED'
        COMMENT 'ENROLLED 已报名 / ATTENDED 已出席 / ABSENT 缺席 / CANCELLED 已取消',
    `attendance_checked_at` DATETIME DEFAULT NULL COMMENT '出勤核销时间',
    `cancelled_at` DATETIME DEFAULT NULL,

    `created_at` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    `updated_at` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,

    PRIMARY KEY (`enrollment_id`),
    UNIQUE KEY `uk_user_session` (`user_id`, `session_id`),
    KEY `idx_session_status` (`session_id`, `enrollment_status`),
    KEY `idx_user` (`user_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='课程报名与活动参与记录表';
