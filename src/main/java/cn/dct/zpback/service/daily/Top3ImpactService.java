package cn.dct.zpback.service.daily;

import cn.dct.zpback.dto.daily.DailyObservationDtos;
import cn.dct.zpback.engine.daily.DailyObservationAssets;
import lombok.Data;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Top3 个人星象影响
 *
 * 对应《APP开发文档 V1.7》1.1.12 / 1.1.13：
 * - rank_score = base_priority × strength_factor × daily_relevance_factor × repeat_factor（P2P）
 *                base_priority × house_influence_factor × daily_relevance × repeat（P2H）
 * - 基础门槛 rank_score >= 60；
 * - 首轮约束：同一 source_planet 最多 1 条；慢行星最多 2 条；两类都合格时同一 evidence_type 最多 2 条；
 * - 不足 3 条时按顺序逐级放宽；
 * - 命中专属 template → 读取；未命中 → 模块化 fallback；配置缺失 → 轻量 evidence + 通用解释，禁止调用 LLM 编造。
 */
@Service
@Slf4j
public class Top3ImpactService {

    @Autowired
    private DailyObservationAssets assets;

    @Data
    public static class Top3Item {
        private String title;
        private String summary;
        private String astrologyEvidence;
        private List<String> impactDomains = new ArrayList<>();
        // 仅后端使用
        private double finalRankScore;
        private String impactSignature;
        private double repeatFactor;
        private String evidenceType;
        private String sourcePlanet;
        private String templateId;
        private boolean fallbackUsed;
    }

    /** 慢行星集合 */
    private List<String> slowPlanets() {
        String v = assets.param("TOP3_SLOW_PLANETS", "Jupiter,Saturn,Rahu,Ketu");
        List<String> list = new ArrayList<>();
        for (String s : v.split(",")) {
            if (!s.trim().isEmpty()) {
                list.add(s.trim());
            }
        }
        return list;
    }

    /**
     * 计算 Top3
     *
     * @param evidence       与四领域评分共用的同一批证据
     * @param yesterdaySignatures 昨日 Top3 的 impact_signature 列表（可为空 → repeat_factor 全 1.00）
     * @param consecutiveDaysOf   signature → 已连续进入 Top3 天数（可为空）
     */
    public List<Top3Item> computeTop3(List<DailyPersonalScoreService.Evidence> evidence,
                                      List<String> yesterdaySignatures,
                                      Map<String, Integer> consecutiveDaysOf) {
        int count = (int) assets.param("TOP3_COUNT", 3);
        double minRank = assets.param("TOP3_MIN_RANK_SCORE", 60);

        // 1) 计算 rank_score，过滤门槛
        List<Ranked> pool = new ArrayList<>();
        for (DailyPersonalScoreService.Evidence e : evidence) {
            if (!e.isTop3Eligible()) {
                continue;
            }
            double score = rankScore(e, yesterdaySignatures, consecutiveDaysOf);
            if (score < minRank) {
                continue;
            }
            Ranked r = new Ranked();
            r.evidence = e;
            r.rankScore = score;
            r.repeatFactor = repeatFactor(e, yesterdaySignatures, consecutiveDaysOf);
            pool.add(r);
        }
        pool.sort(Comparator.comparingDouble((Ranked r) -> r.rankScore).reversed());

        // 2) 首轮约束选取
        List<Ranked> selected = firstPass(pool, count);

        // 3) 不足 3 条时逐级放宽
        if (selected.size() < count) {
            selected = relax(pool, selected, count);
        }

        List<Top3Item> items = new ArrayList<>();
        for (Ranked r : selected) {
            items.add(toItem(r));
        }
        return items;
    }

    private static class Ranked {
        DailyPersonalScoreService.Evidence evidence;
        double rankScore;
        double repeatFactor;
    }

    /** rank_score */
    public double rankScore(DailyPersonalScoreService.Evidence e,
                            List<String> yesterdaySignatures,
                            Map<String, Integer> consecutiveDaysOf) {
        double daily = assets.param("TOP3_DAILY_FACTOR_" + e.getSourcePlanet().toUpperCase(), 1.0);
        double repeat = repeatFactor(e, yesterdaySignatures, consecutiveDaysOf);
        if ("P2P".equals(e.getEvidenceType())) {
            return e.getBasePriority() * strengthFactorOf(e) * daily * repeat;
        }
        double influenceFactor = "placement_influence".equals(e.getInfluenceType())
                ? assets.param("TOP3_P2H_PLACEMENT_FACTOR", 1.0)
                : assets.param("TOP3_P2H_DRISHTI_FACTOR", 0.85);
        return e.getBasePriority() * influenceFactor * daily * repeat;
    }

