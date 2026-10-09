package cn.dct.zpback.nakshatra;

import cn.dct.zpback.constant.NakshatraRelationConstants;
import cn.dct.zpback.dto.nakshatra.NakshatraRegistries;
import cn.dct.zpback.dto.nakshatra.NakshatraRelationContent;
import cn.dct.zpback.entity.Chart;
import cn.dct.zpback.mapper.UserMapper;
import cn.dct.zpback.service.ChartService;
import cn.dct.zpback.service.VedicAstroService;
import cn.dct.zpback.service.nakshatra.NakshatraRelationException;
import cn.dct.zpback.service.nakshatra.NakshatraRelationService;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.*;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.anyMap;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * 星宿关系确定性引擎测试
 *
 * 覆盖 skill/docs/06 §3：27宿注册表、27 offset、729 全矩阵、正反对称、54 组业胎距离；
 * 以及 §4 Golden Cases G01—G05、名称一致性、同档案阻断。
 *
 * 不依赖数据库与排盘服务：档案用合成数据 + 直接注入月亮星宿名称驱动引擎。
 */
class NakshatraRelationEngineTest {

    private static final String USER = "user_test";

    private NakshatraRelationService service;
    private ChartService chartService;
    private VedicAstroService vedicAstroService;
    private final Map<String, Object> capturedParams = new HashMap<>();
    private List<NakshatraRegistries.NakshatraEntry> nakshatras;

    @BeforeEach
    void setUp() {
        ObjectMapper mapper = new ObjectMapper();
        chartService = mock(ChartService.class);
        capturedParams.clear();
        UserMapper userMapper = mock(UserMapper.class);
        vedicAstroService = mock(VedicAstroService.class);

        when(userMapper.selectById(anyString())).thenReturn(null);

        service = new NakshatraRelationService(mapper, chartService, userMapper, vedicAstroService);
        service.loadRegistries();

        nakshatras = loadNakshatraEntries(mapper);
        assertNotNull(nakshatras);

        // 任意 chartId 都能拿到一份合成档案，月亮星宿按 chartId 尾段编码（"c_a_05" → NK05）
        // 尾段不是数字时返回 null，避免在 when(...) 打桩阶段抛异常
        when(chartService.getChartById(anyString())).thenAnswer(inv -> {
            String id = inv.getArgument(0);
            Integer idx = parseIndex(id);
            return idx == null ? null : syntheticChart(id, idx);
        });
    }

    // ============================================================
    // §3.1 27星宿注册表
    // ============================================================

    @Test
    void nakshatraRegistry_isCanonical27() {
        assertEquals(27, nakshatras.size(), "必须为 27 宿，不含 Abhijit");

        int expectedStart = 0;
        for (int i = 0; i < 27; i++) {
            NakshatraRegistries.NakshatraEntry e = nakshatras.get(i);
            assertEquals(String.format("NK%02d", i + 1), e.getNakshatra_id());
            assertEquals(i + 1, e.getIndex());
            assertEquals(expectedStart, e.getStart_arcminutes_inclusive(), "区间必须连续无重叠");
            assertEquals(expectedStart + 800, e.getEnd_arcminutes_exclusive(), "每段 800 角分");
            expectedStart += 800;
        }
        assertEquals(NakshatraRelationConstants.TOTAL_ARCMINUTES, expectedStart, "必须覆盖 0～21600 角分");
    }

    @Test
    void nakshatraNames_p0Locked() {
        assertEquals("昴宿", nakshatras.get(2).getChinese(), "NK03 必须为昴宿");
        assertEquals("毕宿", nakshatras.get(3).getChinese(), "NK04 必须为毕宿");
        assertEquals("觜宿", nakshatras.get(4).getChinese(), "NK05 必须为觜宿");
        assertEquals("角宿", nakshatras.get(13).getChinese(), "NK14 必须为角宿");
        assertEquals("箕宿", nakshatras.get(19).getChinese(), "NK20 必须为箕宿");
    }

    // ============================================================
    // §3.3 / §3.4 / §3.5 全矩阵 + 正反对称 + 业胎
    // ============================================================

