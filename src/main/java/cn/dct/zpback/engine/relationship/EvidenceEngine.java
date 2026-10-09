package cn.dct.zpback.engine.relationship;

import cn.dct.zpback.constant.*;
import cn.dct.zpback.entity.relationship.*;
import cn.dct.zpback.entity.relationship.CanonicalChartData.NatalChart;
import cn.dct.zpback.entity.relationship.CanonicalChartData.PlanetPosition;
import cn.dct.zpback.entity.relationship.CanonicalChartData.HouseInfo;
import cn.dct.zpback.entity.relationship.CanonicalChartData.ShadbalaData;
import cn.dct.zpback.entity.relationship.CanonicalChartData.NakshatraData;
import cn.dct.zpback.entity.relationship.CanonicalChartData.DashaPeriod;
import cn.dct.zpback.entity.relationship.CanonicalChartData.ULData;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.*;

/**
 * Evidence Engine - 证据引擎 (STEP 1-2H)
 *
 * 从 Canonical Astrology Data 构建 6 类 Evidence Family
 */
@Component
@Slf4j
public class EvidenceEngine {

    private final List<Evidence> evidenceList = new ArrayList<>();

    /**
     * 主入口：从星盘数据构建所有 Evidence
     */
    public List<Evidence> buildEvidence(CanonicalChartData chartData) {
        evidenceList.clear();

        if (chartData == null || chartData.getD1() == null) {
            log.warn("星盘数据为空，返回空证据列表");
            return evidenceList;
        }

        // STEP 2A: D1 恋爱与婚姻结构
        buildD1StructuralEvidence(chartData);

        // STEP 2B: 核心关系 Significators
        buildSignificatorEvidence(chartData);

        // STEP 2C: D9 Marriage Architecture
        buildD9Evidence(chartData);

        // STEP 2D: Partner Archetype / Jaimini / UL
        buildPartnerArchetypeEvidence(chartData);

        // STEP 2E: Shadbala Weighting
        buildShadbalaEvidence(chartData);

        // STEP 2F: Nakshatra Refinement
        buildNakshatraEvidence(chartData);

        // STEP 2G: Yoga & Modifier
        buildModifierEvidence(chartData);

        // STEP 2H: Timing
        buildTimingEvidence(chartData);

        log.info("Evidence 构建完成: total={}", evidenceList.size());
        return Collections.unmodifiableList(evidenceList);
    }

    /**
     * STEP 2A: D1 恋爱与婚姻结构
     */
    private void buildD1StructuralEvidence(CanonicalChartData chartData) {
        NatalChart d1 = chartData.getD1();
        if (d1 == null || d1.getPlanets() == null) return;

        // 5H Sign Base
        String fifthSign = getHouseSign(d1, 5);
        List<String> fifthBase = SignBaseRegistry.get5HSignBase(fifthSign);
        if (!fifthBase.isEmpty()) {
            addEvidence(EvidenceType.STRUCTURAL_EVIDENCE, "5H", "D1_5H",
                    "恋爱领域基调: " + String.join(", ", fifthBase), 0.9, "ROMANCE_FIELD", true);
        }

        // 7H Sign Base
        String seventhSign = getHouseSign(d1, 7);
        List<String> seventhBase = SignBaseRegistry.get7HSignBase(seventhSign);
        if (!seventhBase.isEmpty()) {
            addEvidence(EvidenceType.STRUCTURAL_EVIDENCE, "7H", "D1_7H",
                    "婚姻关系领域基调: " + String.join(", ", seventhBase), 0.9, "PARTNERSHIP_FIELD", true);
        }

        // 5L / 7L
        String fifthLord = getHouseLord(d1, 5);
        String seventhLord = getHouseLord(d1, 7);
        if (fifthLord != null) {
            addEvidence(EvidenceType.STRUCTURAL_EVIDENCE, fifthLord, "D1_5L",
                    "恋爱机制: " + fifthLord, 0.8, "ROMANCE_MECHANISM", false);
        }
        if (seventhLord != null) {
            addEvidence(EvidenceType.STRUCTURAL_EVIDENCE, seventhLord, "D1_7L",
                    "婚姻机制: " + seventhLord, 0.8, "PARTNERSHIP_MECHANISM", false);
        }

        // Romance-Marriage Bridge
        if (fifthLord != null && seventhLord != null) {
            if (fifthLord.equals(seventhLord)) {
                addEvidence(EvidenceType.STRUCTURAL_EVIDENCE, fifthLord, "5L=7L",
                        "恋爱与婚姻宫主共享: " + fifthLord, 0.85, "SHARED_ROMANCE_PARTNERSHIP_LORD", false);
            }
        }
    }

