-- ============================================================
-- 问星渡｜学习课程 与 主题活动 —— 开发/联调测试数据
--
-- 用法：
--   mysql -u root -p wenxingdu < learning_course_event_seed.sql
--
-- 数据分层：
--   1. DRAFT 课程/活动       —— 验证「未发布不展示」
--   2. PUBLISHED 免费课程     —— 可直接报名
--   3. PUBLISHED 付费课程     —— 走「下单 → 支付 → Fulfillment」
--   4. PUBLISHED 活动(线上/线下/已结束)
--   5. 已存在的权限与订单     —— 验证我的学习 / 我的订单
--
-- 幂等：全部先 DELETE 再 INSERT，可反复执行。
-- 不会影响真实业务数据：所有 ID 都带 DEMO_ 前缀。
-- ============================================================

SET NAMES utf8mb4;

-- ------------------------------------------------------------
-- 清理（按外键依赖倒序）
-- ------------------------------------------------------------
DELETE FROM learning_enrollment   WHERE user_id LIKE 'user\_demo\_%';
DELETE FROM learning_entitlement  WHERE user_id LIKE 'user\_demo\_%';
DELETE FROM learning_order        WHERE user_id LIKE 'user\_demo\_%';
DELETE FROM course_session        WHERE course_id LIKE 'DEMO_%';
DELETE FROM learning_event_session WHERE event_id LIKE 'DEMO_%';
DELETE FROM learning_course       WHERE course_id LIKE 'DEMO_%';
DELETE FROM learning_event        WHERE event_id LIKE 'DEMO_%';

-- ------------------------------------------------------------
-- 1. 课程：3 门已发布 + 1 门草稿（验证过滤）
-- ------------------------------------------------------------

INSERT INTO `learning_course`
(`course_id`, `title`, `subtitle`, `description`, `cover_url`,
 `category`, `level`, `course_status`, `teacher_id`,
 `session_count`, `total_minutes`, `lesson_count`, `tags`)
VALUES
('DEMO_C_ASTRO', '占星入门十二讲', '从零建立本命盘阅读能力',
 '十二次课走完占星基础框架：行星、星座、宫位、相位。每讲 90 分钟，含课后练习与答疑。不需要任何占星基础，适合第一次接触的朋友。',
 'https://cdn.example.com/demo/astro.jpg',
 'ASTROLOGY', 'BEGINNER', 'PUBLISHED', 'user_teacher_lin',
 4, 360, 12, '["零基础","系统入门","有回放"]'),

('DEMO_C_VEDIC', '吠陀体系精讲', '九曜与nakshatra的实操路径',
 '从吠陀体系的基础概念入手，落到 nakshatra 与行星关系分析。面向已经会看本命盘、希望提升判断准确度的学习者。',
 'https://cdn.example.com/demo/vedic.jpg',
 'VEDIC', 'ADVANCED', 'PUBLISHED', 'user_teacher_lin',
 3, 270, 9, '["进阶","九曜","实操"]'),

('DEMO_C_INTERPRET', '解读方法论', '把技术语言写成客户听得懂的话',
 '面向已经出单的咨询师：解读结构、边界表述、风险提示与常见沟通误区。',
 'https://cdn.example.com/demo/interpret.jpg',
 'INTERPRETATION', 'INTERMEDIATE', 'PUBLISHED', 'user_teacher_summer',
 2, 150, 6, '["咨询技巧","落地"]'),

-- 草稿：必须不出现在前台列表
('DEMO_C_DRAFT', '冥想与星象结合（筹备中）', '筹备中的新课',
 '这门课还在筹备，发布后才会出现在前台。',
 NULL,
 'MEDITATION', 'BEGINNER', 'DRAFT', 'user_teacher_summer',
 1, 60, 2, NULL);


-- ------------------------------------------------------------
-- 2. 课程场次
--    免费场次 price=0，直接报名
--    付费场次 price>0，必须下单支付
--    已结束场次 status=DONE，验证「不可报名」
-- ------------------------------------------------------------

INSERT INTO `course_session`
(`session_id`, `course_id`, `session_no`, `title`, `start_at`, `end_at`,
 `duration_minutes`, `is_live`, `live_url`, `recording_url`, `session_status`,
 `capacity`, `price`, `enrolled_count`)
