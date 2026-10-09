package cn.dct.zpback.daily;

import cn.dct.zpback.constant.EphemerisNameNormalizer;
import cn.dct.zpback.engine.daily.DailyObservationAssets;
import cn.dct.zpback.engine.daily.VedicPlanetaryInfluenceEngine;
import cn.dct.zpback.engine.daily.VedicPlanetaryInfluenceEngine.HouseInfluence;
import cn.dct.zpback.engine.daily.VedicPlanetaryInfluenceEngine.PlanetPos;
import cn.dct.zpback.engine.daily.VedicPlanetaryInfluenceEngine.PlanetRelation;
import cn.dct.zpback.service.VedicAstroService;
import cn.dct.zpback.service.daily.DailyPersonalScoreService;
import cn.dct.zpback.service.daily.MoonDailyTimelineService;
import cn.dct.zpback.service.daily.PublicObservationService;
import cn.dct.zpback.service.daily.TodayObservationService;
import cn.dct.zpback.service.daily.Top3ImpactService;
import cn.dct.zpback.service.daily.TransitSnapshotService;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

/**
 * 今日观星 + 公共印占行星影响引擎测试
 *
 * 覆盖：influence positions、P2H 公式、conjunction/graha_drishti 规则、
 * degree bands、跨盘 relative_position、四领域评分口径、Top3 排名与约束、
 * 公共关键词与 CASE 拼接、无本人星盘降级。
 */
class TodayObservationTest {

    private DailyObservationAssets assets;
    private DailyPersonalScoreService scoreService;
    private Top3ImpactService top3Service;
    private PublicObservationService publicService;
    private TodayObservationService todayService;

    @BeforeEach
    void setUp() {
        ObjectMapper mapper = new ObjectMapper();
        assets = new DailyObservationAssets();
        ReflectionTestUtils.setField(assets, "objectMapper", mapper);
        assets.load();

        scoreService = new DailyPersonalScoreService();
        ReflectionTestUtils.setField(scoreService, "assets", assets);

        top3Service = new Top3ImpactService();
        ReflectionTestUtils.setField(top3Service, "assets", assets);

        publicService = new PublicObservationService();
        ReflectionTestUtils.setField(publicService, "assets", assets);

        todayService = new TodayObservationService();
        ReflectionTestUtils.setField(todayService, "assets", assets);
        ReflectionTestUtils.setField(todayService, "publicObservationService", publicService);
        ReflectionTestUtils.setField(todayService, "scoreService", scoreService);
        ReflectionTestUtils.setField(todayService, "top3Service", top3Service);
        // 星历相关服务在单测中不可用：显式置为不可用桩，验证降级路径而不是崩溃
        ReflectionTestUtils.setField(todayService, "transitSnapshotService", unavailableTransit());
        ReflectionTestUtils.setField(todayService, "moonDailyTimelineService", unavailableTimeline());
    }

    /** 星历不可用桩：始终返回空快照 */
    private TransitSnapshotService unavailableTransit() {
        return new TransitSnapshotService() {
            @Override
            public Snapshot snapshotAt(java.time.LocalDateTime localTime, String timezone) {
                Snapshot s = new Snapshot();
                s.setSnapshotId("unavailable");
                return s;
            }
        };
    }

    /** 时间轴不可用桩：始终返回空 */
    private MoonDailyTimelineService unavailableTimeline() {
        return new MoonDailyTimelineService() {
            @Override
            public java.util.List<PublicObservationService.DaySegment> segmentsFor(
                    java.time.LocalDate date, String timezone) {
                return new ArrayList<>();
            }
        };
    }

    // ============================================================
    // 资产完整性
    // ============================================================

    @Test
    void assets_matchLockedCounts() {
        assertEquals(81, assets.getScoreRules().getP2p_rules().size(), "P2P 规则必须 81 条");
        assertEquals(108, assets.getScoreRules().getP2h_rules().size(), "P2H 规则必须 108 条");
        assertEquals(27, assets.getDailyCopy().getNakshatras().size(), "星宿文案必须 27 条");
        assertEquals(12, assets.getDailyCopy().getSigns().size());
        assertEquals(20, assets.getDailyCopy().getDomain_sentences().size(), "四领域短句 5 档 × 4 领域 = 20");
    }