    /**
     * STEP 2B: 核心关系 Significators
     */
    private void buildSignificatorEvidence(CanonicalChartData chartData) {
        NatalChart d1 = chartData.getD1();
        if (d1 == null || d1.getPlanets() == null) return;

        // Venus - Relationship Function Significator
        PlanetPosition venus = d1.getPlanets().get("Venus");
        if (venus != null) {
            String sign = venus.getSign() != null ? venus.getSign() : "";
            addEvidence(EvidenceType.SIGNIFICATOR_EVIDENCE, "Venus", "VENUS",
                    "金星关系功能: " + sign + " - ATTRACTION / AFFECTION / RECIPROCITY / INTIMACY_PREFERENCE",
                    0.85, "RELATIONSHIP_FUNCTION", true);
        }

        // Moon - Emotional Security Significator
        PlanetPosition moon = d1.getPlanets().get("Moon");
        if (moon != null) {
            String sign = moon.getSign() != null ? moon.getSign() : "";
            addEvidence(EvidenceType.SIGNIFICATOR_EVIDENCE, "Moon", "MOON",
                    "月亮情感安全: " + sign + " - EMOTIONAL_NEED / SECURITY / COMFORT / RESPONSIVENESS",
                    0.85, "EMOTIONAL_SECURITY", true);
        }

        // Mars - Relational Drive Significator
        PlanetPosition mars = d1.getPlanets().get("Mars");
        if (mars != null) {
            String sign = mars.getSign() != null ? mars.getSign() : "";
            addEvidence(EvidenceType.SIGNIFICATOR_EVIDENCE, "Mars", "MARS",
                    "火星关系驱动: " + sign + " - DESIRE_STYLE / INITIATIVE / ASSERTION / BOUNDARY_RESPONSE",
                    0.85, "RELATIONAL_DRIVE", true);
        }

        // Jupiter - Values & Repair Significator
        PlanetPosition jupiter = d1.getPlanets().get("Jupiter");
        if (jupiter != null) {
            String sign = jupiter.getSign() != null ? jupiter.getSign() : "";
            addEvidence(EvidenceType.SIGNIFICATOR_EVIDENCE, "Jupiter", "JUPITER",
                    "木星价值与修复: " + sign + " - VALUES / COMMITMENT_MEANING / GROWTH / REPAIR_CAPACITY",
                    0.85, "VALUES_MEANING_REPAIR", true);
        }
    }