VALUES

-- DEMO_C_ASTRO：2 场免费（一场不限名额）
('DEMO_CS_A1', 'DEMO_C_ASTRO', 1, '第一讲 · 行星与符号',
 DATE_ADD(NOW(), INTERVAL 3 DAY),  DATE_ADD(NOW(), INTERVAL 3 DAY) + INTERVAL 90 MINUTE,
 90, 1, 'https://live.example.com/demo/astro/1', NULL, 'SCHEDULED', 30, 0.00, 0),

('DEMO_CS_A2', 'DEMO_C_ASTRO', 2, '第二讲 · 星座与元素',
 DATE_ADD(NOW(), INTERVAL 5 DAY),  DATE_ADD(NOW(), INTERVAL 5 DAY) + INTERVAL 90 MINUTE,
 90, 1, 'https://live.example.com/demo/astro/2', NULL, 'SCHEDULED', 0, 0.00, 0),

-- DEMO_C_VEDIC：付费 380，验证下单流程
('DEMO_CS_V1', 'DEMO_C_VEDIC', 1, '第一讲 · 九曜总览',
 DATE_ADD(NOW(), INTERVAL 7 DAY),  DATE_ADD(NOW(), INTERVAL 7 DAY) + INTERVAL 90 MINUTE,
 90, 1, 'https://live.example.com/demo/vedic/1', NULL, 'SCHEDULED', 20, 380.00, 0),

('DEMO_CS_V2', 'DEMO_C_VEDIC', 2, '第二讲 · nakshatra 详解',
 DATE_ADD(NOW(), INTERVAL 9 DAY),  DATE_ADD(NOW(), INTERVAL 9 DAY) + INTERVAL 90 MINUTE,
 90, 1, 'https://live.example.com/demo/vedic/2', NULL, 'SCHEDULED', 20, 380.00, 0),

-- DEMO_C_INTERPRET：付费 260
('DEMO_CS_I1', 'DEMO_C_INTERPRET', 1, '解读结构与边界',
 DATE_ADD(NOW(), INTERVAL 4 DAY),  DATE_ADD(NOW(), INTERVAL 4 DAY) + INTERVAL 75 MINUTE,
 75, 1, 'https://live.example.com/demo/interpret/1', NULL, 'SCHEDULED', 50, 260.00, 0),

-- 已结束场次：验证「不可报名」与回放可见
('DEMO_CS_A0', 'DEMO_C_ASTRO', 0, '公开导学课（已结束）',
 DATE_SUB(NOW(), INTERVAL 7 DAY),  DATE_SUB(NOW(), INTERVAL 7 DAY) + INTERVAL 60 MINUTE,
 60, 1, 'https://live.example.com/demo/astro/0',
 'https://rec.example.com/demo/astro/0.mp4', 'DONE', 100, 0.00, 8),

-- 名额将满：capacity=2 已占 2，验证「名额已满」
('DEMO_CS_A3', 'DEMO_C_ASTRO', 3, '第三讲 · 宫位结构（仅剩 1 位）',
 DATE_ADD(NOW(), INTERVAL 6 DAY),  DATE_ADD(NOW(), INTERVAL 6 DAY) + INTERVAL 90 MINUTE,
 90, 1, 'https://live.example.com/demo/astro/3', NULL, 'SCHEDULED', 1, 0.00, 1);


-- ------------------------------------------------------------
-- 3. 活动：线上/线下/已结束 各一
-- ------------------------------------------------------------

INSERT INTO `learning_event`
(`event_id`, `title`, `subtitle`, `description`, `cover_url`,
 `event_type`, `event_status`, `host_id`,
 `venue_name`, `venue_address`, `is_online`, `live_url`,
 `duration_minutes`, `agenda`)
VALUES

('DEMO_E_TALK', '新月上新 · 秋季主题分享', '从秋分到水逆：接下来三个月怎么看',
 '面向所有好奇者的开放分享。讲三个月的天象节奏，以及这段时间适合做与不适合做的事。',
 'https://cdn.example.com/demo/talk.jpg',
 'TALK', 'PUBLISHED', 'user_teacher_summer',
 '线上 · 视频号直播', NULL, 1, 'https://live.example.com/demo/talk',
 90, '["00:00 破题与自我介绍","00:10 三个月天象节奏","00:40 常见问题答疑","01:20 自由交流"]'),

