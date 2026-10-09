package cn.dct.zpback.synbasic;

import cn.dct.zpback.constant.SynBasicConstants;
import cn.dct.zpback.engine.synbasic.PairComparator;
import cn.dct.zpback.engine.synbasic.PersonDomainResolver;
import cn.dct.zpback.engine.synbasic.SynBasicAssets;
import cn.dct.zpback.engine.synbasic.SynBasicValidator;
import cn.dct.zpback.service.synbasic.SynBasicInteractionService;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

/**
 * 双人合盘基础互动（156 Canonical Library）测试
 *
 * 覆盖 06_DEVELOPER_INTEGRATION_CHECKLIST：
 * 156 库完整性、stable ID 归一化、作用域 lookup、source_mode 分流、
 * light_tip 融合、Evidence Hint、viewer 三种视角、Claim Firewall、四模块 scene 不重复。
 */
class SynBasicInteractionTest {

    private SynBasicInteractionService service;
    private SynBasicAssets assets;

    @BeforeEach
    void setUp() {
        ObjectMapper mapper = new ObjectMapper();
        assets = new SynBasicAssets();
        ReflectionTestUtils.setField(assets, "objectMapper", mapper);
        assets.load();

        PersonDomainResolver resolver = new PersonDomainResolver();
        ReflectionTestUtils.setField(resolver, "assets", assets);
        PairComparator comparator = new PairComparator();
        ReflectionTestUtils.setField(comparator, "assets", assets);
        SynBasicValidator validator = new SynBasicValidator();

        service = new SynBasicInteractionService();
        ReflectionTestUtils.setField(service, "assets", assets);
        ReflectionTestUtils.setField(service, "resolver", resolver);
        ReflectionTestUtils.setField(service, "comparator", comparator);
        ReflectionTestUtils.setField(service, "validator", validator);
    }

    // ============================================================
    // 库完整性（QA 01）
    // ============================================================

    @Test
    void library_has156EntriesWithCorrectSplit() {
        assertEquals(156, assets.getLibrary().getEntries().size());
        assertEquals(48, assets.getLibrary().getCounts_by_source_type().get("SIGN_CORE").intValue());
        assertEquals(108, assets.getLibrary().getCounts_by_source_type().get("NAKSHATRA_REFINEMENT").intValue());
        for (String planet : SynBasicConstants.PLANETS) {
            assertEquals(39, assets.getLibrary().getCounts_by_planet().get(planet).intValue(),
                    planet + " 必须有 39 条（12 Sign + 27 Nak）");
        }
    }

    @Test
    void slotWhiteList_matchesContract() {
        assertEquals(Arrays.asList("emotional_need", "expression_style", "reassurance_channel",
                "stress_response", "repair_preference"), assets.slotsOf("MOON"));
        assertEquals(Arrays.asList("affection_expression", "appreciation_channel", "attraction_cue",
                "closeness_style", "cooling_trigger"), assets.slotsOf("VENUS"));
        assertEquals(Arrays.asList("initiation_style", "pace_preference", "execution_style",
                "obstacle_response", "friction_response"), assets.slotsOf("MARS"));
        assertEquals(Arrays.asList("information_focus", "thought_processing", "expression_style",
                "clarity_requirement", "misunderstanding_trigger"), assets.slotsOf("MERCURY"));
    }

    // ============================================================
    // 命名（A1.3 Hard Assertions）
    // ============================================================

    @Test
    void namingDictionary_hardAssertions() {
        assertEquals("虚宿", assets.nakLabelZh("NAK_23_DHANISHTA"));
        assertEquals("斗宿", assets.nakLabelZh("NAK_21_UTTARA_ASHADHA"));
        assertEquals("女宿", assets.nakLabelZh("NAK_22_SHRAVANA"));
        assertNotEquals("斗宿", assets.nakLabelZh("NAK_23_DHANISHTA"), "Dhanishta 绝不能渲染为斗宿");
    }