    @Test
    void globalParams_areLocked() {
        assertEquals(50, assets.param("BASE_SCORE", -1), "基础分 50");
        assertEquals(20, assets.param("MIN_SCORE", -1));
        assertEquals(95, assets.param("MAX_SCORE", -1));
        assertEquals(5, assets.param("TOP_N_PER_DOMAIN", -1));
        assertEquals(60, assets.param("TOP3_MIN_RANK_SCORE", -1), "Top3 门槛 60");
        assertEquals(1.3, assets.param("TOP3_DAILY_FACTOR_MOON", -1), 0.001);
        assertEquals(0.85, assets.param("TOP3_DAILY_FACTOR_RAHU", -1), 0.001);
        assertEquals(0, assets.param("USE_LLM_FOR_SCORE", -1), "评分必须 0 LLM");
        assertEquals(0, assets.param("USE_LLM_FOR_TOP3", -1), "Top3 必须 0 LLM");
    }

    // ============================================================
    // 引擎：influence positions / 公式
    // ============================================================

    @Test
    void influencePositions_matchLockedSpec() {
        assertEquals(java.util.Set.of(1, 7), VedicPlanetaryInfluenceEngine.INFLUENCE_POSITIONS.get("Sun"));
        assertEquals(java.util.Set.of(1, 4, 7, 8), VedicPlanetaryInfluenceEngine.INFLUENCE_POSITIONS.get("Mars"));
        assertEquals(java.util.Set.of(1, 5, 7, 9), VedicPlanetaryInfluenceEngine.INFLUENCE_POSITIONS.get("Jupiter"));
        assertEquals(java.util.Set.of(1, 3, 7, 10), VedicPlanetaryInfluenceEngine.INFLUENCE_POSITIONS.get("Saturn"));
        assertEquals(java.util.Set.of(1, 5, 7, 9), VedicPlanetaryInfluenceEngine.INFLUENCE_POSITIONS.get("Rahu"));
        assertEquals(java.util.Set.of(1, 5, 7, 9), VedicPlanetaryInfluenceEngine.INFLUENCE_POSITIONS.get("Ketu"));
    }

    @Test
    void p2hFormula_marsIn10thHouse() {
        // 文档示例：Mars 在 10H → 1st=10H, 4th=1H, 7th=4H, 8th=5H
        List<HouseInfluence> list = VedicPlanetaryInfluenceEngine.planetToHouse("Mars", 10);
        Map<Integer, Integer> byPosition = new LinkedHashMap<>();
        for (HouseInfluence i : list) {
            byPosition.put(i.getAspectPosition(), i.getTargetHouse());
        }
        assertEquals(10, byPosition.get(1));
        assertEquals(1, byPosition.get(4));
        assertEquals(4, byPosition.get(7));
        assertEquals(5, byPosition.get(8));
        // position 1 是 placement_influence，其余是 graha_drishti
        for (HouseInfluence i : list) {
            assertEquals(i.getAspectPosition() == 1 ? "placement_influence" : "graha_drishti",
                    i.getInfluenceType());
        }
    }

    @Test
    void conjunction_generatedOncePerPair_notAsFirstAspect() {
        List<PlanetPos> planets = List.of(
                new PlanetPos("Sun", 4, 10.0),
                new PlanetPos("Mercury", 4, 18.0));
        List<PlanetRelation> rels = VedicPlanetaryInfluenceEngine.planetToPlanet(planets);
        assertEquals(1, rels.size(), "同宫按无序 pair 只生成一条 conjunction，不重复生成 1st aspect");
        assertEquals("conjunction", rels.get(0).getRelationType());
        assertEquals(1, rels.get(0).getAspectPosition());
        assertTrue(rels.get(0).isBidirectional());
    }

    @Test
    void grahaDrishti_respectsPlanetPositions() {
        // Saturn 在 1 宫（Aries），目标在 3 宫（Gemini）→ Saturn 的 3rd 位置成立
        List<PlanetPos> planets = List.of(
                new PlanetPos("Saturn", 0, 5.0),
                new PlanetPos("Venus", 2, 8.0));
        List<PlanetRelation> rels = VedicPlanetaryInfluenceEngine.planetToPlanet(planets);
        assertEquals(1, rels.size());
        assertEquals("graha_drishti", rels.get(0).getRelationType());
        assertEquals(3, rels.get(0).getAspectPosition());
        assertEquals(60, rels.get(0).getExactAngleDeg());
        // 不成立的相对位置不得生成关系：Venus 只能 1/7，Saturn 在 Aries 时 Venus 在 3 宫 → Venus→Saturn 也不成立
        // （反向：Venus 在 3 宫对 Saturn 在 1 宫，相对位置 12，不在 {1,7}）
        assertEquals(1, rels.size());
    }

