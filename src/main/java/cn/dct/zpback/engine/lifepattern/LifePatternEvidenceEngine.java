package cn.dct.zpback.engine.lifepattern;

import cn.dct.zpback.entity.lifepattern.*;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.*;

/**
 * 人生课题报告 - Evidence 引擎
 * 符合 SEALED 文档 01 第 5-6 节规范
 *
 * 职责：
 * 1. 将原始星盘数据标准化为 EvidenceObject
 * 2. 按 7 类 Semantic Family 分类
 * 3. Root 去重（One Root → One Vote-Bearing Direction per Claim）
 * 4. Role Alias 合并
 */
@Component
@Slf4j
public class LifePatternEvidenceEngine {

    /**
     * 构建所有 Evidence
     */
    public List<EvidenceObject> buildEvidence(LifePatternChartData chartData) {
        List<EvidenceObject> evidenceList = new ArrayList<>();

        if (chartData == null || chartData.getD1() == null) {
            log.warn("D1 数据缺失，无法构建 Evidence");
            return evidenceList;
        }

        // 0. 补齐行星宫位（VedicAstro 响应可能缺失 house 字段）
        // 整宫制：以 D1 上升星座为第 1 宫起点计算
        fillMissingHouses(chartData.getD1());

        // 1. D1 行星落点 Evidence
        buildPlanetPlacementEvidence(chartData.getD1(), evidenceList);

        // 2. D1 宫主落点 Evidence
        buildHouseLordPlacementEvidence(chartData.getD1(), evidenceList);

        // 3. D1 行星关系 Evidence（合相 + Graha Drishti）
        buildPlanetaryRelationEvidence(chartData.getD1(), evidenceList);

        // 4. D1 上升结构 Evidence
        buildAscendantStructureEvidence(chartData.getD1(), evidenceList);

        // 5. D1 月亮结构 Evidence
        buildMoonStructureEvidence(chartData.getD1(), evidenceList);

        // 6. Node Axis Evidence
        buildNodeAxisEvidence(chartData.getD1(), evidenceList);

        // 7. Jaimini Role Alias（AK 等）
        buildJaiminiRoleEvidence(chartData, evidenceList);

        // 8. Shadbala Modifier
        buildShadbalaModifierEvidence(chartData, evidenceList);

        // 9. D9 Validator
        buildD9ValidatorEvidence(chartData, evidenceList);

        // 10. Dasha Activator
        buildDashaActivatorEvidence(chartData, evidenceList);

        // 去重
        return deduplicateEvidence(evidenceList);
    }

    /**
     * D1 行星落点 Evidence
     */
    private void buildPlanetPlacementEvidence(LifePatternNatalChart d1, List<EvidenceObject> evidenceList) {
        if (d1.getPlanets() == null) return;

        for (Map.Entry<String, LifePatternPlanetPosition> entry : d1.getPlanets().entrySet()) {
            String planet = entry.getKey();
            LifePatternPlanetPosition pos = entry.getValue();
            if (pos == null || pos.getSign() == null) continue;

            EvidenceObject ev = new EvidenceObject();
            ev.setEvidenceId(UUID.randomUUID().toString());
            ev.setChartSource("D1");
            ev.setEvidenceKind("PLANET_PLACEMENT");
            ev.setSubject(planet);
            ev.setTarget(pos.getSign() + "_" + pos.getHouse());
            ev.setRawFact(planet + " in " + pos.getSign() + " in House " + pos.getHouse());
            ev.setNormalizedFeature(planet + "_PLACEMENT");
            ev.setAuthorityRole(determineAuthorityRole(planet));
            ev.setAnchorKey(planet + "_IN_" + pos.getSign() + "_" + pos.getHouse());
            ev.setIndependenceClusterKey(planet + "_PLACEMENT");
            ev.setSemanticCorrelationKey(planet + "_" + pos.getSign());
            ev.setDependencyStatus("INDEPENDENT");
            ev.setEvidenceStatus("ACTIVE");
            ev.setEvidenceLane("NATAL_CLAIM");
            ev.setNatalVoteEligibility(isPrimaryEligible(planet) ? "P" : "S");
            ev.setClaimEligibility("P");
            ev.setPolarity("NEUTRAL");
            ev.setStrengthModifier("MODERATE");

            // 语义标签
            List<String> tags = new ArrayList<>(PlanetFunctionRegistry.getCoreFunctionTags(planet));
            tags.addAll(SignExpressionRegistry.getSignTags(pos.getSign()));
            ev.setSemanticTags(tags);

            // 7 Family 分类
            ev.setSemanticFamily(classifySemanticFamily(planet));

            evidenceList.add(ev);
        }
    }