    @Test
    void legacyLabelConflict_stableIdWins() {
        // 旧 label "斗宿" 与 stable ID NAK_23_DHANISHTA 冲突时，ID 胜出并回绑 虚宿
        assertEquals("NAK_23_DHANISHTA", assets.normalizeNakId("NAK_23_DHANISHTA"));
        assertEquals("NAK_23_DHANISHTA", assets.normalizeNakId("Dhanishta"));
        assertEquals("NAK_23_DHANISHTA", assets.normalizeNakId("dhanishta"));
        // 中文显示名也可反解
        assertEquals("NAK_23_DHANISHTA", assets.normalizeNakId("虚宿"));
        // legacy 拼写别名
        assertEquals("NAK_01_ASHWINI", assets.normalizeNakId("Aswini"));
        assertEquals("SIGN_SAGITTARIUS", assets.normalizeSignId("Saggitarius"));
        // 不做 fuzzy：无法解析返回 null
        assertNull(assets.normalizeNakId("DhanishtaXX"));
    }

    // ============================================================
    // Resolver source_mode 分流
    // ============================================================

    @Test
    void sourceMode_signNakshatra_whenRefinementApplied() {
        Map<String, Object> r = resolve(person("SIGN_LEO", "NAK_11_PURVA_PHALGUNI"),
                person("SIGN_AQUARIUS", "NAK_24_SHATABHISHA"));
        JsonNode moon = module(r, "moon");
        assertEquals("READY", moon.get("module_status").asText());
        assertEquals("SIGN_NAKSHATRA", moon.get("person_a").get("placement").get("source_mode").asText());
        assertNull(moon.get("person_a").get("degradation_reason").isNull() ? null : "notnull");
        // SIGN_NAKSHATRA 必须可追溯到至少 1 Sign + 1 Nak basis
        List<String> basis = toStrings(moon.get("person_a").get("semantic_basis_ids"));
        assertTrue(basis.stream().anyMatch(b -> b.contains("SIGN_LEO")), "缺 Sign Core basis");
        assertTrue(basis.stream().anyMatch(b -> b.contains("NAK_11_PURVA_PHALGUNI")), "缺 Nak Refinement basis");
    }

    @Test
    void sourceMode_signOnly_whenNakshatraDataMissing() {
        Map<String, Object> r = resolve(person("SIGN_LEO", null), person("SIGN_AQUARIUS", null));
        JsonNode moon = module(r, "moon");
        assertEquals("SIGN_ONLY", moon.get("person_a").get("placement").get("source_mode").asText());
        assertEquals("NAKSHATRA_DATA_MISSING", moon.get("person_a").get("degradation_reason").asText());
        // 降级时不应出现 NAK basis
        List<String> basis = toStrings(moon.get("person_a").get("semantic_basis_ids"));
        assertTrue(basis.stream().noneMatch(b -> b.contains("NAK_")), "SIGN_ONLY 不得出现 Nak basis");
    }

    @Test
    void sourceMode_signOnly_whenNakshatraLibraryMissing_butLabelStillShown() {
        // 原始星宿存在、但名称无法解析成 stable ID → Library missing，UI 仍应显示真实星宿名
        Map<String, Object> r = resolve(person("SIGN_LEO", "某某宿"), person("SIGN_AQUARIUS", "NAK_24_SHATABHISHA"));
        JsonNode pa = module(r, "moon").get("person_a");
        assertEquals("SIGN_ONLY", pa.get("placement").get("source_mode").asText());
        assertEquals("NAKSHATRA_LIBRARY_MISSING", pa.get("degradation_reason").asText());
        assertEquals("某某宿", pa.get("placement").get("nakshatra_label_zh").asText(),
                "Library 缺失时仍显示真实星宿名");
    }

    @Test
    void sourceMode_dataInsufficient_whenSignMissing() {
        Map<String, Object> r = resolve(person(null, "NAK_11_PURVA_PHALGUNI"),
                person("SIGN_AQUARIUS", "NAK_24_SHATABHISHA"));
        JsonNode moon = module(r, "moon");
        assertEquals("UNAVAILABLE", moon.get("module_status").asText());
        JsonNode pa = moon.get("person_a");
        assertEquals("DATA_INSUFFICIENT", pa.get("placement").get("source_mode").asText());
        assertEquals("SIGN_DATA_MISSING", pa.get("degradation_reason").asText());
        // DATA_INSUFFICIENT 不得伪造 basis
        assertEquals(0, pa.get("semantic_basis_ids").size());
        // UNAVAILABLE 必须为 null Pair + 有 fallback
        assertTrue(moon.get("pair_interaction").isNull());
        assertFalse(moon.get("fallback_message").isNull());
    }