    @Test
    void fullMatrix_729() {
        int same = 0, karma = 0, other = 0;
        for (int a = 1; a <= 27; a++) {
            for (int b = 1; b <= 27; b++) {
                Map<String, Object> r = resolve(a, b);
                Map<String, Object> rel = relationship(r);

                int offset = (int) rel.get("forward_offset");
                assertEquals(Math.floorMod(b - a, 27), offset, "offset 公式必须为 (target-base+27)%27");

                assertNotNull(rel.get("family_code"));
                assertNotNull(rel.get("distance_code"));
                assertNotNull(rel.get("role_a_code"));
                assertNotNull(rel.get("role_b_code"));
                assertNotNull(rel.get("template_key"));

                String family = (String) rel.get("family_code");
                if (NakshatraRelationConstants.FAMILY_SAME.equals(family)) same++;
                else if (NakshatraRelationConstants.FAMILY_KARMA_WOMB.equals(family)) karma++;
                else other++;
            }
        }
        assertEquals(27, same, "命之星必须 27 组");
        assertEquals(54, karma, "业胎必须 54 组");
        assertEquals(648, other, "其余关系必须 648 组");
    }

    @Test
    void karmaWomb_alwaysNoneDistance() {
        for (int a = 1; a <= 27; a++) {
            for (int offset : new int[]{9, 18}) {
                int b = ((a - 1 + offset) % 27) + 1;
                Map<String, Object> rel = relationship(resolve(a, b));
                assertEquals(NakshatraRelationConstants.FAMILY_KARMA_WOMB, rel.get("family_code"));
                assertEquals(NakshatraRelationConstants.DISTANCE_NONE, rel.get("distance_code"),
                        "所有业胎距离必须为 NONE，不得出现近/中/远距离业胎");
                assertEquals("无距离业胎", rel.get("relation_display_name"));
            }
        }
    }

    @Test
    void reverseDirection_familyDistanceSame_rolesSwap() {
        for (int a = 1; a <= 27; a++) {
            for (int b = 1; b <= 27; b++) {
                if (a == b) continue;
                Map<String, Object> fwd = relationship(resolve(a, b));
                Map<String, Object> rev = relationship(resolve(b, a));

                assertEquals(fwd.get("family_code"), rev.get("family_code"), "交换 A/B 后关系族不变");
                assertEquals(fwd.get("distance_code"), rev.get("distance_code"), "交换 A/B 后距离不变");
                assertEquals(fwd.get("role_a_code"), rev.get("role_b_code"), "交换 A/B 后角色必须互换");
                assertEquals(fwd.get("role_b_code"), rev.get("role_a_code"), "交换 A/B 后角色必须互换");
            }
        }
    }

    // ============================================================
    // §4 Golden Cases
    // ============================================================

    @Test
    void goldenCase_01_and_02_ziJiao() {
        // G01：觜宿 NK05 → 角宿 NK14
        Map<String, Object> rel = relationship(resolve(5, 14));
        assertEquals(9, rel.get("forward_offset"));
        assertEquals("WOMB", rel.get("role_a_code"));
        assertEquals("KARMA", rel.get("role_b_code"));
        assertEquals("无距离业胎", rel.get("relation_display_name"));

        // G02：角宿 NK14 → 觜宿 NK05
        Map<String, Object> rel2 = relationship(resolve(14, 5));
        assertEquals(18, rel2.get("forward_offset"));
        assertEquals("KARMA", rel2.get("role_a_code"));
        assertEquals("WOMB", rel2.get("role_b_code"));
        assertEquals("无距离业胎", rel2.get("relation_display_name"));
    }

    @Test
    void goldenCase_03_sameNakshatra() {
        Map<String, Object> r = resolve(1, 1);
        Map<String, Object> rel = relationship(r);
        assertEquals("SAME", rel.get("family_code"));
        assertEquals("命之星关系", rel.get("relation_display_name"));
        assertEquals("命", rel.get("role_a_label"));
        assertEquals("命", rel.get("role_b_label"));
        assertEquals("MUTUAL", content(r).getInfluenceMode());
        assertNotNull(content(r).getMutualResonance(), "命之星必须展示「默契」模块");
        assertNotNull(content(r).getMutualBlindspot(), "命之星必须展示「一起卡住」模块");
        assertNull(content(r).getAToB(), "命之星不得伪造 A→B");
        assertNull(content(r).getBToA(), "命之星不得伪造 B→A");
    }

