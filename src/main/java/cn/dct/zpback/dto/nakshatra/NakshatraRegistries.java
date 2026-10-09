package cn.dct.zpback.dto.nakshatra;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import lombok.Data;

import java.util.List;
import java.util.Map;

/**
 * 星宿关系规则资产的反序列化结构
 *
 * 与 skill/assets 下三份机器可读资产一一对应：
 * - nakshatra_registry.json
 * - relationship_offset_registry.json
 * - copy_registry_zh-CN.json
 *
 * 字段名与 JSON 完全一致（snake_case），Jackson 默认即可正确映射。
 * 这些资产是运行时唯一事实源，禁止在代码里另写一套关系表。
 */
public final class NakshatraRegistries {

    private NakshatraRegistries() {}

    // ============================================================
    // nakshatra_registry.json
    // ============================================================

    @Data
    @JsonIgnoreProperties(ignoreUnknown = true)
    public static class NakshatraRegistry {
        private String registry_version;
        private String ayanamsa;
        private String source_point;
        private int count;
        private String boundary_policy;
        private List<NakshatraEntry> entries;
    }

    @Data
    @JsonIgnoreProperties(ignoreUnknown = true)
    public static class NakshatraEntry {
        private String nakshatra_id;
        private int index;
        private String english;
        private String chinese;
        private int start_arcminutes_inclusive;
        private int end_arcminutes_exclusive;
        private String degree_range_display;
    }

    // ============================================================
    // relationship_offset_registry.json
    // ============================================================

    @Data
    @JsonIgnoreProperties(ignoreUnknown = true)
    public static class OffsetRegistry {
        private String calculation_version;
        private String direction;
        private String formula;
        private String karma_womb_distance_override;
        private List<OffsetRule> entries;
    }

    @Data
    @JsonIgnoreProperties(ignoreUnknown = true)
    public static class OffsetRule {
        private int forward_offset;
        private String family_code;
        private String family_label;
        private String distance_code;
        private String distance_label;
        private String role_a_code;
        private String role_a_label;
        private String role_b_code;
        private String role_b_label;
        private String relation_display_name;
        private String template_key;
    }

    // ============================================================
    // copy_registry_zh-CN.json
    // ============================================================

    @Data
    @JsonIgnoreProperties(ignoreUnknown = true)
    public static class CopyRegistry {
        private String content_version;
        private String contract_version;
        private String locale;
        private String content_mode;
        private int template_count;
        /** family_code → Family 解释（6 族） */
        private Map<String, String> family_explainers;
        /** family_code → 边界提示（可为空值，运行时非空才展示） */
        private Map<String, String> family_boundary_notes;
        /** 统一免责声明（V2 起不再按条目携带） */
        private String disclaimer_short;
        private List<CopyTemplate> entries;
    }

    @Data
    @JsonIgnoreProperties(ignoreUnknown = true)
    public static class CopyTemplate {
        private String template_key;
        private int forward_offset;
        private String family_code;
        private String distance_code;
        private String role_a_code;
        private String role_a_label;
        private String role_b_code;
        private String role_b_label;
        private String influence_mode;
        private CopyContent content;
    }

    @Data
    @JsonIgnoreProperties(ignoreUnknown = true)
    public static class CopyContent {
        private String summary;
        private String relationship_tone;
        /** DIRECTIONAL：A→B；MUTUAL 时为 null */
        private String a_to_b;
        /** DIRECTIONAL：B→A；MUTUAL 时为 null */
        private String b_to_a;
        /** MUTUAL（命之星）：默契处；DIRECTIONAL 时为 null */
        private String mutual_resonance;
        /** MUTUAL（命之星）：一起卡住处；DIRECTIONAL 时为 null */
        private String mutual_blindspot;
        private String relationship_key;
    }
}
