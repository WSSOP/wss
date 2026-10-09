package cn.dct.zpback.service.jewelry;

import cn.dct.zpback.service.jewelry.JewelryQuestionnaireScorer.PlanetScore;
import cn.dct.zpback.service.jewelry.JewelryQuestionnaireScorer.ScoreResult;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

/**
 * 候选行星资格判定测试（GC17）
 *
 * 回归背景：{@code isNode} 曾写成「不在 nodes 表中就当作节点」，
 * 而 nodes 表只登记 RAHU / KETU 两条，于是 SUN~SATURN 七颗正常行星
 * 全被当成节点过滤，候选集恒为空，表现为"星盘数据齐全但永远无推荐"。
 */
class PlanetCandidateEligibilityTest {

    private JewelryPolicies policies;

    @BeforeEach
    void setUp() throws Exception {
        policies = new JewelryPolicies();
        policies.load();
    }

    @Test
    @DisplayName("七颗正常行星都允许作为候选")
    void normalPlanets_areAllowed() {
        for (String p : JewelryPolicies.SEVEN) {
            assertFalse(policies.isNode(p), p + " 是正常行星，不该被判定为节点");
        }
    }

    @Test
    @DisplayName("RAHU / KETU 禁止作为候选（GC17）")
    void nodes_areBlocked() {
        assertTrue(policies.isNode("RAHU"));
        assertTrue(policies.isNode("KETU"));
    }

    @Test
    @DisplayName("打分链路：单题答案必须产出非零分与有效证据")
    void scoring_producesNonZeroScore() {
        JewelryQuestionnaireScorer scorer = new JewelryQuestionnaireScorer(policies);
        Map<String, List<String>> answers = new LinkedHashMap<>();
        answers.put("S01", List.of("SELF_DECISION"));

        ScoreResult r = scorer.score("SELF_ASSESSMENT", answers);

        // SELF_DECISION 权重 SUN=4 / JUPITER=2 / MERCURY=1
        assertEquals(4, r.scores.get("SUN").rawScore, "SUN 应得 4 分");
        assertEquals(2, r.scores.get("JUPITER").rawScore);
        assertEquals(1, r.scores.get("MERCURY").rawScore);
        assertEquals(1, r.scores.get("SUN").evidence.size(), "应记录 1 条证据");
        assertEquals(1, r.scores.get("SUN").qualifyingEvidenceCount());
        assertEquals(4, r.scores.get("SUN").contributions.get("SELF_DECISION"));
    }

    @Test
    @DisplayName("打分链路：完整自测答案产出候选行星")
    void scoring_producesCandidates() {
        JewelryQuestionnaireScorer scorer = new JewelryQuestionnaireScorer(policies);
        Map<String, List<String>> answers = new LinkedHashMap<>();
        answers.put("S01", List.of("SELF_DECISION", "SELF_EMOTIONAL"));
        answers.put("S02", List.of("DELAY_START"));
        answers.put("S03", List.of("TOO_IMPULSIVE"));
        answers.put("S04", List.of("OUTCOME_CONFIDENT"));
        answers.put("S05", List.of("UNDER_ONE_MONTH"));   // copy_only，不计分
        answers.put("S06", List.of("GENTLE_DAILY"));       // copy_only，不计分

        ScoreResult r = scorer.score("SELF_ASSESSMENT", answers);

        assertFalse(r.candidates.isEmpty(),
                "候选不应为空，实际 suppressed=" + r.suppressed);

        // 候选按分值降序
        PlanetScore first = r.candidates.get(0);
        for (int i = 1; i < r.candidates.size(); i++) {
            assertTrue(r.candidates.get(i - 1).rawScore >= r.candidates.get(i).rawScore,
                    "候选必须按 rawScore 降序");
        }
        // 候选上限来自配置 max_goal_candidates_before_gate
        assertTrue(r.candidates.size() <= policies.maxGoalCandidates());
    }

    @Test
    @DisplayName("copy_only 题不产生证据，但保留文案答案")
    void copyOnly_producesNoEvidence() {
        JewelryQuestionnaireScorer scorer = new JewelryQuestionnaireScorer(policies);
        Map<String, List<String>> answers = new LinkedHashMap<>();
        answers.put("S05", List.of("UNDER_ONE_MONTH"));

        ScoreResult r = scorer.score("SELF_ASSESSMENT", answers);

        assertEquals(List.of("UNDER_ONE_MONTH"), r.copyAnswers.get("S05"));
        for (PlanetScore ps : r.scores.values()) {
            assertEquals(0, ps.rawScore, "copy_only 题不应给任何行星加分");
        }
    }

    @Test
    @DisplayName("负权重选项产生负分，钳制为 0 后不进入候选")
    void negativeWeights_areClampedToZero() {
        JewelryQuestionnaireScorer scorer = new JewelryQuestionnaireScorer(policies);
        Map<String, List<String>> answers = new LinkedHashMap<>();
        answers.put("S03", List.of("TOO_IMPULSIVE"));   // MARS=-4, SUN=-1

        ScoreResult r = scorer.score("SELF_ASSESSMENT", answers);

        assertTrue(r.scores.get("MARS").rawScore >= 0, "负分必须钳制为 0");
        assertTrue(r.scores.get("SUN").rawScore >= 0);
        assertTrue(r.candidates.stream().noneMatch(c -> "MARS".equals(c.planet)),
                "纯负分行星不应成为候选");
    }

    @Test
    @DisplayName("节点权重即使出现在选项里也不会进入候选")
    void nodeWeights_neverCandidate() {
        JewelryQuestionnaireScorer scorer = new JewelryQuestionnaireScorer(policies);
        Map<String, List<String>> answers = new LinkedHashMap<>();
        answers.put("S01", List.of("SELF_DECISION"));

        ScoreResult r = scorer.score("SELF_ASSESSMENT", answers);

        assertNull(r.scores.get("RAHU"), "RAHU 不应有分数");
        assertNull(r.scores.get("KETU"));
        assertTrue(r.candidates.stream().noneMatch(c -> "RAHU".equals(c.planet)
                || "KETU".equals(c.planet)));
    }
}