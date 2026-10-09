package cn.dct.zpback.engine.daily;

import cn.dct.zpback.dto.daily.DailyObservationDtos;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.annotation.PostConstruct;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.core.io.ClassPathResource;
import org.springframework.stereotype.Component;

import java.io.InputStream;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 今日观星规则资产
 *
 * 三份资产均由配置表 XLSX 提取，运行时唯一事实源：
 * - daily-score-rules-v1.json         189 条评分规则（81 P2P + 108 P2H）+ 36 全局参数
 * - nakshatra-daily-copy-v1.json      27 星宿文案 + 12 星座切换短语 + 20 条四领域短句
 * - top3-explanation-templates-v1.json 189 条 Top3 专属解释模板
 *
 * 禁止在代码里另写一套规则表；配置缺失时按确定性 fallback 退化，不得调用 LLM 编造。
 */
@Component
@Slf4j
public class DailyObservationAssets {

    public static final String RES_SCORE_RULES = "daily-observation/daily-score-rules-v1.json";
    public static final String RES_DAILY_COPY = "daily-observation/nakshatra-daily-copy-v1.json";
    public static final String RES_TOP3_TEMPLATES = "daily-observation/top3-explanation-templates-v1.json";

    @Autowired
    private ObjectMapper objectMapper;

    private DailyObservationDtos.ScoreRules scoreRules;
    private DailyObservationDtos.DailyCopy dailyCopy;
    private DailyObservationDtos.ExplanationTemplates templates;

    /** "SOURCE_PLANET|TARGET_PLANET" → P2P 规则 */
    private Map<String, DailyObservationDtos.P2pRule> p2pByPair = Collections.emptyMap();
    /** "SOURCE_PLANET|HOUSE" → P2H 规则 */
    private Map<String, DailyObservationDtos.P2hRule> p2hByHouse = Collections.emptyMap();
    /** template_id → P2P 模板 */
    private Map<String, DailyObservationDtos.P2pTemplate> p2pTemplates = Collections.emptyMap();
    /** template_id → P2H 模板 */
    private Map<String, DailyObservationDtos.P2hTemplate> p2hTemplates = Collections.emptyMap();
    /** 星宿英文名（归一化） → 文案 */
    private Map<String, DailyObservationDtos.NakshatraCopy> nakByName = Collections.emptyMap();
    /** 星座英文名（归一化） → 文案 */
    private Map<String, DailyObservationDtos.SignCopy> signByName = Collections.emptyMap();

