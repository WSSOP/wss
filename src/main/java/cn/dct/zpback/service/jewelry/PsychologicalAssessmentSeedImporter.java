package cn.dct.zpback.service.jewelry;

import cn.dct.zpback.entity.PsychologicalAssessment;
import cn.dct.zpback.entity.PsychologicalOption;
import cn.dct.zpback.entity.PsychologicalQuestion;
import cn.dct.zpback.mapper.PsychologicalAssessmentMapper;
import cn.dct.zpback.mapper.PsychologicalOptionMapper;
import cn.dct.zpback.mapper.PsychologicalQuestionMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.CommandLineRunner;
import org.springframework.stereotype.Component;

import java.util.List;

/**
 * 心理测评中心｜种子数据导入
 *
 * V1 内置 3 个常用量表（PHQ-9 / GAD-7 / SDS），
 * 每个量表按标准题库初始化，仅当表中无数据时才写入。
 *
 * <b>设计说明</b>：
 * - 运营可通过后台增删改量表，本 SeedImporter 只在启动时跑一次；
 * - 已写入的题不会被重复导入（通过 assessment_id 判断）；
 * - 题目与选项均为标准计分（无反向题，V2 可加）。
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class PsychologicalAssessmentSeedImporter implements CommandLineRunner {

    private final PsychologicalAssessmentMapper assessmentMapper;
    private final PsychologicalQuestionMapper questionMapper;
    private final PsychologicalOptionMapper optionMapper;

    @Override
    public void run(String... args) {
        try {
            if (assessmentMapper.countPublished() > 0) {
                log.info("心理测评已有数据，跳过种子导入");
                return;
            }
            seedPHQ9();
            seedGAD7();
            seedSDS();
            log.info("心理测评种子数据导入完成");
        } catch (Exception e) {
            log.warn("心理测评种子导入失败，不影响主流程：{}", e.getMessage());
        }
    }

    // ================================================================
    // PHQ-9（患者健康问卷-9项）
    // ================================================================
    private void seedPHQ9() {
        String id = "phq-9";
        String aid = "psych_phq9";
        PsychologicalAssessment a = new PsychologicalAssessment();
        a.setAssessmentId(aid);
        a.setSlug(id);
        a.setTitle("PHQ-9 抑郁症状自评");
        a.setSubtitle("过去两周内，你有多大程度被以下问题困扰？");
        a.setCategory("MOOD");
        a.setIcon("🌧️");
        a.setColor("#5b8abf");
        a.setQuestionCount(9);
        a.setEstimatedMinutes(5);
        a.setMinScore(0);
        a.setMaxScore(27);
        a.setPublishStatus("PUBLISHED");
        a.setSortOrder(1);
        a.setDescription(
            "PHQ-9（Patient Health Questionnaire-9）是最常用的抑郁症状筛查量表，" +
            "由 Kroenke 等人在 2001 年基于 DSM-IV 标准编制。" +
            "总分范围 0–27，分数越高表示抑郁症状越严重。\n\n" +
            "参考标准：\n" +
            "0–4 分：没有或极轻微\n" +
            "5–9 分：轻度抑郁\n" +
            "10–14 分：中度抑郁\n" +
            "15–19 分：中重度抑郁\n" +
            "20–27 分：重度抑郁"
        );
        assessmentMapper.insert(a);

        String[][] questions = {
            {"phq9_q1", "做事时提不起劲或没有兴趣", "0", "1", "2", "3"},
            {"phq9_q2", "感到心情低落、沮丧或绝望", "0", "1", "2", "3"},
            {"phq9_q3", "入睡困难、睡不安稳或睡眠过多", "0", "1", "2", "3"},
            {"phq9_q4", "感觉疲倦或没有活力", "0", "1", "2", "3"},
            {"phq9_q5", "食欲不振或吃太多", "0", "1", "2", "3"},
            {"phq9_q6", "觉得自己很糟糕，或觉得自己很失败，让自己或家人失望", "0", "1", "2", "3"},
            {"phq9_q7", "对事物专注有困难，例如阅读报纸或看电视时", "0", "1", "2", "3"},
            {"phq9_q8", "动作或说话速度缓慢到别人可以觉察到？或者正好相反，坐立不安或动来动去", "0", "1", "2", "3"},
            {"phq9_q9", "有不如死掉或用某种方式伤害自己的念头", "0", "1", "2", "3"}
        };

        String[] options = {"完全没有", "有几天", "一半以上的天数", "几乎每天"};

        for (String[] q : questions) {
            PsychologicalQuestion question = new PsychologicalQuestion();
            question.setQuestionId(q[0]);
            question.setAssessmentId(aid);
            question.setText(q[1]);
            question.setOrderIndex(Integer.parseInt(q[0].replaceAll("\\D+", "")));
            question.setReverseScore(false);
            questionMapper.insert(question);

            for (int i = 0; i < options.length; i++) {
                PsychologicalOption opt = new PsychologicalOption();
                opt.setOptionId(q[0] + "_o" + i);
                opt.setQuestionId(q[0]);
                opt.setText(options[i]);
                opt.setScore(i);
                opt.setOrderIndex(i);
                optionMapper.insert(opt);
            }
        }
    }

    // ================================================================
    // GAD-7（广泛性焦虑量表-7项）
    // ================================================================
    private void seedGAD7() {
        String id = "gad-7";
        String aid = "psych_gad7";
        PsychologicalAssessment a = new PsychologicalAssessment();
        a.setAssessmentId(aid);
        a.setSlug(id);
        a.setTitle("GAD-7 焦虑症状自评");
        a.setSubtitle("过去两周内，你有多大程度被以下问题困扰？");
        a.setCategory("MOOD");
        a.setIcon("😰");
        a.setColor("#e07b4c");
        a.setQuestionCount(7);
        a.setEstimatedMinutes(3);
        a.setMinScore(0);
        a.setMaxScore(21);
        a.setPublishStatus("PUBLISHED");
        a.setSortOrder(2);
        a.setDescription(
            "GAD-7（Generalized Anxiety Disorder-7）是最常用的焦虑症状筛查量表，" +
            "由 Spitzer 等人在 2006 年编制。总分范围 0–21。\n\n" +
            "参考标准：\n" +
            "0–4 分：没有或极轻微\n" +
            "5–9 分：轻度焦虑\n" +
            "10–14 分：中度焦虑\n" +
            "15–21 分：重度焦虑"
        );
        assessmentMapper.insert(a);

        String[][] questions = {
            {"gad7_q1", "感到紧张、焦虑或急切", "0", "1", "2", "3"},
            {"gad7_q2", "不能够停止或控制担忧", "0", "1", "2", "3"},
            {"gad7_q3", "对各种各样的事情担忧过多", "0", "1", "2", "3"},
            {"gad7_q4", "很难放松下来", "0", "1", "2", "3"},
            {"gad7_q5", "由于不安而无法静坐", "0", "1", "2", "3"},
            {"gad7_q6", "变得容易烦恼或急躁", "0", "1", "2", "3"},
            {"gad7_q7", "感到似乎将有可怕的事情发生", "0", "1", "2", "3"}
        };

        String[] options = {"完全没有", "有几天", "一半以上的天数", "几乎每天"};

        for (String[] q : questions) {
            PsychologicalQuestion question = new PsychologicalQuestion();
            question.setQuestionId(q[0]);
            question.setAssessmentId(aid);
            question.setText(q[1]);
            question.setOrderIndex(Integer.parseInt(q[0].replaceAll("\\D+", "")));
            question.setReverseScore(false);
            questionMapper.insert(question);

            for (int i = 0; i < options.length; i++) {
                PsychologicalOption opt = new PsychologicalOption();
                opt.setOptionId(q[0] + "_o" + i);
                opt.setQuestionId(q[0]);
                opt.setText(options[i]);
                opt.setScore(i);
                opt.setOrderIndex(i);
                optionMapper.insert(opt);
            }
        }
    }

    // ================================================================
    // SDS（Zung 自rating抑郁量表，简化版10题）
    // ================================================================
    private void seedSDS() {
        String id = "sds";
        String aid = "psych_sds";
        PsychologicalAssessment a = new PsychologicalAssessment();
        a.setAssessmentId(aid);
        a.setSlug(id);
        a.setTitle("SDS 抑郁自评量表（简版）");
        a.setSubtitle("请根据最近两周的感受，选择最符合的选项。");
        a.setCategory("MOOD");
        a.setIcon("🌧️");
        a.setColor("#6b8e9b");
        a.setQuestionCount(10);
        a.setEstimatedMinutes(5);
        a.setMinScore(10);
        a.setMaxScore(40);
        a.setPublishStatus("PUBLISHED");
        a.setSortOrder(3);
        a.setDescription(
            "SDS（Zung Self-Rating Depression Scale）由 William W.K. Zung 于 1965 年编制，" +
            "是最经典的抑郁自评量表之一。本版本为简化10题版，适合快速筛查。\n\n" +
            "参考标准（粗分）：\n" +
            "10–19 分：正常范围\n" +
            "20–29 分：轻度抑郁倾向\n" +
            "30–39 分：中度抑郁倾向\n" +
            "40 分：重度抑郁倾向"
        );
        assessmentMapper.insert(a);

        // 正向题（1,3,4,7,8）：完全/有时/ frequently/大多
        // 反向题（2,5,6,9,10）：大多/frequently/有时/完全
        String[][] questions = {
            {"sds_q1", "我觉得情绪沮丧，郁闷", "0", "1", "2", "3"},
            {"sds_q2", "我感到早晨心情最好", "3", "2", "1", "0"},
            {"sds_q3", "我要哭或想哭", "0", "1", "2", "3"},
            {"sds_q4", "我夜间睡眠不好", "0", "1", "2", "3"},
            {"sds_q5", "我吃饭像平时一样多", "3", "2", "1", "0"},
            {"sds_q6", "我的性功能正常", "3", "2", "1", "0"},
            {"sds_q7", "我感到体重减轻", "0", "1", "2", "3"},
            {"sds_q8", "我为便秘烦恼", "3", "2", "1", "0"},
            {"sds_q9", "我的心跳比平时快", "3", "2", "1", "0"},
            {"sds_q10", "我无故感到疲劳", "0", "1", "2", "3"}
        };

        String[] optionTexts = {"完全或大部分时间", "一小部分时间", "有时", "几乎没有或没有"};
        boolean[] reverse = {false, true, false, false, true, true, false, true, true, false};

        for (int i = 0; i < questions.length; i++) {
            String[] q = questions[i];
            PsychologicalQuestion question = new PsychologicalQuestion();
            question.setQuestionId(q[0]);
            question.setAssessmentId(aid);
            question.setText(q[1]);
            question.setOrderIndex(i + 1);
            question.setReverseScore(reverse[i]);
            questionMapper.insert(question);

            for (int j = 0; j < 4; j++) {
                PsychologicalOption opt = new PsychologicalOption();
                opt.setOptionId(q[0] + "_o" + j);
                opt.setQuestionId(q[0]);
                opt.setText(optionTexts[j]);
                // 题目数组已预存各选项得分（反向题存 3,2,1,0；正向题存 0,1,2,3）
                int score = Integer.parseInt(q[2 + j]);
                opt.setScore(score);
                opt.setOrderIndex(j);
                optionMapper.insert(opt);
            }
        }
    }
}