    @Test
    void degreeBands_matchSpec() {
        assertEquals("exact_critical", VedicPlanetaryInfluenceEngine.exactnessBand(0.5)[0]);
        assertEquals("very_tight", VedicPlanetaryInfluenceEngine.exactnessBand(2)[0]);
        assertEquals("tight", VedicPlanetaryInfluenceEngine.exactnessBand(5)[0]);
        assertEquals("moderate", VedicPlanetaryInfluenceEngine.exactnessBand(9)[0]);
        assertEquals("loose", VedicPlanetaryInfluenceEngine.exactnessBand(12)[0]);
        assertEquals("broad", VedicPlanetaryInfluenceEngine.exactnessBand(18)[0]);
        assertEquals("very_broad", VedicPlanetaryInfluenceEngine.exactnessBand(25)[0]);
        assertEquals("strong", VedicPlanetaryInfluenceEngine.exactnessBand(1)[1]);
        assertEquals("weak", VedicPlanetaryInfluenceEngine.exactnessBand(21)[1]);
        // exactness_score = max(0, 1 - delta/30)
        assertEquals(0.9667, VedicPlanetaryInfluenceEngine.exactnessScore(1), 0.001);
        assertEquals(0, VedicPlanetaryInfluenceEngine.exactnessScore(45), 0.001);
    }

    @Test
    void crossChart_producesBothDirections() {
        // A 的火星在 Capricorn（sign 9），B 的月亮在 Aquarius（sign 10）
        // relative_position = 2 → Mars 只覆盖 {1,4,7,8}，不成立
        List<PlanetPos> a = List.of(new PlanetPos("Mars", 9, 7.0));
        List<PlanetPos> b = List.of(new PlanetPos("Moon", 10, 3.0));
        assertEquals(0, VedicPlanetaryInfluenceEngine.crossChartPlanetToPlanet(a, b, false).size());

        // 同 sign → conjunction，且只生成一条（无序 pair）
        List<PlanetPos> b2 = List.of(new PlanetPos("Moon", 9, 12.0));
        List<PlanetRelation> rels = VedicPlanetaryInfluenceEngine.crossChartPlanetToPlanet(a, b2, false);
        assertEquals(1, rels.size(), "同宫只生成一条 conjunction");
        assertEquals("conjunction", rels.get(0).getRelationType());
    }

    @Test
    void mutual_isAggregateMarkOnly() {
        // Jupiter(Aries) ↔ Saturn(Libra)：相对位置 7，双方都覆盖 7 → mutual
        List<PlanetPos> a = List.of(new PlanetPos("Jupiter", 0, 10.0));
        List<PlanetPos> b = List.of(new PlanetPos("Saturn", 6, 10.0));
        List<PlanetRelation> rels = VedicPlanetaryInfluenceEngine.crossChartPlanetToPlanet(a, b, true);
        assertEquals(2, rels.size(), "A→B 与 B→A 两条原始关系必须分别保留");
        for (PlanetRelation r : rels) {
            assertTrue(r.isBidirectional(), "mutual 只做聚合标记");
            assertEquals("graha_drishti", r.getRelationType());
        }
    }

    @Test
    void overlayHouse_wholeSign() {
        // sender Aries(0) vs receiver Asc Taurus(1) → 12 宫
        assertEquals(12, VedicPlanetaryInfluenceEngine.overlayHouse(0, 1));
        assertEquals(1, VedicPlanetaryInfluenceEngine.overlayHouse(0, 0));
    }

    // ============================================================
    // 评分口径
    // ============================================================

    @Test
    void scores_clampedTo20_95_andTop5PerDomain() {
        List<PlanetPos> natal = ninePlanets(0);
        List<PlanetPos> transit = ninePlanets(0); // 全部同宫 → 大量 conjunction 证据
        DailyPersonalScoreService.ScoreResult r = scoreService.computeScores(0, natal, transit);
        assertEquals(4, r.getScores().size());
        for (DailyPersonalScoreService.DomainScore s : r.getScores()) {
            assertTrue(s.getScore() >= 20 && s.getScore() <= 95,
                    s.getDomain() + " 必须落在 20–95，实际 " + s.getScore());
            assertNotNull(s.getSentence(), "四领域必须有固定一句话");
        }
    }

