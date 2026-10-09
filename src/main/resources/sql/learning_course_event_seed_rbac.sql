-- ============================================================
-- 问星渡｜课程与活动 —— RBAC 与讲师侧测试数据（增量补丁）
--
-- 依赖：先执行 learning_course_event_seed.sql
--
-- 用法：
--   mysql -u root -p wenxingdu < learning_course_event_seed_rbac.sql
--
-- 【重要】身份映射口径
--   BearerUserIdResolver 把 token_X 解析为 user_X；
--   LearningRoleResolver 目前按 userId 前缀推断角色（过渡方案）。
--   因此下表的 user_id 必须与「token 名 + user_」一致，否则权限判定会落空。
--
--   角色            userId                    对应 token
--   ---------------- ------------------------- ------------------
--   Admin           user_admin_root           token_admin_root
--   客服            user_cs_xiaoli            token_cs_xiaoli
--   讲师            user_teacher_lin          token_teacher_lin
--   助教            user_assist_zhou          token_assist_zhou
--   主持人          user_host_chen            token_host_chen
--   普通用户        demo_001             token_demo_001
--
-- 幂等：先 DELETE 再 INSERT，可反复执行。
-- ============================================================

SET NAMES utf8mb4;

-- ------------------------------------------------------------
-- 清理
--
-- 三张表都按 ID 前缀清理。基础 seed 已经占用 (user_demo_001, DEMO_CS_A0)，
-- 本文件不再重复插入该组合，否则撞 uk_user_session 唯一索引。
-- ------------------------------------------------------------
DELETE FROM `learning_enrollment` WHERE `enrollment_id` LIKE 'DEMO_RB%';
DELETE FROM `learning_entitlement` WHERE `entitlement_id` LIKE 'DEMO_RB%';
DELETE FROM `learning_order` WHERE `order_id` LIKE 'DEMO_RB%';

-- ------------------------------------------------------------
-- 1. 为讲师与助教分配课程
--
-- 覆盖 16_Backend_Menu_Mapping 的：
--   Teacher           被分配课程和班期
--   Assistant Teacher 辅助学习运营
--
-- 主讲 lin：DEMO_C_ASTRO（免费多场次，适合演示班级管理）
-- 助教 zhou 辅助同一门课：验证助教也能看名单、核出勤
-- 主讲 lin 另有 DEMO_C_VEDIC（付费），用于验证「权限不放行则报名被拒」
-- ------------------------------------------------------------
UPDATE `learning_course` SET `teacher_id` = 'user_teacher_lin'
WHERE `course_id` = 'DEMO_C_ASTRO';

UPDATE `learning_course` SET `assistant_teacher_id` = 'user_assist_zhou'
WHERE `course_id` = 'DEMO_C_ASTRO';

UPDATE `learning_course` SET `teacher_id` = 'user_teacher_lin'
WHERE `course_id` = 'DEMO_C_VEDIC';

-- 主持人：host_id 供活动归属校验用
UPDATE `learning_event` SET `host_id` = 'user_host_chen'
WHERE `event_id` IN ('DEMO_E_WORK', 'DEMO_E_PAST');

UPDATE `learning_event` SET `host_id` = 'user_teacher_lin'
WHERE `event_id` IN ('DEMO_E_TALK', 'DEMO_E_MEDIT');

-- ------------------------------------------------------------
-- 2. 班级管理：多场次多学员
--
-- demo_001..004 分别代表四个真实用户，分布在不同场次，
-- 便于验证：
--   - 名单按场次过滤正确（不会串场）
--   - 出勤核销只影响指定用户
--   - 取消报名后名额回落
-- ------------------------------------------------------------
INSERT INTO `learning_enrollment`
(`enrollment_id`, `user_id`, `session_id`, `target_type`,
 `enrollment_status`, `attendance_checked_at`, `created_at`)
VALUES
-- 讲师端「班级管理」主场景：一门课一个班，四个学员
--
-- 用户编号从 005 起，与基础 seed 的 001~003 隔离：
-- (user_id, session_id) 上有唯一索引，同一用户同一场次只能有一条报名。
('DEMO_RB_EN_01', 'user_demo_005', 'DEMO_CS_A1', 'COURSE', 'ENROLLED',
 NULL, DATE_SUB(NOW(), INTERVAL 2 DAY)),
('DEMO_RB_EN_02', 'user_demo_006', 'DEMO_CS_A1', 'COURSE', 'ENROLLED',
 NULL, DATE_SUB(NOW(), INTERVAL 2 DAY)),
('DEMO_RB_EN_03', 'user_demo_007', 'DEMO_CS_A1', 'COURSE', 'ENROLLED',
 NULL, DATE_SUB(NOW(), INTERVAL 1 DAY)),
('DEMO_RB_EN_04', 'user_demo_008', 'DEMO_CS_A1', 'COURSE', 'ENROLLED',
 NULL, DATE_SUB(NOW(), INTERVAL 1 DAY)),

