package cn.dct.zpback.engine.children;

import cn.dct.zpback.entity.children.ChildrenEvidenceObject;
import cn.dct.zpback.entity.lifepattern.LifePatternChartData;
import cn.dct.zpback.entity.lifepattern.LifePatternNatalChart;
import cn.dct.zpback.entity.lifepattern.LifePatternPlanetPosition;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.*;

/**
 * 子女报告 - Evidence 引擎
 * 符合 SEALED 文档《01_子女与养育深度报告_最终开发总说明》第 6、7 节
 *
 * 固定处理顺序（不可改变）：
 * 原始事实 → 有效性 → 标准化 → root → aliases → modifiers
 * → 同根去重 → 候选匹配 → 一根一方向 → family stacking → 冲突 → Claim
 */
@Component
@Slf4j
public class ChildrenEvidenceEngine {

    /** C02-C09：direct claims 可接收投票 */
    private static final List<String> DIRECT_CLAIMS = Arrays.asList("C02", "C03", "C04", "C05", "C06", "C07", "C08", "C09");

    private static final String[] SIGNS = {
        "Aries", "Taurus", "Gemini", "Cancer", "Leo", "Virgo",
        "Libra", "Scorpio", "Sagittarius", "Capricorn", "Aquarius", "Pisces"
    };

    private static final List<String> PRIMARY_PLANETS = Arrays.asList(
        "Sun", "Moon", "Mars", "Mercury", "Jupiter", "Venus", "Saturn", "Rahu", "Ketu");

    /**
     * 构建 Evidence 全集
     */
    public List<ChildrenEvidenceObject> buildEvidence(LifePatternChartData chartData, String akPlanet) {
        List<ChildrenEvidenceObject> all = new ArrayList<>();

        LifePatternNatalChart d1 = chartData != null ? chartData.getD1() : null;
        LifePatternNatalChart d7 = chartData != null ? chartData.getD7() : null;
        LifePatternNatalChart d9 = chartData != null ? chartData.getD9() : null;

        // 整宫制补齐宫位
        fillMissingHouses(d1);
        fillMissingHouses(d7);
        fillMissingHouses(d9);

        // ============ D1 核心锚点 ============
        if (d1 != null) {
            buildD1ChildLord(d1, all);
            buildD1House5Occupancy(d1, all);
            buildD1JupiterMoon(d1, all);
            buildD1Coparenting(d1, all);
        }

        // ============ D7 专业锚点 ============
        if (d7 != null) {
            buildD7Asc(d7, all);
            buildD7ChildHouseAndLord(d7, all);
            buildD7Jupiter(d7, all);
            buildD7Coparenting(d7, all);
        }

        // ============ D9 验证（限权） ============
        if (d9 != null) {
            buildD9Validation(d9, all);
        }

        // ============ Shadbala 修饰 ============
        if (chartData != null && chartData.getShadbala() != null) {
            buildShadbalaModifiers(chartData, all);
        }

        // ============ Putrakaraka 别名 ============
        if (chartData != null && akPlanet != null && !akPlanet.isEmpty()) {
            buildPutrakarakaAlias(chartData, akPlanet, all);
        }

        // 同根去重 + 候选匹配 + 一根一方向
        deduplicateSameRoots(all);
        matchCandidates(all);
        enforceOneRootOneDirection(all);

        log.info("Children Evidence 构建完成: total={}, eligible={}",
                all.size(), all.stream().filter(e -> "ELIGIBLE".equals(e.getEvidenceStatus())).count());
        return all;
    }

    // ============================================================
    // D1 锚点
    // ============================================================