    /**
     * STEP 2C: D9 Marriage Architecture
     */
    private void buildD9Evidence(CanonicalChartData chartData) {
        NatalChart d9 = chartData.getD9();
        if (d9 == null) return;

        // D9 Lagna
        if (d9.getAscendantSign() != null) {
            List<String> d9LagnaBase = SignBaseRegistry.getD9LagnaBase(d9.getAscendantSign());
            if (!d9LagnaBase.isEmpty()) {
                addEvidence(EvidenceType.STRUCTURAL_EVIDENCE, "D9_Lagna", "D9_ASC",
                        "婚姻阶段自我基调: " + String.join(", ", d9LagnaBase), 0.85, "MARRIAGE_STAGE_SELF", true);
            }
        }

        // D9 7H
        String d9SeventhSign = getHouseSign(d9, 7);
        if (d9SeventhSign != null) {
            List<String> d9SeventhBase = SignBaseRegistry.getD97HSignBase(d9SeventhSign);
            if (!d9SeventhBase.isEmpty()) {
                addEvidence(EvidenceType.STRUCTURAL_EVIDENCE, "7H", "D9_7H",
                        "成熟伴侣领域: " + String.join(", ", d9SeventhBase), 0.85, "MATURE_PARTNERSHIP_FIELD", true);
            }
        }

        // D9 Significator Maturation
        if (d9.getPlanets() != null) {
            PlanetPosition d9Venus = d9.getPlanets().get("Venus");
            if (d9Venus != null && d9Venus.getSign() != null) {
                addEvidence(EvidenceType.STRUCTURAL_EVIDENCE, "Venus", "D9_VENUS",
                        "D9 金星成熟情感: " + d9Venus.getSign() + " - Mature Affection / Reciprocity",
                        0.8, "MARRIAGE_FUNCTION_MATURATION", false);
            }

            PlanetPosition d9Moon = d9.getPlanets().get("Moon");
            if (d9Moon != null && d9Moon.getSign() != null) {
                addEvidence(EvidenceType.STRUCTURAL_EVIDENCE, "Moon", "D9_MOON",
                        "D9 月亮成熟情感需求: " + d9Moon.getSign() + " - Mature Emotional Need / Adaptation",
                        0.8, "MARRIAGE_FUNCTION_MATURATION", false);
            }

            PlanetPosition d9Mars = d9.getPlanets().get("Mars");
            if (d9Mars != null && d9Mars.getSign() != null) {
                addEvidence(EvidenceType.STRUCTURAL_EVIDENCE, "Mars", "D9_MARS",
                        "D9 火星成熟行动: " + d9Mars.getSign() + " - Mature Assertion / Agency",
                        0.8, "MARRIAGE_FUNCTION_MATURATION", false);
            }

            PlanetPosition d9Jupiter = d9.getPlanets().get("Jupiter");
            if (d9Jupiter != null && d9Jupiter.getSign() != null) {
                addEvidence(EvidenceType.STRUCTURAL_EVIDENCE, "Jupiter", "D9_JUPITER",
                        "D9 木星成熟价值: " + d9Jupiter.getSign() + " - Mature Values / Meaning",
                        0.8, "MARRIAGE_FUNCTION_MATURATION", false);
            }
        }
    }

    /**
     * STEP 2D: Partner Archetype / Jaimini / UL
     */
    private void buildPartnerArchetypeEvidence(CanonicalChartData chartData) {
        String dkPlanet = chartData.getDkPlanet();
        if (dkPlanet == null) {
            dkPlanet = findDKPlanet(chartData.getD1());
        }

        if (dkPlanet != null) {
            // DK Planet Base
            List<String> dkBase = DKPlanetBaseRegistry.getDKPlanetBase(dkPlanet);
            String dkRole = DKPlanetBaseRegistry.getDKPlanetRole(dkPlanet);
            if (!dkBase.isEmpty()) {
                addEvidence(EvidenceType.SIGNIFICATOR_EVIDENCE, dkPlanet, "DK",
                        "伴侣原型核心: " + dkPlanet + " - " + String.join(", ", dkBase),
                        0.85, "PARTNER_ARCHETYPE", true);
                addEvidence(EvidenceType.SIGNIFICATOR_EVIDENCE, dkPlanet, "DK_ROLE",
                        "伴侣激活角色: " + dkRole, 0.8, "PARTNER_ARCHETYPE", false);
            }

            // DK in D9
            NatalChart d9 = chartData.getD9();
            if (d9 != null && d9.getPlanets() != null) {
                PlanetPosition dkInD9 = d9.getPlanets().get(dkPlanet);
                if (dkInD9 != null && dkInD9.getSign() != null) {
                    addEvidence(EvidenceType.SIGNIFICATOR_EVIDENCE, dkPlanet, "DK_D9",
                            "D9 伴侣原型成熟: " + dkPlanet + " in " + dkInD9.getSign(),
                            0.8, "PARTNER_ARCHETYPE_MATRURATION", false);
                }
            }
        }

        // AK×DK
        String akPlanet = chartData.getAkPlanet();
        if (akPlanet != null && dkPlanet != null) {
            addEvidence(EvidenceType.SIGNIFICATOR_EVIDENCE, akPlanet + "_" + dkPlanet, "AK_DK",
                    "自我发展×伴侣关系轴: " + akPlanet + " × " + dkPlanet,
                    0.7, "SELF_PARTNER_AXIS", false);
        }

        // UL
        ULData ul = chartData.getUl();
        if (ul != null && ul.getUlSign() != null) {
            addEvidence(EvidenceType.STRUCTURAL_EVIDENCE, "UL", "MARRIAGE_MANIFESTATION",
                    "婚姻显现层: " + ul.getUlSign() + " - Marriage Presentation Base",
                    0.75, "MARRIAGE_MANIFESTATION_LAYER", false);
        }
    }