    @Test
    void p2pContribution_usesStrengthFactor_p2hUsesFactorOne() {
        DailyPersonalScoreService.Evidence p2p = new DailyPersonalScoreService.Evidence();
        p2p.setEvidenceType("P2P");
        p2p.setStrength("medium");
        p2p.getDeltas().put("career", 4.0);
        assertEquals(2.8, scoreService.contributionOf(p2p, "career"), 0.001, "4 × 0.7");

        DailyPersonalScoreService.Evidence p2h = new DailyPersonalScoreService.Evidence();
        p2h.setEvidenceType("P2H");
        p2h.getDeltas().put("career", 4.0);
        assertEquals(4.0, scoreService.contributionOf(p2h, "career"), 0.001, "P2H 不使用 exactness");
    }

    @Test
    void levelKeyBands() {
        assertEquals("LOW", DailyPersonalScoreService.levelKeyOf(30));
        assertEquals("WATCH", DailyPersonalScoreService.levelKeyOf(45));
        assertEquals("STEADY", DailyPersonalScoreService.levelKeyOf(55));
        assertEquals("GOOD", DailyPersonalScoreService.levelKeyOf(70));
        assertEquals("STRONG", DailyPersonalScoreService.levelKeyOf(85));
    }

    // ============================================================
    // Top3
    // ============================================================

    @Test
    void top3_respectsThresholdAndCount() {
        List<PlanetPos> natal = ninePlanets(0);
        List<PlanetPos> transit = ninePlanets(0);
        DailyPersonalScoreService.ScoreResult r = scoreService.computeScores(0, natal, transit);
        List<Top3ImpactService.Top3Item> top3 =
                top3Service.computeTop3(r.getEvidence(), null, null);
        assertTrue(top3.size() <= 3, "Top3 最多 3 条");
        for (Top3ImpactService.Top3Item item : top3) {
            assertTrue(item.getFinalRankScore() >= 60, "低于 60 不得进入 Top3");
            assertNotNull(item.getTitle());
            assertNotNull(item.getAstrologyEvidence());
            assertFalse(item.getImpactDomains().isEmpty(), "impact_domains 至少 1 个");
            assertTrue(item.getImpactDomains().size() <= 2, "impact_domains 最多 2 个");
        }
    }

    @Test
    void rankScore_formula_p2pAndP2h() {
        DailyPersonalScoreService.Evidence p2p = new DailyPersonalScoreService.Evidence();
        p2p.setEvidenceType("P2P");
        p2p.setSourcePlanet("Moon");
        p2p.setStrength("strong");
        p2p.setBasePriority(80);
        p2p.setImpactSignature("P2P|Moon|Sun|conjunction|1");
        // 80 × 1.0 × 1.3 × 1.0 = 104
        assertEquals(104, top3Service.rankScore(p2p, null, null), 0.001);

        DailyPersonalScoreService.Evidence p2h = new DailyPersonalScoreService.Evidence();
        p2h.setEvidenceType("P2H");
        p2h.setSourcePlanet("Saturn");
        p2h.setInfluenceType("graha_drishti");
        p2h.setBasePriority(80);
        p2h.setImpactSignature("P2H|Saturn|7|graha_drishti|7");
        // 80 × 0.85 × 0.9 × 1.0 = 61.2
        assertEquals(61.2, top3Service.rankScore(p2h, null, null), 0.001);
    }

    @Test
    void repeatFactor_ladder() {
        DailyPersonalScoreService.Evidence e = new DailyPersonalScoreService.Evidence();
        e.setEvidenceType("P2P");
        e.setSourcePlanet("Sun");
        e.setStrength("strong");
        e.setImpactSignature("SIG");
        assertEquals(1.0, top3Service.repeatFactor(e, List.of(), null), 0.001);
        assertEquals(0.85, top3Service.repeatFactor(e, List.of("SIG"), Map.of("SIG", 1)), 0.001);
        assertEquals(0.70, top3Service.repeatFactor(e, List.of("SIG"), Map.of("SIG", 2)), 0.001);
        assertEquals(0.60, top3Service.repeatFactor(e, List.of("SIG"), Map.of("SIG", 5)), 0.001);
    }

