package cn.dct.zpback.engine.education;

import cn.dct.zpback.constant.EducationReportConstants;
import cn.dct.zpback.entity.education.EducationEvidenceObject;
import cn.dct.zpback.entity.lifepattern.LifePatternChartData;
import cn.dct.zpback.entity.lifepattern.LifePatternNatalChart;
import cn.dct.zpback.entity.lifepattern.LifePatternPlanetPosition;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.*;

/**
 * 学业报告 - Evidence 引擎
 * 符合 SEALED 文档《01_学业深度解析报告》§8 D1 注册表、§9 D24 注册表、§10 Root 与去重
 *
 * 运行时不变量（claim contract §1）：
 * canonical facts → eligible Evidence → deduplicated Candidates
 * → D1 sufficiency → D1×D24 resolution → derived Claims → Dasha activation → Recommendations
 */
@Component
@Slf4j
public class EducationEvidenceEngine {

    private static final String[] SIGNS = {
        "Aries", "Taurus", "Gemini", "Cancer", "Leo", "Virgo",
        "Libra", "Scorpio", "Sagittarius", "Capricorn", "Aquarius", "Pisces"
    };

    /** C02-C12 可接收投票的 Claim */
    private static final List<String> VOTABLE_CLAIMS = Arrays.asList(
        "C02", "C03", "C04", "C05", "C06", "C08", "C09", "C10", "C11");

    /**
     * 构建 Evidence 全集
     */
    public List<EducationEvidenceObject> buildEvidence(LifePatternChartData chartData) {
        List<EducationEvidenceObject> all = new ArrayList<>();

        LifePatternNatalChart d1 = chartData != null ? chartData.getD1() : null;
        LifePatternNatalChart d24 = chartData != null ? chartData.getD24() : null;

        fillMissingHouses(d1);
        fillMissingHouses(d24);

        if (d1 != null) {
            buildD1PrimaryPlanets(d1, all);
            buildD1PrimaryHouseLords(d1, all);
            buildD1ConditionalFamilies(d1, all);
        }

        if (d24 != null) {
            buildD24Confirmation(d24, all);
        }

        if (chartData != null && chartData.getShadbala() != null) {
            buildShadbalaModifiers(chartData, all);
        }

        deduplicateSameRoots(all);

        log.info("Education Evidence 构建完成: total={}, voteBearing={}",
                all.size(), all.stream().filter(e -> "VOTE_BEARING".equals(e.getNatalVoteEligibility())).count());
        return all;
    }

    // ============================================================
    // D1 主证据 Family（§8.1）
    // ============================================================

    /**
     * Mercury / Jupiter / Moon 主行星锚点
     */
    private void buildD1PrimaryPlanets(LifePatternNatalChart d1, List<EducationEvidenceObject> all) {
        for (String planet : EducationTagRegistry.PRIMARY_PLANETS) {
            LifePatternPlanetPosition pos = d1.getPlanets().get(planet);
            if (pos == null || pos.getSign() == null) continue;
            Integer house = pos.getHouse();

            String family = EducationTagRegistry.PLANET_FAMILY.get(planet);
            EducationEvidenceObject ev = base(planet, "D1", "PLACEMENT", "PLANET",
                    planet + ":" + pos.getSign() + (house != null ? ":H" + house : ""),
                    "D1 " + planet + " in " + pos.getSign() + (house != null ? " House " + house : ""));
            ev.setFamily(family);
            ev.setConditionalFamily(false);
            ev.setAuthorityRole(EducationReportConstants.ROLE_NATAL_PRIMARY);
            ev.setNatalVoteEligibility("VOTE_BEARING");
            ev.setClaimEligibility(new ArrayList<>(VOTABLE_CLAIMS));

            // 功能条件：行星天然倾向（简化：主行星默认支持面）
            ev.setFunctionalCondition("SUPPORTIVE");
            ev.setPolarity("SUPPORT");
            ev.setSemanticTags(new ArrayList<>(EducationTagRegistry.getFamilySupportTags(family)));

            // 修饰：落宫带来的挑战面
            if (house != null && EducationTagRegistry.CONDITIONAL_HOUSE_FAMILY.containsKey(house)) {
                String condFamily = EducationTagRegistry.CONDITIONAL_HOUSE_FAMILY.get(house);
                ev.getSemanticTags().addAll(EducationTagRegistry.getFamilyChallengeTags(condFamily));
                ev.getNotes().add("primary_planet_in_conditional_house:" + condFamily);
            }
            all.add(ev);
        }
    }