    /**
     * STEP 2E: Shadbala Weighting
     */
    private void buildShadbalaEvidence(CanonicalChartData chartData) {
        Map<String, ShadbalaData> shadbala = chartData.getShadbala();
        if (shadbala == null || shadbala.isEmpty()) return;

        for (Map.Entry<String, ShadbalaData> entry : shadbala.entrySet()) {
            String planet = entry.getKey();
            ShadbalaData data = entry.getValue();
            if (data.getExecutionCapacity() != null) {
                addEvidence(EvidenceType.STRENGTH_EVIDENCE, planet, "SHADBALA",
                        planet + " 执行能力: " + data.getExecutionCapacity(),
                        0.7, "EXECUTION_CAPACITY", false);
            }
        }
    }

    /**
     * STEP 2F: Nakshatra Refinement
     */
    private void buildNakshatraEvidence(CanonicalChartData chartData) {
        Map<String, NakshatraData> nakshatra = chartData.getNakshatra();
        if (nakshatra == null || nakshatra.isEmpty()) return;

        // Tier 1 Mandatory: Venus, Moon, Mars, Jupiter
        String[] tier1Planets = {"Venus", "Moon", "Mars", "Jupiter"};
        for (String planet : tier1Planets) {
            NakshatraData naks = nakshatra.get(planet);
            if (naks != null && naks.getName() != null) {
                List<String> core = NakshatraRegistry.getBehavioralCore(naks.getName());
                if (!core.isEmpty()) {
                    addEvidence(EvidenceType.NAKSHATRA_EVIDENCE, planet, "NAKSHATRA",
                            planet + " 行为精修: " + naks.getName() + " - " + String.join(", ", core),
                            0.6, "BEHAVIORAL_REFINEMENT", false);
                }
            }
        }
    }

    /**
     * STEP 2G: Yoga & Modifier
     */
    private void buildModifierEvidence(CanonicalChartData chartData) {
        NatalChart d1 = chartData.getD1();
        if (d1 == null || d1.getPlanets() == null) return;

        // Mars Pressure Pattern (Kuja)
        PlanetPosition mars = d1.getPlanets().get("Mars");
        if (mars != null && mars.getHouse() != null) {
            int marsHouse = mars.getHouse();
            if (marsHouse == 1 || marsHouse == 2 || marsHouse == 4 ||
                marsHouse == 7 || marsHouse == 8 || marsHouse == 12) {
                addEvidence(EvidenceType.MODIFIER_EVIDENCE, "Mars", "MARS_PRESSURE",
                        "火星关系压力模式: 第" + marsHouse + "宫",
                        0.65, "RELATIONSHIP_PRESSURE", false);
            }
        }

        // Saturn Modifier
        PlanetPosition saturn = d1.getPlanets().get("Saturn");
        if (saturn != null) {
            addEvidence(EvidenceType.MODIFIER_EVIDENCE, "Saturn", "SATURN_MODIFIER",
                    "土星关系修饰: TIME / DUTY / BOUNDARY / ENDURANCE",
                    0.6, "STRUCTURE_MODIFIER", false);
        }

        // Rahu / Ketu
        PlanetPosition rahu = d1.getPlanets().get("Rahu");
        if (rahu != null) {
            addEvidence(EvidenceType.MODIFIER_EVIDENCE, "Rahu", "RAHU_MODIFIER",
                    "北交点关系修饰: AMPLIFICATION / NOVELTY / NONCONVENTIONALITY",
                    0.55, "AMPLIFICATION_MODIFIER", false);
        }

        PlanetPosition ketu = d1.getPlanets().get("Ketu");
        if (ketu != null) {
            addEvidence(EvidenceType.MODIFIER_EVIDENCE, "Ketu", "KETU_MODIFIER",
                    "南交点关系修饰: DETACHMENT / SELECTIVITY / INTERNALIZATION",
                    0.55, "DETACHMENT_MODIFIER", false);
        }
    }