    /**
     * D1 5L placement - C02-C08 Primary 功能根
     */
    private void buildD1ChildLord(LifePatternNatalChart d1, List<ChildrenEvidenceObject> all) {
        String ascSign = d1.getAscendantSign();
        if (ascSign == null) return;
        int house5SignIdx = (signIndex(ascSign) + 4) % 12;
        String house5Sign = SIGNS[house5SignIdx];

        for (Map.Entry<String, LifePatternPlanetPosition> entry : d1.getPlanets().entrySet()) {
            String planet = entry.getKey();
            LifePatternPlanetPosition pos = entry.getValue();
            if (!isPrimaryPlanet(planet) || pos == null || pos.getSign() == null) continue;

            if (pos.getSign().equalsIgnoreCase(house5Sign)) {
                // 该行星是 5L
                Integer house = pos.getHouse();
                List<String> bridgeTags = house != null
                        ? PlanetTagRegistry.getHouseBridgeTags(house)
                        : Collections.singletonList("identity_guidance");

                ChildrenEvidenceObject ev = base(planet, "D1", "LORD_RELATION",
                        "D1_5L", planet, "D1 5L " + planet + " in " + pos.getSign()
                                + (house != null ? " H" + house : ""));
                ev.setAuthorityRole("PRIMARY");
                ev.setEvidenceLane("NATAL_PRIMARY");
                ev.setRootKey("D1:PLANET:" + planet + ":FUNCTION_5L:PLACEMENT"
                        + (house != null ? "_H" + house : ""));
                ev.setIndependenceClusterKey("D1_CHILD_LORD");
                ev.setSemanticCorrelationKey("CHILDREN_PATH:" + planet);
                ev.setClaimEligibility(new ArrayList<>(Arrays.asList("C02", "C03", "C04", "C05", "C06", "C07", "C08")));
                ev.setFunctionalCondition(inferConditionFromTags(planet, bridgeTags));
                ev.setSemanticTags(new ArrayList<>(bridgeTags));
                ev.getNotes().add("5L placement: " + house5Sign + " lord");
                all.add(ev);
            }
        }
    }

    /**
     * D1 5H occupancy - 每颗行星独立物理 root
     */
    private void buildD1House5Occupancy(LifePatternNatalChart d1, List<ChildrenEvidenceObject> all) {
        for (Map.Entry<String, LifePatternPlanetPosition> entry : d1.getPlanets().entrySet()) {
            String planet = entry.getKey();
            LifePatternPlanetPosition pos = entry.getValue();
            if (!isPrimaryPlanet(planet) || pos == null) continue;
            Integer house = pos.getHouse();
            if (house == null || house != 5 || pos.getSign() == null) continue;

            ChildrenEvidenceObject ev = base(planet, "D1", "HOUSE_OCCUPANCY",
                    planet, "D1_5H", planet + " in D1 5H in " + pos.getSign());
            ev.setAuthorityRole("PRIMARY");
            ev.setEvidenceLane("NATAL_PRIMARY");
            ev.setRootKey("D1:PLANET:" + planet + ":CHILD_HOUSE_OCCUPANCY:H5");
            ev.setIndependenceClusterKey("D1_CHILD_HOUSE");
            ev.setSemanticCorrelationKey("CHILDREN_PATH:" + planet);
            ev.setClaimEligibility(new ArrayList<>(Arrays.asList("C02", "C03", "C04", "C05", "C06", "C07", "C08")));
            ev.setFunctionalCondition(inferD1Condition(planet));
            ev.setSemanticTags(new ArrayList<>(PlanetTagRegistry.getTagsByCondition(
                    planet, ev.getFunctionalCondition())));
            ev.getSemanticTags().addAll(PlanetTagRegistry.getSignQualifiers(pos.getSign()));
            all.add(ev);
        }
    }

    /**
     * D1 Jupiter / Moon 天然养育锚点
     */
    private void buildD1JupiterMoon(LifePatternNatalChart d1, List<ChildrenEvidenceObject> all) {
        for (String planet : Arrays.asList("Jupiter", "Moon")) {
            LifePatternPlanetPosition pos = d1.getPlanets().get(planet);
            if (pos == null || pos.getSign() == null) continue;

            ChildrenEvidenceObject ev = base(planet, "D1", "NATURAL_ANCHOR",
                    planet, planet, planet + " in " + pos.getSign()
                            + (pos.getHouse() != null ? " H" + pos.getHouse() : ""));
            ev.setAuthorityRole("PRIMARY");
            ev.setEvidenceLane("NATAL_PRIMARY");
            ev.setRootKey("D1:PLANET:" + planet + ":NATURAL_" + planet.toUpperCase() + ":PLACEMENT");
            ev.setIndependenceClusterKey("D1_" + planet.toUpperCase());
            ev.setSemanticCorrelationKey("NURTURING:" + planet);
            ev.setClaimEligibility(new ArrayList<>(Arrays.asList("C04", "C05", "C07", "C08")));
            ev.setFunctionalCondition("SUPPORTIVE");
            ev.setSemanticTags(new ArrayList<>(PlanetTagRegistry.getSupportTags(planet)));
            ev.getSemanticTags().addAll(PlanetTagRegistry.getSignQualifiers(pos.getSign()));
            all.add(ev);
        }
    }