    private double strengthFactorOf(DailyPersonalScoreService.Evidence e) {
        if ("strong".equals(e.getStrength())) return assets.param("P2P_STRONG_FACTOR", 1.0);
        if ("medium".equals(e.getStrength())) return assets.param("P2P_MEDIUM_FACTOR", 0.7);
        return assets.param("P2P_WEAK_FACTOR", 0.4);
    }

    /**
     * repeat_factor：
     * 昨天不在 Top3 = 1.00；连续 1 天 = 0.85；连续 2 天 = 0.70；≥3 天 = 0.60。
     * P2P 例外：strength 从 weak→medium 或 medium→strong 时当日重置 1.00。
     */
    public double repeatFactor(DailyPersonalScoreService.Evidence e,
                               List<String> yesterdaySignatures,
                               Map<String, Integer> consecutiveDaysOf) {
        if (yesterdaySignatures == null || !yesterdaySignatures.contains(e.getImpactSignature())) {
            return assets.param("TOP3_REPEAT_FACTOR_NONE", 1.0);
        }
        int days = consecutiveDaysOf == null ? 1 : consecutiveDaysOf.getOrDefault(e.getImpactSignature(), 1);
        if (days >= 3) return assets.param("TOP3_REPEAT_FACTOR_3PLUS", 0.6);
        if (days == 2) return assets.param("TOP3_REPEAT_FACTOR_2DAYS", 0.7);
        return assets.param("TOP3_REPEAT_FACTOR_1DAY", 0.85);
    }

    /** 首轮：同一 source 最多 1 条；慢行星最多 2 条；两类都存在合格候选时同一 evidence_type 最多 2 条 */
    private List<Ranked> firstPass(List<Ranked> pool, int count) {
        int maxSlow = (int) assets.param("TOP3_MAX_SLOW_FIRST_PASS", 2);
        int maxPerType = (int) assets.param("TOP3_MAX_EVIDENCE_TYPE_FIRST_PASS", 2);
        boolean diversity = assets.param("TOP3_EVIDENCE_DIVERSITY_IF_BOTH_QUALIFY", 1) > 0;

        boolean hasP2p = pool.stream().anyMatch(r -> "P2P".equals(r.evidence.getEvidenceType()));
        boolean hasP2h = pool.stream().anyMatch(r -> "P2H".equals(r.evidence.getEvidenceType()));
        boolean bothQualify = diversity && hasP2p && hasP2h;

        List<Ranked> out = new ArrayList<>();
        List<String> usedSources = new ArrayList<>();
        List<String> slow = slowPlanets();
        int slowCount = 0;
        Map<String, Integer> typeCount = new LinkedHashMap<>();

        for (Ranked r : pool) {
            if (out.size() >= count) {
                break;
            }
            String source = r.evidence.getSourcePlanet();
            if (usedSources.contains(source)) {
                continue;
            }
            if (slow.contains(source) && slowCount >= maxSlow) {
                continue;
            }
            if (bothQualify) {
                String type = r.evidence.getEvidenceType();
                if (typeCount.getOrDefault(type, 0) >= maxPerType) {
                    continue;
                }
            }
            out.add(r);
            usedSources.add(source);
            if (slow.contains(source)) {
                slowCount++;
            }
            typeCount.merge(r.evidence.getEvidenceType(), 1, Integer::sum);
        }
        return out;
    }

    /** 逐级放宽：① 类型多样性 ② 慢行星上限 ③ 允许同一 source 第 2 条（最多 2 条） */
    private List<Ranked> relax(List<Ranked> pool, List<Ranked> selected, int count) {
        List<Ranked> out = new ArrayList<>(selected);
        int maxPerSource = (int) assets.param("TOP3_MAX_PER_SOURCE_SECOND_PASS", 2);

        for (Ranked r : pool) {
            if (out.size() >= count) {
                break;
            }
            if (out.contains(r)) {
                continue;
            }
            long sameSource = out.stream()
                    .filter(x -> x.evidence.getSourcePlanet().equals(r.evidence.getSourcePlanet()))
                    .count();
            if (sameSource >= maxPerSource) {
                continue;
            }
            out.add(r);
        }
        return out;
    }