    @PostConstruct
    public void load() {
        try {
            scoreRules = read(RES_SCORE_RULES, DailyObservationDtos.ScoreRules.class);
            dailyCopy = read(RES_DAILY_COPY, DailyObservationDtos.DailyCopy.class);
            templates = read(RES_TOP3_TEMPLATES, DailyObservationDtos.ExplanationTemplates.class);

            if (scoreRules == null || scoreRules.getP2p_rules() == null
                    || scoreRules.getP2p_rules().size() != 81) {
                throw new IllegalStateException("P2P 评分规则必须为 81 条");
            }
            if (scoreRules.getP2h_rules() == null || scoreRules.getP2h_rules().size() != 108) {
                throw new IllegalStateException("P2H 评分规则必须为 108 条");
            }
            if (dailyCopy == null || dailyCopy.getNakshatras() == null
                    || dailyCopy.getNakshatras().size() != 27) {
                throw new IllegalStateException("27 星宿文案必须为 27 条");
            }

            Map<String, DailyObservationDtos.P2pRule> p2p = new LinkedHashMap<>();
            for (DailyObservationDtos.P2pRule r : scoreRules.getP2p_rules()) {
                p2p.put(key(r.getSource_planet(), r.getTarget_planet()), r);
            }
            p2pByPair = Collections.unmodifiableMap(p2p);

            Map<String, DailyObservationDtos.P2hRule> p2h = new LinkedHashMap<>();
            for (DailyObservationDtos.P2hRule r : scoreRules.getP2h_rules()) {
                p2h.put(r.getSource_planet() + "|" + r.getTarget_house(), r);
            }
            p2hByHouse = Collections.unmodifiableMap(p2h);

            Map<String, DailyObservationDtos.P2pTemplate> pt = new LinkedHashMap<>();
            if (templates.getP2p_templates() != null) {
                for (DailyObservationDtos.P2pTemplate t : templates.getP2p_templates()) {
                    pt.put(t.getTemplate_id(), t);
                }
            }
            p2pTemplates = Collections.unmodifiableMap(pt);

            Map<String, DailyObservationDtos.P2hTemplate> ht = new LinkedHashMap<>();
            if (templates.getP2h_templates() != null) {
                for (DailyObservationDtos.P2hTemplate t : templates.getP2h_templates()) {
                    ht.put(t.getTemplate_id(), t);
                }
            }
            p2hTemplates = Collections.unmodifiableMap(ht);

            Map<String, DailyObservationDtos.NakshatraCopy> naks = new LinkedHashMap<>();
            for (DailyObservationDtos.NakshatraCopy n : dailyCopy.getNakshatras()) {
                naks.put(norm(n.getNakshatra_en()), n);
                naks.put(norm(n.getNakshatra_cn()), n);
            }
            nakByName = Collections.unmodifiableMap(naks);

            Map<String, DailyObservationDtos.SignCopy> signs = new LinkedHashMap<>();
            if (dailyCopy.getSigns() != null) {
                for (DailyObservationDtos.SignCopy s : dailyCopy.getSigns()) {
                    signs.put(norm(s.getSign_en()), s);
                    signs.put(norm(s.getSign_cn()), s);
                }
            }
            signByName = Collections.unmodifiableMap(signs);

            log.info("今日观星规则资产加载完成: score={}, copy={}, templates={}, p2p={}, p2h={}, nakshatra={}",
                    scoreRules.getVersion(), dailyCopy.getVersion(), templates.getVersion(),
                    p2pByPair.size(), p2hByHouse.size(), dailyCopy.getNakshatras().size());
        } catch (Exception e) {
            throw new IllegalStateException("今日观星规则资产加载失败: " + e.getMessage(), e);
        }
    }

    private <T> T read(String path, Class<T> type) throws Exception {
        try (InputStream in = new ClassPathResource(path).getInputStream()) {
            return objectMapper.readValue(in, type);
        }
    }

    private static String key(String a, String b) {
        return a + "|" + b;
    }

    public static String norm(String raw) {
        return raw == null ? "" : raw.trim().toLowerCase().replaceAll("[\\s\\-_]+", "");
    }

    public double param(String key, double fallback) {
        String v = scoreRules.getGlobal_params().get(key);
        if (v == null) {
            return fallback;
        }
        try {
            return Double.parseDouble(v);
        } catch (Exception e) {
            return fallback;
        }
    }

    public String param(String key, String fallback) {
        String v = scoreRules.getGlobal_params().get(key);
        return v == null ? fallback : v;
    }

    public DailyObservationDtos.P2pRule p2pRule(String sourcePlanet, String targetPlanet) {
        return p2pByPair.get(key(sourcePlanet, targetPlanet));
    }

    public DailyObservationDtos.P2hRule p2hRule(String sourcePlanet, int targetHouse) {
        return p2hByHouse.get(sourcePlanet + "|" + targetHouse);
    }

    public DailyObservationDtos.P2pTemplate p2pTemplate(String templateId) {
        return templateId == null ? null : p2pTemplates.get(templateId);
    }

    public DailyObservationDtos.P2hTemplate p2hTemplate(String templateId) {
        return templateId == null ? null : p2hTemplates.get(templateId);
    }

    public DailyObservationDtos.NakshatraCopy nakshatra(String rawName) {
        return nakByName.get(norm(rawName));
    }

    public DailyObservationDtos.SignCopy sign(String rawName) {
        return signByName.get(norm(rawName));
    }

    /** 四领域分数短句：domain + final_score → template_text */
    public String domainSentence(String domain, int score) {
        for (DailyObservationDtos.DomainSentence s : dailyCopy.getDomain_sentences()) {
            if (s.getDomain().equals(domain) && score >= s.getScore_min() && score <= s.getScore_max()) {
                return s.getTemplate_text();
            }
        }
        return null;
    }

    public DailyObservationDtos.ScoreRules getScoreRules() {
        return scoreRules;
    }

    public DailyObservationDtos.DailyCopy getDailyCopy() {
        return dailyCopy;
    }
}