('DEMO_E_WORK', '本命盘一对一工作坊', '把手工排盘变成可复用的方法',
 '带上你的出生信息，现场完成一张本命盘，并学会自己复盘。限 12 人。',
 'https://cdn.example.com/demo/workshop.jpg',
 'WORKSHOP', 'PUBLISHED', 'user_teacher_lin',
 '上海 · 静安共创空间 302 室', '上海市静安区延安路 1000 号 302 室', 0, NULL,
 180, '["00:00 排盘原理 30min","00:30 个人实操 60min","01:30 复盘方法 45min","02:15 答疑 15min"]'),

('DEMO_E_MEDIT', '新月共修 · 静心一小时', '一起安静下来',
 '线上语音共修，无需摄像头。全程静音引导，适合任何状态的你。',
 'https://cdn.example.com/demo/meditation.jpg',
 'MEDITATION', 'PUBLISHED', 'user_teacher_summer',
 '线上 · 音频', NULL, 1, 'https://live.example.com/demo/meditation',
 60, '["00:00 落座与呼吸","00:10 引导","00:50 分享感受"]'),

('DEMO_E_PAST', '夏至长夜冥想营（已结束）', '已结束的静修营',
 '这场活动已经结束，用于验证历史记录仍可查看。',
 'https://cdn.example.com/demo/past.jpg',
 'RETREAT', 'PUBLISHED', 'user_teacher_lin',
 '杭州 · 西湖', '浙江省杭州市西湖区', 0, NULL,
 480, '["全天静修"]'),

-- 草稿活动：不出现在前台
('DEMO_E_DRAFT', '冬至workshop（筹备中）', '筹备中',
 '筹备中的活动，发布后才可见。',
 NULL, 'WORKSHOP', 'DRAFT', 'user_teacher_summer',
 NULL, NULL, 1, NULL, 120, NULL);


-- ------------------------------------------------------------
-- 4. 活动场次
-- ------------------------------------------------------------

INSERT INTO `learning_event_session`
(`session_id`, `event_id`, `session_no`, `title`, `start_at`, `end_at`,
 `duration_minutes`, `is_live`, `live_url`, `session_status`,
 `capacity`, `price`, `registered_count`)
VALUES

('DEMO_ES_T1', 'DEMO_E_TALK', 1, '秋季分享主场',
 DATE_ADD(NOW(), INTERVAL 2 DAY),  DATE_ADD(NOW(), INTERVAL 2 DAY) + INTERVAL 90 MINUTE,
 90, 1, 'https://live.example.com/demo/talk/1', 'SCHEDULED', 200, 0.00, 12),

('DEMO_ES_M1', 'DEMO_E_MEDIT', 1, '新月共修',
 DATE_ADD(NOW(), INTERVAL 1 DAY),  DATE_ADD(NOW(), INTERVAL 1 DAY) + INTERVAL 60 MINUTE,
 60, 1, 'https://live.example.com/demo/meditation/1', 'SCHEDULED', 0, 0.00, 0),

('DEMO_ES_M2', 'DEMO_E_MEDIT', 2, '满月共修',
 DATE_ADD(NOW(), INTERVAL 16 DAY), DATE_ADD(NOW(), INTERVAL 16 DAY) + INTERVAL 60 MINUTE,
 60, 1, 'https://live.example.com/demo/meditation/2', 'SCHEDULED', 30, 88.00, 0),

-- 线下工作坊：付费 + 限 12 位
('DEMO_ES_W1', 'DEMO_E_WORK', 1, '工作坊现场',
 DATE_ADD(NOW(), INTERVAL 10 DAY), DATE_ADD(NOW(), INTERVAL 10 DAY) + INTERVAL 180 MINUTE,
 180, 0, NULL, 'SCHEDULED', 12, 580.00, 0),