    /**
     * 2/4/5/9 宫主与宫内行星（§8.1 主证据宫位）
     */
    private void buildD1PrimaryHouseLords(LifePatternNatalChart d1, List<EducationEvidenceObject> all) {
        String ascSign = d1.getAscendantSign();
        if (ascSign == null) return;
        int ascIdx = signIndex(ascSign);
        if (ascIdx < 0) return;

        for (int house : EducationTagRegistry.PRIMARY_HOUSES) {
            String family = EducationTagRegistry.PRIMARY_HOUSE_FAMILY.get(house);
            int houseSignIdx = (ascIdx + house - 1) % 12;
            String houseSign = SIGNS[houseSignIdx];

            // 宫主 root
            for (Map.Entry<String, LifePatternPlanetPosition> entry : d1.getPlanets().entrySet()) {
                String planet = entry.getKey();
                LifePatternPlanetPosition pos = entry.getValue();
                if (pos == null || pos.getSign() == null) continue;

                if (pos.getSign().equalsIgnoreCase(houseSign)) {
                    Integer placedHouse = pos.getHouse();
                    EducationEvidenceObject ev = base(planet, "D1", "LORD_RELATION", "HOUSE_LORD",
                            house + "L:" + planet + ":" + pos.getSign(),
                            house + "L " + planet + " in " + pos.getSign()
                                    + (placedHouse != null ? " House " + placedHouse : ""));
                    ev.setFamily(family);
                    ev.setConditionalFamily(false);
                    ev.setAuthorityRole(EducationReportConstants.ROLE_NATAL_PRIMARY);
                    ev.setNatalVoteEligibility("VOTE_BEARING");
                    ev.setClaimEligibility(new ArrayList<>(VOTABLE_CLAIMS));

                    boolean benevolent = isBenevolentHouse(placedHouse);
                    ev.setFunctionalCondition(benevolent ? "SUPPORTIVE" : "CONSTRAINING");
                    ev.setPolarity(benevolent ? "SUPPORT" : "CHALLENGE");
                    ev.setSemanticTags(new ArrayList<>(benevolent
                            ? EducationTagRegistry.getFamilySupportTags(family)
                            : EducationTagRegistry.getFamilyChallengeTags(family)));
                    // 补充对面标签供语义参考（不投票）
                    ev.getSemanticTags().addAll(benevolent
                            ? EducationTagRegistry.getFamilyChallengeTags(family)
                            : EducationTagRegistry.getFamilySupportTags(family));
                    ev.getNotes().add("lord_of_primary_house_" + house + (benevolent ? "" : ":challenged"));
                    all.add(ev);
                }
            }

            // 宫内行星 occupancy root（独立物理 root）
            for (Map.Entry<String, LifePatternPlanetPosition> entry : d1.getPlanets().entrySet()) {
                String planet = entry.getKey();
                LifePatternPlanetPosition pos = entry.getValue();
                if (pos == null || pos.getSign() == null) continue;
                if (pos.getHouse() == null || pos.getHouse() != house) continue;

                EducationEvidenceObject ev = base(planet, "D1", "HOUSE_OCCUPANCY", "HOUSE",
                        house + "H:" + planet, planet + " in D1 " + house + "H in " + pos.getSign());
                ev.setFamily(family);
                ev.setConditionalFamily(false);
                ev.setAuthorityRole(EducationReportConstants.ROLE_NATAL_PRIMARY);
                ev.setNatalVoteEligibility("VOTE_BEARING");
                ev.setClaimEligibility(new ArrayList<>(VOTABLE_CLAIMS));
                ev.setFunctionalCondition("SUPPORTIVE");
                ev.setPolarity("SUPPORT");
                ev.setSemanticTags(new ArrayList<>(EducationTagRegistry.getFamilySupportTags(family)));
                ev.getNotes().add("occupies_primary_house_" + house);
                all.add(ev);
            }
        }
    }