    /**
     * D1 宫主落点 Evidence
     */
    private void buildHouseLordPlacementEvidence(LifePatternNatalChart d1, List<EvidenceObject> evidenceList) {
        if (d1.getHouses() == null) return;

        for (int h = 1; h <= 12; h++) {
            LifePatternHouseInfo hi = d1.getHouses().get(String.valueOf(h));
            if (hi == null || hi.getLord() == null) continue;

            String lord = hi.getLord();
            // 查找该宫主行星的位置
            LifePatternPlanetPosition lordPos = d1.getPlanets() != null ? d1.getPlanets().get(lord) : null;
            if (lordPos == null || lordPos.getSign() == null) continue;

            EvidenceObject ev = new EvidenceObject();
            ev.setEvidenceId(UUID.randomUUID().toString());
            ev.setChartSource("D1");
            ev.setEvidenceKind("HOUSE_LORD_PLACEMENT");
            ev.setSubject(lord);
            ev.setTarget("LORD_OF_" + h + "_IN_" + lordPos.getSign() + "_" + lordPos.getHouse());
            ev.setRawFact(lord + " is Lord of House " + h + ", placed in " + lordPos.getSign() + " in House " + lordPos.getHouse());
            ev.setNormalizedFeature(lord + "_HOUSE_LORD_FUNCTION");
            ev.setAuthorityRole("HOUSE_LORD_" + h);
            ev.setAnchorKey(lord + "_LORD_OF_" + h + "_IN_" + lordPos.getSign() + "_" + lordPos.getHouse());
            ev.setIndependenceClusterKey(lord + "_LORD_OF_" + h);
            ev.setSemanticCorrelationKey(lord + "_" + h + "_" + lordPos.getSign());
            ev.setDependencyStatus("INDEPENDENT");
            ev.setEvidenceStatus("ACTIVE");
            ev.setEvidenceLane("NATAL_CLAIM");
            ev.setNatalVoteEligibility(isPrimaryEligible(lord) ? "P" : "S");
            ev.setClaimEligibility("P");
            ev.setPolarity("NEUTRAL");
            ev.setStrengthModifier("MODERATE");

            List<String> tags = new ArrayList<>(PlanetFunctionRegistry.getCoreFunctionTags(lord));
            tags.add("HOUSE_LORD_" + h);
            ev.setSemanticTags(tags);
            ev.setSemanticFamily(classifySemanticFamily(lord));

            evidenceList.add(ev);
        }
    }