-- 已结束场次：已核销出勤 / 缺席，用于历史名单
('DEMO_RB_EN_05', 'user_demo_005', 'DEMO_CS_A0', 'COURSE', 'ATTENDED',
 DATE_SUB(NOW(), INTERVAL 7 DAY) + INTERVAL 60 MINUTE, DATE_SUB(NOW(), INTERVAL 12 DAY)),
('DEMO_RB_EN_06', 'user_demo_006', 'DEMO_CS_A0', 'COURSE', 'ATTENDED',
 DATE_SUB(NOW(), INTERVAL 7 DAY) + INTERVAL 60 MINUTE, DATE_SUB(NOW(), INTERVAL 12 DAY)),
('DEMO_RB_EN_07', 'user_demo_007', 'DEMO_CS_A0', 'COURSE', 'ABSENT',
 DATE_SUB(NOW(), INTERVAL 7 DAY) + INTERVAL 65 MINUTE, DATE_SUB(NOW(), INTERVAL 11 DAY)),

-- 名额将满场次：capacity=1 已占 1，验证「名额已满」
('DEMO_RB_EN_08', 'user_demo_005', 'DEMO_CS_A3', 'COURSE', 'ENROLLED',
 NULL, DATE_SUB(NOW(), INTERVAL 3 HOUR)),

-- 活动侧：主持人核验出勤的样本
('DEMO_RB_EN_09', 'user_demo_005', 'DEMO_ES_T1', 'EVENT', 'ENROLLED',
 NULL, DATE_SUB(NOW(), INTERVAL 5 HOUR)),
('DEMO_RB_EN_10', 'user_demo_006', 'DEMO_ES_T1', 'EVENT', 'ENROLLED',
 NULL, DATE_SUB(NOW(), INTERVAL 4 HOUR)),

('DEMO_RB_EN_11', 'user_demo_004', 'DEMO_CS_A2', 'COURSE', 'CANCELLED',
 NULL, DATE_SUB(NOW(), INTERVAL 6 DAY));

-- 人数按「有效报名实数」重算，而不是写死数字：
-- 基础 seed 与本文件都会往同一场次加/删人，写死会在重跑后对不上。
UPDATE `course_session` s SET `enrolled_count` = (
    SELECT COUNT(*) FROM `learning_enrollment` e
    WHERE e.session_id = s.session_id
      AND e.enrollment_status IN ('ENROLLED', 'ATTENDED'));

UPDATE `learning_event_session` s SET `registered_count` = (
    SELECT COUNT(*) FROM `learning_enrollment` e
    WHERE e.session_id = s.session_id
      AND e.enrollment_status IN ('ENROLLED', 'ATTENDED'));

INSERT INTO `learning_entitlement`
(`entitlement_id`, `user_id`, `target_type`, `target_id`, `session_id`,
 `entitlement_status`, `source`, `order_id`, `granted_at`)
VALUES
('DEMO_RB_ET_01', 'user_demo_001', 'COURSE', 'DEMO_C_ASTRO', NULL,
 'ACTIVE', 'FREE_GRANT', NULL, DATE_SUB(NOW(), INTERVAL 2 DAY)),
('DEMO_RB_ET_02', 'user_demo_002', 'COURSE', 'DEMO_C_ASTRO', 'DEMO_CS_A1',
 'ACTIVE', 'FREE_GRANT', NULL, DATE_SUB(NOW(), INTERVAL 2 DAY)),
('DEMO_RB_ET_03', 'user_demo_003', 'COURSE', 'DEMO_C_INTERPRET', 'DEMO_CS_I1',
 'EXPIRED', 'ORDER_PAID', NULL, DATE_SUB(NOW(), INTERVAL 60 DAY));

INSERT INTO `learning_order`
(`order_id`, `order_no`, `user_id`, `order_type`, `target_id`, `session_id`,
 `target_title`, `amount`, `paid_amount`, `currency`, `order_status`,
 `payment_channel`, `paid_at`, `fulfilled_at`, `source`, `created_at`)
VALUES
('DEMO_RB_O_01', 'DEMO20260201000001', 'user_demo_001', 'COURSE', 'DEMO_C_VEDIC', 'DEMO_CS_V1',
 '第一讲 · 九曜总览', 380.00, 380.00, 'CNY', 'FULFILLED', 'WECHAT',
 DATE_SUB(NOW(), INTERVAL 5 DAY), DATE_SUB(NOW(), INTERVAL 5 DAY) + INTERVAL 2 MINUTE,
 'COURSE_DETAIL', DATE_SUB(NOW(), INTERVAL 5 DAY) - INTERVAL 30 MINUTE);

INSERT INTO `learning_order`
(`order_id`, `order_no`, `user_id`, `order_type`, `target_id`, `session_id`,
 `target_title`, `amount`, `paid_amount`, `currency`, `order_status`,
 `payment_channel`, `source`, `created_at`)