    /**
     * 条件性 Family 3/6/8/11/12（§8.2）
     * 只有与主证据 Family 建立一跳关系时参与：主行星（Mercury/Jupiter/Moon）
     * 落入条件宫、或条件宫主与主行星同座/互容时激活。
     */
    private void buildD1ConditionalFamilies(LifePatternNatalChart d1, List<EducationEvidenceObject> all) {
        String ascSign = d1.getAscendantSign();
        if (ascSign == null) return;
        int ascIdx = signIndex(ascSign);
        if (ascIdx < 0) return;

        for (Map.Entry<Integer, String> entry : EducationTagRegistry.CONDITIONAL_HOUSE_FAMILY.entrySet()) {
            int house = entry.getKey();
            String family = entry.getValue();
            int houseSignIdx = (ascIdx + house - 1) % 12;
            String houseSign = SIGNS[houseSignIdx];

            for (Map.Entry<String, LifePatternPlanetPosition> pe : d1.getPlanets().entrySet()) {
                String planet = pe.getKey();
                LifePatternPlanetPosition pos = pe.getValue();
                if (pos == null || pos.getSign() == null) continue;

                boolean lordOfConditional = pos.getSign().equalsIgnoreCase(houseSign);
                boolean occupiesConditional = pos.getHouse() != null && pos.getHouse() == house;
                if (!lordOfConditional && !occupiesConditional) continue;

                // 一跳关系：主行星落在该条件宫，或该行星是主行星本身
                boolean hopViaPrimaryOccupancy = EducationTagRegistry.PRIMARY_PLANETS.contains(planet)
                        && occupiesConditional;
                boolean hopViaSameSign = false;
                LifePatternPlanetPosition primary = d1.getPlanets().get("Mercury");
                if (primary != null && primary.getSign() != null
                        && primary.getSign().equalsIgnoreCase(pos.getSign())) {
                    hopViaSameSign = true;
                }
                if (!hopViaPrimaryOccupancy && !hopViaSameSign && !EducationTagRegistry.PRIMARY_PLANETS.contains(planet)) {
                    // 无一跳关系：仅审计，不投票
                    EducationEvidenceObject audit = base(planet, "D1", "LORD_RELATION", "HOUSE_LORD",
                            house + "L:" + planet, house + "L " + planet + " (conditional, no hop)");
                    audit.setFamily(family);
                    audit.setConditionalFamily(true);
                    audit.setAuthorityRole("CONTEXT_ONLY");
                    audit.setNatalVoteEligibility("NON_VOTE_BEARING");
                    audit.setEvidenceStatus("AUDIT_ONLY");
                    audit.setPolarity("CONTEXT_ONLY");
                    audit.setFunctionalCondition("CONTEXT_ONLY");
                    audit.getNotes().add("conditional family without primary hop - audit only");
                    all.add(audit);
                    continue;
                }

                EducationEvidenceObject ev = base(planet, "D1", lordOfConditional ? "LORD_RELATION" : "HOUSE_OCCUPANCY",
                        lordOfConditional ? "HOUSE_LORD" : "HOUSE",
                        house + (lordOfConditional ? "L:" : "H:") + planet,
                        (lordOfConditional ? house + "L " : "") + planet + " in " + pos.getSign()
                                + (pos.getHouse() != null ? " House " + pos.getHouse() : ""));
                ev.setFamily(family);
                ev.setConditionalFamily(true);
                ev.setAuthorityRole(EducationReportConstants.ROLE_NATAL_PRIMARY);
                ev.setNatalVoteEligibility("VOTE_BEARING");
                ev.setClaimEligibility(new ArrayList<>(VOTABLE_CLAIMS));
                // 条件性 Family 默认以挑战面进入 C09，支持面进入相关 Claim
                ev.setFunctionalCondition("CONSTRAINING");
                ev.setPolarity("CHALLENGE");
                ev.setSemanticTags(new ArrayList<>(EducationTagRegistry.getFamilyChallengeTags(family)));
                ev.getSemanticTags().addAll(EducationTagRegistry.getFamilySupportTags(family));
                ev.getNotes().add("conditional_family_via_hop:" + family);
                all.add(ev);
            }
        }
    }

    // ============================================================
    // D24 确认（§9）
    // ============================================================