    /**
     * D1 行星关系 Evidence（合相 + Graha Drishti）
     */
    private void buildPlanetaryRelationEvidence(LifePatternNatalChart d1, List<EvidenceObject> evidenceList) {
        if (d1.getPlanets() == null) return;

        List<String> planetNames = new ArrayList<>(d1.getPlanets().keySet());

        for (int i = 0; i < planetNames.size(); i++) {
            for (int j = i + 1; j < planetNames.size(); j++) {
                String p1 = planetNames.get(i);
                String p2 = planetNames.get(j);
                LifePatternPlanetPosition pos1 = d1.getPlanets().get(p1);
                LifePatternPlanetPosition pos2 = d1.getPlanets().get(p2);

                if (pos1 == null || pos2 == null) continue;

                // 合相（同星座）
                if (pos1.getSign() != null && pos1.getSign().equals(pos2.getSign())) {
                    EvidenceObject ev = createRelationEvidence(p1, p2, pos1.getSign(), "CONJUNCTION", d1);
                    evidenceList.add(ev);
                }

                // Graha Drishti（相位）- 宫位缺失时跳过
                Integer house1 = pos1.getHouse();
                Integer house2 = pos2.getHouse();
                if (house1 != null && house2 != null) {
                    String aspect = AspectEngine.getGrahaDrishti(p1, house1, house2);
                    if (aspect != null) {
                        EvidenceObject ev = createRelationEvidence(p1, p2, aspect, "ASPECT", d1);
                        evidenceList.add(ev);
                    }
                }
            }
        }
    }

    /**
     * 整宫制宫位序号表
     */
    private static final String[] SIGNS = {
        "Aries", "Taurus", "Gemini", "Cancer", "Leo", "Virgo",
        "Libra", "Scorpio", "Sagittarius", "Capricorn", "Aquarius", "Pisces"
    };

    private int signIndex(String sign) {
        if (sign == null) return -1;
        for (int i = 0; i < SIGNS.length; i++) {
            if (SIGNS[i].equalsIgnoreCase(sign.trim())) return i;
        }
        return -1;
    }

    /**
     * 补齐缺失的行星宫位（整宫制：上升星座 = 第 1 宫）
     */
    private void fillMissingHouses(LifePatternNatalChart d1) {
        if (d1 == null || d1.getPlanets() == null || d1.getAscendantSign() == null) return;

        int ascIdx = signIndex(d1.getAscendantSign());
        if (ascIdx < 0) return;

        for (LifePatternPlanetPosition pos : d1.getPlanets().values()) {
            if (pos == null || pos.getHouse() != null) continue;
            int signIdx = signIndex(pos.getSign());
            if (signIdx < 0) continue;
            int house = ((signIdx - ascIdx + 12) % 12) + 1;
            pos.setHouse(house);
        }
    }

    private EvidenceObject createRelationEvidence(String p1, String p2, String relation, String kind, LifePatternNatalChart d1) {
        EvidenceObject ev = new EvidenceObject();
        ev.setEvidenceId(UUID.randomUUID().toString());
        ev.setChartSource("D1");
        ev.setEvidenceKind(kind.equals("CONJUNCTION") ? "PLANETARY_CONJUNCTION" : "PLANETARY_ASPECT");
        ev.setSubject(p1);
        ev.setTarget(p2);
        ev.setRawFact(p1 + " " + relation + " " + p2);
        ev.setNormalizedFeature(p1 + "_" + relation + "_" + p2);
        ev.setAuthorityRole("PLANETARY_RELATION");
        ev.setAnchorKey(p1 + "_" + relation + "_" + p2);
        ev.setIndependenceClusterKey(p1 + "_" + p2 + "_RELATION");
        ev.setSemanticCorrelationKey(p1 + "_" + p2);
        ev.setDependencyStatus("INDEPENDENT");
        ev.setEvidenceStatus("ACTIVE");
        ev.setEvidenceLane("NATAL_CLAIM");
        ev.setNatalVoteEligibility("S");
        ev.setClaimEligibility("S");
        ev.setPolarity("NEUTRAL");
        ev.setStrengthModifier("MODERATE");

        List<String> tags = new ArrayList<>();
        tags.addAll(PlanetFunctionRegistry.getCoreFunctionTags(p1));
        tags.addAll(PlanetFunctionRegistry.getCoreFunctionTags(p2));
        ev.setSemanticTags(tags);
        ev.setSemanticFamily(classifySemanticFamily(p1 + "_" + p2));

        return ev;
    }