    @Test
    void goldenCase_04_and_05_louWei() {
        // G04：娄宿 NK01 → 胃宿 NK02
        Map<String, Object> r = relationship(resolve(1, 2));
        assertEquals("GLORY_KIN", r.get("family_code"));
        assertEquals("KIN", r.get("role_a_code"));
        assertEquals("GLORY", r.get("role_b_code"));
        assertEquals("NEAR", r.get("distance_code"));
        assertEquals("近距离荣亲", r.get("relation_display_name"));

        // G05：胃宿 NK02 → 娄宿 NK01
        Map<String, Object> r2 = relationship(resolve(2, 1));
        assertEquals("GLORY_KIN", r2.get("family_code"));
        assertEquals("GLORY", r2.get("role_a_code"));
        assertEquals("KIN", r2.get("role_b_code"));
        assertEquals("NEAR", r2.get("distance_code"));
        assertEquals("近距离荣亲", r2.get("relation_display_name"));
    }

    @Test
    void directional_hasBothDirections() {
        Map<String, Object> r = resolve(1, 2); // 近距离荣亲
        NakshatraRelationContent c = content(r);
        assertEquals("DIRECTIONAL", c.getInfluenceMode());
        assertNotNull(c.getAToB());
        assertNotNull(c.getBToA());
        assertNotNull(c.getAToBTitle(), "V2 方向标题必须存在");
        assertNotNull(c.getBToATitle(), "V2 方向标题必须存在");
        assertNull(c.getMutualResonance(), "DIRECTIONAL 时 mutual_resonance 必须为 null");
        assertNull(c.getMutualBlindspot(), "DIRECTIONAL 时 mutual_blindspot 必须为 null");
        assertNull(c.getMutualResonanceTitle());
        assertNull(c.getMutualBlindspotTitle());
    }

    @Test
    void directionalTitles_showNamesAndRoles() {
        // 张宿 NK11 → 角宿 NK14：offset 3 / 近距离安坏 / A坏 B安
        Map<String, Object> r = resolve(11, 14);
        NakshatraRelationContent c = content(r);
        Map<String, Object> pa = person(r, "person_a");
        Map<String, Object> pb = person(r, "person_b");
        String nameA = (String) pa.get("display_name");
        String nameB = (String) pb.get("display_name");

        assertEquals(nameA + "（坏）对 " + nameB + "（安）的影响", c.getAToBTitle());
        assertEquals(nameB + "（安）对 " + nameA + "（坏）的影响", c.getBToATitle());
        assertEquals(nameA + "（坏） × " + nameB + "（安）", c.getRoleMappingText());
        // 标题不得用箭头替代姓名 + 角色
        assertFalse(c.getAToBTitle().contains("→"));
    }

    @Test
    void mutualTitles_useTwoModules() {
        // 命之星：只输出「默契」与「一起卡住」，不输出双方向
        NakshatraRelationContent c = content(resolve(3, 3));
        assertEquals("MUTUAL", c.getInfluenceMode());
        assertNull(c.getAToB());
        assertNull(c.getBToA());
        assertNull(c.getAToBTitle());
        assertNull(c.getBToATitle());
        assertEquals(NakshatraRelationConstants.TITLE_MUTUAL_RESONANCE, c.getMutualResonanceTitle());
        assertEquals(NakshatraRelationConstants.TITLE_MUTUAL_BLINDSPOT, c.getMutualBlindspotTitle());
        assertNotNull(c.getMutualResonance());
        assertNotNull(c.getMutualBlindspot());
        // 命之星角色映射不重复标注同一角色
        assertFalse(c.getRoleMappingText().contains("（命）"));
    }

