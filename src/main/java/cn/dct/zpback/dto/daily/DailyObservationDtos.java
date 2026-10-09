package cn.dct.zpback.dto.daily;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import lombok.Data;

import java.util.List;
import java.util.Map;

/**
 * 今日观星规则资产反序列化结构
 *
 * 对应三份随包机器可读资产（均由配置表 XLSX 提取，是运行时唯一事实源）：
 * - daily-score-rules-v1.json（每日评分 Top3 规则配置表 V1.7）
 * - nakshatra-daily-copy-v1.json（27星宿公共日运文案配置表 V1.9）
 * - top3-explanation-templates-v1.json（Top3解释模板库 V3.0）
 */
public final class DailyObservationDtos {

    private DailyObservationDtos() {
    }

    // ============================================================
    // daily-score-rules-v1.json
    // ============================================================

    @Data
    @JsonIgnoreProperties(ignoreUnknown = true)
    public static class ScoreRules {
        private String version;
        private Map<String, String> global_params;
        private List<P2pRule> p2p_rules;
        private List<P2hRule> p2h_rules;
    }

    @Data
    @JsonIgnoreProperties(ignoreUnknown = true)
    public static class P2pRule {
        private String rule_id;
        private boolean enabled;
        private String source_planet;
        private String target_planet;
        private String relation_type_filter;
        private String aspect_position_filter;
        private double career_delta;
        private double love_delta;
        private double study_delta;
        private double growth_delta;
        private double base_priority;
        private boolean top3_eligible;
        private String template_id;
    }

    @Data
    @JsonIgnoreProperties(ignoreUnknown = true)
    public static class P2hRule {
        private String rule_id;
        private boolean enabled;
        private String source_planet;
        private int target_house;
        private String influence_type_filter;
        private String aspect_position_filter;
        private double career_delta;
        private double love_delta;
        private double study_delta;
        private double growth_delta;
        private double base_priority;
        private boolean top3_eligible;
        private String template_id;
    }

    // ============================================================
    // nakshatra-daily-copy-v1.json
    // ============================================================

    @Data
    @JsonIgnoreProperties(ignoreUnknown = true)
    public static class DailyCopy {
        private String version;
        private List<NakshatraCopy> nakshatras;
        private List<SignCopy> signs;
        private List<DomainSentence> domain_sentences;
    }

    @Data
    @JsonIgnoreProperties(ignoreUnknown = true)
    public static class NakshatraCopy {
        private int order;
        private String nakshatra_en;
        private String nakshatra_cn;
        private String keyword_1;
        private String keyword_2;
        private String keyword_3;
        private String daily_core_sentence;
        private String transition_action;
    }

    @Data
    @JsonIgnoreProperties(ignoreUnknown = true)
    public static class SignCopy {
        private String sign_en;
        private String sign_cn;
        private String sign_transition_phrase;
    }

    @Data
    @JsonIgnoreProperties(ignoreUnknown = true)
    public static class DomainSentence {
        private String domain;
        private int score_min;
        private int score_max;
        private String level_key;
        private String template_text;
    }

    // ============================================================
    // top3-explanation-templates-v1.json
    // ============================================================

    @Data
    @JsonIgnoreProperties(ignoreUnknown = true)
    public static class ExplanationTemplates {
        private String version;
        private List<P2pTemplate> p2p_templates;
        private List<P2hTemplate> p2h_templates;
    }

    @Data
    @JsonIgnoreProperties(ignoreUnknown = true)
    public static class P2pTemplate {
        private String template_id;
        private String source_planet;
        private String target_planet;
        private String primary_domains;
        private String title;
        private String one_line_summary;
        private String evidence_text;
    }

    @Data
    @JsonIgnoreProperties(ignoreUnknown = true)
    public static class P2hTemplate {
        private String template_id;
        private String source_planet;
        private Integer target_house;
        private String primary_domains;
        private String title;
        private String one_line_summary;
        private String evidence_text;
    }
}