    /**
     * D1 上升结构 Evidence
     */
    private void buildAscendantStructureEvidence(LifePatternNatalChart d1, List<EvidenceObject> evidenceList) {
        if (d1.getAscendantSign() == null) return;

        EvidenceObject ev = new EvidenceObject();
        ev.setEvidenceId(UUID.randomUUID().toString());
        ev.setChartSource("D1");
        ev.setEvidenceKind("ASCENDANT_STRUCTURE");
        ev.setSubject("Ascendant");
        ev.setTarget(d1.getAscendantSign());
        ev.setRawFact("Ascendant in " + d1.getAscendantSign());
        ev.setNormalizedFeature("ASCENDANT_" + d1.getAscendantSign());
        ev.setAuthorityRole("ASCENDANT");
        ev.setAnchorKey("ASCENDANT_" + d1.getAscendantSign());
        ev.setIndependenceClusterKey("ASCENDANT_STRUCTURE");
        ev.setSemanticCorrelationKey("ASCENDANT_" + d1.getAscendantSign());
        ev.setDependencyStatus("INDEPENDENT");
        ev.setEvidenceStatus("ACTIVE");
        ev.setEvidenceLane("NATAL_CLAIM");
        ev.setNatalVoteEligibility("P");
        ev.setClaimEligibility("P");
        ev.setPolarity("NEUTRAL");
        ev.setStrengthModifier("MODERATE");

        List<String> tags = new ArrayList<>();
        tags.add("IDENTITY");
        tags.add("SELF_EXPRESSION");
        tags.addAll(SignExpressionRegistry.getSignTags(d1.getAscendantSign()));
        ev.setSemanticTags(tags);
        ev.setSemanticFamily("A_SELF_IDENTITY");

        evidenceList.add(ev);
    }

    /**
     * D1 月亮结构 Evidence
     */
    private void buildMoonStructureEvidence(LifePatternNatalChart d1, List<EvidenceObject> evidenceList) {
        if (d1.getPlanets() == null) return;
        LifePatternPlanetPosition moon = d1.getPlanets().get("Moon");
        if (moon == null || moon.getSign() == null) return;

        EvidenceObject ev = new EvidenceObject();
        ev.setEvidenceId(UUID.randomUUID().toString());
        ev.setChartSource("D1");
        ev.setEvidenceKind("MOON_STRUCTURE");
        ev.setSubject("Moon");
        ev.setTarget(moon.getSign() + "_" + moon.getHouse());
        ev.setRawFact("Moon in " + moon.getSign() + " in House " + moon.getHouse());
        ev.setNormalizedFeature("MOON_" + moon.getSign() + "_" + moon.getHouse());
        ev.setAuthorityRole("MOON");
        ev.setAnchorKey("MOON_IN_" + moon.getSign() + "_" + moon.getHouse());
        ev.setIndependenceClusterKey("MOON_PLACEMENT");
        ev.setSemanticCorrelationKey("MOON_" + moon.getSign());
        ev.setDependencyStatus("INDEPENDENT");
        ev.setEvidenceStatus("ACTIVE");
        ev.setEvidenceLane("NATAL_CLAIM");
        ev.setNatalVoteEligibility("P");
        ev.setClaimEligibility("P");
        ev.setPolarity("NEUTRAL");
        ev.setStrengthModifier("MODERATE");

        List<String> tags = new ArrayList<>(PlanetFunctionRegistry.getCoreFunctionTags("Moon"));
        tags.addAll(SignExpressionRegistry.getSignTags(moon.getSign()));
        ev.setSemanticTags(tags);
        ev.setSemanticFamily("B_EMOTIONAL_SECURITY");

        evidenceList.add(ev);
    }