    /**
     * D1 7H/7L 共同养育锚点 - 仅 C09
     */
    private void buildD1Coparenting(LifePatternNatalChart d1, List<ChildrenEvidenceObject> all) {
        String ascSign = d1.getAscendantSign();
        if (ascSign == null) return;
        int house7SignIdx = (signIndex(ascSign) + 6) % 12;
        String house7Sign = SIGNS[house7SignIdx];

        for (Map.Entry<String, LifePatternPlanetPosition> entry : d1.getPlanets().entrySet()) {
            String planet = entry.getKey();
            LifePatternPlanetPosition pos = entry.getValue();
            if (!isPrimaryPlanet(planet) || pos == null || pos.getSign() == null) continue;

            boolean is7Lord = pos.getSign().equalsIgnoreCase(house7Sign);
            boolean in7H = pos.getHouse() != null && pos.getHouse() == 7;
            if (!is7Lord && !in7H) continue;

            ChildrenEvidenceObject ev = base(planet, "D1", is7Lord ? "LORD_RELATION" : "HOUSE_OCCUPANCY",
                    is7Lord ? "D1_7L" : planet, planet,
                    (is7Lord ? "D1 7L " : "") + planet + " in " + pos.getSign()
                            + (pos.getHouse() != null ? " H" + pos.getHouse() : ""));
            ev.setAuthorityRole("PRIMARY");
            ev.setEvidenceLane("NATAL_PRIMARY");
            ev.setRootKey("D1:PLANET:" + planet + ":COPARENTING:" + (is7Lord ? "LORD_7" : "OCCUPANCY_7"));
            ev.setIndependenceClusterKey("D1_COPARENTING");
            ev.setSemanticCorrelationKey("COPARENTING:" + planet);
            ev.setClaimEligibility(new ArrayList<>(Collections.singletonList("C09")));
            ev.setFunctionalCondition(inferD1Condition(planet));
            List<String> tags = new ArrayList<>(PlanetTagRegistry.getTagsByCondition(planet, ev.getFunctionalCondition()));
            tags.add("shared_duty");
            ev.setSemanticTags(tags);
            all.add(ev);
        }
    }

    // ============================================================
    // D7 专业锚点
    // ============================================================

    /**
     * D7 Asc Lord - C04-C08 Specialist
     */
    private void buildD7Asc(LifePatternNatalChart d7, List<ChildrenEvidenceObject> all) {
        String ascSign = d7.getAscendantSign();
        if (ascSign == null) return;

        for (Map.Entry<String, LifePatternPlanetPosition> entry : d7.getPlanets().entrySet()) {
            String planet = entry.getKey();
            LifePatternPlanetPosition pos = entry.getValue();
            if (!isPrimaryPlanet(planet) || pos == null || pos.getSign() == null) continue;

            if (pos.getSign().equalsIgnoreCase(ascSign)) {
                ChildrenEvidenceObject ev = base(planet, "D7", "LORD_RELATION",
                        "D7_ASC_LORD", planet, "D7 Asc Lord " + planet + " in " + pos.getSign()
                                + (pos.getHouse() != null ? " H" + pos.getHouse() : ""));
                ev.setAuthorityRole("SPECIALIST");
                ev.setEvidenceLane("CHILDREN_SPECIALIST");
                ev.setRootKey("D7:PLANET:" + planet + ":FUNCTION_ASC_LORD:PLACEMENT");
                ev.setIndependenceClusterKey("D7_IDENTITY");
                ev.setSemanticCorrelationKey("PARENT_ROLE:" + planet);
                ev.setClaimEligibility(new ArrayList<>(Arrays.asList("C04", "C05", "C06", "C07", "C08")));
                ev.setFunctionalCondition(inferD1Condition(planet));
                ev.setSemanticTags(new ArrayList<>(PlanetTagRegistry.getTagsByCondition(
                        planet, ev.getFunctionalCondition())));
                all.add(ev);
            }
        }
    }

