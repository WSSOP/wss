package cn.dct.zpback.service.jewelry;

import com.fasterxml.jackson.databind.JsonNode;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 行星珠宝｜Questionnaire Scorer
 *
 * 严格实现 03_测试题与需求映射.md §5：
 * 1. 对已答选项累加 planet_weights（含过度表达题的负权重）；
 * 2. 每颗行星 Raw Score 钳制为 0；
 * 3. Raw Score >= candidate_raw_score_min 才进入 Goal Candidate；
 * 4. 至少一项分数必须来自优先题或分支题（copy-only 题不产生证据）；
 * 5. 仅保留前 N 个候选进入星盘 Gate。
 *
 * 自测与赠礼的原始分语义分开保存：need_score_raw / blessing_fit_score_raw。
 * 分数只有"用户意愿匹配度"权力，没有任何绕过星盘 Hard Block 的权力。
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class JewelryQuestionnaireScorer {

    /** 证据来源分类：主干优先题 / 分支题 / 仅文案题（不构成证据） */
    public enum EvidenceSource {
        DIRECT_PRIORITY, BRANCH, COPY_ONLY
    }

    public static class ScoredOption {
        public final String questionId;
        public final String optionId;
        public final EvidenceSource source;

        ScoredOption(String questionId, String optionId, EvidenceSource source) {
            this.questionId = questionId;
            this.optionId = optionId;
            this.source = source;
        }
    }

    /** 单颗行星的目标分与证据 */
    public static class PlanetScore {
        public String planet;
        /** 意图匹配原始分（已钳制为 0，不向用户展示） */
        public int rawScore;
        /** 来自优先题/分支题的证据 */
        public final List<ScoredOption> evidence = new ArrayList<>();
        /** 逐题得分明细，供审计 */
        public final Map<String, Integer> contributions = new LinkedHashMap<>();
        /**
         * 该行星命中的 goal_tags（题库选项自带），用于输出 GOAL_*_DIRECT 证据。
         * 顺序即用户作答顺序，保证输出稳定。
         */
        public final List<String> goalTags = new ArrayList<>();
        /** 首个有效证据的下标，用于同分时的确定性排序 */
        public int firstEvidenceIndex = Integer.MAX_VALUE;
        /** 每条证据带来的绝对分，directPriorityScore 用于同分排序 */
        private final Map<String, Integer> absoluteBySource = new LinkedHashMap<>();

        public int directPriorityScore() {
            return absoluteBySource.getOrDefault("DIRECT_PRIORITY", 0);
        }

        public int branchScore() {
            return absoluteBySource.getOrDefault("BRANCH", 0);
        }

        public int qualifyingEvidenceCount() {
            return (int) evidence.stream()
                    .filter(e -> e.source != EvidenceSource.COPY_ONLY)
                    .count();
        }

        void recordContribution(ScoredOption e, int weight) {
            absoluteBySource.merge(e.source.name(), Math.abs(weight), Integer::sum);
        }
    }

    public static class ScoreResult {
        public String mode;
        /** 自测：need_score_raw；赠礼：blessing_fit_score_raw */
        public final Map<String, PlanetScore> scores = new LinkedHashMap<>();
        /** 进入星盘 Gate 的候选（已按分值排序、截断到 maxGoalCandidates） */
        public final List<PlanetScore> candidates = new ArrayList<>();
        /** 未进入候选的行星及其原因 */
        public final Map<String, List<String>> suppressed = new LinkedHashMap<>();
        /** 已激活的分支题 id（按用户选择顺序） */
        public final List<String> activeBranches = new ArrayList<>();
        /** copy-only 答案，供文案使用，不参与计分 */
        public final Map<String, List<String>> copyAnswers = new LinkedHashMap<>();
    }

    private final JewelryPolicies policies;

    // ------------------------------------------------------------------

    /**
     * @param mode   SELF_ASSESSMENT / GIFT_BLESSING
     * @param answers question_id -> option_ids（保持用户选择顺序）
     */
    public ScoreResult score(String mode, Map<String, List<String>> answers) {
        ScoreResult result = new ScoreResult();
        result.mode = mode;

        // 所有候选行星先占位，保证输出顺序稳定（canonical order）
        for (String planet : policies.canonicalPlanetOrder()) {
            PlanetScore ps = new PlanetScore();
            ps.planet = planet;
            result.scores.put(planet, ps);
        }

        int order = 0;
        for (Map.Entry<String, List<String>> e : answers.entrySet()) {
            String questionId = e.getKey();
            JsonNode q = policies.findQuestion(mode, questionId);
            if (q == null) {
                log.warn("计分跳过未知题目: {} / {}", mode, questionId);
                continue;
            }
            boolean copyOnly = policies.isCopyOnly(mode, questionId);
            boolean isBranch = questionId.startsWith(mode.startsWith("SELF") ? "SB" : "GB");

            if (copyOnly) {
                result.copyAnswers.put(questionId, e.getValue());
                continue;
            }

            for (String optionId : e.getValue()) {
                JsonNode opt = policies.findOption(mode, optionId);
                if (opt == null) continue;
                order++;
                EvidenceSource src = copyOnly ? EvidenceSource.COPY_ONLY
                        : isBranch ? EvidenceSource.BRANCH : EvidenceSource.DIRECT_PRIORITY;
                applyWeights(result, opt, new ScoredOption(questionId, optionId, src), order);
            }
        }

        // 1) 钳制为 0  2) 阈值 3) 必须含非 copy-only 证据
        List<PlanetScore> qualified = new ArrayList<>();
        for (PlanetScore ps : result.scores.values()) {
            ps.rawScore = Math.max(0, ps.rawScore);
            if (ps.rawScore < policies.candidateRawScoreMin()) {
                result.suppressed.computeIfAbsent(ps.planet, k -> new ArrayList<>())
                        .add("RAW_SCORE_BELOW_ELIGIBLE_RANK");
            } else if (ps.qualifyingEvidenceCount() == 0) {
                result.suppressed.computeIfAbsent(ps.planet, k -> new ArrayList<>())
                        .add("NO_PRIORITY_OR_BRANCH_EVIDENCE");
            } else {
                qualified.add(ps);
            }
        }

        // 4) 排序：意图分 → 优先题证据 → 证据先后 → canonical order（仅作技术稳定性）
        List<String> canonical = policies.canonicalPlanetOrder();
        qualified.sort(Comparator
                .comparingInt((PlanetScore p) -> p.rawScore).reversed()
                .thenComparingInt(p -> -p.directPriorityScore())
                .thenComparingInt(p -> p.firstEvidenceIndex)
                .thenComparingInt(p -> canonical.indexOf(p.planet)));

        int max = policies.maxGoalCandidates();
        for (int i = 0; i < qualified.size(); i++) {
            if (i < max) {
                result.candidates.add(qualified.get(i));
            } else {
                result.suppressed.computeIfAbsent(qualified.get(i).planet, k -> new ArrayList<>())
                        .add("BEYOND_MAX_GOAL_CANDIDATES");
            }
        }
        return result;
    }

    /** 选项自带的 goal_tags（可能缺省） */
    private static List<String> goalTagsOf(JsonNode option) {
        List<String> out = new ArrayList<>();
        option.path("goal_tags").forEach(t -> out.add(t.asText()));
        return out;
    }

    private void applyWeights(ScoreResult result, JsonNode option, ScoredOption scored, int order) {
        JsonNode weights = option.path("planet_weights");
        if (!weights.isObject()) return;
        weights.fields().forEachRemaining(entry -> {
            String planet = entry.getKey().toUpperCase();
            // 节点只能作为风险 Modifier，永不进入候选（GC17）
            if (policies.isNode(planet)) return;
            PlanetScore ps = result.scores.get(planet);
            if (ps == null) return;
            int w = entry.getValue().asInt(0);
            ps.rawScore += w;
            ps.contributions.merge(scored.optionId, w, Integer::sum);
            // 该行星只要正向加过分，就记下这道题命中的 goal_tags：
            // 负权重选项不构成 Goal Bridge，不应作为证据展示给用户
            if (w > 0) {
                for (String tag : goalTagsOf(option)) {
                    if (!ps.goalTags.contains(tag)) ps.goalTags.add(tag);
                }
            }
            // 分支题与优先题都算有效证据；只有 copy-only 题不计入
            ps.evidence.add(scored);
            ps.recordContribution(scored, w);
            ps.firstEvidenceIndex = Math.min(ps.firstEvidenceIndex, order);
        });
    }
}