    /**
     * Node Axis Evidence
     */
    private void buildNodeAxisEvidence(LifePatternNatalChart d1, List<EvidenceObject> evidenceList) {
        if (d1.getPlanets() == null) return;
        LifePatternPlanetPosition rahu = d1.getPlanets().get("Rahu");
        LifePatternPlanetPosition ketu = d1.getPlanets().get("Ketu");

        if (rahu != null && rahu.getSign() != null) {
            EvidenceObject ev = new EvidenceObject();
            ev.setEvidenceId(UUID.randomUUID().toString());
            ev.setChartSource("D1");
            ev.setEvidenceKind("NODE_AXIS");
            ev.setSubject("Rahu");
            ev.setTarget(rahu.getSign() + "_" + rahu.getHouse());
            ev.setRawFact("Rahu in " + rahu.getSign() + " in House " + rahu.getHouse());
            ev.setNormalizedFeature("RAHU_" + rahu.getSign());
            ev.setAuthorityRole("RAHU");
            ev.setAnchorKey("RAHU_IN_" + rahu.getSign() + "_" + rahu.getHouse());
            ev.setIndependenceClusterKey("RAHU_NODE");
            ev.setSemanticCorrelationKey("RAHU_" + rahu.getSign());
            ev.setDependencyStatus("INDEPENDENT");
            ev.setEvidenceStatus("ACTIVE");
            ev.setEvidenceLane("NATAL_CLAIM");
            ev.setNatalVoteEligibility("S");
            ev.setClaimEligibility("S");
            ev.setPolarity("NEUTRAL");
            ev.setStrengthModifier("MODERATE");

            List<String> tags = new ArrayList<>(PlanetFunctionRegistry.getCoreFunctionTags("Rahu"));
            ev.setSemanticTags(tags);
            ev.setSemanticFamily("G_NODE_DYNAMICS");

            evidenceList.add(ev);
        }

        if (ketu != null && ketu.getSign() != null) {
            EvidenceObject ev = new EvidenceObject();
            ev.setEvidenceId(UUID.randomUUID().toString());
            ev.setChartSource("D1");
            ev.setEvidenceKind("NODE_AXIS");
            ev.setSubject("Ketu");
            ev.setTarget(ketu.getSign() + "_" + ketu.getHouse());
            ev.setRawFact("Ketu in " + ketu.getSign() + " in House " + ketu.getHouse());
            ev.setNormalizedFeature("KETU_" + ketu.getSign());
            ev.setAuthorityRole("KETU");
            ev.setAnchorKey("KETU_IN_" + ketu.getSign() + "_" + ketu.getHouse());
            ev.setIndependenceClusterKey("KETU_NODE");
            ev.setSemanticCorrelationKey("KETU_" + ketu.getSign());
            ev.setDependencyStatus("INDEPENDENT");
            ev.setEvidenceStatus("ACTIVE");
            ev.setEvidenceLane("NATAL_CLAIM");
            ev.setNatalVoteEligibility("S");
            ev.setClaimEligibility("S");
            ev.setPolarity("NEUTRAL");
            ev.setStrengthModifier("MODERATE");

            List<String> tags = new ArrayList<>(PlanetFunctionRegistry.getCoreFunctionTags("Ketu"));
            ev.setSemanticTags(tags);
            ev.setSemanticFamily("G_NODE_DYNAMICS");

            evidenceList.add(ev);
        }
    }