    @Test
    void familyExplainer_presentForEveryFamily() {
        for (int a = 1; a <= 27; a++) {
            NakshatraRelationContent c = content(resolve(a, 1));
            assertNotNull(c.getFamilyExplainer(), "每个 Family 都必须有解释文案");
            assertFalse(c.getFamilyExplainer().isEmpty());
        }
    }

    @Test
    void nameBinding_escapesHostileNicknames() {
        // 昵称含 < > {{ }} 时按纯文本原样绑定，不执行 HTML / 不解析模板
        when(chartService.getChartById(anyString())).thenAnswer(inv -> {
            String id = inv.getArgument(0);
            Integer idx = parseIndex(id);
            if (idx == null) return null;
            Chart ch = syntheticChart(id, idx);
            ch.setName("<script>{{name_a}}</script>");
            return ch;
        });
        NakshatraRelationContent c = content(resolve(1, 2));
        // A 侧昵称原样保留（含 {{name_a}} 字面量，属用户数据而非占位符）
        assertTrue(c.getSummary().contains("<script>{{name_a}}</script>"), "昵称必须原样保留为纯文本");
        // B 侧占位符已全部绑定
        assertFalse(c.getSummary().contains("{{name_b}}"), "B 侧占位符必须完成绑定");
    }

    @Test
    void nameBinding_allPlaceholdersResolvedInNormalCase() {
        for (int a = 1; a <= 27; a++) {
            for (int b = 1; b <= 27; b++) {
                NakshatraRelationContent c = content(resolve(a, b));
                for (String text : new String[]{c.getSummary(), c.getRelationshipTone(), c.getRelationshipKey(),
                        c.getAToB(), c.getBToA(), c.getMutualResonance(), c.getMutualBlindspot(),
                        c.getFamilyExplainer(), c.getFamilyBoundaryNote()}) {
                    if (text != null) {
                        assertFalse(text.contains("{{name_"), "普通昵称下不得残留占位符");
                    }
                }
            }
        }
    }

    @Test
    void retiredV1Fields_areGone() {
        JsonNode node = new ObjectMapper().valueToTree(resolve(5, 14));
        JsonNode contentNode = node.get("content");
        assertFalse(contentNode.has("mutual_influence"), "V2 已删除 mutual_influence");
        for (String key : new String[]{"role_mapping_text", "family_explainer", "family_boundary_note"}) {
            assertTrue(contentNode.has(key), "V2 content 缺少新字段: " + key);
        }
    }

    @Test
    void goldenCases_regression() {
        // G01/G02 觜宿(NK05) ↔ 角宿(NK14)
        assertGolden(5, 14, 9, "胎", "业", "NONE", "无距离业胎");
        assertGolden(14, 5, 18, "业", "胎", "NONE", "无距离业胎");
        // G06/G07 张宿(NK11) ↔ 角宿(NK14)：V2 上线阻断回归项
        assertGolden(11, 14, 3, "坏", "安", "NEAR", "近距离安坏");
        assertGolden(14, 11, 24, "安", "坏", "NEAR", "近距离安坏");
    }

    private void assertGolden(int a, int b, int offset, String roleA, String roleB,
                              String distance, String display) {
        Map<String, Object> rel = relationship(resolve(a, b));
        assertEquals(offset, rel.get("forward_offset"), "offset 不符");
        assertEquals(roleA, rel.get("role_a_label"), "A 角色不符");
        assertEquals(roleB, rel.get("role_b_label"), "B 角色不符");
        assertEquals(distance, rel.get("distance_code"), "距离不符");
        assertEquals(display, rel.get("relation_display_name"), "展示名不符");
    }

