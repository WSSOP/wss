package cn.dct.zpback.service.daily;

import cn.dct.zpback.dto.daily.DailyObservationDtos;
import cn.dct.zpback.engine.daily.DailyObservationAssets;
import cn.dct.zpback.engine.daily.VedicPlanetaryInfluenceEngine;
import cn.dct.zpback.engine.daily.VedicPlanetaryInfluenceEngine.HouseInfluence;
import cn.dct.zpback.engine.daily.VedicPlanetaryInfluenceEngine.PlanetPos;
import cn.dct.zpback.engine.daily.VedicPlanetaryInfluenceEngine.PlanetRelation;
import lombok.Data;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 今日观星个性化链路（四领域评分 + Top3）
 *
 * 对应《APP开发文档 V1.7》1.1.11 / 1.1.12 / 1.1.13：
 * - 评分与 Top3 共用同一批 P2P / P2H 证据，不创建第二套占星引擎；
 * - 基础分 50，clamp 20–95，每领域只取 |contribution| Top5；
 * - P2P contribution = rule_delta × strength_factor；
 * - P2H contribution = rule_delta × 1.0（不使用 degree exactness）；
 * - Top3 rank_score = base_priority × strength_factor × daily_relevance × repeat；
 * - 四领域 delta 不进入 Top3 rank_score；
 * - 默认 0 次 LLM 调用。
 */
@Service
@Slf4j
public class DailyPersonalScoreService {

    @Autowired
    private DailyObservationAssets assets;

    /** 四领域 */
    public static final String[] DOMAINS = {"career", "love", "study", "growth"};

    /** 单条证据（评分与 Top3 共用） */
    @Data
    public static class Evidence {
        private String evidenceType;      // P2P / P2H
        private String sourcePlanet;
        private String targetPlanet;      // P2P
        private Integer targetHouse;      // P2H
        private String relationType;      // conjunction / graha_drishti / placement_influence
        private int aspectPosition;
        private String strength;          // strong / medium / weak（P2P）
        private String influenceType;     // P2H
        private double degreeDeltaDeg;
        private String exactnessClass;
        private double basePriority;
        private String templateId;
        private boolean top3Eligible;
        private Map<String, Double> deltas = new LinkedHashMap<>();
        private String impactSignature;

        public double delta(String domain) {
            return deltas.getOrDefault(domain, 0.0);
        }

        public double maxAbsDelta() {
            return deltas.values().stream().mapToDouble(Math::abs).max().orElse(0);
        }
    }

    /** 四领域最终分 + 短句 */
    @Data
    public static class DomainScore {
        private String domain;
        private int score;
        private String sentence;
        private String levelKey;
    }

    /** 评分结果 */
    @Data
    public static class ScoreResult {
        private List<DomainScore> scores = new ArrayList<>();
        private List<Evidence> evidence = new ArrayList<>();

        public DomainScore of(String domain) {
            for (DomainScore s : scores) {
                if (s.getDomain().equals(domain)) {
                    return s;
                }
            }
            return null;
        }
    }

    /**
     * 计算四领域评分
     *
     * @param natalAscSignIndex 本命上升星座序号（0=Aries）
     * @param natalPlanets      本命九大行星位置
     * @param transitPlanets    流年九大行星位置（Transit Snapshot）
     */
    public ScoreResult computeScores(int natalAscSignIndex,
                                     List<PlanetPos> natalPlanets,
                                     List<PlanetPos> transitPlanets) {
        List<Evidence> evidence = collectEvidence(natalAscSignIndex, natalPlanets, transitPlanets);

        ScoreResult result = new ScoreResult();
        result.setEvidence(evidence);

        double base = assets.param("BASE_SCORE", 50);
        double min = assets.param("MIN_SCORE", 20);
        double max = assets.param("MAX_SCORE", 95);
        int topN = (int) assets.param("TOP_N_PER_DOMAIN", 5);

        for (String domain : DOMAINS) {
            // 每领域只取 abs(contribution) 排序后的 Top5 再求和
            List<Double> contributions = new ArrayList<>();
            for (Evidence e : evidence) {
                double c = contributionOf(e, domain);
                if (c != 0) {
                    contributions.add(c);
                }
            }
            contributions.sort(Comparator.comparingDouble((Double d) -> Math.abs(d)).reversed());
            double sum = 0;
            for (int i = 0; i < Math.min(topN, contributions.size()); i++) {
                sum += contributions.get(i);
            }
            int finalScore = (int) Math.round(Math.min(max, Math.max(min, base + sum)));

            DomainScore ds = new DomainScore();
            ds.setDomain(domain);
            ds.setScore(finalScore);
            ds.setSentence(assets.domainSentence(domain, finalScore));
            ds.setLevelKey(levelKeyOf(finalScore));
            result.getScores().add(ds);
        }
        return result;
    }

    /** 档位（仅后端使用，不向普通用户展示） */
    public static String levelKeyOf(int score) {
        if (score <= 39) return "LOW";
        if (score <= 49) return "WATCH";
        if (score <= 59) return "STEADY";
        if (score <= 74) return "GOOD";
        return "STRONG";
    }