    @Test
    void top3Item_neverExposesBackendOnlyFields() {
        List<PlanetPos> natal = ninePlanets(0);
        List<PlanetPos> transit = ninePlanets(0);
        Map<String, Object> resp = todayService.build("2026-08-15", "Asia/Shanghai",
                singleSegmentDay(), "12:00", "06:13", false, 0, natal, transit, 39.9042, 116.4074, null, null);
        String json = assertDoesNotThrow(() -> new ObjectMapper().writeValueAsString(resp));
        assertFalse(json.contains("final_rank_score"), "final_rank_score 不得向用户展示");
        assertFalse(json.contains("impact_signature"), "impact_signature 不得向用户展示");
        assertFalse(json.contains("repeat_factor"), "repeat_factor 不得向用户展示");
        assertFalse(json.contains("level_key"), "level_key 不得向用户展示");
    }

    // ============================================================
    // 公共今日观星
    // ============================================================

    @Test
    void keywords_singleNakshatraDay_usesThreeKeywords() {
        PublicObservationService.PublicObservation po =
                publicService.build(singleSegmentDay(), null, "06:13", false);
        assertEquals(3, po.getKeywords().size());
        assertEquals("CASE_1", po.getCaseKey());
        assertNotNull(po.getDailySummary());
        assertEquals("娄宿", po.getNakshatraCn());
        assertEquals("白羊座", po.getMoonSignCn());
    }

    @Test
    void keywords_multiNakshatraDay_longestGetsTwo() {
        // 娄宿占 6h（较长，先进入），胃宿占 18h（最长）→ 胃宿得 2 个关键词
        List<PublicObservationService.DaySegment> segs = new ArrayList<>();
        segs.add(seg("00:00", "06:00", "Aries", "Aswini"));
        segs.add(seg("06:00", "24:00", "Aries", "Bharani"));
        PublicObservationService.PublicObservation po = publicService.build(segs, null, "06:00", false);
        assertEquals(3, po.getKeywords().size());
        // 最长星宿（Bharani/胃宿）贡献 keyword_1 + keyword_2
        assertTrue(po.getKeywords().contains("承载"));
        assertTrue(po.getKeywords().contains("约束"));
        // 第二长（Aswini/娄宿）只贡献 keyword_1
        assertTrue(po.getKeywords().contains("迅捷"));
        assertEquals("CASE_2", po.getCaseKey(), "跨星宿不跨星座 → CASE_2");
    }

    @Test
    void caseRule_crossSign_notCrossNakshatra_isCase3() {
        List<PublicObservationService.DaySegment> segs = new ArrayList<>();
        segs.add(seg("00:00", "12:00", "Aries", "Bharani"));
        segs.add(seg("12:00", "24:00", "Taurus", "Bharani"));
        PublicObservationService.PublicObservation po = publicService.build(segs, null, "12:00", false);
        assertEquals("CASE_3", po.getCaseKey());
        assertTrue(po.getDailySummary().contains("今天的主线仍是"));
        assertEquals("金牛座", po.getMoonSignCn());
    }

    @Test
    void nextNakshatraFields_areStructured() {
        List<PublicObservationService.DaySegment> segs = new ArrayList<>();
        segs.add(seg("00:00", "06:13", "Leo", "Purva Phalguni"));
        segs.add(seg("06:13", "24:00", "Leo", "Uttara Phalguni"));
        // 当前时刻 03:00 落在第一段 → 当前星宿为张宿，下一宿为翼宿
        PublicObservationService.PublicObservation po = publicService.build(segs, "03:00", "06:13", false);
        assertEquals("张宿", po.getNakshatraCn());
        assertEquals("翼宿", po.getNextNakshatraCn());
        assertEquals("06:13", po.getNextChangeAtLocal());
        assertFalse(po.isNextChangeIsTomorrow());
    }

    // ============================================================
    // 响应结构 / 降级
    // ============================================================

    @Test
    void response_matchesContractShape() {
        List<PlanetPos> natal = ninePlanets(0);
        List<PlanetPos> transit = ninePlanets(0);
        Map<String, Object> resp = todayService.build("2026-08-15", "Asia/Shanghai",
                singleSegmentDay(), "12:00", "06:13", false, 0, natal, transit, 39.9042, 116.4074, null, null);
        JsonNode node = new ObjectMapper().valueToTree(resp);
        for (String k : new String[]{"local_date", "timezone", "public_observation",
                "personalized_available", "personalized"}) {
            assertTrue(node.has(k), "顶层缺字段: " + k);
        }
        JsonNode po = node.get("public_observation");
        for (String k : new String[]{"moon_sign", "nakshatra", "nakshatra_cn", "next_nakshatra",
                "next_nakshatra_cn", "next_change_at_local", "next_change_is_tomorrow",
                "keywords", "daily_summary"}) {
            assertTrue(po.has(k), "public_observation 缺字段: " + k);
        }
        assertTrue(node.get("personalized_available").asBoolean());
        JsonNode p = node.get("personalized");
        for (String domain : new String[]{"career", "love", "study", "growth"}) {
            assertTrue(p.get("scores").has(domain), "缺领域: " + domain);
            assertTrue(p.get("scores").get(domain).has("score"));
            assertTrue(p.get("scores").get(domain).has("sentence"));
        }
        assertTrue(p.has("top_impacts"));
    }