    @Test
    void sourceMode_dataInsufficient_whenSignLibraryMissing() {
        Map<String, Object> r = resolve(person("SIGN_ATLANTIS", "NAK_11_PURVA_PHALGUNI"),
                person("SIGN_AQUARIUS", "NAK_24_SHATABHISHA"));
        JsonNode pa = module(r, "moon").get("person_a");
        assertEquals("DATA_INSUFFICIENT", pa.get("placement").get("source_mode").asText());
        assertEquals("SIGN_LIBRARY_MISSING", pa.get("degradation_reason").asText());
    }

    @Test
    void moduleStatus_degraded_whenOneSideSignOnly() {
        Map<String, Object> r = resolve(person("SIGN_LEO", "NAK_11_PURVA_PHALGUNI"),
                person("SIGN_AQUARIUS", null));
        JsonNode moon = module(r, "moon");
        assertEquals("DEGRADED", moon.get("module_status").asText());
        assertFalse(moon.get("pair_interaction").isNull(), "DEGRADED 仍必须存在 Pair Interaction");
        assertTrue(moon.get("fallback_message").isNull());
    }

    // ============================================================
    // Pair / Renderer / light_tip
    // ============================================================

    @Test
    void pairInteraction_hasAllRequiredFieldsAndFusedLightTip() {
        Map<String, Object> r = resolve(person("SIGN_LEO", "NAK_11_PURVA_PHALGUNI"),
                person("SIGN_AQUARIUS", "NAK_24_SHATABHISHA"));
        JsonNode pair = module(r, "moon").get("pair_interaction");
        for (String k : new String[]{"primary_pattern", "secondary_pattern", "basis_a", "basis_b",
                "scene_type", "rendered_copy", "light_tip"}) {
            assertTrue(pair.has(k), "pair_interaction 缺字段: " + k);
        }
        assertFalse(pair.get("basis_a").isEmpty(), "basis_a 至少 1 个");
        assertFalse(pair.get("basis_b").isEmpty(), "basis_b 至少 1 个");
        // light_tip 若存在必须已融合进 rendered_copy（UI 不再单独渲染）
        if (!pair.get("light_tip").isNull()) {
            String tip = pair.get("light_tip").asText();
            assertTrue(pair.get("rendered_copy").asText().contains(tip),
                    "non-null light_tip 必须融合进 rendered_copy");
        }
    }

    @Test
    void patterns_alwaysWithinAllowedSet() {
        for (String planet : SynBasicConstants.PLANETS) {
            List<String> allowed = assets.allowedPatternsOf(planet);
            for (String signA : new String[]{"SIGN_LEO", "SIGN_ARIES", "SIGN_CAPRICORN"}) {
                for (String signB : new String[]{"SIGN_AQUARIUS", "SIGN_CANCER", "SIGN_LIBRA"}) {
                    Map<String, Object> r = resolve(person(signA, null), person(signB, null));
                    JsonNode pair = module(r, SynBasicConstants.MODULE_KEY_BY_PLANET.get(planet))
                            .get("pair_interaction");
                    if (pair == null || pair.isNull()) {
                        continue;
                    }
                    assertTrue(allowed.contains(pair.get("primary_pattern").asText()),
                            planet + " primary_pattern 越界: " + pair.get("primary_pattern").asText());
                    if (!pair.get("secondary_pattern").isNull()) {
                        assertTrue(allowed.contains(pair.get("secondary_pattern").asText()),
                                planet + " secondary_pattern 越界");
                    }
                    assertTrue(assets.scenePoolOf(planet).contains(pair.get("scene_type").asText()),
                            planet + " scene_type 必须落在 scene pool 内");
                }
            }
        }
    }

    @Test
    void sameSign_isAlignmentPattern() {
        Map<String, Object> r = resolve(person("SIGN_LEO", null), person("SIGN_LEO", null));
        JsonNode pair = module(r, "moon").get("pair_interaction");
        assertEquals("SIMILAR_NEED", pair.get("primary_pattern").asText());
    }

    // ============================================================
    // Viewer Perspective / relationship_type
    // ============================================================