    /**
     * D7 5H/5L - Specialist 核心
     */
    private void buildD7ChildHouseAndLord(LifePatternNatalChart d7, List<ChildrenEvidenceObject> all) {
        String ascSign = d7.getAscendantSign();
        if (ascSign == null) return;
        int house5SignIdx = (signIndex(ascSign) + 4) % 12;
        String house5Sign = SIGNS[house5SignIdx];

        for (Map.Entry<String, LifePatternPlanetPosition> entry : d7.getPlanets().entrySet()) {
            String planet = entry.getKey();
            LifePatternPlanetPosition pos = entry.getValue();
            if (!isPrimaryPlanet(planet) || pos == null || pos.getSign() == null) continue;

            boolean is5L = pos.getSign().equalsIgnoreCase(house5Sign);
            boolean in5H = pos.getHouse() != null && pos.getHouse() == 5;
            if (!is5L && !in5H) continue;

            ChildrenEvidenceObject ev = base(planet, "D7", is5L ? "LORD_RELATION" : "HOUSE_OCCUPANCY",
                    is5L ? "D7_5L" : planet, planet,
                    (is5L ? "D7 5L " : "") + planet + " in " + pos.getSign()
                            + (pos.getHouse() != null ? " H" + pos.getHouse() : ""));
            ev.setAuthorityRole("SPECIALIST");
            ev.setEvidenceLane("CHILDREN_SPECIALIST");
            ev.setRootKey("D7:PLANET:" + planet + ":FUNCTION_5L:" + (is5L ? "LORDSHIP" : "OCCUPANCY_H5"));
            ev.setIndependenceClusterKey(is5L ? "D7_CHILD_LORD" : "D7_CHILD_HOUSE");
            ev.setSemanticCorrelationKey("CHILDREN_PATH:" + planet);
            ev.setClaimEligibility(new ArrayList<>(Arrays.asList("C02", "C03", "C04", "C05", "C06", "C07", "C08")));
            ev.setFunctionalCondition(inferD1Condition(planet));
            ev.setSemanticTags(new ArrayList<>(PlanetTagRegistry.getTagsByCondition(
                    planet, ev.getFunctionalCondition())));
            all.add(ev);
        }
    }

    /**
     * D7 Jupiter - 需与 D7 Asc/5H/5L 桥接才可投票
     */
    private void buildD7Jupiter(LifePatternNatalChart d7, List<ChildrenEvidenceObject> all) {
        LifePatternPlanetPosition jup = d7.getPlanets().get("Jupiter");
        if (jup == null || jup.getSign() == null) return;

        ChildrenEvidenceObject ev = base("Jupiter", "D7", "NATURAL_ANCHOR",
                "D7_JUPITER", "Jupiter", "D7 Jupiter in " + jup.getSign()
                        + (jup.getHouse() != null ? " H" + jup.getHouse() : ""));
        ev.setAuthorityRole("SPECIALIST");
        ev.setEvidenceLane("CHILDREN_SPECIALIST");
        ev.setRootKey("D7:PLANET:JUPITER:NATURAL_JUPITER:PLACEMENT");
        ev.setIndependenceClusterKey("D7_JUPITER");
        ev.setSemanticCorrelationKey("NURTURING:JUPITER");
        ev.setClaimEligibility(new ArrayList<>(Arrays.asList("C04", "C05", "C07", "C08")));
        ev.setFunctionalCondition("SUPPORTIVE");
        ev.setSemanticTags(new ArrayList<>(PlanetTagRegistry.getSupportTags("Jupiter")));
        all.add(ev);
    }

