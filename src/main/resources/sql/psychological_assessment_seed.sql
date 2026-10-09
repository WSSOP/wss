-- ============================================================
-- 心理测评种子数据（PHQ-9 / GAD-7 / SDS简版）
-- 直接执行即可，不依赖 Spring Bean
-- ============================================================

-- ------------------------------------------------------------
-- 1. PHQ-9 患者健康问卷
-- ------------------------------------------------------------
INSERT INTO psychological_assessment (assessment_id, slug, title, subtitle, category, icon, color, question_count, estimated_minutes, min_score, max_score, description, disclaimer, publish_status, sort_order)
VALUES ('psych_phq9', 'phq-9', 'PHQ-9 抑郁症状自评', '过去两周内，你有多大程度被以下问题困扰？', 'MOOD', '🌧️', '#5b8abf', 9, 5, 0, 27,
'PHQ-9（Patient Health Questionnaire-9）是最常用的抑郁症状筛查量表，由 Kroenke 等人在 2001 年基于 DSM-IV 标准编制。总分范围 0–27，分数越高表示抑郁症状越严重。',
'本测评结果仅供参考，不构成医学诊断。如有疑问请咨询专业心理工作者。',
'PUBLISHED', 1);

SET @aid = 'psych_phq9';

INSERT INTO psychological_question (question_id, assessment_id, text, order_index, reverse_score) VALUES
('phq9_q1', @aid, '做事时提不起劲或没有兴趣', 1, 0),
('phq9_q2', @aid, '感到心情低落、沮丧或绝望', 2, 0),
('phq9_q3', @aid, '入睡困难、睡不安稳或睡眠过多', 3, 0),
('phq9_q4', @aid, '感觉疲倦或没有活力', 4, 0),
('phq9_q5', @aid, '食欲不振或吃太多', 5, 0),
('phq9_q6', @aid, '觉得自己很糟糕，或觉得自己很失败，让自己或家人失望', 6, 0),
('phq9_q7', @aid, '对事物专注有困难，例如阅读报纸或看电视时', 7, 0),
('phq9_q8', @aid, '动作或说话速度缓慢到别人可以觉察到？或者正好相反，坐立不安或动来动去', 8, 0),
('phq9_q9', @aid, '有不如死掉或用某种方式伤害自己的念头', 9, 0);

SET @opts = '完全没有,有几天,一半以上的天数,几乎每天';
SET @qids = 'phq9_q1,phq9_q2,phq9_q3,phq9_q4,phq9_q5,phq9_q6,phq9_q7,phq9_q8,phq9_q9';

-- PHQ-9 选项：分数 0/1/2/3 正向
INSERT INTO psychological_option (option_id, question_id, text, score, order_index)
SELECT CONCAT('phq9_q', t.n, '_o', o.i), CONCAT('phq9_q', t.n), SUBSTRING_INDEX(SUBSTRING_INDEX(@opts, ',', o.i+1), ',', -1), o.i, o.i
FROM (SELECT 1 n UNION SELECT 2 UNION SELECT 3 UNION SELECT 4 UNION SELECT 5 UNION SELECT 6 UNION SELECT 7 UNION SELECT 8 UNION SELECT 9) t
JOIN (SELECT 0 i UNION SELECT 1 UNION SELECT 2 UNION SELECT 3) o ON o.i < 4;

-- ------------------------------------------------------------
-- 2. GAD-7 广泛性焦虑量表
-- ------------------------------------------------------------
INSERT INTO psychological_assessment (assessment_id, slug, title, subtitle, category, icon, color, question_count, estimated_minutes, min_score, max_score, description, disclaimer, publish_status, sort_order)
VALUES ('psych_gad7', 'gad-7', 'GAD-7 焦虑症状自评', '过去两周内，你有多大程度被以下问题困扰？', 'MOOD', '😰', '#e07b4c', 7, 3, 0, 21,
'GAD-7（Generalized Anxiety Disorder-7）是最常用的焦虑症状筛查量表，由 Spitzer 等人在 2006 年编制。总分范围 0–21。',
'本测评结果仅供参考，不构成医学诊断。如有疑问请咨询专业心理工作者。',
'PUBLISHED', 2);

SET @gid = 'psych_gad7';

