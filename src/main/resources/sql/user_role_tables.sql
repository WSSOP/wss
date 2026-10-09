-- ============================================================
-- 问星渡｜用户角色（RBAC）
--
-- 背景：
--   原始 skill 目录提到 Admin / Customer Service / Teacher /
--   Assistant Teacher 四个角色，但既有的 user 表<b>没有 role 列</b>，
--   无处存储。本文件补上最小可用的角色表。
--
-- 【设计决策】用独立关联表而不是给 user 加 role 列：
--   1. user 表被报告、图表、对话等多个模块共用，加列影响面大；
--   2. 一人可能同时是讲师和客服，独立表天然支持多角色；
--   3. 存量数据零迁移——没有 role 记录的用户一律视为 CUSTOMER。
--
-- 角色语义（与 07_RBAC 提纲一致）：
--   USER            普通用户：浏览、报名、查看自己的订单
--   TEACHER         讲师：管理自己的课程与班级、出勤核销
--   ASSISTANT_TEACHER  助教：同讲师，但不含课程编辑
--   CUSTOMER_SERVICE  客服：查看订单、协助处理，不可退款
--   ADMIN           管理员：全部权限，含退款与交付
--
-- 【安全约定】资金类操作（退款）仅 ADMIN。
--   客服可查不可退——退款会真实改变营收，必须留最窄的口子。
-- ============================================================

CREATE TABLE IF NOT EXISTS `user_role` (
    `user_id` VARCHAR(32) NOT NULL COMMENT '对应 user.user_id',
    `role_code` VARCHAR(24) NOT NULL
        COMMENT 'USER/TEACHER/ASSISTANT_TEACHER/CUSTOMER_SERVICE/ADMIN',
    `granted_by` VARCHAR(32) DEFAULT NULL COMMENT '授权人 user_id',
    `created_at` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,

    PRIMARY KEY (`user_id`, `role_code`),
    KEY `idx_role` (`role_code`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='用户角色关联表';


-- ------------------------------------------------------------
-- 讲师归属：课程/活动的负责人与助教
--
-- 放在独立表而不是直接读 learning_course.teacher_id：
-- 归属关系会频繁调整（换讲师、代课），改表比改主数据安全，
-- 也便于保留变更痕迹。
-- ------------------------------------------------------------

CREATE TABLE IF NOT EXISTS `learning_assignment` (
    `assignment_id` BIGINT NOT NULL AUTO_INCREMENT,
    `target_type` VARCHAR(16) NOT NULL COMMENT 'COURSE / EVENT',
    `target_id` VARCHAR(64) NOT NULL,
    `user_id` VARCHAR(32) NOT NULL COMMENT '讲师或助教',
    `assignment_role` VARCHAR(16) NOT NULL DEFAULT 'TEACHER'
        COMMENT 'OWNER 主讲 / ASSISTANT 助教',
    `created_at` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,

    PRIMARY KEY (`assignment_id`),
    UNIQUE KEY `uk_target_user` (`target_type`, `target_id`, `user_id`),
    KEY `idx_user` (`user_id`),
    KEY `idx_target` (`target_type`, `target_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='课程与活动的讲师归属表';


-- ------------------------------------------------------------
-- 测试角色数据
-- 联调身份对应（BearerUserIdResolver: token_X -> user_X）
-- ------------------------------------------------------------

-- 清掉本文件写入的全部联调角色。
-- 不能只按 user_demo / user_teacher 两个前缀删：user_cs_demo 与
-- user_admin_demo 同样由本文件写入，只删前两个前缀会让脚本
-- 第二次执行就撞主键——这与文件头声明的「可反复执行」矛盾。
DELETE FROM `user_role` WHERE `user_id` IN (
    'user_demo_001', 'user_demo_002', 'user_demo_003',
    'user_teacher_lin', 'user_teacher_summer',
    'user_cs_demo', 'user_admin_demo'
);
DELETE FROM `learning_assignment` WHERE `target_id` LIKE 'DEMO%';

INSERT INTO `user_role` (`user_id`, `role_code`) VALUES
('user_demo_001', 'USER'),
('user_demo_002', 'USER'),
('user_demo_003', 'USER'),
('user_teacher_lin', 'TEACHER'),
('user_teacher_summer', 'TEACHER'),
('user_cs_demo', 'CUSTOMER_SERVICE'),
('user_admin_demo', 'ADMIN');

-- 讲师归属：与 seed 中 course.teacher_id / event.host_id 对应
INSERT INTO `learning_assignment` (`target_type`, `target_id`, `user_id`, `assignment_role`) VALUES
('COURSE', 'DEMO_C_ASTRO',     'user_teacher_lin',    'OWNER'),
('COURSE', 'DEMO_C_VEDIC',     'user_teacher_lin',    'OWNER'),
('COURSE', 'DEMO_C_INTERPRET', 'user_teacher_summer', 'OWNER'),
('EVENT',  'DEMO_E_TALK',      'user_teacher_summer', 'OWNER'),
('EVENT',  'DEMO_E_MEDIT',     'user_teacher_summer', 'OWNER'),
('EVENT',  'DEMO_E_WORK',      'user_teacher_lin',    'OWNER'),
('EVENT',  'DEMO_E_PAST',      'user_teacher_lin',    'OWNER');

SELECT '角色数' AS x, COUNT(*) AS n FROM `user_role`
UNION ALL SELECT '归属数', COUNT(*) FROM `learning_assignment`;