    @Test
    void withoutNatal_personalizedIsNull() {
        Map<String, Object> resp = todayService.build("2026-08-15", "Asia/Shanghai",
                singleSegmentDay(), "12:00", "06:13", false, null, null, ninePlanets(0), 39.9042, 116.4074, null, null);
        JsonNode node = new ObjectMapper().valueToTree(resp);
        assertFalse(node.get("personalized_available").asBoolean());
        assertTrue(node.get("personalized").isNull());
        assertEquals("NO_NATAL_CHART", node.get("personalized_unavailable_reason").asText());
        // 公共层仍必须可用
        assertFalse(node.get("public_observation").get("keywords").isEmpty());
    }

    /**
     * 回归：流年快照为空时绝不允许返回“四个领域全是基础分 50”的假数据。
     *
     * 这是线上真实出现过的 bug —— 前端传了空 transit，后端照样算出四领域 = 50，
     * 用户看到的就是四个 50 的假分数。
     */
    @Test
    void emptyTransit_mustNotReturnBaseScores() {
        List<PlanetPos> natal = ninePlanets(0);
        Map<String, Object> resp = todayService.build("2026-08-15", "Asia/Shanghai",
                singleSegmentDay(), "12:00", "06:13", false, 0, natal, new ArrayList<>(), 39.9042, 116.4074, null, null);
        JsonNode node = new ObjectMapper().valueToTree(resp);
        assertFalse(node.get("personalized_available").asBoolean(),
                "拿不到流年数据时个性化必须不可用");
        assertTrue(node.get("personalized").isNull(), "不得返回 personalized 占位对象");
        assertEquals("TRANSIT_UNAVAILABLE", node.get("personalized_unavailable_reason").asText());
    }

    @Test
    void withEvidence_notAllDomainsStayAtBaseScore() {
        // 本命与流年同星座 → 仍会产生 conjunction 证据，分数不应四个领域都等于 50
        List<PlanetPos> natal = ninePlanets(0);
        List<PlanetPos> transit = ninePlanets(0);
        DailyPersonalScoreService.ScoreResult r = scoreService.computeScores(0, natal, transit);
        assertFalse(r.getEvidence().isEmpty(), "同星座也应产生 conjunction 证据");
        long atBase = r.getScores().stream().filter(s -> s.getScore() == 50).count();
        assertTrue(atBase < 4, "有证据时不应四个领域都停在基础分 50");
    }

    // ============================================================
    // 星座名解析：兼容星历服务历史拼写
    // ============================================================

    @Test
    void signParsing_toleratesLegacySpellings() {
        assertEquals(8, TransitSnapshotService.signIndex("Sagittarius"));
        assertEquals(8, TransitSnapshotService.signIndex("Saggitarius"), "真实星历返回过这个旧拼写");
        assertEquals(8, TransitSnapshotService.signIndex("saggitarius"));
        assertEquals(8, TransitSnapshotService.signIndex("Dhanu"));
        assertEquals(0, TransitSnapshotService.signIndex("Aries"));
        assertEquals(0, TransitSnapshotService.signIndex("Mesha"));
        assertNull(TransitSnapshotService.signIndex("Atlantis"), "无法解析不得猜测");
        assertNull(TransitSnapshotService.signIndex(null));
    }