    /**
     * D7 7H/7L - C09
     */
    private void buildD7Coparenting(LifePatternNatalChart d7, List<ChildrenEvidenceObject> all) {
        String ascSign = d7.getAscendantSign();
        if (ascSign == null) return;
        int house7SignIdx = (signIndex(ascSign) + 6) % 12;
        String house7Sign = SIGNS[house7SignIdx];

        for (Map.Entry<String, LifePatternPlanetPosition> entry : d7.getPlanets().entrySet()) {
            String planet = entry.getKey();
            LifePatternPlanetPosition pos = entry.getValue();
            if (!isPrimaryPlanet(planet) || pos == null || pos.getSign() == null) continue;

            boolean is7L = pos.getSign().equalsIgnoreCase(house7Sign);
            boolean in7H = pos.getHouse() != null && pos.getHouse() == 7;
            if (!is7L && !in7H) continue;

            ChildrenEvidenceObject ev = base(planet, "D7", is7L ? "LORD_RELATION" : "HOUSE_OCCUPANCY",
                    is7L ? "D7_7L" : planet, planet,
                    (is7L ? "D7 7L " : "") + planet + " in " + pos.getSign());
            ev.setAuthorityRole("SPECIALIST");
            ev.setEvidenceLane("CHILDREN_SPECIALIST");
            ev.setRootKey("D7:PLANET:" + planet + ":COPARENTING:" + (is7L ? "LORD_7" : "OCCUPANCY_7"));
            ev.setIndependenceClusterKey("D7_COPARENTING");
            ev.setSemanticCorrelationKey("COPARENTING:" + planet);
            ev.setClaimEligibility(new ArrayList<>(Collections.singletonList("C09")));
            ev.setFunctionalCondition(inferD1Condition(planet));
            List<String> tags = new ArrayList<>(PlanetTagRegistry.getTagsByCondition(planet, ev.getFunctionalCondition()));
            tags.add("shared_duty");
            ev.setSemanticTags(tags);
            all.add(ev);
        }
    }

    // ============================================================
    // D9 限权验证
    // ============================================================

    private void buildD9Validation(LifePatternNatalChart d9, List<ChildrenEvidenceObject> all) {
        for (Map.Entry<String, LifePatternPlanetPosition> entry : d9.getPlanets().entrySet()) {
            String planet = entry.getKey();
            LifePatternPlanetPosition pos = entry.getValue();
            if (!isPrimaryPlanet(planet) || pos == null || pos.getSign() == null) continue;

            // 仅验证 D1/D7 已使用的关键行星（Jupiter / Moon / Venus）
            if (!Arrays.asList("Jupiter", "Moon", "Venus").contains(planet)) continue;

            ChildrenEvidenceObject ev = base(planet, "D9", "D9_VALIDATOR",
                    planet, planet, "D9 " + planet + " in " + pos.getSign());
            ev.setAuthorityRole("VALIDATION_ONLY");
            ev.setEvidenceLane("MATURITY_VALIDATION");
            ev.setRootKey("D9:PLANET:" + planet + ":MATURITY_VALIDATION");
            ev.setIndependenceClusterKey("D9_MATURITY");
            ev.setNatalVoteEligibility("INELIGIBLE_VALIDATION");
            ev.setPolarity("CONTEXT_ONLY");
            ev.setFunctionalCondition("CONTEXT_ONLY");
            ev.setClaimEligibility(new ArrayList<>(Collections.singletonList("C09")));
            ev.getNotes().add("validation only - cannot vote");
            all.add(ev);
        }
    }

    // ============================================================
    // Shadbala / PK
    // ============================================================

    private void buildShadbalaModifiers(LifePatternChartData chartData, List<ChildrenEvidenceObject> all) {
        for (Map.Entry<String, cn.dct.zpback.entity.lifepattern.LifePatternShadbalaData> entry
                : chartData.getShadbala().entrySet()) {
            String planet = entry.getKey();
            // 仅 Sun-Saturn；Rahu/Ketu 不适用
            if (!Arrays.asList("Sun", "Moon", "Mars", "Mercury", "Jupiter", "Venus", "Saturn").contains(planet)) {
                continue;
            }
            Double score = entry.getValue() != null ? entry.getValue().getScore() : null;
            String modifier = scoreToModifier(score);

            ChildrenEvidenceObject ev = base(planet, "SHADBALA", "SHADBALA_MODIFIER",
                    planet, planet, planet + " Shadbala " + modifier);
            ev.setAuthorityRole("MODIFIER");
            ev.setEvidenceLane("EXPRESSION_MODIFIER");
            ev.setRootKey("SHADBALA:" + planet + ":MODIFIER");
            ev.setIndependenceClusterKey("SHADBALA_MODIFIER");
            ev.setNatalVoteEligibility("INELIGIBLE_MODIFIER");
            ev.setPolarity("CONTEXT_ONLY");
            ev.setFunctionalCondition("CONTEXT_ONLY");
            ev.setStrengthModifier(modifier);
            ev.setClaimEligibility(new ArrayList<>(DIRECT_CLAIMS));
            ev.getNotes().add("modifier only - cannot vote or change confidence");
            all.add(ev);
        }
    }