    /**
     * Jaimini Role Alias Evidence（AK 等）
     */
    private void buildJaiminiRoleEvidence(LifePatternChartData chartData, List<EvidenceObject> evidenceList) {
        if (chartData.getAkPlanet() == null) return;

        String ak = chartData.getAkPlanet();
        LifePatternNatalChart d1 = chartData.getD1();
        if (d1 == null || d1.getPlanets() == null) return;
        LifePatternPlanetPosition akPos = d1.getPlanets().get(ak);
        if (akPos == null) return;

        EvidenceObject ev = new EvidenceObject();
        ev.setEvidenceId(UUID.randomUUID().toString());
        ev.setChartSource("JAIMINI_ROLE");
        ev.setEvidenceKind("JAIMINI_ROLE_ALIAS");
        ev.setSubject(ak);
        ev.setTarget("AK_" + akPos.getSign() + "_" + akPos.getHouse());
        ev.setRawFact("AK (Atmakaraka) = " + ak + " in " + akPos.getSign());
        ev.setNormalizedFeature("AK_" + ak);
        ev.setAuthorityRole("ATMAKARAKA");
        ev.setAnchorKey("AK_" + ak);
        ev.setIndependenceClusterKey("AK_ROLE");
        ev.setSemanticCorrelationKey("AK_" + ak);
        ev.setDependencyStatus("DERIVED");
        ev.setParentEvidenceIds(Collections.singletonList(ak + "_PLACEMENT"));
        ev.setEvidenceStatus("ACTIVE");
        ev.setEvidenceLane("DEVELOPMENTAL_SUPPORT");
        ev.setNatalVoteEligibility("S");
        ev.setClaimEligibility("S");
        ev.setPolarity("NEUTRAL");
        ev.setStrengthModifier("MODERATE");

        List<String> tags = new ArrayList<>(PlanetFunctionRegistry.getCoreFunctionTags(ak));
        tags.add("ATMAKARAKA");
        tags.add("DEVELOPMENTAL_SUPPORT");
        ev.setSemanticTags(tags);
        ev.setSemanticFamily("E_BELIEF_MEANING");

        evidenceList.add(ev);
    }

    /**
     * Shadbala Modifier Evidence
     */
    private void buildShadbalaModifierEvidence(LifePatternChartData chartData, List<EvidenceObject> evidenceList) {
        if (chartData.getShadbala() == null) return;

        for (Map.Entry<String, LifePatternShadbalaData> entry : chartData.getShadbala().entrySet()) {
            String planet = entry.getKey();
            LifePatternShadbalaData data = entry.getValue();
            if (data.getExecutionCapacity() == null) continue;

            EvidenceObject ev = new EvidenceObject();
            ev.setEvidenceId(UUID.randomUUID().toString());
            ev.setChartSource("SHADBALA");
            ev.setEvidenceKind("SHADBALA_MODIFIER");
            ev.setSubject(planet);
            ev.setTarget("SHADBALA_" + data.getExecutionCapacity());
            ev.setRawFact(planet + " Shadbala Execution Capacity: " + data.getExecutionCapacity());
            ev.setNormalizedFeature(planet + "_EXECUTION_" + data.getExecutionCapacity());
            ev.setAuthorityRole("SHADBALA_MODIFIER");
            ev.setAnchorKey(planet + "_SHADBALA");
            ev.setIndependenceClusterKey(planet + "_SHADBALA");
            ev.setSemanticCorrelationKey(planet + "_STRENGTH");
            ev.setDependencyStatus("DERIVED");
            ev.setEvidenceStatus("ACTIVE");
            ev.setEvidenceLane("EXPRESSION_MODIFIER");
            ev.setNatalVoteEligibility("I");
            ev.setClaimEligibility("M");
            ev.setPolarity("NEUTRAL");
            ev.setStrengthModifier(mapShadbalaToStrengthModifier(data.getExecutionCapacity()));

            List<String> tags = new ArrayList<>();
            tags.add("EXECUTION_CAPACITY");
            tags.add(planet);
            ev.setSemanticTags(tags);
            ev.setSemanticFamily("F_CONSTRAINT_RESPONSIBILITY");

            evidenceList.add(ev);
        }
    }

