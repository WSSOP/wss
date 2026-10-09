package cn.dct.zpback.dto.synbasic;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import lombok.Data;

import java.util.List;
import java.util.Map;

/**
 * 双人合盘基础互动（156 Canonical Library）规则资产反序列化结构
 *
 * 与 RUNTIME 下三份机器可读资产一一对应：
 * - syn_chart_basic_interaction_156_canonical_v1.json
 * - canonical_naming_dictionary_v1.json
 * - canonical_lookup_index_v1.json
 *
 * 字段名与 JSON 完全一致（snake_case），Jackson 默认即可映射。
 * 这些资产是运行时唯一事实源，禁止在代码里另写一套关系表。
 */
public final class SynBasicDtos {

    private SynBasicDtos() {
    }

    // ============================================================
    // syn_chart_basic_interaction_156_canonical_v1.json
    // ============================================================

    @Data
    @JsonIgnoreProperties(ignoreUnknown = true)
    public static class CanonicalLibrary {
        private String contract;
        private String content_library_version;
        private String status;
        private int entry_count;
        private Map<String, Integer> counts_by_planet;
        private Map<String, Integer> counts_by_source_type;
        private String naming_dictionary_version;
        private List<CanonicalEntry> entries;
    }

    @Data
    @JsonIgnoreProperties(ignoreUnknown = true)
    public static class CanonicalEntry {
        private String library_version;
        private String planet_domain;
        /** SIGN_CORE / NAKSHATRA_REFINEMENT */
        private String source_type;
        private String source_id;
        private String source_label_en;
        private String source_label_zh;
        private String canonical_summary_zh;
        /** SIGN_CORE 使用：5 个白名单 slot，每个含 core + behavior_cues */
        private Map<String, SlotCore> semantic_slots;
        /** NAKSHATRA_REFINEMENT 使用：当前行星白名单 slot 的子集，每个含 refinement + behavior_cues */
        private Map<String, SlotRefinement> refines_slots;
        private List<String> entry_forbidden_overreach;
        private String canonical_entry_id;
        private String lock_status;
    }

    @Data
    @JsonIgnoreProperties(ignoreUnknown = true)
    public static class SlotCore {
        private String core;
        private List<String> behavior_cues;
    }

    @Data
    @JsonIgnoreProperties(ignoreUnknown = true)
    public static class SlotRefinement {
        private String refinement;
        private List<String> behavior_cues;
    }

    // ============================================================
    // canonical_naming_dictionary_v1.json
    // ============================================================

    @Data
    @JsonIgnoreProperties(ignoreUnknown = true)
    public static class NamingDictionary {
        private String contract;
        private String version;
        private String status;
        private List<String> planet_ids;
        private List<SignEntry> signs;
        private List<NakEntry> nakshatras;
        private String content_library_version;
    }

    @Data
    @JsonIgnoreProperties(ignoreUnknown = true)
    public static class SignEntry {
        private int order;
        private String id;
        private String canonical_en;
        private String display_zh;
        private List<String> aliases;
    }

    @Data
    @JsonIgnoreProperties(ignoreUnknown = true)
    public static class NakEntry {
        private int order;
        private String id;
        private String canonical_en;
        private String display_zh;
        private List<String> aliases;
    }

    // ============================================================
    // canonical_lookup_index_v1.json
    // ============================================================

    @Data
    @JsonIgnoreProperties(ignoreUnknown = true)
    public static class LookupIndex {
        private String content_library_version;
        private String resolver_version;
        private String render_profile_version;
        private Map<String, List<String>> slots_by_planet;
        private Map<String, List<String>> allowed_patterns_by_planet;
        private Map<String, List<String>> scene_pool_by_planet;
        private Map<String, Map<String, String>> evidence_hint_by_planet_and_source_mode;
        private Map<String, Map<String, String>> lookup;
    }
}
