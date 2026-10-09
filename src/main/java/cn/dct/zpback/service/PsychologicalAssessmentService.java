package cn.dct.zpback.service;

import cn.dct.zpback.entity.PsychologicalAssessment;
import cn.dct.zpback.entity.PsychologicalQuestion;
import cn.dct.zpback.entity.PsychologicalOption;
import cn.dct.zpback.entity.PsychologicalUserResult;
import cn.dct.zpback.entity.PsychologicalUserAnswer;
import cn.dct.zpback.mapper.PsychologicalAssessmentMapper;
import cn.dct.zpback.mapper.PsychologicalQuestionMapper;
import cn.dct.zpback.mapper.PsychologicalOptionMapper;
import cn.dct.zpback.mapper.PsychologicalUserResultMapper;
import cn.dct.zpback.mapper.PsychologicalUserAnswerMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.*;
import java.util.stream.Collectors;

/**
 * 心理测评中心｜评分与答题服务
 *
 * 核心职责：
 * 1. 提供测评目录（catalog）与单题详情；
 * 2. 处理用户答题（start / submit）；
 * 3. 计算分数与等级；
 * 4. 提供历史结果查询。
 *
 * <b>评分契约（V1 固定）：</b>
 * - 总分 = Σ(各题得分)，不含反向题加权（反向题已在选项 score 中处理）；
 * - 等级按 min/max 区间等分（5 档：NONE/LOW/MEDIUM/HIGH/SEVERE）；
 * - 解读与建议由运营通过 CMS 配置，后端不硬编码文案。
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class PsychologicalAssessmentService {

    private final PsychologicalAssessmentMapper assessmentMapper;
    private final PsychologicalQuestionMapper questionMapper;
    private final PsychologicalOptionMapper optionMapper;
    private final PsychologicalUserResultMapper resultMapper;
    private final PsychologicalUserAnswerMapper answerMapper;

    // ============================================================
    // 目录浏览
    // ============================================================

    /**
     * 获取所有已发布的测评目录。
     *
     * @param category 可选，按分类过滤；null = 全部
     */
    public List<Map<String, Object>> listCatalog(String category) {
        List<PsychologicalAssessment> list = (category == null || category.isBlank())
                ? assessmentMapper.findAllPublished()
                : assessmentMapper.findByCategory(category);
        return list.stream().map(this::toCatalogView).collect(Collectors.toList());
    }

    /**
     * 按 slug 获取单题测评详情（含题目+选项）。
     *
     * 前端在答题页首屏即需要整张试卷，一次请求全量返回，避免逐题请求。
     */
    public Map<String, Object> getDetail(String slug) {
        PsychologicalAssessment a = assessmentMapper.findBySlug(slug);
        if (a == null) {
            throw new RuntimeException("测评不存在");
        }

        List<PsychologicalQuestion> questions = questionMapper.findByAssessmentId(a.getAssessmentId());
        Map<String, List<PsychologicalOption>> optionsByQuestion = new HashMap<>();
        for (PsychologicalQuestion q : questions) {
            optionsByQuestion.put(q.getQuestionId(), optionMapper.findByQuestionId(q.getQuestionId()));
        }

        Map<String, Object> view = toCatalogView(a);
        view.put("questions", toQuestionViews(questions, optionsByQuestion));
        return view;
    }

    // ============================================================
    // 答题流程
    // ============================================================

    /**
     * 开始答题：创建空结果记录，返回 result_id。
     *
     * V1 不强制要求用户登录才答题，但登录后才能保存历史。
     * userId 为 null 时仍创建匿名作答（finish_reason='DROPPED'），
     * 后续登录再关联即可。
     */
    public Map<String, Object> startAssessment(String userId, String slug) {
        PsychologicalAssessment a = assessmentMapper.findBySlug(slug);
        if (a == null) {
            throw new RuntimeException("测评不存在");
        }

        // 同一用户+测评只保留一条结果记录：已存在则直接复用（允许从半途继续）
        PsychologicalUserResult existing = resultMapper.findLatestByAssessment(
                userId != null ? userId : "anonymous", a.getAssessmentId());
        if (existing != null) {
            Map<String, Object> view = toCatalogView(a);
            view.put("result_id", existing.getResultId());
            view.put("answer_count", answerMapper.countByResultId(existing.getResultId()));
            view.put("total_questions", a.getQuestionCount());
            view.put("finish_reason", existing.getFinishReason());
            return view;
        }

        PsychologicalUserResult result = new PsychologicalUserResult();
        result.setResultId(UUID.randomUUID().toString());
        result.setUserId(userId != null ? userId : "anonymous");
        result.setAssessmentId(a.getAssessmentId());
        result.setTotalScore(0);
        result.setLevel("NONE");
        result.setLevelLabel("未作答");
        result.setLevelColor("#8a7a6a");
        result.setFinishReason("IN_PROGRESS");
        result.setStartedAt(LocalDateTime.now());
        resultMapper.insert(result);

        Map<String, Object> view = toCatalogView(a);
        view.put("result_id", result.getResultId());
        view.put("answer_count", 0);
        view.put("total_questions", a.getQuestionCount());
        view.put("finish_reason", result.getFinishReason());
        return view;
    }

    /**
     * 提交答案。
     *
     * V1 一次提交所有答案（前端暂存后一次性发送），减少往返；
     * 后端校验题数与选项合法性，非法直接拒绝。
     *
     * @param resultId  开始答题时返回的 result_id
     * @param answers   [{ question_id, option_id }]
     */
    public Map<String, Object> submitAnswers(String resultId, String userId,
                                             List<Map<String, String>> answers) {
        PsychologicalUserResult existing = resultMapper.findById(resultId);
        if (existing == null) {
            throw new RuntimeException("测评记录不存在");
        }
        if ("COMPLETED".equals(existing.getFinishReason())) {
            throw new RuntimeException("该测评已完成，不可重复提交");
        }

        String actualUserId = userId != null ? userId : existing.getUserId();
        Map<String, PsychologicalQuestion> questionMap = new HashMap<>();
        Map<String, Map<String, Integer>> optionScoreMap = new HashMap<>();
        Map<String, Boolean> reverseMap = new HashMap<>();

        List<PsychologicalQuestion> allQuestions =
                questionMapper.findByAssessmentId(existing.getAssessmentId());
        for (PsychologicalQuestion q : allQuestions) {
            questionMap.put(q.getQuestionId(), q);
            reverseMap.put(q.getQuestionId(), Boolean.TRUE.equals(q.getReverseScore()));
            List<PsychologicalOption> opts = optionMapper.findByQuestionId(q.getQuestionId());
            Map<String, Integer> scoreMap = new HashMap<>();
            for (PsychologicalOption o : opts) {
                scoreMap.put(o.getOptionId(), o.getScore());
            }
            optionScoreMap.put(q.getQuestionId(), scoreMap);
        }

        if (answers == null || answers.isEmpty()) {
            throw new RuntimeException("答案不能为空");
        }

        int totalScore = 0;
        List<Map<String, Object>> scoredAnswers = new ArrayList<>();
        for (Map<String, String> a : answers) {
            String qId = a.get("question_id");
            String oId = a.get("option_id");
            if (qId == null || oId == null) continue;
            PsychologicalQuestion q = questionMap.get(qId);
            if (q == null) continue;
            Map<String, Integer> scores = optionScoreMap.get(qId);
            Integer rawScore = (scores != null) ? scores.get(oId) : null;
            if (rawScore == null) continue;

            // 反向题：用 (max_score + min_score) - rawScore 翻转
            int score = rawScore;
            if (Boolean.TRUE.equals(reverseMap.get(qId))) {
                List<PsychologicalOption> opts = optionMapper.findByQuestionId(qId);
                int maxS = opts.stream().mapToInt(PsychologicalOption::getScore).max().orElse(0);
                int minS = opts.stream().mapToInt(PsychologicalOption::getScore).min().orElse(0);
                score = maxS + minS - rawScore;
            }
            totalScore += score;

            PsychologicalUserAnswer ans = new PsychologicalUserAnswer();
            ans.setAnswerId(UUID.randomUUID().toString());
            ans.setResultId(resultId);
            ans.setQuestionId(qId);
            ans.setOptionId(oId);
            ans.setScore(score);
            answerMapper.insert(ans);

            scoredAnswers.add(Map.of("question_id", qId, "option_id", oId, "score", score));
        }

        // 计算等级（分数范围从测评目录取）
        PsychologicalAssessment assessment = assessmentMapper.findById(existing.getAssessmentId());
        int minScore = assessment != null && assessment.getMinScore() != null ? assessment.getMinScore() : 0;
        int maxScore = assessment != null && assessment.getMaxScore() != null ? assessment.getMaxScore() : 100;

        LevelResult lr = calculateLevelAndAdvice(minScore, maxScore, totalScore);

        PsychologicalUserResult update = new PsychologicalUserResult();
        update.setResultId(resultId);
        update.setTotalScore(totalScore);
        update.setLevel(lr.level);
        update.setLevelLabel(lr.levelLabel);
        update.setLevelColor(lr.levelColor);
        update.setAdviceText(lr.adviceText);
        update.setFinishReason("COMPLETED");
        update.setFinishedAt(LocalDateTime.now());
        resultMapper.updateResult(resultId, totalScore, lr.level, lr.levelLabel, lr.levelColor,
                lr.adviceText, "COMPLETED", LocalDateTime.now());

        // 返回结果
        PsychologicalAssessment a = assessmentMapper.findById(existing.getAssessmentId());
        Map<String, Object> resultView = toCatalogView(a);
        resultView.put("result_id", resultId);
        resultView.put("total_score", totalScore);
        resultView.put("level", lr.level);
        resultView.put("level_label", lr.levelLabel);
        resultView.put("level_color", lr.levelColor);
        resultView.put("advice_text", lr.adviceText);
        resultView.put("answers", scoredAnswers);
        resultView.put("finished_at", LocalDateTime.now().toString());
        return resultView;
    }

    // ============================================================
    // 历史结果
    // ============================================================

    /**
     * 用户的测评历史（最新在前）。
     *
     * 未登录返回空列表；登录但未作答过也返回空（不返回 null）。
     */
    public List<Map<String, Object>> listMyResults(String userId) {
        if (userId == null) return Collections.emptyList();
        List<PsychologicalUserResult> results = resultMapper.findByUser(userId);
        return results.stream().map(this::toResultSummaryView).collect(Collectors.toList());
    }

    /**
     * 单次结果详情（含逐题作答）。
     *
     * 用于「我的测评」页点击查看结果详情。
     */
    public Map<String, Object> getResultDetail(String resultId, String userId) {
        PsychologicalUserResult r = resultMapper.findById(resultId);
        if (r == null) throw new RuntimeException("结果不存在");
        // 非本人结果只返回脱敏摘要（不泄露完整解读）
        if (userId != null && !userId.equals(r.getUserId())) {
            throw new RuntimeException("无权限查看他人结果");
        }
        Map<String, Object> view = toResultSummaryView(r);
        List<PsychologicalUserAnswer> answers = answerMapper.findByResultId(resultId);
        List<PsychologicalQuestion> questions = questionMapper.findByAssessmentId(r.getAssessmentId());
        Map<String, String> questionTextMap = questions.stream()
                .collect(Collectors.toMap(PsychologicalQuestion::getQuestionId, PsychologicalQuestion::getText));
        List<Map<String, Object>> answerDetails = new ArrayList<>();
        for (PsychologicalUserAnswer ans : answers) {
            Map<String, Object> ad = new LinkedHashMap<>();
            ad.put("question_id", ans.getQuestionId());
            ad.put("question_text", questionTextMap.getOrDefault(ans.getQuestionId(), ""));
            ad.put("option_id", ans.getOptionId());
            ad.put("score", ans.getScore());
            // 选项文案需要从选项表取，这里用 optionMapper 懒加载
            PsychologicalOption opt = optionMapper.findById(ans.getOptionId());
            ad.put("option_text", opt != null ? opt.getText() : "");
            answerDetails.add(ad);
        }
        view.put("answers", answerDetails);
        return view;
    }

    // ============================================================
    // 内部工具
    // ============================================================

    /**
     * 计算等级与建议。
     *
     * V1 采用等距 5 档：
     *   [min, min + span)          → LOW
     *   [min + span, min + 2*span) → MEDIUM
     *   [min + 2*span, min + 3*span) → HIGH
     *   [min + 3*span, max]        → SEVERE
     *   score = min - 1 以下 → NONE（未完成）
     *
     * 等级文案与配色由后端硬编码（V1 无 CMS），V2 改为从后台配置读取。
     */
    /**
     * 计算等级与建议，返回不可变的结果对象。
     *
     * V1 采用等距 5 档：
     *   [min, min + span)          → LOW
     *   [min + span, min + 2*span) → MEDIUM
     *   [min + 2*span, min + 3*span) → HIGH
     *   [min + 3*span, max]        → SEVERE
     *   score <= min - 1           → NONE（未完成）
     */
    private LevelResult calculateLevelAndAdvice(int min, int max, int score) {
        String level, levelLabel, levelColor;
        int span = Math.max(1, (max - min));
        if (score <= min - 1) {
            level = "NONE"; levelLabel = "未作答"; levelColor = "#8a7a6a";
        } else if (score < min + span) {
            level = "LOW"; levelLabel = "轻度"; levelColor = "#4e7459";
        } else if (score < min + 2 * span) {
            level = "MEDIUM"; levelLabel = "中度"; levelColor = "#9a6a2f";
        } else if (score < min + 3 * span) {
            level = "HIGH"; levelLabel = "重度"; levelColor = "#d9534f";
        } else {
            level = "SEVERE"; levelLabel = "极重度"; levelColor = "#8b0000";
        }
        String advice;
        switch (level) {
            case "NONE": advice = null; break;
            case "LOW": advice = "您的得分处于偏低区间，情绪状态整体平稳，建议继续保持当前的生活方式与节奏。"; break;
            case "MEDIUM": advice = "您的得分处于中等区间，近期可能感受到一定压力或情绪波动，建议适度关注自我调节，必要时可寻求专业支持。"; break;
            case "HIGH": advice = "您的得分处于较高分区，近期可能承受较大压力或情绪困扰，建议尽快联系心理咨询师或专业人士获得支持。"; break;
            default: advice = "您的得分很高，建议尽快寻求专业心理帮助，我们平台提供咨询师预约服务，您可以前往咨询页面查看。"; break;
        }
        return new LevelResult(level, levelLabel, levelColor, advice);
    }

    /** 等级计算结果的不可变值对象 */
    private static class LevelResult {
        final String level;
        final String levelLabel;
        final String levelColor;
        final String adviceText;
        LevelResult(String level, String levelLabel, String levelColor, String adviceText) {
            this.level = level; this.levelLabel = levelLabel;
            this.levelColor = levelColor; this.adviceText = adviceText;
        }
    }

    private Map<String, Object> toCatalogView(PsychologicalAssessment a) {
        Map<String, Object> v = new LinkedHashMap<>();
        v.put("assessment_id", a.getAssessmentId());
        v.put("slug", a.getSlug());
        v.put("title", a.getTitle());
        v.put("subtitle", a.getSubtitle());
        v.put("category", a.getCategory());
        v.put("icon", a.getIcon());
        v.put("color", a.getColor());
        v.put("question_count", a.getQuestionCount());
        v.put("estimated_minutes", a.getEstimatedMinutes());
        v.put("min_score", a.getMinScore());
        v.put("max_score", a.getMaxScore());
        v.put("description", a.getDescription());
        v.put("disclaimer", a.getDisclaimer());
        return v;
    }

    private List<Map<String, Object>> toQuestionViews(
            List<PsychologicalQuestion> questions,
            Map<String, List<PsychologicalOption>> optionsMap) {
        return questions.stream().map(q -> {
            Map<String, Object> v = new LinkedHashMap<>();
            v.put("question_id", q.getQuestionId());
            v.put("text", q.getText());
            v.put("reverse_score", Boolean.TRUE.equals(q.getReverseScore()));
            List<PsychologicalOption> opts = optionsMap.getOrDefault(q.getQuestionId(), Collections.emptyList());
            v.put("options", opts.stream().map(o -> {
                Map<String, Object> ov = new LinkedHashMap<>();
                ov.put("option_id", o.getOptionId());
                ov.put("text", o.getText());
                ov.put("score", o.getScore());
                return ov;
            }).collect(Collectors.toList()));
            return v;
        }).collect(Collectors.toList());
    }

    private Map<String, Object> toResultSummaryView(PsychologicalUserResult r) {
        Map<String, Object> v = new LinkedHashMap<>();
        v.put("result_id", r.getResultId());
        v.put("assessment_id", r.getAssessmentId());
        v.put("total_score", r.getTotalScore());
        v.put("level", r.getLevel());
        v.put("level_label", r.getLevelLabel());
        v.put("level_color", r.getLevelColor());
        v.put("finished_at", r.getFinishedAt());
        // 附赠测评基本信息（含分数范围）
        PsychologicalAssessment a = assessmentMapper.findById(r.getAssessmentId());
        if (a != null) {
            v.put("title", a.getTitle());
            v.put("icon", a.getIcon());
            v.put("color", a.getColor());
            v.put("disclaimer", a.getDisclaimer());
            v.put("max_score", a.getMaxScore());
        }
        return v;
    }
}