    /**
     * D9 Validator Evidence
     */
    private void buildD9ValidatorEvidence(LifePatternChartData chartData, List<EvidenceObject> evidenceList) {
        if (chartData.getD9() == null) return;
        LifePatternNatalChart d9 = chartData.getD9();

        if (d9.getAscendantSign() != null) {
            EvidenceObject ev = new EvidenceObject();
            ev.setEvidenceId(UUID.randomUUID().toString());
            ev.setChartSource("D9");
            ev.setEvidenceKind("D9_VALIDATOR");
            ev.setSubject("D9_Ascendant");
            ev.setTarget(d9.getAscendantSign());
            ev.setRawFact("D9 Ascendant in " + d9.getAscendantSign());
            ev.setNormalizedFeature("D9_LAGNA_" + d9.getAscendantSign());
            ev.setAuthorityRole("D9_LAGNA");
            ev.setAnchorKey("D9_LAGNA_" + d9.getAscendantSign());
            ev.setIndependenceClusterKey("D9_LAGNA");
            ev.setSemanticCorrelationKey("D9_LAGNA_" + d9.getAscendantSign());
            ev.setDependencyStatus("DERIVED");
            ev.setEvidenceStatus("ACTIVE");
            ev.setEvidenceLane("MATURITY_VALIDATION");
            ev.setNatalVoteEligibility("S");
            ev.setClaimEligibility("V");
            ev.setPolarity("NEUTRAL");
            ev.setStrengthModifier("MODERATE");

            List<String> tags = new ArrayList<>();
            tags.add("MATURITY_VALIDATION");
            tags.add("D9_LAGNA");
            ev.setSemanticTags(tags);
            ev.setSemanticFamily("A_SELF_IDENTITY");

            evidenceList.add(ev);
        }

        // D9 行星位置
        if (d9.getPlanets() != null) {
            for (Map.Entry<String, LifePatternPlanetPosition> entry : d9.getPlanets().entrySet()) {
                String planet = entry.getKey();
                LifePatternPlanetPosition pos = entry.getValue();
                if (pos == null || pos.getSign() == null) continue;

                EvidenceObject ev = new EvidenceObject();
                ev.setEvidenceId(UUID.randomUUID().toString());
                ev.setChartSource("D9");
                ev.setEvidenceKind("D9_VALIDATOR");
                ev.setSubject(planet);
                ev.setTarget("D9_" + pos.getSign() + "_" + pos.getHouse());
                ev.setRawFact("D9 " + planet + " in " + pos.getSign());
                ev.setNormalizedFeature("D9_" + planet + "_" + pos.getSign());
                ev.setAuthorityRole("D9_VALIDATOR");
                ev.setAnchorKey("D9_" + planet + "_" + pos.getSign());
                ev.setIndependenceClusterKey("D9_" + planet);
                ev.setSemanticCorrelationKey("D9_" + planet + "_" + pos.getSign());
                ev.setDependencyStatus("DERIVED");
                ev.setEvidenceStatus("ACTIVE");
                ev.setEvidenceLane("MATURITY_VALIDATION");
                ev.setNatalVoteEligibility("S");
                ev.setClaimEligibility("V");
                ev.setPolarity("NEUTRAL");
                ev.setStrengthModifier("MODERATE");

                List<String> tags = new ArrayList<>();
                tags.add("MATURITY_VALIDATION");
                tags.add(planet);
                ev.setSemanticTags(tags);
                ev.setSemanticFamily(classifySemanticFamily(planet));

                evidenceList.add(ev);
            }
        }
    }

    /**
     * Dasha Activator Evidence
     */
    private void buildDashaActivatorEvidence(LifePatternChartData chartData, List<EvidenceObject> evidenceList) {
        if (chartData.getDashaList() == null || chartData.getDashaList().isEmpty()) return;

        LifePatternDashaPeriod current = chartData.getDashaList().get(0);

        EvidenceObject ev = new EvidenceObject();
        ev.setEvidenceId(UUID.randomUUID().toString());
        ev.setChartSource("DASHA");
        ev.setEvidenceKind("DASHA_ACTIVATOR");
        ev.setSubject(current.getMahadasha());
        ev.setTarget("DASHA_" + current.getMahadasha() + "_" + current.getAntardasha());
        ev.setRawFact("Current Dasha: " + current.getMahadasha() + " / " + current.getAntardasha());
        ev.setNormalizedFeature("DASHA_ACTIVATION_" + current.getMahadasha());
        ev.setAuthorityRole("DASHA_LORD");
        ev.setAnchorKey("DASHA_" + current.getMahadasha());
        ev.setIndependenceClusterKey("DASHA_" + current.getMahadasha());
        ev.setSemanticCorrelationKey("DASHA_" + current.getMahadasha());
        ev.setDependencyStatus("DERIVED");
        ev.setEvidenceStatus("ACTIVE");
        ev.setEvidenceLane("TIMING_ACTIVATION");
        ev.setNatalVoteEligibility("I");
        ev.setClaimEligibility("M");
        ev.setPolarity("NEUTRAL");
        ev.setStrengthModifier("MODERATE");

        List<String> tags = new ArrayList<>();
        tags.add("TIMING_ACTIVATION");
        tags.add(current.getMahadasha());
        ev.setSemanticTags(tags);
        ev.setSemanticFamily("F_CONSTRAINT_RESPONSIBILITY");

        evidenceList.add(ev);
    }