-- 已结束：验证历史场次
('DEMO_ES_P1', 'DEMO_E_PAST', 1, '夏至长夜',
 DATE_SUB(NOW(), INTERVAL 30 DAY), DATE_SUB(NOW(), INTERVAL 30 DAY) + INTERVAL 480 MINUTE,
 480, 0, NULL, 'DONE', 30, 480.00, 18);


-- ------------------------------------------------------------
-- 5. 订单与权限：覆盖状态机各阶段
--
-- 联调身份对应关系（BearerUserIdResolver: token_X -> user_X）：
--   demo_001 → Bearer token_001   免费报名 + 已有付费权限
--   demo_002 → Bearer token_002   仅待支付订单
--   demo_003 → Bearer token_003   已支付待交付（验证补发）
-- ------------------------------------------------------------

-- demo_002：待支付订单
INSERT INTO `learning_order`
(`order_id`, `order_no`, `user_id`, `order_type`, `target_id`, `session_id`,
 `target_title`, `amount`, `paid_amount`, `currency`, `order_status`,
 `payment_channel`, `source`, `created_at`)
VALUES
('DEMO_O_002', 'DEMO20260101000001', 'user_demo_002', 'COURSE', 'DEMO_C_VEDIC', 'DEMO_CS_V1',
 '第一讲 · 九曜总览', 380.00, 0.00, 'CNY', 'CREATED', NULL, 'COURSE_DETAIL',
 DATE_SUB(NOW(), INTERVAL 2 HOUR));

-- demo_003：已支付但未交付 —— 用于验证 POST /fulfill/retry 补发
INSERT INTO `learning_order`
(`order_id`, `order_no`, `user_id`, `order_type`, `target_id`, `session_id`,
 `target_title`, `amount`, `paid_amount`, `currency`, `order_status`,
 `payment_channel`, `paid_at`, `source`, `created_at`)
VALUES
('DEMO_O_003', 'DEMO20260101000002', 'user_demo_003', 'COURSE', 'DEMO_C_INTERPRET', 'DEMO_CS_I1',
 '解读结构与边界', 260.00, 260.00, 'CNY', 'PAID', 'WECHAT',
 DATE_SUB(NOW(), INTERVAL 5 HOUR), 'COURSE_DETAIL', DATE_SUB(NOW(), INTERVAL 6 HOUR));

-- demo_001：已完成订单
INSERT INTO `learning_order`
(`order_id`, `order_no`, `user_id`, `order_type`, `target_id`, `session_id`,
 `target_title`, `amount`, `paid_amount`, `currency`, `order_status`,
 `payment_channel`, `paid_at`, `fulfilled_at`, `source`, `created_at`)
VALUES
('DEMO_O_001', 'DEMO20260101000003', 'user_demo_001', 'COURSE', 'DEMO_C_VEDIC', 'DEMO_CS_V2',
 '第二讲 · nakshatra 详解', 380.00, 380.00, 'CNY', 'FULFILLED', 'WECHAT',
 DATE_SUB(NOW(), INTERVAL 3 DAY), DATE_SUB(NOW(), INTERVAL 3 DAY) + INTERVAL 1 MINUTE,
 'COURSE_DETAIL', DATE_SUB(NOW(), INTERVAL 3 DAY) - INTERVAL 10 MINUTE);

-- 已退款订单：验证历史记录保留
INSERT INTO `learning_order`
(`order_id`, `order_no`, `user_id`, `order_type`, `target_id`, `session_id`,
 `target_title`, `amount`, `paid_amount`, `currency`, `order_status`,
 `payment_channel`, `paid_at`, `fulfilled_at`, `refunded_at`, `source`, `remark`)
VALUES
('DEMO_O_004', 'DEMO20260101000004', 'user_demo_001', 'EVENT', 'DEMO_E_PAST', 'DEMO_ES_P1',
 '夏至长夜', 480.00, 480.00, 'CNY', 'REFUNDED', 'WECHAT',
 DATE_SUB(NOW(), INTERVAL 32 DAY), DATE_SUB(NOW(), INTERVAL 32 DAY) + INTERVAL 1 MINUTE,
 DATE_SUB(NOW(), INTERVAL 20 DAY), 'EVENT_DETAIL', '用户申请退改，已退');