    private String scoreToModifier(Double score) {
        if (score == null) return "NOT_APPLICABLE";
        if (score >= 1.3) return "STRONG";
        if (score >= 1.0) return "ABOVE_REQUIRED";
        if (score >= 0.8) return "NEAR_REQUIRED";
        return "BELOW_REQUIRED";
    }

    private void buildPutrakarakaAlias(LifePatternChartData chartData, String akPlanet, List<ChildrenEvidenceObject> all) {
        // PK = Chara Karaka 中度数排序后对应子女议题的行星（简化：使用 AK 行星作为 PK 别名源）
        // PK 是角色别名，不增加 root
        ChildrenEvidenceObject ev = base(akPlanet, "JAIMINI_ROLE", "ROLE_ALIAS",
                akPlanet, "PUTRAKARAKA", akPlanet + " as Putrakaraka (alias)");
        ev.setAuthorityRole("CORROBORATIVE");
        ev.setEvidenceLane("ROLE_SUPPORT");
        ev.setRootKey("D1:PLANET:" + akPlanet + ":PUTRAKARAKA_ALIAS");
        ev.setIndependenceClusterKey("D1_CHILD_LORD");
        ev.setDependencyStatus("ALIAS");
        ev.setNatalVoteEligibility("INELIGIBLE_QUALIFIER");
        ev.setPolarity("CONTEXT_ONLY");
        ev.setFunctionalCondition("CONTEXT_ONLY");
        ev.getRoleAliases().add("PUTRAKARAKA");
        ev.setClaimEligibility(new ArrayList<>(Arrays.asList("C04", "C05", "C10")));
        ev.getNotes().add("alias - same physical root, no extra vote");
        all.add(ev);
    }

    // ============================================================
    // 去重 / 候选匹配 / 一根一方向
    // ============================================================

    /**
     * 同根去重：相同 rootKey 保留第一条，其余标记 SUPPRESSED
     */
    private void deduplicateSameRoots(List<ChildrenEvidenceObject> all) {
        Set<String> seen = new HashSet<>();
        for (ChildrenEvidenceObject ev : all) {
            if (!"ELIGIBLE".equals(ev.getEvidenceStatus())) continue;
            if (!seen.add(ev.getRootKey())) {
                ev.setEvidenceStatus("SUPPRESSED");
                ev.getNotes().add("duplicate root: " + ev.getRootKey());
            }
        }
    }

    /**
     * 候选匹配：通过语义标签桥接映射候选方向
     */
    private void matchCandidates(List<ChildrenEvidenceObject> all) {
        for (ChildrenEvidenceObject ev : all) {
            if (!"ELIGIBLE".equals(ev.getEvidenceStatus())) continue;
            if ("CONTEXT_ONLY".equals(ev.getPolarity())) continue;

            Map<String, String> matched = new LinkedHashMap<>();
            for (String tag : ev.getSemanticTags()) {
                for (Map.Entry<String, Map<String, String>> bridge : tagBridges()) {
                    String claim = bridge.getKey();
                    if (!ev.getClaimEligibility().contains(claim)) continue;
                    String candidate = bridge.getValue().get(tag);
                    if (candidate != null && !matched.containsKey(claim)) {
                        matched.put(claim, candidate);
                    }
                }
            }
            // 保存首个候选方向到 normalized_feature 的扩展信息
            if (!matched.isEmpty()) {
                Map.Entry<String, String> first = matched.entrySet().iterator().next();
                ev.getNotes().add("candidate_match:" + first.getKey() + "=" + first.getValue());
            }
        }
    }

    private List<Map.Entry<String, Map<String, String>>> bridgeListCache;

    private List<Map.Entry<String, Map<String, String>>> tagBridges() {
        if (bridgeListCache == null) {
            List<Map.Entry<String, Map<String, String>>> list = new ArrayList<>();
            list.add(new AbstractMap.SimpleEntry<>("C02", PlanetTagRegistry.C02_TAG_BRIDGE));
            list.add(new AbstractMap.SimpleEntry<>("C03", PlanetTagRegistry.C03_TAG_BRIDGE));
            list.add(new AbstractMap.SimpleEntry<>("C04", PlanetTagRegistry.C04_TAG_BRIDGE));
            list.add(new AbstractMap.SimpleEntry<>("C05", PlanetTagRegistry.C05_TAG_BRIDGE));
            list.add(new AbstractMap.SimpleEntry<>("C06", PlanetTagRegistry.C06_TAG_BRIDGE));
            list.add(new AbstractMap.SimpleEntry<>("C07", PlanetTagRegistry.C07_TAG_BRIDGE));
            list.add(new AbstractMap.SimpleEntry<>("C09", PlanetTagRegistry.C09_TAG_BRIDGE));
            bridgeListCache = list;
        }
        return bridgeListCache;
    }