    // ============================================================
    // 去重
    // ============================================================

    /**
     * Evidence 去重：One Root → One Vote-Bearing Direction per Claim
     */
    private List<EvidenceObject> deduplicateEvidence(List<EvidenceObject> evidenceList) {
        Map<String, EvidenceObject> dedupMap = new LinkedHashMap<>();

        for (EvidenceObject ev : evidenceList) {
            String key = ev.getAnchorKey();
            if (!dedupMap.containsKey(key)) {
                dedupMap.put(key, ev);
            } else {
                // 合并语义标签
                EvidenceObject existing = dedupMap.get(key);
                List<String> mergedTags = new ArrayList<>(existing.getSemanticTags());
                for (String tag : ev.getSemanticTags()) {
                    if (!mergedTags.contains(tag)) {
                        mergedTags.add(tag);
                    }
                }
                existing.setSemanticTags(mergedTags);
            }
        }

        return new ArrayList<>(dedupMap.values());
    }

    // ============================================================
    // 辅助方法
    // ============================================================

    private String determineAuthorityRole(String planet) {
        switch (planet) {
            case "Moon": return "EMOTIONAL_SECURITY_SIGNIFICATOR";
            case "Mars": return "ACTION_PROTECTION_SIGNIFICATOR";
            case "Jupiter": return "MEANING_GROWTH_SIGNIFICATOR";
            case "Venus": return "VALUE_RELATION_SIGNIFICATOR";
            case "Saturn": return "STRUCTURE_RESPONSIBILITY_SIGNIFICATOR";
            case "Sun": return "IDENTITY_DIRECTION_SIGNIFICATOR";
            case "Rahu": return "AMPLIFICATION_SIGNIFICATOR";
            case "Ketu": return "DETACHMENT_SIGNIFICATOR";
            default: return "PLANETARY_FUNCTION";
        }
    }

    private boolean isPrimaryEligible(String planet) {
        return Arrays.asList("Sun", "Moon", "Mars", "Jupiter", "Venus", "Saturn").contains(planet);
    }

    private String classifySemanticFamily(String planet) {
        switch (planet) {
            case "Sun": return "A_SELF_IDENTITY";
            case "Moon": return "B_EMOTIONAL_SECURITY";
            case "Mars": return "C_ACTION_PROTECTION";
            case "Venus": return "D_VALUE_RELATIONAL_REGULATION";
            case "Jupiter": return "E_BELIEF_MEANING";
            case "Saturn": return "F_CONSTRAINT_RESPONSIBILITY";
            case "Rahu":
            case "Ketu":
                return "G_NODE_DYNAMICS";
            default:
                return "A_SELF_IDENTITY";
        }
    }

    private String mapShadbalaToStrengthModifier(String executionCapacity) {
        if (executionCapacity == null) return "MODERATE";
        switch (executionCapacity) {
            case "HIGH": return "STRONG";
            case "ADEQUATE": return "MODERATE";
            case "LOW": return "CONDITIONAL";
            case "VERY_LOW": return "CONDITIONAL";
            default: return "MODERATE";
        }
    }
}