    /**
     * D24 确认证据：Asc/AscLord、Mercury/Jupiter、4/5/9H+L
     * authority 只能是 DOMAIN_CONFIRMATION（VALIDATING/SUPPORTING）
     */
    private void buildD24Confirmation(LifePatternNatalChart d24, List<EducationEvidenceObject> all) {
        String ascSign = d24.getAscendantSign();
        int ascIdx = ascSign != null ? signIndex(ascSign) : -1;

        // Asc / Asc Lord
        if (ascIdx >= 0) {
            String ascSignName = SIGNS[ascIdx];
            for (Map.Entry<String, LifePatternPlanetPosition> entry : d24.getPlanets().entrySet()) {
                String planet = entry.getKey();
                LifePatternPlanetPosition pos = entry.getValue();
                if (pos == null || pos.getSign() == null) continue;

                boolean isAscLord = pos.getSign().equalsIgnoreCase(ascSignName);
                boolean isEducationPlanet = EducationTagRegistry.PRIMARY_PLANETS.contains(planet);
                Integer house = pos.getHouse();
                boolean inKeyHouse = house != null && EducationTagRegistry.PRIMARY_HOUSES.contains(house);

                if (!isAscLord && !isEducationPlanet && !inKeyHouse) continue;

                EducationEvidenceObject ev = base(planet, "D24",
                        isAscLord ? "LORD_RELATION" : "PLACEMENT", "PLANET",
                        "D24:" + planet + ":" + pos.getSign(),
                        "D24 " + planet + " in " + pos.getSign() + (house != null ? " House " + house : ""));
                ev.setFamily(isEducationPlanet
                        ? EducationTagRegistry.PLANET_FAMILY.get(planet)
                        : (house != null ? EducationTagRegistry.PRIMARY_HOUSE_FAMILY.get(house) : "D24_DOMAIN"));
                ev.setConditionalFamily(true);
                ev.setAuthorityRole(EducationReportConstants.ROLE_DOMAIN_CONFIRMATION);
                ev.setNatalVoteEligibility("NON_VOTE_BEARING");
                ev.setClaimEligibility(new ArrayList<>(VOTABLE_CLAIMS));
                ev.setFunctionalCondition("SUPPORTIVE");
                ev.setPolarity("SUPPORT");
                ev.setSemanticTags(new ArrayList<>(isEducationPlanet
                        ? EducationTagRegistry.getFamilySupportTags(EducationTagRegistry.PLANET_FAMILY.get(planet))
                        : Arrays.asList("domain_confirmation")));
                ev.getNotes().add("D24 validation only - cannot create natal claim");
                all.add(ev);
            }
        }
    }

    // ============================================================
    // Shadbala 修饰（§12）
    // ============================================================

    private void buildShadbalaModifiers(LifePatternChartData chartData, List<EducationEvidenceObject> all) {
        for (Map.Entry<String, cn.dct.zpback.entity.lifepattern.LifePatternShadbalaData> entry
                : chartData.getShadbala().entrySet()) {
            String planet = entry.getKey();
            if (!Arrays.asList("Sun", "Moon", "Mars", "Mercury", "Jupiter", "Venus", "Saturn").contains(planet)) {
                continue; // 传统七曜，不给 Rahu/Ketu 伪造数值
            }
            Double score = entry.getValue() != null ? entry.getValue().getScore() : null;
            String modifier = scoreToModifier(score);

            EducationEvidenceObject ev = base(planet, "SHADBALA", "MODIFIER", "PLANET",
                    planet + ":SHADBALA", planet + " Shadbala " + modifier);
            ev.setAuthorityRole(EducationReportConstants.ROLE_EXPRESSION_MODIFIER);
            ev.setNatalVoteEligibility("NON_VOTE_BEARING");
            ev.setEvidenceStatus("AUDIT_ONLY");
            ev.setPolarity("CONTEXT_ONLY");
            ev.setFunctionalCondition("CONTEXT_ONLY");
            ev.setStrengthModifier(modifier);
            ev.setClaimEligibility(new ArrayList<>(VOTABLE_CLAIMS));
            ev.getNotes().add("expression modifier - cannot vote, only qualify stability");
            all.add(ev);
        }
    }