    /**
     * 一根一方向：同一 root 对同一 Claim 只能支持一个候选
     */
    private void enforceOneRootOneDirection(List<ChildrenEvidenceObject> all) {
        Map<String, Set<String>> rootClaimCandidates = new HashMap<>();
        for (ChildrenEvidenceObject ev : all) {
            if (!"ELIGIBLE".equals(ev.getEvidenceStatus())) continue;
            for (String note : ev.getNotes()) {
                if (note.startsWith("candidate_match:")) {
                    String[] parts = note.substring("candidate_match:".length()).split("=");
                    if (parts.length == 2) {
                        String key = ev.getRootKey() + ":" + parts[0];
                        Set<String> candidates = rootClaimCandidates.computeIfAbsent(key, k -> new HashSet<>());
                        if (!candidates.add(parts[1]) && candidates.size() > 1) {
                            ev.setEvidenceStatus("AMBIGUOUS_SUPPRESSED");
                            ev.getNotes().add("ONE_ROOT_ONE_DIRECTION violation on " + key);
                        }
                    }
                }
            }
        }
    }

    // ============================================================
    // 工具方法
    // ============================================================

    private ChildrenEvidenceObject base(String subject, String chartSource, String kind,
                                         String anchor, String target, String rawFact) {
        ChildrenEvidenceObject ev = new ChildrenEvidenceObject();
        ev.setEvidenceId("EV_" + chartSource + "_" + anchor + "_" + UUID.randomUUID().toString().substring(0, 8).toUpperCase());
        ev.setChartSource(chartSource);
        ev.setEvidenceKind(kind);
        ev.setSubject(subject);
        ev.setTarget(target);
        ev.setRawFact(rawFact);
        ev.setAnchorKey(chartSource + ":" + anchor);
        ev.setDependencyStatus("INDEPENDENT");
        ev.setEvidenceStatus("ELIGIBLE");
        ev.setNatalVoteEligibility("ELIGIBLE");
        ev.setPolarity("SUPPORT");
        ev.setStrengthModifier("NOT_APPLICABLE");
        return ev;
    }

    private boolean isPrimaryPlanet(String planet) {
        return PRIMARY_PLANETS.contains(planet);
    }

    private int signIndex(String sign) {
        if (sign == null) return -1;
        for (int i = 0; i < SIGNS.length; i++) {
            if (SIGNS[i].equalsIgnoreCase(sign.trim())) return i;
        }
        return -1;
    }

    /**
     * 推断 D1 功能条件：吉性结构支持 / 压力结构约束
     * 简化实现：Saturn/Mars 倾向 CONSTRAINING，其余倾向 SUPPORTIVE
     */
    private String inferD1Condition(String planet) {
        if ("Saturn".equals(planet) || "Mars".equals(planet)) return "CONSTRAINING";
        return "SUPPORTIVE";
    }

    private String inferConditionFromTags(String planet, List<String> bridgeTags) {
        boolean hasPressure = false;
        for (String tag : bridgeTags) {
            if (Arrays.asList("duty_routine", "uncertainty", "nonlinear_path", "responsibility").contains(tag)) {
                hasPressure = true;
            }
        }
        return hasPressure ? "CONSTRAINING" : inferD1Condition(planet);
    }

    /**
     * 整宫制补齐宫位
     */
    private void fillMissingHouses(LifePatternNatalChart chart) {
        if (chart == null || chart.getPlanets() == null || chart.getAscendantSign() == null) return;
        int ascIdx = signIndex(chart.getAscendantSign());
        if (ascIdx < 0) return;
        for (LifePatternPlanetPosition pos : chart.getPlanets().values()) {
            if (pos == null || pos.getHouse() != null) continue;
            int signIdx = signIndex(pos.getSign());
            if (signIdx < 0) continue;
            pos.setHouse(((signIdx - ascIdx + 12) % 12) + 1);
        }
    }
}