    /** 收集 P2P + P2H 证据 */
    public List<Evidence> collectEvidence(int natalAscSignIndex,
                                          List<PlanetPos> natalPlanets,
                                          List<PlanetPos> transitPlanets) {
        List<Evidence> out = new ArrayList<>();

        // ---- P2P：流年行星 → 本命行星 ----
        List<PlanetRelation> relations =
                VedicPlanetaryInfluenceEngine.crossChartPlanetToPlanet(transitPlanets, natalPlanets, true);
        for (PlanetRelation rel : relations) {
            DailyObservationDtos.P2pRule rule =
                    assets.p2pRule(rel.getSourcePlanet(), rel.getTargetPlanet());
            if (rule == null || !rule.isEnabled()) {
                continue; // 规则缺失不编造，直接跳过
            }
            if (!matchesFilter(rule.getRelation_type_filter(), rel.getRelationType())) {
                continue;
            }
            Evidence e = new Evidence();
            e.setEvidenceType("P2P");
            e.setSourcePlanet(rel.getSourcePlanet());
            e.setTargetPlanet(rel.getTargetPlanet());
            e.setRelationType(rel.getRelationType());
            e.setAspectPosition(rel.getAspectPosition());
            e.setStrength(rel.getStrength());
            e.setDegreeDeltaDeg(rel.getDegreeDeltaDeg());
            e.setExactnessClass(rel.getExactnessClass());
            e.setBasePriority(rule.getBase_priority());
            e.setTemplateId(rule.getTemplate_id());
            e.setTop3Eligible(rule.isTop3_eligible());
            e.getDeltas().put("career", rule.getCareer_delta());
            e.getDeltas().put("love", rule.getLove_delta());
            e.getDeltas().put("study", rule.getStudy_delta());
            e.getDeltas().put("growth", rule.getGrowth_delta());
            e.setImpactSignature("P2P|" + rel.getSourcePlanet() + "|" + rel.getTargetPlanet()
                    + "|" + rel.getRelationType() + "|" + rel.getAspectPosition());
            out.add(e);
        }

        // ---- P2H：流年行星 → 本命宫位（Whole Sign，相对本命上升）----
        for (PlanetPos tp : transitPlanets) {
            int house = VedicPlanetaryInfluenceEngine.overlayHouse(tp.getSignIndex(), natalAscSignIndex);
            List<HouseInfluence> influences =
                    VedicPlanetaryInfluenceEngine.planetToHouse(tp.getPlanet(), house);
            for (HouseInfluence inf : influences) {
                DailyObservationDtos.P2hRule rule =
                        assets.p2hRule(inf.getSourcePlanet(), inf.getTargetHouse());
                if (rule == null || !rule.isEnabled()) {
                    continue;
                }
                if (!matchesFilter(rule.getInfluence_type_filter(), inf.getInfluenceType())) {
                    continue;
                }
                Evidence e = new Evidence();
                e.setEvidenceType("P2H");
                e.setSourcePlanet(inf.getSourcePlanet());
                e.setTargetHouse(inf.getTargetHouse());
                e.setInfluenceType(inf.getInfluenceType());
                e.setRelationType(inf.getInfluenceType());
                e.setAspectPosition(inf.getAspectPosition());
                e.setBasePriority(rule.getBase_priority());
                e.setTemplateId(rule.getTemplate_id());
                e.setTop3Eligible(rule.isTop3_eligible());
                e.getDeltas().put("career", rule.getCareer_delta());
                e.getDeltas().put("love", rule.getLove_delta());
                e.getDeltas().put("study", rule.getStudy_delta());
                e.getDeltas().put("growth", rule.getGrowth_delta());
                e.setImpactSignature("P2H|" + inf.getSourcePlanet() + "|" + inf.getTargetHouse()
                        + "|" + inf.getInfluenceType() + "|" + inf.getAspectPosition());
                out.add(e);
            }
        }
        return out;
    }

    /** 单条证据在某领域的 contribution（评分层） */
    public double contributionOf(Evidence e, String domain) {
        double delta = e.delta(domain);
        if ("P2P".equals(e.getEvidenceType())) {
            return delta * strengthFactor(e.getStrength());
        }
        // P2H：不使用度数 exactness，factor 固定 1.0
        return delta * assets.param("P2H_FACTOR", 1.0);
    }

    /** P2P strength_factor */
    public double strengthFactor(String strength) {
        if ("strong".equals(strength)) return assets.param("P2P_STRONG_FACTOR", 1.0);
        if ("medium".equals(strength)) return assets.param("P2P_MEDIUM_FACTOR", 0.7);
        return assets.param("P2P_WEAK_FACTOR", 0.4);
    }

    private boolean matchesFilter(String filter, String actual) {
        return filter == null || filter.isEmpty()
                || "ANY".equals(filter) || "ANY_VALID_RELATION".equals(filter)
                || "ANY_VALID_INFLUENCE".equals(filter) || filter.equals(actual);
    }

    /** 该证据按 abs(delta) 排序后非 0 的前 2 个领域 */
    public List<String> impactDomainsOf(Evidence e) {
        List<String> list = new ArrayList<>();
        for (String d : DOMAINS) {
            if (e.delta(d) != 0) {
                list.add(d);
            }
        }
        list.sort(Comparator.comparingDouble((String d) -> Math.abs(e.delta(d))).reversed());
        return list.subList(0, Math.min(2, list.size()));
    }

    public List<String> sortedDomains() {
        return Collections.unmodifiableList(new ArrayList<>(List.of(DOMAINS)));
    }
}