    private String scoreToModifier(Double score) {
        if (score == null) return EducationReportConstants.SHADBALA_UNKNOWN;
        if (score >= 1.10) return EducationReportConstants.SHADBALA_STABLY_AVAILABLE;
        if (score >= 0.90) return EducationReportConstants.SHADBALA_AVAILABLE_WITH_EFFORT;
        return EducationReportConstants.SHADBALA_INCONSISTENT_ACCESS;
    }

    // ============================================================
    // 去重（§10.3）
    // ============================================================

    /**
     * 相同 root_key + claim 方向最多一个 vote-bearing Candidate；
     * 同一根因被多次描述时聚合为一个 Candidate
     */
    private void deduplicateSameRoots(List<EducationEvidenceObject> all) {
        Set<String> seenVoteBearing = new HashSet<>();
        for (EducationEvidenceObject ev : all) {
            if (!"VOTE_BEARING".equals(ev.getNatalVoteEligibility())) continue;
            if (!seenVoteBearing.add(ev.getRootKey())) {
                // 同 root 重复：保留第一个，后续降级为审计
                ev.setEvidenceStatus("SUPPRESSED");
                ev.setNatalVoteEligibility("NON_VOTE_BEARING");
                ev.getNotes().add("duplicate root_key: " + ev.getRootKey());
            }
        }

        // 同一 Root 内 support/challenge 并存 → AMBIVALENT（不得拆成两票）
        Map<String, List<EducationEvidenceObject>> byRoot = new LinkedHashMap<>();
        for (EducationEvidenceObject ev : all) {
            if ("VOTE_BEARING".equals(ev.getNatalVoteEligibility())) {
                byRoot.computeIfAbsent(ev.getRootKey(), k -> new ArrayList<>()).add(ev);
            }
        }
        for (List<EducationEvidenceObject> group : byRoot.values()) {
            if (group.size() <= 1) continue;
            boolean hasSupport = group.stream().anyMatch(e -> "SUPPORT".equals(e.getPolarity()));
            boolean hasChallenge = group.stream().anyMatch(e -> "CHALLENGE".equals(e.getPolarity()));
            if (hasSupport && hasChallenge) {
                for (EducationEvidenceObject ev : group) {
                    ev.setFunctionalCondition("AMBIVALENT");
                    ev.setPolarity("SUPPORT"); // 归并后按支持面进入，语义为混合
                    ev.getSemanticTags().add("MIXED_SIGNAL");
                    ev.getNotes().add("root-level ambivalent merge");
                }
            }
        }
    }

    // ============================================================
    // 工具
    // ============================================================

    private EducationEvidenceObject base(String subject, String chartSource, String kind,
                                          String rootType, String rootIdentity, String rawFact) {
        EducationEvidenceObject ev = new EducationEvidenceObject();
        ev.setEvidenceId("EV_" + chartSource + "_" + UUID.randomUUID().toString().substring(0, 8).toUpperCase());
        ev.setChartSource(chartSource);
        ev.setEvidenceKind(kind);
        ev.setSubject(subject);
        ev.setTarget(rootIdentity);
        ev.setRawFact(rawFact);
        ev.setRootType(rootType);
        ev.setRootKey(chartSource + ":" + rootType + ":" + rootIdentity);
        ev.setAnchorKey(chartSource + ":" + rootIdentity);
        ev.setIndependenceClusterKey(chartSource + "_" + rootType);
        ev.setDependencyStatus("INDEPENDENT");
        ev.setEvidenceStatus("ELIGIBLE");
        ev.setStrengthModifier(EducationReportConstants.SHADBALA_UNKNOWN);
        return ev;
    }

    private boolean isBenevolentHouse(Integer house) {
        // 简化：主宫位落点在 1/2/4/5/7/9/10/11 视为支持面，3/6/8/12 视为挑战面
        if (house == null) return true;
        return house == 1 || house == 2 || house == 4 || house == 5
                || house == 7 || house == 9 || house == 10 || house == 11;
    }

    private int signIndex(String sign) {
        if (sign == null) return -1;
        for (int i = 0; i < SIGNS.length; i++) {
            if (SIGNS[i].equalsIgnoreCase(sign.trim())) return i;
        }
        return -1;
    }

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