    /**
     * 回归：VedicAstro 的 planets 主格式是数组（ChartDataParser 注释明示），
     * TransitSnapshotService 曾只支持 Map 形态，导致「星历返回空行星列表」。
     */
    @Test
    void transitSnapshot_parsesArrayFormPlanets() {
        TransitSnapshotService svc = new TransitSnapshotService();
        ReflectionTestUtils.setField(svc, "vedicAstroService", new VedicAstroService(null) {
            @Override
            public Map<String, Object> getJyotishyaData(Map<String, Object> params) {
                return arrayFormTransitResponse("Bharani");
            }
        });
        TransitSnapshotService.Snapshot snap = svc.snapshotAt(
                java.time.LocalDateTime.of(2026, 9, 24, 16, 0), "Asia/Shanghai");
        assertEquals(9, snap.getPlanets().size(), "数组形态的 planets 必须能解析出九大行星");
        // Moon 时间轴依赖 nakshatra 字段
        TransitSnapshotService.MoonState st = svc.moonStateAt(
                java.time.LocalDateTime.of(2026, 9, 24, 16, 0), "Asia/Shanghai");
        assertTrue(st.isFound(), "必须能从中定位到月亮");
        assertNotNull(st.getNakshatra());
    }

    /** 按真实响应结构构造数组形态的 Transit 响应（月亮可指定星宿，用于验证变体拼写归一化） */
    private Map<String, Object> arrayFormTransitResponse(String moonNakshatra) {
        String[] names = {"Sun", "Moon", "Mercury", "Venus", "Mars",
                "Jupiter", "Saturn", "Rahu", "Ketu"};
        String[] signs = {"Aries", "Taurus", "Gemini", "Cancer", "Leo",
                "Virgo", "Libra", "Scorpio", "Saggitarius"};
        List<Map<String, Object>> planets = new ArrayList<>();
        for (int i = 0; i < names.length; i++) {
            Map<String, Object> pos = new LinkedHashMap<>();
            pos.put("dec_deg", 5.0 + i);
            Map<String, Object> p = new LinkedHashMap<>();
            p.put("name", names[i]);
            p.put("sign", signs[i]);
            p.put("pos", pos);
            p.put("nakshatra", "Moon".equals(names[i]) ? moonNakshatra : "Bharani");
            planets.add(p);
        }
        Map<String, Object> d1 = new LinkedHashMap<>();
        d1.put("planets", planets);
        Map<String, Object> resp = new LinkedHashMap<>();
        resp.put("D1", d1);
        return resp;
    }

    /** 星历变体拼写必须对齐锁定表命名，否则中文名查表失败（前端出现空括号） */
    @Test
    void moonState_normalizesVariantNakshatraSpelling() {
        TransitSnapshotService svc = new TransitSnapshotService();
        ReflectionTestUtils.setField(svc, "vedicAstroService", new VedicAstroService(null) {
            @Override
            public Map<String, Object> getJyotishyaData(Map<String, Object> params) {
                return arrayFormTransitResponse("Shatabhishak");
            }
        });
        TransitSnapshotService.MoonState st = svc.moonStateAt(
                java.time.LocalDateTime.of(2026, 9, 24, 16, 0), "Asia/Shanghai");
        assertEquals("Shatabhisha", st.getNakshatra(), "Shatabhishak 必须归一化为锁定表命名");
    }

    @Test
    void ephemerisNameNormalizer_coversKnownVariants() {
        assertEquals("Aswini", EphemerisNameNormalizer.normalizeNakshatra("Ashwini"));
        assertEquals("Krittika", EphemerisNameNormalizer.normalizeNakshatra("Kritika"));
        assertEquals("Jyeshtha", EphemerisNameNormalizer.normalizeNakshatra("Jyeshta"));
        assertEquals("Anuradha", EphemerisNameNormalizer.normalizeNakshatra("Anurada"));
        assertEquals("Vishakha", EphemerisNameNormalizer.normalizeNakshatra("Vishaka"));
        assertEquals("Shatabhisha", EphemerisNameNormalizer.normalizeNakshatra("Shatabhishak"));
        // 已合规写法原样返回（含大小写纠正）
        assertEquals("Shatabhisha", EphemerisNameNormalizer.normalizeNakshatra("shatabhisha"));
        assertEquals("Dhanishta", EphemerisNameNormalizer.normalizeNakshatra("Dhanishta"));
        assertEquals("危宿", assets.nakshatra(EphemerisNameNormalizer.normalizeNakshatra("Shatabhishak"))
                .getNakshatra_cn(), "归一化后必须能在锁定表查到中文名");
        assertEquals("虚宿", assets.nakshatra("Dhanishta").getNakshatra_cn());
        assertEquals("Sagittarius", EphemerisNameNormalizer.normalizeSign("Saggitarius"));
        // 无法识别不猜测
        assertEquals("Atlantis", EphemerisNameNormalizer.normalizeNakshatra("Atlantis"));
        assertNull(EphemerisNameNormalizer.normalizeNakshatra(null));
    }