-- 权限：demo_001 持有 V2 与已退款活动
INSERT INTO `learning_entitlement`
(`entitlement_id`, `user_id`, `target_type`, `target_id`, `session_id`,
 `entitlement_status`, `source`, `order_id`, `granted_at`, `expires_at`)
VALUES
('DEMO_E_001', 'user_demo_001', 'COURSE', 'DEMO_C_VEDIC', 'DEMO_CS_V2',
 'ACTIVE', 'ORDER_PAID', 'DEMO_O_001', DATE_SUB(NOW(), INTERVAL 3 DAY), NULL),

-- 退款后撤销：状态为 REVOKED，但记录保留用于对账
('DEMO_E_002', 'user_demo_001', 'EVENT', 'DEMO_E_PAST', 'DEMO_ES_P1',
 'REVOKED', 'ORDER_PAID', 'DEMO_O_004', DATE_SUB(NOW(), INTERVAL 32 DAY), NULL),

-- 免费课程整门权限
('DEMO_E_003', 'user_demo_001', 'COURSE', 'DEMO_C_ASTRO', NULL,
 'ACTIVE', 'FREE_GRANT', NULL, DATE_SUB(NOW(), INTERVAL 1 DAY), NULL);

-- 报名记录：用于「我的学习」列表与讲师点名
INSERT INTO `learning_enrollment`
(`enrollment_id`, `user_id`, `session_id`, `target_type`,
 `enrollment_status`, `attendance_checked_at`, `created_at`)
VALUES
-- demo_001 已参加已结束场次
('DEMO_EN_001', 'user_demo_001', 'DEMO_CS_A0', 'COURSE', 'ATTENDED',
 DATE_SUB(NOW(), INTERVAL 7 DAY) + INTERVAL 60 MINUTE, DATE_SUB(NOW(), INTERVAL 10 DAY)),

-- demo_001 报名了即将开始的免费课
('DEMO_EN_002', 'user_demo_001', 'DEMO_CS_A1', 'COURSE', 'ENROLLED',
 NULL, DATE_SUB(NOW(), INTERVAL 1 DAY)),

-- demo_002 报名了开放活动
('DEMO_EN_003', 'user_demo_002', 'DEMO_ES_T1', 'EVENT', 'ENROLLED',
 NULL, DATE_SUB(NOW(), INTERVAL 12 HOUR)),

-- demo_002 曾报名后取消：验证重新报名（revive 路径）
('DEMO_EN_004', 'user_demo_002', 'DEMO_ES_M1', 'EVENT', 'CANCELLED',
 NULL, DATE_SUB(NOW(), INTERVAL 3 DAY));

-- 修正人数与报名记录保持一致
UPDATE `course_session` SET `enrolled_count` = 1 WHERE `session_id` = 'DEMO_CS_A0';
UPDATE `course_session` SET `enrolled_count` = 1 WHERE `session_id` = 'DEMO_CS_A1';
UPDATE `learning_event_session` SET `registered_count` = 1 WHERE `session_id` = 'DEMO_ES_T1';

-- ------------------------------------------------------------
-- 校验
-- ------------------------------------------------------------
SELECT '课程(已发布)' AS 项目, COUNT(*) AS 数量 FROM `learning_course` WHERE `course_status`='PUBLISHED'
UNION ALL SELECT '课程(草稿,前台不可见)', COUNT(*) FROM `learning_course` WHERE `course_status`='DRAFT'
UNION ALL SELECT '课程场次', COUNT(*) FROM `course_session`
UNION ALL SELECT '活动(已发布)', COUNT(*) FROM `learning_event` WHERE `event_status`='PUBLISHED'
UNION ALL SELECT '活动场次', COUNT(*) FROM `learning_event_session`
UNION ALL SELECT '订单', COUNT(*) FROM `learning_order`
UNION ALL SELECT '有效权限', COUNT(*) FROM `learning_entitlement` WHERE `entitlement_status`='ACTIVE'
UNION ALL SELECT '已撤销权限', COUNT(*) FROM `learning_entitlement` WHERE `entitlement_status`='REVOKED'
UNION ALL SELECT '有效报名', COUNT(*) FROM `learning_enrollment` WHERE `enrollment_status` IN ('ENROLLED','ATTENDED');