    @Test
    void contentNullFields_surviveNonNullJacksonConfig() {
        // 复现 application.yml 的 spring.jackson.default-property-inclusion=non_null
        ObjectMapper nonNull = new ObjectMapper();
        nonNull.setSerializationInclusion(JsonInclude.Include.NON_NULL);

        // DIRECTIONAL：命之星两模块为 null，但 schema 要求这些键必须存在
        JsonNode dirContent = nonNull.valueToTree(resolve(5, 14)).get("content");
        assertTrue(dirContent.has("a_to_b"), "DIRECTIONAL 必须出现 a_to_b");
        assertTrue(dirContent.has("b_to_a"), "DIRECTIONAL 必须出现 b_to_a");
        assertTrue(dirContent.has("mutual_resonance"), "schema required：即使为 null 也必须出现");
        assertTrue(dirContent.get("mutual_resonance").isNull());
        assertTrue(dirContent.has("mutual_blindspot") && dirContent.get("mutual_blindspot").isNull());

        // MUTUAL：a_to_b / b_to_a 为 null，同样必须出现
        JsonNode mutContent = nonNull.valueToTree(resolve(3, 3)).get("content");
        assertTrue(mutContent.has("a_to_b") && mutContent.get("a_to_b").isNull());
        assertTrue(mutContent.has("b_to_a") && mutContent.get("b_to_a").isNull());
        assertTrue(mutContent.has("mutual_resonance") && !mutContent.get("mutual_resonance").isNull());
        assertTrue(mutContent.has("mutual_blindspot") && !mutContent.get("mutual_blindspot").isNull());
    }

    @Test
    void responseMatchesSchemaShape() {
        ObjectMapper mapper = new ObjectMapper();
        JsonNode node = mapper.valueToTree(resolve(5, 14));

        for (String key : new String[]{"request_id", "status", "contract_version", "calculation_version",
                "content_version", "person_a", "person_b", "relationship", "content", "ai_handoff", "generated_at"}) {
            assertTrue(node.has(key), "顶层缺少 schema required 字段: " + key);
        }
        for (String key : new String[]{"profile_id", "display_name", "nakshatra_id", "nakshatra_english",
                "nakshatra_chinese", "role_code", "role_label"}) {
            assertTrue(node.get("person_a").has(key), "person 缺少 required 字段: " + key);
        }
        for (String key : new String[]{"forward_offset", "family_code", "family_label", "distance_code",
                "distance_label", "relation_display_name", "role_a_code", "role_a_label", "role_b_code",
                "role_b_label", "template_key"}) {
            assertTrue(node.get("relationship").has(key), "relationship 缺少 required 字段: " + key);
        }
        for (String key : new String[]{"content_mode", "influence_mode", "role_mapping_text", "family_explainer",
                "summary", "relationship_tone", "a_to_b_title", "a_to_b", "b_to_a_title", "b_to_a",
                "mutual_resonance_title", "mutual_resonance", "mutual_blindspot_title", "mutual_blindspot",
                "relationship_key", "family_boundary_note", "disclaimer_short"}) {
            assertTrue(node.get("content").has(key), "content 缺少 required 字段: " + key);
        }
        for (String key : new String[]{"target_mode", "source", "profile_a_id", "profile_b_id",
                "nakshatra_relation_context", "remaining_count", "suggested_questions"}) {
            assertTrue(node.get("ai_handoff").has(key), "ai_handoff 缺少 required 字段: " + key);
        }
    }

    // ============================================================
    // §6 阻断错误
    // ============================================================

    @Test
    void sameProfile_isBlocked() {
        NakshatraRelationException ex = assertThrows(NakshatraRelationException.class,
                () -> service.resolve(USER, "c_a_01", "c_a_01", "zh-CN", NakshatraRelationConstants.CONTRACT_VERSION));
        assertEquals(NakshatraRelationConstants.ERR_SAME_PROFILE, ex.getErrorCode());
        assertEquals(400, ex.getHttpStatus());
    }

    @Test
    void unsupportedContractVersion_isBlocked() {
        NakshatraRelationException ex = assertThrows(NakshatraRelationException.class,
                () -> service.resolve(USER, "c_a_01", "c_b_02", "zh-CN", "9.9.9"));
        assertEquals(NakshatraRelationConstants.ERR_VERSION_NOT_SUPPORTED, ex.getErrorCode());
        assertEquals(409, ex.getHttpStatus());
    }