    /** 顶部月亮卡：当前时段 + 今日经历（2026-09-24 的真实切分场景） */
    @Test
    void publicMoonCard_currentWindowAndJourney() {
        List<PublicObservationService.DaySegment> segs = new ArrayList<>();
        segs.add(seg("00:00", "00:28", "Capricorn", "Dhanishta"));
        segs.add(seg("00:28", "13:05", "Aquarius", "Dhanishta"));
        // 故意用星历变体拼写，验证入口归一化
        segs.add(seg("13:05", "24:00", "Aquarius", "Shatabhishak"));

        PublicObservationService.PublicObservation po = publicService.build(segs, "14:00", null, false);

        assertEquals("Shatabhisha", po.getNakshatra());
        assertEquals("危宿", po.getNakshatraCn());
        assertEquals("水瓶座", po.getMoonSignCn());
        assertEquals("13:05", po.getCurrentSegmentStart());
        assertEquals("24:00", po.getCurrentSegmentEnd());

        List<PublicObservationService.JourneyStop> journey = po.getJourney();
        assertEquals(2, journey.size(), "星座切换但星宿未变不产生新停留");
        assertEquals("00:00", journey.get(0).getStartLocal());
        assertEquals("Dhanishta", journey.get(0).getNakshatra());
        assertEquals("虚宿", journey.get(0).getNakshatraCn());
        assertEquals("13:05", journey.get(1).getStartLocal());
        assertEquals("Shatabhisha", journey.get(1).getNakshatra());
        assertEquals("危宿", journey.get(1).getNakshatraCn());
    }

    /** 接口必须把月亮卡字段带出去，且 journey 为结构化数组 */
    @Test
    void response_exposesMoonCardFields() {
        List<PublicObservationService.DaySegment> segs = new ArrayList<>();
        segs.add(seg("00:00", "00:28", "Capricorn", "Dhanishta"));
        segs.add(seg("00:28", "13:05", "Aquarius", "Dhanishta"));
        segs.add(seg("13:05", "24:00", "Aquarius", "Shatabhishak"));
        Map<String, Object> resp = todayService.build("2026-09-24", "Asia/Shanghai", segs,
                "14:00", null, false, null, null, null, null, null, null, null);

        @SuppressWarnings("unchecked")
        Map<String, Object> po = (Map<String, Object>) resp.get("public_observation");
        assertEquals("Shatabhisha", po.get("nakshatra"));
        assertEquals("危宿", po.get("nakshatra_cn"));
        assertEquals("13:05", po.get("current_segment_start"));
        assertEquals("24:00", po.get("current_segment_end"));
        @SuppressWarnings("unchecked")
        List<Map<String, Object>> journey = (List<Map<String, Object>>) po.get("today_journey");
        assertEquals(2, journey.size());
        assertEquals("虚宿", journey.get(0).get("nakshatra_cn"));
        assertEquals("危宿", journey.get(1).get("nakshatra_cn"));
        assertEquals("13:05", journey.get(1).get("start_local"));
    }

    @Test
    void noLlmFlag_assetsDeclareDeterministicOnly() {
        assertEquals(0, assets.param("USE_LLM_FOR_SCORE", -1));
        assertEquals(0, assets.param("USE_LLM_FOR_TOP3", -1));
    }

    // ============================================================
    // helpers
    // ============================================================

    private static final String[] NINE = {"Sun", "Moon", "Mercury", "Venus", "Mars",
            "Jupiter", "Saturn", "Rahu", "Ketu"};

    /** 九大行星各占一个星座（用于制造稳定可预期的证据集合） */
    private List<PlanetPos> ninePlanets(int offset) {
        List<PlanetPos> list = new ArrayList<>();
        for (int i = 0; i < NINE.length; i++) {
            list.add(new PlanetPos(NINE[i], (offset + i) % 12, 5.0 + i * 2));
        }
        return list;
    }

    private List<PublicObservationService.DaySegment> singleSegmentDay() {
        return new ArrayList<>(List.of(seg("00:00", "24:00", "Aries", "Aswini")));
    }

    private PublicObservationService.DaySegment seg(String s, String e, String sign, String nak) {
        PublicObservationService.DaySegment d = new PublicObservationService.DaySegment();
        d.setStartLocal(s);
        d.setEndLocal(e);
        d.setMoonSign(sign);
        d.setMoonNakshatra(nak);
        return d;
    }
}