    /** 生成用户侧条目：专属模板 → 模块化 fallback → 通用解释（禁止 LLM 编造） */
    private Top3Item toItem(Ranked r) {
        DailyPersonalScoreService.Evidence e = r.evidence;
        Top3Item item = new Top3Item();
        item.setEvidenceType(e.getEvidenceType());
        item.setSourcePlanet(e.getSourcePlanet());
        item.setTemplateId(e.getTemplateId());
        item.setFinalRankScore(r.rankScore);
        item.setImpactSignature(e.getImpactSignature());
        item.setRepeatFactor(r.repeatFactor);
        item.setImpactDomains(impactDomains(e));

        String title = null;
        String summary = null;
        String evidenceText = null;

        if ("P2P".equals(e.getEvidenceType())) {
            DailyObservationDtos.P2pTemplate t = assets.p2pTemplate(e.getTemplateId());
            if (t != null) {
                title = t.getTitle();
                summary = t.getOne_line_summary();
                evidenceText = t.getEvidence_text();
            }
        } else {
            DailyObservationDtos.P2hTemplate t = assets.p2hTemplate(e.getTemplateId());
            if (t != null) {
                title = t.getTitle();
                summary = t.getOne_line_summary();
                evidenceText = t.getEvidence_text();
            }
        }

        if (title == null) {
            // 配置缺失 → 轻量 astrology_evidence + 通用解释，记录 missing-template log
            item.setFallbackUsed(true);
            log.warn("Top3 专属模板缺失，使用确定性 fallback: template_id={}, signature={}",
                    e.getTemplateId(), e.getImpactSignature());
            title = genericTitle(e);
            summary = genericSummary(e);
        }
        if (evidenceText == null) {
            evidenceText = lightweightEvidence(e);
        }
        item.setTitle(title);
        item.setSummary(summary);
        item.setAstrologyEvidence(evidenceText);
        return item;
    }

    private List<String> impactDomains(DailyPersonalScoreService.Evidence e) {
        List<String> list = new ArrayList<>();
        for (String d : DailyPersonalScoreService.DOMAINS) {
            if (e.delta(d) != 0) {
                list.add(d);
            }
        }
        list.sort(Comparator.comparingDouble((String d) -> Math.abs(e.delta(d))).reversed());
        return list.subList(0, Math.min(2, list.size()));
    }

    /** 通用标题（不编造占星结论，只描述证据本身） */
    private String genericTitle(DailyPersonalScoreService.Evidence e) {
        if ("P2P".equals(e.getEvidenceType())) {
            return "流年" + cn(e.getSourcePlanet()) + "作用于本命" + cn(e.getTargetPlanet());
        }
        return "流年" + cn(e.getSourcePlanet()) + "影响本命第" + e.getTargetHouse() + "宫";
    }

    /** 通用解释：只按 contribution 正负给方向，不虚构事件 */
    private String genericSummary(DailyPersonalScoreService.Evidence e) {
        double max = 0;
        for (String d : DailyPersonalScoreService.DOMAINS) {
            if (Math.abs(e.delta(d)) > Math.abs(max)) {
                max = e.delta(d);
            }
        }
        String polarity = max > 0 ? "偏正向" : (max < 0 ? "偏紧张" : "中性");
        String intensity = Math.abs(max) >= 4 ? "强" : (Math.abs(max) >= 2 ? "中" : "轻");
        return "这一条影响今天整体" + polarity + "，强度" + intensity + "，可在下方对应领域里体会。";
    }

    /** 轻量证据（前端可展示，例如「土星 → 本命月亮 · 强影响」） */
    private String lightweightEvidence(DailyPersonalScoreService.Evidence e) {
        if ("P2P".equals(e.getEvidenceType())) {
            return cn(e.getSourcePlanet()) + " → 本命" + cn(e.getTargetPlanet())
                    + " · " + strengthCn(e.getStrength());
        }
        return cn(e.getSourcePlanet()) + " → 本命第" + e.getTargetHouse() + "宫"
                + " · " + ("placement_influence".equals(e.getInfluenceType()) ? "落宫" : "相位");
    }

    private static String strengthCn(String s) {
        if ("strong".equals(s)) return "强影响";
        if ("medium".equals(s)) return "中等影响";
        return "轻影响";
    }

    private static final Map<String, String> CN = new LinkedHashMap<>();
    static {
        CN.put("Sun", "太阳"); CN.put("Moon", "月亮"); CN.put("Mercury", "水星");
        CN.put("Venus", "金星"); CN.put("Mars", "火星"); CN.put("Jupiter", "木星");
        CN.put("Saturn", "土星"); CN.put("Rahu", "北交点"); CN.put("Ketu", "南交点");
    }

    private static String cn(String planet) {
        return CN.getOrDefault(planet, planet);
    }
}