    @Test
    void responseCarriesThreeVersions() {
        Map<String, Object> r = resolve(1, 2);
        assertEquals(NakshatraRelationConstants.CONTRACT_VERSION, r.get("contract_version"));
        assertEquals(NakshatraRelationConstants.CALCULATION_VERSION, r.get("calculation_version"));
        assertEquals(NakshatraRelationConstants.CONTENT_VERSION, r.get("content_version"));
        assertEquals("READY", r.get("status"));
    }

    @Test
    void extractMoon_handlesVargaChartsLayer() {
        // get_varga_charts 返回 { "vargas": { "D1": { "planets": { "Moon": {...} } } } }
        Chart vargaChart = new Chart();
        vargaChart.setChartId("c_varga");
        vargaChart.setUserId(USER);
        vargaChart.setName("varga");
        vargaChart.setChartData("{\"vargas\":{\"D1\":{\"planets\":{\"Moon\":{\"nakshatra\":\"Chitra\"}}}}}");
        when(chartService.getChartById("c_varga")).thenReturn(vargaChart);

        // 角宿 NK14 → 觜宿 NK05 = offset 18 → 无距离业胎，A业 B胎
        Map<String, Object> rel = relationship(service.resolve(USER, "c_varga", "c_b_5", "zh-CN", NakshatraRelationConstants.CONTRACT_VERSION));
        assertEquals(18, rel.get("forward_offset"));
        assertEquals("KARMA_WOMB", rel.get("family_code"));
        assertEquals("KARMA", rel.get("role_a_code"));
        assertEquals("WOMB", rel.get("role_b_code"));
        assertEquals("NONE", rel.get("distance_code"));
    }

    @Test
    void missingCoordinates_stillComputesMoonWithPlaceholder() {
        Chart noCoord = new Chart();
        noCoord.setChartId("c_nocoord");
        noCoord.setUserId(USER);
        noCoord.setName("无坐标");
        noCoord.setBirthDate("1997-09-18");
        noCoord.setBirthTime("12:00");
        // chartData 为空 + 经纬度为 null：月亮黄经与地点无关，应使用中性占位坐标继续计算
        when(chartService.getChartById("c_nocoord")).thenReturn(noCoord);
        when(vedicAstroService.getVargaCharts(anyMap())).thenAnswer(inv -> {
            capturedParams.putAll(inv.getArgument(0));
            return vargaResponse("Chitra"); // NK14 角宿
        });

        // 角宿 NK14 → 觜宿 NK05 = offset 18 → 无距离业胎，A业 B胎
        Map<String, Object> rel = relationship(service.resolve(USER, "c_nocoord", "c_b_5", "zh-CN", NakshatraRelationConstants.CONTRACT_VERSION));
        assertEquals(18, rel.get("forward_offset"));
        assertEquals("KARMA_WOMB", rel.get("family_code"));
        assertEquals("NONE", rel.get("distance_code"));

        // 排盘服务 Schema 要求 latitude/longitude 必填且为数字，不得传 null
        assertNotNull(capturedParams.get("latitude"), "坐标缺失时必须传占位值");
        assertNotNull(capturedParams.get("longitude"), "坐标缺失时必须传占位值");
        assertEquals(1997, capturedParams.get("year"), "year 必须为 integer 而非字符串");
        assertEquals(9, capturedParams.get("month"));
        assertEquals(18, capturedParams.get("day"));
        assertEquals(12, capturedParams.get("hour"));
        assertEquals(0, capturedParams.get("minute"));
    }

    @Test
    void missingBirthDateTime_yieldsNakshatraUnavailable() {
        Chart noDate = new Chart();
        noDate.setChartId("c_nodate");
        noDate.setUserId(USER);
        noDate.setName("无出生时间");
        // 出生日期/时间缺失时月亮位置无法确定，必须返回稳定错误
        when(chartService.getChartById("c_nodate")).thenReturn(noDate);

        NakshatraRelationException ex = assertThrows(NakshatraRelationException.class,
                () -> service.resolve(USER, "c_nodate", "c_b_5", "zh-CN", NakshatraRelationConstants.CONTRACT_VERSION));
        assertEquals(NakshatraRelationConstants.ERR_NAKSHATRA_UNAVAILABLE, ex.getErrorCode());
        assertEquals(422, ex.getHttpStatus());
    }