    @Test
    void viewerPerspective_changesBlockTitlesOnly() {
        Map<String, Object> base = new HashMap<>();
        base.put("person_a", person("SIGN_LEO", "NAK_11_PURVA_PHALGUNI"));
        base.put("person_b", person("SIGN_AQUARIUS", "NAK_24_SHATABHISHA"));

        Map<String, Object> reqA = new HashMap<>(base);
        reqA.put("viewer_perspective", "VIEWER_IS_A");
        JsonNode mA = module(service.resolve((Map<String, Object>) reqA.get("person_a"),
                (Map<String, Object>) reqA.get("person_b"), null, "VIEWER_IS_A"), "moon");

        Map<String, Object> reqB = new HashMap<>(base);
        reqB.put("viewer_perspective", "VIEWER_IS_B");
        JsonNode mB = module(service.resolve((Map<String, Object>) reqB.get("person_a"),
                (Map<String, Object>) reqB.get("person_b"), null, "VIEWER_IS_B"), "moon");

        assertEquals("你在情绪上更需要什么", mA.get("block_titles").get("a").asText());
        assertEquals("TA 在情绪上更需要什么", mA.get("block_titles").get("b").asText());
        // VIEWER_IS_B 时可见标题互换，但技术 A/B 身份不变
        assertEquals("TA 在情绪上更需要什么", mB.get("block_titles").get("a").asText());
        assertEquals("你在情绪上更需要什么", mB.get("block_titles").get("b").asText());
        assertEquals(mA.get("person_a").get("placement").get("sign_id").asText(),
                mB.get("person_a").get("placement").get("sign_id").asText(), "技术 A 身份不得改变");
    }

    @Test
    void observerPerspective_usesDisplayNames() {
        Map<String, Object> pa = person("SIGN_LEO", "NAK_11_PURVA_PHALGUNI");
        pa.put("display_name", "若岚");
        Map<String, Object> pb = person("SIGN_AQUARIUS", "NAK_24_SHATABHISHA");
        pb.put("display_name", "陈舟");
        JsonNode moon = module(service.resolve(pa, pb, null, "OBSERVER"), "moon");
        assertEquals("若岚在情绪上更需要什么", moon.get("block_titles").get("a").asText());
        assertEquals("陈舟在情绪上更需要什么", moon.get("block_titles").get("b").asText());
    }

    @Test
    void relationshipType_null_staysNullAndNotRomanticized() {
        Map<String, Object> r = resolve(person("SIGN_LEO", null), person("SIGN_AQUARIUS", null));
        assertTrue(((JsonNode) null) == null || r.get("pair") != null);
        ObjectMapper mapper = new ObjectMapper();
        JsonNode node = mapper.valueToTree(r);
        assertTrue(node.get("pair").get("relationship_type").isNull(), "缺失 relationship_type 必须保持 null");
        // 不得自动恋爱化
        String all = node.toString();
        assertFalse(all.contains("恋爱"), "relationship_type 缺失时不得自动恋爱化");
    }

    // ============================================================
    // 四模块 Cross QA / Claim Firewall / Schema
    // ============================================================

    @Test
    void fourModules_produceDistinctPrimaryScenes() {
        Map<String, Object> r = resolve(
                personAll("SIGN_LEO", "SIGN_GEMINI", "SIGN_CAPRICORN", "SIGN_ARIES"),
                personAll("SIGN_AQUARIUS", "SIGN_CAPRICORN", "SIGN_LIBRA", "SIGN_CANCER"));
        List<String> scenes = new ArrayList<>();
        for (String planet : SynBasicConstants.PLANETS) {
            JsonNode pair = module(r, SynBasicConstants.MODULE_KEY_BY_PLANET.get(planet)).get("pair_interaction");
            if (pair != null && !pair.isNull()) {
                scenes.add(pair.get("scene_type").asText());
            }
        }
        assertEquals(scenes.size(), scenes.stream().distinct().count(), "四模块 Primary scene 不得重复");
    }

    @Test
    void claimFirewall_noOverallRelationshipConclusion() {
        Map<String, Object> r = resolve(
                personAll("SIGN_LEO", "SIGN_GEMINI", "SIGN_CAPRICORN", "SIGN_ARIES"),
                personAll("SIGN_AQUARIUS", "SIGN_CAPRICORN", "SIGN_LIBRA", "SIGN_CANCER"));
        String all = new ObjectMapper().valueToTree(r).toString();
        for (String forbidden : SynBasicConstants.FORBIDDEN_OUTPUT_PATTERNS) {
            assertFalse(all.contains(forbidden), "输出命中禁止词: " + forbidden);
        }
    }