VALUES
('DEMO_RB_O_02', 'DEMO20260201000002', 'user_demo_002', 'COURSE', 'DEMO_C_VEDIC', 'DEMO_CS_V2',
 '第二讲 · nakshatra 详解', 380.00, 0.00, 'CNY', 'CREATED', NULL,
 'COURSE_DETAIL', DATE_SUB(NOW(), INTERVAL 90 MINUTE));

INSERT INTO `learning_order`
(`order_id`, `order_no`, `user_id`, `order_type`, `target_id`, `session_id`,
 `target_title`, `amount`, `paid_amount`, `currency`, `order_status`,
 `payment_channel`, `paid_at`, `source`, `created_at`)
VALUES
('DEMO_RB_O_03', 'DEMO20260201000003', 'user_demo_003', 'EVENT', 'DEMO_E_WORK', 'DEMO_ES_W1',
 '工作坊现场', 580.00, 580.00, 'CNY', 'PAID', 'WECHAT',
 DATE_SUB(NOW(), INTERVAL 40 MINUTE), 'EVENT_DETAIL', DATE_SUB(NOW(), INTERVAL 45 MINUTE));

INSERT INTO `learning_order`
(`order_id`, `order_no`, `user_id`, `order_type`, `target_id`, `session_id`,
 `target_title`, `amount`, `paid_amount`, `currency`, `order_status`,
 `payment_channel`, `paid_at`, `fulfilled_at`, `refunded_at`, `source`, `remark`)
VALUES
('DEMO_RB_O_04', 'DEMO20260201000004', 'user_demo_004', 'EVENT', 'DEMO_E_PAST', 'DEMO_ES_P1',
 '夏至长夜', 480.00, 480.00, 'CNY', 'REFUNDED', 'WECHAT',
 DATE_SUB(NOW(), INTERVAL 35 DAY), DATE_SUB(NOW(), INTERVAL 35 DAY) + INTERVAL 1 MINUTE,
 DATE_SUB(NOW(), INTERVAL 20 DAY), 'EVENT_DETAIL', '用户申请退改');

INSERT INTO `learning_entitlement`
(`entitlement_id`, `user_id`, `target_type`, `target_id`, `session_id`,
 `entitlement_status`, `source`, `order_id`, `granted_at`)
VALUES
('DEMO_RB_ET_04', 'user_demo_001', 'COURSE', 'DEMO_C_VEDIC', 'DEMO_CS_V1',
 'ACTIVE', 'ORDER_PAID', 'DEMO_RB_O_01', DATE_SUB(NOW(), INTERVAL 5 DAY)),

('DEMO_RB_ET_05', 'user_demo_004', 'EVENT', 'DEMO_E_PAST', 'DEMO_ES_P1',
 'REVOKED', 'ORDER_PAID', 'DEMO_RB_O_04', DATE_SUB(NOW(), INTERVAL 35 DAY));

SELECT '课程已发布' AS 项目, COUNT(*) AS 数量 FROM `learning_course` WHERE `course_status`='PUBLISHED'
UNION ALL SELECT '课程草稿', COUNT(*) FROM `learning_course` WHERE `course_status`='DRAFT'
UNION ALL SELECT '活动已发布', COUNT(*) FROM `learning_event` WHERE `event_status`='PUBLISHED'
UNION ALL SELECT '讲师名下课程', COUNT(*) FROM `learning_course` WHERE `teacher_id`='user_teacher_lin'
UNION ALL SELECT '助教辅助课程', COUNT(*) FROM `learning_course` WHERE `assistant_teacher_id`='user_assist_zhou'
UNION ALL SELECT '订单(CREATED)', COUNT(*) FROM `learning_order` WHERE `order_status`='CREATED'
UNION ALL SELECT '订单(PAID待交付)', COUNT(*) FROM `learning_order` WHERE `order_status`='PAID'
UNION ALL SELECT '订单(FULFILLED)', COUNT(*) FROM `learning_order` WHERE `order_status`='FULFILLED'
UNION ALL SELECT '订单(REFUNDED)', COUNT(*) FROM `learning_order` WHERE `order_status`='REFUNDED'
UNION ALL SELECT '有效权限', COUNT(*) FROM `learning_entitlement` WHERE `entitlement_status`='ACTIVE'
UNION ALL SELECT '已过期权限', COUNT(*) FROM `learning_entitlement` WHERE `entitlement_status`='EXPIRED'
UNION ALL SELECT '已撤销权限', COUNT(*) FROM `learning_entitlement` WHERE `entitlement_status`='REVOKED'
UNION ALL SELECT '待核销报名', COUNT(*) FROM `learning_enrollment` WHERE `enrollment_status`='ENROLLED'
UNION ALL SELECT '已核销报名', COUNT(*) FROM `learning_enrollment` WHERE `enrollment_status` IN ('ATTENDED','ABSENT')
UNION ALL SELECT '已取消报名', COUNT(*) FROM `learning_enrollment` WHERE `enrollment_status`='CANCELLED';