    /**
     * STEP 2H: Timing
     */
    private void buildTimingEvidence(CanonicalChartData chartData) {
        List<DashaPeriod> dashaList = chartData.getDashaList();
        if (dashaList == null || dashaList.isEmpty()) return;

        DashaPeriod current = dashaList.get(0);
        addEvidence(EvidenceType.TIMING_EVIDENCE, current.getMahadasha(), "DASHA",
                "当前大运: " + current.getMahadasha() + (current.getAntardasha() != null ? " / " + current.getAntardasha() : ""),
                0.7, "TIMING_ACTIVATION", true);
    }

    // ============================================================
    // 辅助方法
    // ============================================================

    private void addEvidence(EvidenceType type, String source, String role, String desc,
                              double weight, String targetDomain, boolean primary) {
        Evidence ev = new Evidence();
        ev.setEvidenceId(UUID.randomUUID().toString());
        ev.setType(type);
        ev.setSourcePlanet(source);
        ev.setDescription(desc);
        ev.setWeight(weight);
        ev.setTargetDomain(targetDomain);
        ev.setRole(role);
        ev.setPrimary(primary);
        ev.setIndependentFamilyCount(1);
        evidenceList.add(ev);
    }

    private String getHouseSign(NatalChart chart, int houseNum) {
        if (chart.getHouses() == null) return null;
        HouseInfo hi = chart.getHouses().get(String.valueOf(houseNum));
        return hi != null ? hi.getSign() : null;
    }

    private String getHouseLord(NatalChart chart, int houseNum) {
        if (chart.getHouses() == null) return null;
        HouseInfo hi = chart.getHouses().get(String.valueOf(houseNum));
        return hi != null ? hi.getLord() : null;
    }

    /**
     * 计算 DK（度数最低行星）
     */
    public String findDKPlanet(NatalChart chart) {
        if (chart == null || chart.getPlanets() == null) return null;

        String dkPlanet = null;
        double minDegree = 360;

        for (Map.Entry<String, PlanetPosition> entry : chart.getPlanets().entrySet()) {
            String planet = entry.getKey();
            PlanetPosition pos = entry.getValue();
            if (pos.getDegree() != null && pos.getDegree() < minDegree) {
                minDegree = pos.getDegree();
                dkPlanet = planet;
            }
        }
        return dkPlanet;
    }

    /**
     * 计算 AK（度数最高行星）
     */
    public String findAKPlanet(NatalChart chart) {
        if (chart == null || chart.getPlanets() == null) return null;

        String akPlanet = null;
        double maxDegree = 0;

        for (Map.Entry<String, PlanetPosition> entry : chart.getPlanets().entrySet()) {
            String planet = entry.getKey();
            PlanetPosition pos = entry.getValue();
            if (pos.getDegree() != null && pos.getDegree() > maxDegree) {
                maxDegree = pos.getDegree();
                akPlanet = planet;
            }
        }
        return akPlanet;
    }

    /**
     * 根据类型筛选 Evidence
     */
    public List<Evidence> getEvidenceByType(EvidenceType type) {
        List<Evidence> result = new ArrayList<>();
        for (Evidence ev : evidenceList) {
            if (ev.getType() == type) {
                result.add(ev);
            }
        }
        return result;
    }
}