    @Test
    void response_matchesSchemaShape() {
        Map<String, Object> r = resolve(
                personAll("SIGN_LEO", "SIGN_GEMINI", "SIGN_CAPRICORN", "SIGN_ARIES"),
                personAll("SIGN_AQUARIUS", "SIGN_CAPRICORN", "SIGN_LIBRA", "SIGN_CANCER"));
        JsonNode node = new ObjectMapper().valueToTree(r);
        for (String k : new String[]{"schema_version", "content_library_version", "render_profile_version",
                "pair", "viewer_perspective", "modules", "deep_report_bridge"}) {
            assertTrue(node.has(k), "顶层缺字段: " + k);
        }
        assertEquals("1.1.2", node.get("schema_version").asText());
        assertEquals("syn-chart-basic-interaction-156-1.0.0", node.get("content_library_version").asText());
        assertEquals("SYN_CHART_BASIC_INTERACTION_RENDER_V1.1", node.get("render_profile_version").asText());
        for (String mk : new String[]{"moon", "venus", "mars", "mercury"}) {
            JsonNode m = node.get("modules").get(mk);
            for (String k : new String[]{"planet", "module_status", "ui_title", "entry_label",
                    "block_titles", "person_a", "person_b", "pair_interaction", "fallback_message"}) {
                assertTrue(m.has(k), mk + " 缺字段: " + k);
            }
            for (String side : new String[]{"person_a", "person_b"}) {
                JsonNode p = m.get(side);
                for (String k : new String[]{"placement", "degradation_reason", "semantic_basis_ids", "rendered_copy"}) {
                    assertTrue(p.has(k), mk + "." + side + " 缺字段: " + k);
                }
                for (String k : new String[]{"planet", "sign_id", "sign_label_zh",
                        "nakshatra_id", "nakshatra_label_zh", "source_mode"}) {
                    assertTrue(p.get("placement").has(k), mk + "." + side + ".placement 缺字段: " + k);
                }
            }
        }
        for (String k : new String[]{"title", "description", "cta_label", "route_key"}) {
            assertTrue(node.get("deep_report_bridge").has(k), "deep_report_bridge 缺字段: " + k);
        }
    }

    @Test
    void renderedCopy_neverMentionsNakshatraName() {
        Map<String, Object> r = resolve(
                personAll("SIGN_LEO", "SIGN_GEMINI", "SIGN_CAPRICORN", "SIGN_ARIES"),
                personAll("SIGN_AQUARIUS", "SIGN_CAPRICORN", "SIGN_LIBRA", "SIGN_CANCER"));
        String all = new ObjectMapper().valueToTree(r).toString();
        assertFalse(all.contains("宿会让"), "正文不得输出「XX宿会让你……」");
        assertFalse(all.contains("因为你是"), "正文不得显式归因到星宿");
    }

    // ============================================================
    // helpers
    // ============================================================

    @SuppressWarnings("unchecked")
    private Map<String, Object> resolve(Map<String, Object> a, Map<String, Object> b) {
        return service.resolve(a, b, null, "VIEWER_IS_A");
    }

    private JsonNode module(Map<String, Object> r, String key) {
        return new ObjectMapper().valueToTree(r).get("modules").get(key);
    }

    private List<String> toStrings(JsonNode arr) {
        List<String> list = new ArrayList<>();
        arr.forEach(n -> list.add(n.asText()));
        return list;
    }

    /** 单行星 person：sign/nak 为 null 时字段缺省 */
    private Map<String, Object> person(String sign, String nak) {
        Map<String, Object> p = new HashMap<>();
        p.put("subject_id", "P");
        p.put("display_name", "测试");
        p.put("moon", planet(sign, nak));
        return p;
    }

    /** 四行星 person */
    private Map<String, Object> personAll(String moon, String venus, String mars, String mercury) {
        Map<String, Object> p = new HashMap<>();
        p.put("subject_id", "P");
        p.put("display_name", "测试");
        p.put("moon", planet(moon, null));
        p.put("venus", planet(venus, null));
        p.put("mars", planet(mars, null));
        p.put("mercury", planet(mercury, null));
        return p;
    }

    private Map<String, Object> planet(String sign, String nak) {
        Map<String, Object> m = new LinkedHashMap<>();
        if (sign != null) {
            m.put("sign", sign);
        }
        if (nak != null) {
            m.put("nakshatra", nak);
        }
        return m;
    }
}