INSERT INTO psychological_question (question_id, assessment_id, text, order_index, reverse_score) VALUES
('gad7_q1', @gid, '感到紧张、焦虑或急切', 1, 0),
('gad7_q2', @gid, '不能够停止或控制担忧', 2, 0),
('gad7_q3', @gid, '对各种各样的事情担忧过多', 3, 0),
('gad7_q4', @gid, '很难放松下来', 4, 0),
('gad7_q5', @gid, '由于不安而无法静坐', 5, 0),
('gad7_q6', @gid, '变得容易烦恼或急躁', 6, 0),
('gad7_q7', @gid, '感到似乎将有可怕的事情发生', 7, 0);

INSERT INTO psychological_option (option_id, question_id, text, score, order_index)
SELECT CONCAT('gad7_q', t.n, '_o', o.i), CONCAT('gad7_q', t.n), SUBSTRING_INDEX(SUBSTRING_INDEX(@opts, ',', o.i+1), ',', -1), o.i, o.i
FROM (SELECT 1 n UNION SELECT 2 UNION SELECT 3 UNION SELECT 4 UNION SELECT 5 UNION SELECT 6 UNION SELECT 7) t
JOIN (SELECT 0 i UNION SELECT 1 UNION SELECT 2 UNION SELECT 3) o ON o.i < 4;

-- ------------------------------------------------------------
-- 3. SDS 抑郁自评量表（简化版 10题）
-- ------------------------------------------------------------
INSERT INTO psychological_assessment (assessment_id, slug, title, subtitle, category, icon, color, question_count, estimated_minutes, min_score, max_score, description, disclaimer, publish_status, sort_order)
VALUES ('psych_sds', 'sds', 'SDS 抑郁自评量表（简版）', '请根据最近两周的感受，选择最符合的选项。', 'MOOD', '🌧️', '#6b8e9b', 10, 5, 10, 40,
'SDS（Zung Self-Rating Depression Scale）由 William W.K. Zung 于 1965 年编制，是最经典的抑郁自评量表之一。本版本为简化10题版，适合快速筛查。',
'本测评结果仅供参考，不构成医学诊断。如有疑问请咨询专业心理工作者。',
'PUBLISHED', 3);

SET @sid = 'psych_sds';

INSERT INTO psychological_question (question_id, assessment_id, text, order_index, reverse_score) VALUES
('sds_q1', @sid, '我觉得情绪沮丧，郁闷', 1, 0),
('sds_q2', @sid, '我感到早晨心情最好', 2, 1),
('sds_q3', @sid, '我要哭或想哭', 3, 0),
('sds_q4', @sid, '我夜间睡眠不好', 4, 0),
('sds_q5', @sid, '我吃饭像平时一样多', 5, 1),
('sds_q6', @sid, '我的性功能正常', 6, 1),
('sds_q7', @sid, '我感到体重减轻', 7, 0),
('sds_q8', @sid, '我为便秘烦恼', 8, 1),
('sds_q9', @sid, '我的心跳比平时快', 9, 1),
('sds_q10', @sid, '我无故感到疲劳', 10, 0);

-- SDS 选项（反向题顺序翻转：大多/ frequently/ 有时/ 几乎没有 → 分数 3/2/1/0）
SET @sopts = '完全或大部分时间,一小部分时间,有时,几乎没有或没有';
SET @srvs = '0,3,0,0,3,3,0,3,3,0';

DELIMITER $$
CREATE TEMPORARY TABLE sds_opt_insert AS
SELECT 
    CONCAT('sds_q', t.n) AS qid,
    t.n AS qn,
    SUBSTRING_INDEX(SUBSTRING_INDEX(@sopts, ',', o.i+1), ',', -1) AS otext,
    CASE 
        WHEN SUBSTRING_INDEX(SUBSTRING_INDEX(@srvs, ',', t.n), ',', -1) = '1' THEN 3 - o.i
        ELSE o.i
    END AS oscore,
    o.i AS oidx
FROM (SELECT 1 n UNION SELECT 2 UNION SELECT 3 UNION SELECT 4 UNION SELECT 5 UNION SELECT 6 UNION SELECT 7 UNION SELECT 8 UNION SELECT 9 UNION SELECT 10) t
JOIN (SELECT 0 i UNION SELECT 1 UNION SELECT 2 UNION SELECT 3) o ON o.i < 4$$
DELIMITER ;

INSERT INTO psychological_option (option_id, question_id, text, score, order_index)
SELECT CONCAT(qid, '_o', oidx), qid, otext, oscore, oidx FROM sds_opt_insert;

DROP TEMPORARY TABLE sds_opt_insert;