    @Test
    void unknownNakshatraName_fallsBackToLongitude() {
        // 排盘服务返回一个注册表里没有的拼写时，用 sign + 度数回退归宿
        // 金牛座 0.5° → 绝对黄经 30.5° → 1830 角分 → 第 3 宿（昴宿 NK03）
        Chart c = new Chart();
        c.setChartId("c_fallback");
        c.setUserId(USER);
        c.setName("回退");
        c.setChartData("{\"D1\":{\"planets\":{\"Moon\":{\"nakshatra\":\"UnknownSpelling\","
                + "\"sign\":\"Taurus\",\"pos\":{\"dec_deg\":0.5}}}}}");
        when(chartService.getChartById("c_fallback")).thenReturn(c);

        // 昴宿 NK03 → 胃宿 NK02 = offset 26 → 近距离荣亲，A荣 B亲
        Map<String, Object> rel = relationship(service.resolve(USER, "c_fallback", "c_b_2", "zh-CN", NakshatraRelationConstants.CONTRACT_VERSION));
        assertEquals(26, rel.get("forward_offset"));
        assertEquals("GLORY_KIN", rel.get("family_code"));
        assertEquals("GLORY", rel.get("role_a_code"));
        assertEquals("KIN", rel.get("role_b_code"));
    }

    // ============================================================
    // 辅助
    // ============================================================

    private Map<String, Object> resolve(int baseIdx, int targetIdx) {
        // 同一星宿必须用两份不同档案（命之星 = 同宿不同档案，不得被 SAME_PROFILE 阻断）
        String a = "c_a_" + baseIdx;
        String b = "c_b_" + targetIdx;
        return service.resolve(USER, a, b, "zh-CN", NakshatraRelationConstants.CONTRACT_VERSION);
    }

    @SuppressWarnings("unchecked")
    private Map<String, Object> relationship(Map<String, Object> r) {
        return (Map<String, Object>) r.get("relationship");
    }

    @SuppressWarnings("unchecked")
    private Map<String, Object> person(Map<String, Object> r, String key) {
        return (Map<String, Object>) r.get(key);
    }

    private NakshatraRelationContent content(Map<String, Object> r) {
        return (NakshatraRelationContent) r.get("content");
    }

    private Map<String, Object> vargaResponse(String moonNakshatra) {
        Map<String, Object> moon = new HashMap<>();
        moon.put("nakshatra", moonNakshatra);
        Map<String, Object> planets = new HashMap<>();
        planets.put("Moon", moon);
        Map<String, Object> d1 = new HashMap<>();
        d1.put("planets", planets);
        Map<String, Object> vargas = new HashMap<>();
        vargas.put("D1", d1);
        Map<String, Object> root = new HashMap<>();
        root.put("vargas", vargas);
        return root;
    }

    private Integer parseIndex(String chartId) {        try {
            return Integer.parseInt(chartId.substring(chartId.lastIndexOf('_') + 1));
        } catch (Exception e) {
            return null;
        }
    }

    private Chart syntheticChart(String chartId, int nakshatraIndex) {        NakshatraRegistries.NakshatraEntry e = nakshatras.get(nakshatraIndex - 1);
        String json = "{\"D1\":{\"planets\":{\"Moon\":{\"nakshatra\":\"" + e.getEnglish()
                + "\",\"sign\":\"Aries\"}}}}";
        Chart chart = new Chart();
        chart.setChartId(chartId);
        chart.setUserId(USER);
        chart.setName("测试" + nakshatraIndex);
        chart.setChartData(json);
        return chart;
    }

    private List<NakshatraRegistries.NakshatraEntry> loadNakshatraEntries(ObjectMapper mapper) {
        try (var in = new org.springframework.core.io.ClassPathResource(
                NakshatraRelationConstants.RES_NAKSHATRA_REGISTRY).getInputStream()) {
            return mapper.readValue(in, NakshatraRegistries.NakshatraRegistry.class).getEntries();
        } catch (Exception e) {
            return null;
        }
    }
}
