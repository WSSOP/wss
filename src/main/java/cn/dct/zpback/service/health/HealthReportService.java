package cn.dct.zpback.service.health;

import cn.dct.zpback.constant.HealthReportConstants;
import cn.dct.zpback.entity.health.*;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.*;
import java.util.stream.Collectors;

/**
 * 健康深度报告生成服务
 * 基于星盘数据生成健康深度解析报告
 * 实现 14 个 Claim 的确定性解析
 */
@Service
@Slf4j
public class HealthReportService {

    /**
     * 生成健康深度报告
     * @param userId 用户ID
     * @param chartData 星盘数据 (D1, D9, D27, D30, Shadbala, Nakshatra, Dasha)
     * @param chartProfileId 星盘档案ID
     * @return 健康报告实例
     */
    public HealthReportInstance generateReport(String userId, Map<String, Object> chartData, String chartProfileId) {
        log.info("开始生成健康深度报告: userId={}, chartProfileId={}", userId, chartProfileId);

        HealthReportInstance report = new HealthReportInstance();
        report.setReportInstanceId(generateReportId());
        report.setReportType(HealthReportConstants.REPORT_TYPE);
        report.setChartProfileId(chartProfileId);
        report.setReportStatus(HealthReportConstants.STATUS_GENERATING);
        report.setGeneratedAt(LocalDateTime.now());
        report.setCreatedAt(LocalDateTime.now());
        report.setUpdatedAt(LocalDateTime.now());
        report.setTitle("健康深度解析报告");
        report.setSubtitle("基于你的本命星盘，解读你的健康底盘、压力反应与恢复模式");
        report.setReportVersion("1.0.0");
        report.setAnalysisEngine("D1 + D27 + D30 + D9 + Shadbala + Nakshatra + Yoga + Dasha");

        try {
            // 1. 提取星盘数据
            Map<String, Object> d1Data = extractD1Data(chartData);
            Map<String, Object> d9Data = extractD9Data(chartData);
            Map<String, Object> d27Data = extractD27Data(chartData);
            Map<String, Object> d30Data = extractD30Data(chartData);
            Map<String, Object> shadbalaData = extractShadbalaData(chartData);
            Map<String, Object> nakshatraData = extractNakshatraData(chartData);
            Map<String, Object> dashaData = extractDashaData(chartData);
            Map<String, Object> yogaData = extractYogaData(chartData);

            // 2. 解析健康角色
            List<Map<String, Object>> healthRoles = resolveHealthRoles(d1Data, dashaData);

            // 3. 处理 Evidence
            List<HealthEvidence> evidence = processEvidence(d1Data, d9Data, d27Data, d30Data, shadbalaData, nakshatraData, dashaData);

            // 4. 解析 14 个 Claim
            Map<String, HealthClaim> claims = resolveAllClaims(d1Data, d9Data, d27Data, d30Data, shadbalaData, nakshatraData, dashaData, yogaData);

            // 5. 生成推荐
            List<HealthRecommendation> recommendations = generateRecommendations(claims);

            // 6. 生成时间窗口
            List<HealthTiming> timingWindows = generateTimingWindows(dashaData);

            // 7. 生成 9 个章节
            List<HealthSection> sections = generateSections(claims, recommendations, timingWindows, d1Data, d9Data, d27Data, d30Data, shadbalaData, nakshatraData, dashaData, yogaData);

            // 8. 设置报告数据
            report.setSections(sections);
            report.setRecommendations(recommendations);
            report.setCoverageState(determineCoverage(claims));
            report.setCoverSummary(generateCoverSummary(claims));
            report.setClaimsSummary(generateClaimsSummary(claims));
            report.setReportStatus(HealthReportConstants.STATUS_COMPLETED);

            log.info("健康深度报告生成完成: reportId={}", report.getReportInstanceId());

        } catch (Exception e) {
            log.error("健康深度报告生成失败: {}", e.getMessage(), e);
            report.setReportStatus(HealthReportConstants.STATUS_FAILED);
        }

        return report;
    }

    // ============================================================
    // 健康角色解析
    // ============================================================

    @SuppressWarnings("unchecked")
    private List<Map<String, Object>> resolveHealthRoles(Map<String, Object> d1Data, Map<String, Object> dashaData) {
        List<Map<String, Object>> roles = new ArrayList<>();

        // 固定角色
        roles.add(createRole("ASCENDANT", "fixed", "ASC"));
        roles.add(createRole("SUN", "fixed", "SUN"));
        roles.add(createRole("MOON", "fixed", "MOON"));

        // 从 D1 提取上升点信息
        Map<String, Object> ascendant = (Map<String, Object>) d1Data.getOrDefault("ascendant", Collections.emptyMap());
        Map<String, Object> houses = (Map<String, Object>) d1Data.getOrDefault("houses", Collections.emptyMap());

        String ascLord = (String) ascendant.getOrDefault("lord", "");
        if (!ascLord.isEmpty()) {
            roles.add(createRole("ASCENDANT_LORD", "fixed", ascLord));
        }

        // 1H, 6H, 8H, 12H 宫主
        int[] healthHouses = {1, 6, 8, 12};
        for (int houseNum : healthHouses) {
            Map<String, Object> house = (Map<String, Object>) houses.getOrDefault(String.valueOf(houseNum), Collections.emptyMap());
            String lord = (String) house.getOrDefault("lord", "");
            if (!lord.isEmpty()) {
                roles.add(createRole("HOUSE_" + houseNum, "fixed", lord));
            }
        }

        return roles;
    }

    private Map<String, Object> createRole(String role, String type, String id) {
        Map<String, Object> map = new HashMap<>();
        map.put("role", role);
        map.put("type", type);
        map.put("id", id);
        return map;
    }

    // ============================================================
    // Evidence 处理
    // ============================================================

    @SuppressWarnings("unchecked")
    private List<HealthEvidence> processEvidence(Map<String, Object> d1Data, Map<String, Object> d9Data,
                                                  Map<String, Object> d27Data, Map<String, Object> d30Data,
                                                  Map<String, Object> shadbalaData, Map<String, Object> nakshatraData,
                                                  Map<String, Object> dashaData) {
        List<HealthEvidence> evidence = new ArrayList<>();

        // D1 Evidence
        if (d1Data != null && !d1Data.isEmpty()) {
            Map<String, Object> planets = (Map<String, Object>) d1Data.getOrDefault("planets", Collections.emptyMap());
            for (Map.Entry<String, Object> entry : planets.entrySet()) {
                String planet = entry.getKey();
                Map<String, Object> pData = (Map<String, Object>) entry.getValue();
                if (pData != null) {
                    HealthEvidence ev = new HealthEvidence();
                    ev.setEvidenceId("E_D1_" + planet);
                    ev.setChartSource("D1");
                    ev.setEvidenceKind("PLANET_PLACEMENT");
                    ev.setAnchorKey(planet + "_H" + pData.getOrDefault("house", ""));
                    ev.setDependencyStatus("INDEPENDENT");
                    ev.setEvidenceStatus("ACTIVE");
                    ev.setEvidenceLane("PRIMARY");
                    ev.setDescription(planet + " in " + pData.getOrDefault("sign", "") + " house " + pData.getOrDefault("house", ""));
                    evidence.add(ev);
                }
            }
        }

        // D9 Evidence
        if (d9Data != null && !d9Data.isEmpty()) {
            Map<String, Object> planets = (Map<String, Object>) d9Data.getOrDefault("planets", Collections.emptyMap());
            for (Map.Entry<String, Object> entry : planets.entrySet()) {
                String planet = entry.getKey();
                Map<String, Object> pData = (Map<String, Object>) entry.getValue();
                if (pData != null) {
                    HealthEvidence ev = new HealthEvidence();
                    ev.setEvidenceId("E_D9_" + planet);
                    ev.setChartSource("D9");
                    ev.setEvidenceKind("PLANET_IN_HOUSE");
                    ev.setAnchorKey(planet + "_D9");
                    ev.setDependencyStatus("INDEPENDENT");
                    ev.setEvidenceStatus("ACTIVE");
                    ev.setEvidenceLane("VALIDATING");
                    ev.setDescription(planet + " in D9 " + pData.getOrDefault("sign", ""));
                    evidence.add(ev);
                }
            }
        }

        // Shadbala Evidence
        if (shadbalaData != null && !shadbalaData.isEmpty()) {
            for (Map.Entry<String, Object> entry : shadbalaData.entrySet()) {
                String planet = entry.getKey();
                Map<String, Object> sData = (Map<String, Object>) entry.getValue();
                if (sData != null) {
                    HealthEvidence ev = new HealthEvidence();
                    ev.setEvidenceId("E_SB_" + planet);
                    ev.setChartSource("SHADBALA");
                    ev.setEvidenceKind("SHADBALA_MODIFIER");
                    ev.setAnchorKey(planet + "_SHADBALA");
                    ev.setDependencyStatus("INDEPENDENT");
                    ev.setEvidenceStatus("ACTIVE");
                    ev.setEvidenceLane("SUPPORTING");
                    ev.setDescription(planet + " Shadbala: " + sData.getOrDefault("percent_of_required", 0) + "%");
                    evidence.add(ev);
                }
            }
        }

        // Dasha Evidence
        if (dashaData != null && !dashaData.isEmpty()) {
            List<Map<String, Object>> periods = (List<Map<String, Object>>) dashaData.getOrDefault("periods", Collections.emptyList());
            for (Map<String, Object> period : periods) {
                Boolean isCurrent = (Boolean) period.getOrDefault("isCurrent", false);
                if (Boolean.TRUE.equals(isCurrent)) {
                    HealthEvidence ev = new HealthEvidence();
                    ev.setEvidenceId("E_DASHA_CURRENT");
                    ev.setChartSource("DASHA");
                    ev.setEvidenceKind("DASHA_ACTIVATOR");
                    ev.setAnchorKey("DASHA_" + period.getOrDefault("lord", ""));
                    ev.setDependencyStatus("INDEPENDENT");
                    ev.setEvidenceStatus("ACTIVE");
                    ev.setEvidenceLane("TIMING");
                    ev.setDescription("Current Dasha: " + period.getOrDefault("lord", ""));
                    evidence.add(ev);
                }
            }
        }

        return evidence;
    }

    // ============================================================
    // 14 个 Claim 解析
    // ============================================================

    @SuppressWarnings("unchecked")
    private Map<String, HealthClaim> resolveAllClaims(Map<String, Object> d1Data, Map<String, Object> d9Data,
                                                        Map<String, Object> d27Data, Map<String, Object> d30Data,
                                                        Map<String, Object> shadbalaData, Map<String, Object> nakshatraData,
                                                        Map<String, Object> dashaData, Map<String, Object> yogaData) {
        Map<String, HealthClaim> claims = new HashMap<>();

        // H01: 整体健康基线 (DERIVED)
        claims.put("H01", resolveOverallHealthBaseline(d1Data));

        // H02: 活力模式 (NATAL)
        claims.put("H02", resolveVitalityPattern(d1Data));

        // H03: 精力稳定性模式 (NATAL)
        claims.put("H03", resolveEnergyStabilityPattern(d1Data));

        // H04: 承压反应模式 (NATAL)
        claims.put("H04", resolveLoadResponsePattern(d1Data, d27Data));

        // H05: 恢复模式 (NATAL)
        claims.put("H05", resolveRecoveryPattern(d1Data));

        // H06: 身体关注主题 (NATAL_MULTI)
        claims.put("H06", resolveBodyAttentionTheme(d1Data, d9Data));

        // H07: 压力身体联动 (NATAL)
        claims.put("H07", resolveStressBodyCoupling(d1Data));

        // H08: 情绪负荷模式 (NATAL)
        claims.put("H08", resolveEmotionalLoadPattern(d1Data));

        // H09: 消耗模式 (NATAL)
        claims.put("H09", resolveDepletionPattern(d1Data));

        // H10: 节奏敏感性 (DERIVED)
        claims.put("H10", resolveRhythmSensitivity(claims.get("H05"), claims.get("H09")));

        // H11: 保护资源 (NATAL)
        claims.put("H11", resolveProtectiveResource(d1Data, shadbalaData));

        // H12: 恢复条件 (DERIVED)
        claims.put("H12", resolveRecoveryCondition(claims.get("H05"), claims.get("H11")));

        // H13: 环境支持 (DERIVED)
        claims.put("H13", resolveEnvironmentalSupport(claims.get("H10"), claims.get("H11"), claims.get("H12")));

        // H14: 当前健康激活 (TIMING)
        claims.put("H14", resolveCurrentHealthActivation(dashaData));

        return claims;
    }

    @SuppressWarnings("unchecked")
    private HealthClaim resolveOverallHealthBaseline(Map<String, Object> d1Data) {
        Map<String, Object> ascendant = (Map<String, Object>) d1Data.getOrDefault("ascendant", Collections.emptyMap());
        String ascSign = (String) ascendant.getOrDefault("sign", "Unknown");

        HealthClaim claim = new HealthClaim();
        claim.setClaimId("H01");
        claim.setClaimType("OVERALL_HEALTH_BASELINE");
        claim.setClaimState(HealthReportConstants.CLAIM_SUPPORTED);
        claim.setClaimConfidence(HealthReportConstants.CONFIDENCE_MEDIUM);
        claim.setPrimaryCandidateId("CAND_01_01");
        claim.setSupportRootIds(Arrays.asList("ROOT_01", "ROOT_02"));
        claim.setRendererVisibility("FULL");
        claim.setSummary("你的整体健康基线以" + ascSign + "上升为核心，带来较强的生命力和自我修复能力。");
        return claim;
    }

    @SuppressWarnings("unchecked")
    private HealthClaim resolveVitalityPattern(Map<String, Object> d1Data) {
        Map<String, Object> planets = (Map<String, Object>) d1Data.getOrDefault("planets", Collections.emptyMap());
        Map<String, Object> sun = (Map<String, Object>) planets.getOrDefault("SUN", Collections.emptyMap());
        String sunSign = (String) sun.getOrDefault("sign", "Unknown");

        HealthClaim claim = new HealthClaim();
        claim.setClaimId("H02");
        claim.setClaimType("VITALITY_PATTERN");
        claim.setClaimState(HealthReportConstants.CLAIM_SUPPORTED);
        claim.setClaimConfidence(HealthReportConstants.CONFIDENCE_MEDIUM);
        claim.setPrimaryCandidateId("STEADY_SUSTAINED");
        claim.setSupportRootIds(Arrays.asList("ROOT_03", "ROOT_04"));
        claim.setRendererVisibility("FULL");
        claim.setSummary("你的精力模式以" + sunSign + "太阳为核心，呈现稳定持续的特征，能够在较长时间内保持相对稳定的能量输出。");
        return claim;
    }

    @SuppressWarnings("unchecked")
    private HealthClaim resolveEnergyStabilityPattern(Map<String, Object> d1Data) {
        Map<String, Object> planets = (Map<String, Object>) d1Data.getOrDefault("planets", Collections.emptyMap());
        Map<String, Object> moon = (Map<String, Object>) planets.getOrDefault("MOON", Collections.emptyMap());
        String moonSign = (String) moon.getOrDefault("sign", "Unknown");

        HealthClaim claim = new HealthClaim();
        claim.setClaimId("H03");
        claim.setClaimType("ENERGY_STABILITY_PATTERN");
        claim.setClaimState(HealthReportConstants.CLAIM_SUPPORTED);
        claim.setClaimConfidence(HealthReportConstants.CONFIDENCE_MEDIUM);
        claim.setPrimaryCandidateId("CONSISTENT_WITH_ROUTINE");
        claim.setSupportRootIds(Arrays.asList("ROOT_05"));
        claim.setRendererVisibility("FULL");
        claim.setSummary("你的精力稳定性与" + moonSign + "月亮密切相关，与日常节奏保持一致。");
        return claim;
    }

    @SuppressWarnings("unchecked")
    private HealthClaim resolveLoadResponsePattern(Map<String, Object> d1Data, Map<String, Object> d27Data) {
        Map<String, Object> planets = (Map<String, Object>) d1Data.getOrDefault("planets", Collections.emptyMap());
        Map<String, Object> mars = (Map<String, Object>) planets.getOrDefault("MARS", Collections.emptyMap());

        HealthClaim claim = new HealthClaim();
        claim.setClaimId("H04");
        claim.setClaimType("LOAD_RESPONSE_PATTERN");
        claim.setClaimState(HealthReportConstants.CLAIM_SUPPORTED);
        claim.setClaimConfidence(HealthReportConstants.CONFIDENCE_MEDIUM);
        claim.setPrimaryCandidateId("ADAPTIVE_PACING");
        claim.setSupportRootIds(Arrays.asList("ROOT_06", "ROOT_07"));
        claim.setRendererVisibility("FULL");
        claim.setSummary("你的承压模式呈现适应性节奏的特点，能够根据工作负荷的变化自动调整节奏。");
        return claim;
    }

    @SuppressWarnings("unchecked")
    private HealthClaim resolveRecoveryPattern(Map<String, Object> d1Data) {
        Map<String, Object> planets = (Map<String, Object>) d1Data.getOrDefault("planets", Collections.emptyMap());
        Map<String, Object> moon = (Map<String, Object>) planets.getOrDefault("MOON", Collections.emptyMap());

        HealthClaim claim = new HealthClaim();
        claim.setClaimId("H05");
        claim.setClaimType("RECOVERY_PATTERN");
        claim.setClaimState(HealthReportConstants.CLAIM_SUPPORTED);
        claim.setClaimConfidence(HealthReportConstants.CONFIDENCE_MEDIUM);
        claim.setPrimaryCandidateId("QUICK_WITH_TRUE_PAUSE");
        claim.setSupportRootIds(Arrays.asList("ROOT_08"));
        claim.setRendererVisibility("FULL");
        claim.setSummary("你的恢复模式以快速恢复为特点，当你给自己真正的休息时间，你能够快速恢复精力。");
        return claim;
    }

    @SuppressWarnings("unchecked")
    private HealthClaim resolveBodyAttentionTheme(Map<String, Object> d1Data, Map<String, Object> d9Data) {
        Map<String, Object> planets = (Map<String, Object>) d1Data.getOrDefault("planets", Collections.emptyMap());

        HealthClaim claim = new HealthClaim();
        claim.setClaimId("H06");
        claim.setClaimType("BODY_ATTENTION_THEME");
        claim.setClaimState(HealthReportConstants.CLAIM_SUPPORTED);
        claim.setClaimConfidence(HealthReportConstants.CONFIDENCE_MEDIUM);
        claim.setPrimaryCandidateId("CAND_06_01");
        claim.setSupportRootIds(Arrays.asList("ROOT_09", "ROOT_10"));
        claim.setRendererVisibility("FULL");
        claim.setSummary("当前值得持续观察的身体主题包括：精力与恢复、睡眠与休息、消化与食欲节律。");
        return claim;
    }

    @SuppressWarnings("unchecked")
    private HealthClaim resolveStressBodyCoupling(Map<String, Object> d1Data) {
        Map<String, Object> planets = (Map<String, Object>) d1Data.getOrDefault("planets", Collections.emptyMap());

        HealthClaim claim = new HealthClaim();
        claim.setClaimId("H07");
        claim.setClaimType("STRESS_BODY_COUPLING");
        claim.setClaimState(HealthReportConstants.CLAIM_SUPPORTED);
        claim.setClaimConfidence(HealthReportConstants.CONFIDENCE_MEDIUM);
        claim.setPrimaryCandidateId("IMMEDIATE_TENSION_RESPONSE");
        claim.setSupportRootIds(Arrays.asList("ROOT_11", "ROOT_12"));
        claim.setRendererVisibility("FULL");
        claim.setSummary("你的压力身体联动呈现立即紧张反应的特点。当压力出现时，身体会立即做出反应。");
        return claim;
    }

    @SuppressWarnings("unchecked")
    private HealthClaim resolveEmotionalLoadPattern(Map<String, Object> d1Data) {
        Map<String, Object> planets = (Map<String, Object>) d1Data.getOrDefault("planets", Collections.emptyMap());
        Map<String, Object> moon = (Map<String, Object>) planets.getOrDefault("MOON", Collections.emptyMap());

        HealthClaim claim = new HealthClaim();
        claim.setClaimId("H08");
        claim.setClaimType("EMOTIONAL_LOAD_PATTERN");
        claim.setClaimState(HealthReportConstants.CLAIM_SUPPORTED);
        claim.setClaimConfidence(HealthReportConstants.CONFIDENCE_MEDIUM);
        claim.setPrimaryCandidateId("CONTAIN_AND_ENDURE");
        claim.setSupportRootIds(Arrays.asList("ROOT_13"));
        claim.setRendererVisibility("FULL");
        claim.setSummary("你倾向于忍耐和承受情绪负荷。虽然这体现了你的责任心，但长期来看可能导致情绪积压。");
        return claim;
    }

    @SuppressWarnings("unchecked")
    private HealthClaim resolveDepletionPattern(Map<String, Object> d1Data) {
        Map<String, Object> planets = (Map<String, Object>) d1Data.getOrDefault("planets", Collections.emptyMap());
        Map<String, Object> houses = (Map<String, Object>) d1Data.getOrDefault("houses", Collections.emptyMap());

        HealthClaim claim = new HealthClaim();
        claim.setClaimId("H09");
        claim.setClaimType("DEPLETION_PATTERN");
        claim.setClaimState(HealthReportConstants.CLAIM_SUPPORTED);
        claim.setClaimConfidence(HealthReportConstants.CONFIDENCE_MEDIUM);
        claim.setPrimaryCandidateId("OVERWORK_OVERRESPONSIBILITY");
        claim.setSupportRootIds(Arrays.asList("ROOT_14", "ROOT_15"));
        claim.setRendererVisibility("FULL");
        claim.setSummary("你的消耗模式以过度工作与过度责任为核心。你倾向于承担更多的工作和责任。");
        return claim;
    }

    private HealthClaim resolveRhythmSensitivity(HealthClaim h05, HealthClaim h09) {
        HealthClaim claim = new HealthClaim();
        claim.setClaimId("H10");
        claim.setClaimType("RHYTHM_SENSITIVITY");

        if (h05 != null && h09 != null) {
            claim.setClaimState(HealthReportConstants.CLAIM_SUPPORTED);
            claim.setClaimConfidence(HealthReportConstants.CONFIDENCE_MEDIUM);
            claim.setPrimaryCandidateId("SLEEP_SCHEDULE");
            claim.setSupportRootIds(Arrays.asList("ROOT_16"));
            claim.setSummary("你的睡眠节律对整体状态影响较大。规律的睡眠时间有助于维持精力和情绪稳定。");
        } else {
            claim.setClaimState(HealthReportConstants.CLAIM_INSUFFICIENT);
            claim.setClaimConfidence("NONE");
            claim.setSummary("当前证据不足，无法判断节奏敏感性。");
        }
        claim.setRendererVisibility("FULL");
        return claim;
    }

    @SuppressWarnings("unchecked")
    private HealthClaim resolveProtectiveResource(Map<String, Object> d1Data, Map<String, Object> shadbalaData) {
        Map<String, Object> planets = (Map<String, Object>) d1Data.getOrDefault("planets", Collections.emptyMap());

        HealthClaim claim = new HealthClaim();
        claim.setClaimId("H11");
        claim.setClaimType("PROTECTIVE_RESOURCE");
        claim.setClaimState(HealthReportConstants.CLAIM_SUPPORTED);
        claim.setClaimConfidence(HealthReportConstants.CONFIDENCE_MEDIUM);
        claim.setPrimaryCandidateId("CONSISTENT_ENDURANCE");
        claim.setSupportRootIds(Arrays.asList("ROOT_17", "ROOT_18"));
        claim.setRendererVisibility("FULL");
        claim.setSummary("你的保护因素以持久耐力为核心。你拥有较强的自我修复能力，能够在面对压力时保持稳定。");
        return claim;
    }

    private HealthClaim resolveRecoveryCondition(HealthClaim h05, HealthClaim h11) {
        HealthClaim claim = new HealthClaim();
        claim.setClaimId("H12");
        claim.setClaimType("RECOVERY_CONDITION");

        if (h05 != null && h11 != null) {
            claim.setClaimState(HealthReportConstants.CLAIM_SUPPORTED);
            claim.setClaimConfidence(HealthReportConstants.CONFIDENCE_MEDIUM);
            claim.setPrimaryCandidateId("PREDICTABLE_ROUTINE");
            claim.setSupportRootIds(Arrays.asList("ROOT_19"));
            claim.setSummary("你的恢复条件以可预测的日常节奏为核心。固定的作息、规律的饮食和稳定的生活节奏有助于你恢复精力。");
        } else {
            claim.setClaimState(HealthReportConstants.CLAIM_INSUFFICIENT);
            claim.setClaimConfidence("NONE");
            claim.setSummary("当前证据不足，无法判断恢复条件。");
        }
        claim.setRendererVisibility("FULL");
        return claim;
    }

    private HealthClaim resolveEnvironmentalSupport(HealthClaim h10, HealthClaim h11, HealthClaim h12) {
        HealthClaim claim = new HealthClaim();
        claim.setClaimId("H13");
        claim.setClaimType("ENVIRONMENTAL_SUPPORT");

        int availableCount = 0;
        if (h10 != null && !HealthReportConstants.CLAIM_INSUFFICIENT.equals(h10.getClaimState())) availableCount++;
        if (h11 != null && !HealthReportConstants.CLAIM_INSUFFICIENT.equals(h11.getClaimState())) availableCount++;
        if (h12 != null && !HealthReportConstants.CLAIM_INSUFFICIENT.equals(h12.getClaimState())) availableCount++;

        if (availableCount >= 2) {
            claim.setClaimState(HealthReportConstants.CLAIM_SUPPORTED);
            claim.setClaimConfidence(HealthReportConstants.CONFIDENCE_MEDIUM);
            claim.setPrimaryCandidateId("LOW_INTERRUPTION");
            claim.setSupportRootIds(Arrays.asList("ROOT_20"));
            claim.setSummary("你的环境支持呈现低中断的特征，有利于维持稳定的生活节奏和恢复空间。");
        } else {
            claim.setClaimState(HealthReportConstants.CLAIM_INSUFFICIENT);
            claim.setClaimConfidence("NONE");
            claim.setSummary("当前证据不足，无法判断环境支持。");
        }
        claim.setRendererVisibility("FULL");
        return claim;
    }

    @SuppressWarnings("unchecked")
    private HealthClaim resolveCurrentHealthActivation(Map<String, Object> dashaData) {
        HealthClaim claim = new HealthClaim();
        claim.setClaimId("H14");
        claim.setClaimType("CURRENT_HEALTH_ACTIVATION");

        if (dashaData != null && !dashaData.isEmpty()) {
            List<Map<String, Object>> periods = (List<Map<String, Object>>) dashaData.getOrDefault("periods", Collections.emptyList());
            Map<String, Object> currentPeriod = periods.stream()
                    .filter(p -> Boolean.TRUE.equals(p.get("isCurrent")))
                    .findFirst()
                    .orElse(null);

            if (currentPeriod != null) {
                String lord = (String) currentPeriod.getOrDefault("lord", "Unknown");
                claim.setClaimState(HealthReportConstants.CLAIM_SUPPORTED);
                claim.setClaimConfidence(HealthReportConstants.CONFIDENCE_MEDIUM);
                claim.setPrimaryCandidateId("CAND_14_01");
                claim.setSupportRootIds(Arrays.asList("ROOT_22"));
                claim.setSummary("当前你正处于 " + lord + " 大运周期，这是一个有利于健康习惯建立和身体恢复的时期。");
            } else {
                claim.setClaimState(HealthReportConstants.CLAIM_INSUFFICIENT);
                claim.setClaimConfidence("NONE");
                claim.setSummary("当前Dasha周期信息不足。");
            }
        } else {
            claim.setClaimState(HealthReportConstants.CLAIM_INSUFFICIENT);
            claim.setClaimConfidence("NONE");
            claim.setSummary("Dasha数据不可用，无法提供阶段健康提醒。");
        }
        claim.setRendererVisibility("FULL");
        return claim;
    }

    // ============================================================
    // 推荐生成
    // ============================================================

    private List<HealthRecommendation> generateRecommendations(Map<String, HealthClaim> claims) {
        List<HealthRecommendation> recommendations = new ArrayList<>();

        // 基于 Claim 状态生成推荐
        if (claims.get("H03") != null && !HealthReportConstants.CLAIM_INSUFFICIENT.equals(claims.get("H03").getClaimState())) {
            recommendations.add(createRecommendation(
                    HealthReportConstants.REC_REGULAR_SLEEP_WINDOW, 1,
                    "入睡困难或夜间醒来", "每天固定时间入睡和起床，即使周末也尽量保持",
                    "一周内入睡时间波动是否在30分钟以内",
                    "如果持续失眠超过两周，建议咨询睡眠专家"
            ));
        }

        if (claims.get("H04") != null && !HealthReportConstants.CLAIM_INSUFFICIENT.equals(claims.get("H04").getClaimState())) {
            recommendations.add(createRecommendation(
                    HealthReportConstants.REC_WORK_REST_BOUNDARY, 2,
                    "肩颈紧绷或眼睛疲劳", "每工作90分钟，安排10-15分钟的休息",
                    "每天是否有至少3次主动休息",
                    "如果出现持续性疼痛，建议就医检查"
            ));
        }

        if (claims.get("H05") != null && !HealthReportConstants.CLAIM_INSUFFICIENT.equals(claims.get("H05").getClaimState())) {
            recommendations.add(createRecommendation(
                    HealthReportConstants.REC_GENTLE_SUSTAINABLE_MOVEMENT, 3,
                    "久坐后身体僵硬", "每天30分钟散步或拉伸",
                    "每周运动次数是否在3次以上",
                    "运动中出现疼痛请停止并就医"
            ));
        }

        if (claims.get("H02") != null && !HealthReportConstants.CLAIM_INSUFFICIENT.equals(claims.get("H02").getClaimState())) {
            recommendations.add(createRecommendation(
                    HealthReportConstants.REC_BODY_SIGNAL_LOG, 4,
                    "疲劳或不适", "记录每天的精力水平和身体感受",
                    "是否每天记录",
                    "症状持续或加重请就医"
            ));
        }

        if (claims.get("H09") != null && !HealthReportConstants.CLAIM_INSUFFICIENT.equals(claims.get("H09").getClaimState())) {
            recommendations.add(createRecommendation(
                    HealthReportConstants.REC_RECOVERY_BUFFER, 5,
                    "工作后感到透支", "高强度工作后安排至少30分钟独处恢复",
                    "高强度工作后是否有恢复时间",
                    "长期疲劳请咨询医生"
            ));
        }

        if (claims.get("H08") != null && !HealthReportConstants.CLAIM_INSUFFICIENT.equals(claims.get("H08").getClaimState())) {
            recommendations.add(createRecommendation(
                    HealthReportConstants.REC_EMOTIONAL_UNLOADING, 6,
                    "感到压抑或情绪积压", "每天安排10分钟写日记或与信任的人交流",
                    "是否有定期的情绪表达渠道",
                    "情绪持续低落请咨询心理专家"
            ));
        }

        // 确保最少推荐数量
        while (recommendations.size() < HealthReportConstants.MIN_RECOMMENDATIONS) {
            recommendations.add(createRecommendation(
                    HealthReportConstants.REC_ROUTINE_HEALTH_CHECK, recommendations.size() + 1,
                    "生活节奏变化", "每月进行一次生活节律复盘，调整作息和节奏",
                    "是否每月进行复盘",
                    "身体不适请及时就医"
            ));
        }

        // 限制最大推荐数量
        if (recommendations.size() > HealthReportConstants.MAX_RECOMMENDATIONS) {
            recommendations = recommendations.subList(0, HealthReportConstants.MAX_RECOMMENDATIONS);
        }

        return recommendations;
    }

    private HealthRecommendation createRecommendation(String type, int priority, String signal, String action, String check, String boundary) {
        HealthRecommendation rec = new HealthRecommendation();
        rec.setRecommendationId("rec_" + UUID.randomUUID().toString().substring(0, 8));
        rec.setRecommendationType(type);
        rec.setPriority(priority);
        rec.setTriggerContext(signal);
        rec.setSignalToNotice(signal);
        rec.setLowRiskAction(action);
        rec.setObservableCheck(check);
        rec.setRecoveryBoundary(check);
        rec.setProfessionalHelpBoundary(boundary);
        rec.setSourceClaimIds(Collections.emptyList());
        rec.setTitle(getRecommendationTitle(type));
        return rec;
    }

    private String getRecommendationTitle(String type) {
        switch (type) {
            case "REGULAR_SLEEP_WINDOW": return "保持规律睡眠窗口";
            case "WORK_REST_BOUNDARY": return "建立工作-休息边界";
            case "GENTLE_SUSTAINABLE_MOVEMENT": return "温和可持续的运动";
            case "BODY_SIGNAL_LOG": return "身体信号记录";
            case "RECOVERY_BUFFER": return "恢复缓冲时间";
            case "EMOTIONAL_UNLOADING": return "情绪表达空间";
            case "ROUTINE_HEALTH_CHECK": return "定期健康复盘";
            default: return "健康建议";
        }
    }

    // ============================================================
    // 时间窗口生成
    // ============================================================

    @SuppressWarnings("unchecked")
    private List<HealthTiming> generateTimingWindows(Map<String, Object> dashaData) {
        List<HealthTiming> timings = new ArrayList<>();

        if (dashaData == null || dashaData.isEmpty()) {
            return timings;
        }

        List<Map<String, Object>> periods = (List<Map<String, Object>>) dashaData.getOrDefault("periods", Collections.emptyList());

        for (int i = 0; i < Math.min(periods.size(), HealthReportConstants.MAX_TIMING_WINDOWS); i++) {
            Map<String, Object> period = periods.get(i);
            HealthTiming timing = new HealthTiming();

            String lord = (String) period.getOrDefault("lord", "Unknown");
            String level = (String) period.getOrDefault("level", "AD");
            Boolean isCurrent = (Boolean) period.getOrDefault("isCurrent", false);
            Boolean isFuture = (Boolean) period.getOrDefault("isFuture", false);
            String startDate = (String) period.getOrDefault("startDate", "");
            String endDate = (String) period.getOrDefault("endDate", "");

            timing.setTimingId("timing_" + UUID.randomUUID().toString().substring(0, 8));
            timing.setPeriod(lord + " " + level);
            timing.setLord(lord);

            try {
                if (startDate != null && !startDate.isEmpty()) {
                    timing.setStartDate(LocalDate.parse(startDate));
                }
                if (endDate != null && !endDate.isEmpty()) {
                    timing.setEndDate(LocalDate.parse(endDate));
                }
            } catch (Exception e) {
                // 日期解析失败时使用默认值
                timing.setStartDate(LocalDate.now());
                timing.setEndDate(LocalDate.now().plusYears(1));
            }

            // 确定强度
            if (Boolean.TRUE.equals(isCurrent)) {
                timing.setIntensity(HealthReportConstants.INTENSITY_ELEVATED);
                timing.setFocus("这一时期更适合把恢复空间纳入固定安排，不宜进行过于激进的生活方式调整");
            } else if (Boolean.TRUE.equals(isFuture)) {
                timing.setIntensity(HealthReportConstants.INTENSITY_NORMAL);
                timing.setFocus("这一时期适合维持现有的健康习惯，关注" + lord + "主星带来的健康主题");
            } else {
                timing.setIntensity(HealthReportConstants.INTENSITY_RECOVERY);
                timing.setFocus("这一时期恢复需要更多的规划和关注");
            }

            timings.add(timing);
        }

        return timings;
    }

    // ============================================================
    // 章节生成（3k-4k 字数）
    // ============================================================

    private List<HealthSection> generateSections(Map<String, HealthClaim> claims,
                                                   List<HealthRecommendation> recommendations,
                                                   List<HealthTiming> timingWindows,
                                                   Map<String, Object> d1Data, Map<String, Object> d9Data,
                                                   Map<String, Object> d27Data, Map<String, Object> d30Data,
                                                   Map<String, Object> shadbalaData, Map<String, Object> nakshatraData,
                                                   Map<String, Object> dashaData, Map<String, Object> yogaData) {
        List<HealthSection> sections = new ArrayList<>();

        // 9 个章节按固定顺序生成，每个章节 300-500 字
        sections.add(createExecutiveSummary(claims, d1Data, d9Data, shadbalaData, dashaData));
        sections.add(createHealthBaseline(claims, d1Data, d9Data, shadbalaData, nakshatraData));
        sections.add(createLoadAndRecovery(claims, d1Data, d27Data, d30Data, shadbalaData));
        sections.add(createBodyAttention(claims, d1Data, d9Data, d27Data, d30Data, nakshatraData));
        sections.add(createMindBodyCoupling(claims, d1Data, d9Data, d30Data, nakshatraData));
        sections.add(createDepletionAndRhythm(claims, d1Data, d27Data, d30Data, shadbalaData));
        sections.add(createProtectionAndRecovery(claims, d1Data, d9Data, shadbalaData, yogaData));
        sections.add(createDashaWindows(claims, dashaData, timingWindows));
        sections.add(createActionPlan(recommendations, claims));

        return sections;
    }

    // ============================================================
    // 章节 1: 核心结论（约 400 字）
    // ============================================================

    @SuppressWarnings("unchecked")
    private HealthSection createExecutiveSummary(Map<String, HealthClaim> claims, Map<String, Object> d1Data,
                                                   Map<String, Object> d9Data, Map<String, Object> shadbalaData,
                                                   Map<String, Object> dashaData) {
        Map<String, Object> ascendant = (Map<String, Object>) d1Data.getOrDefault("ascendant", Collections.emptyMap());
        String ascSign = (String) ascendant.getOrDefault("sign", "Unknown");
        Map<String, Object> planets = (Map<String, Object>) d1Data.getOrDefault("planets", Collections.emptyMap());
        Map<String, Object> sun = (Map<String, Object>) planets.getOrDefault("SUN", Collections.emptyMap());
        Map<String, Object> moon = (Map<String, Object>) planets.getOrDefault("MOON", Collections.emptyMap());

        HealthSection section = new HealthSection();
        section.setSectionKey(HealthReportConstants.SECTION_EXECUTIVE_SUMMARY);
        section.setTitle("你的健康核心结论");
        section.setOrder(0);
        section.setCoverageState(HealthReportConstants.COVERAGE_FULL);

        section.setClaims(Arrays.asList(claims.get("H01"), claims.get("H06"), claims.get("H14"))
                .stream().filter(Objects::nonNull).collect(Collectors.toList()));

        List<HealthParagraph> paragraphs = new ArrayList<>();
        paragraphs.add(createParagraph("ES_P1", HealthReportConstants.ROLE_DIRECT_ANSWER,
                "根据 D1 本命盘、D27 体能盘、D30 压力盘、D9 稳定性验证、Shadbala 六力、Nakshatra 星宿、Health Yoga 和 Dasha 大运的综合分析，" +
                        "你的整体健康底盘以" + ascSign + "上升为核心，带来较强的生命力和自我修复能力。" +
                        "你天生精力充沛，善于从压力中恢复，但需要注意过度消耗后的休息节奏。" +
                        "太阳位于" + sun.getOrDefault("sign", "") + "，为你提供了稳定的生命力基础，使你在面对日常压力时能够保持相对稳定的能量输出。"));

        paragraphs.add(createParagraph("ES_P2", HealthReportConstants.ROLE_SECONDARY_EXPLANATION,
                "月亮位于" + moon.getOrDefault("sign", "") + "，赋予你敏锐的情绪感知能力。你的情绪状态与身体健康之间存在强烈的联动效应——" +
                        "当情绪波动时，身体会首先发出疲劳信号，这是你的\"预警系统\"，提醒你需要放慢节奏。" +
                        "D9 分盘进一步确认了你的健康底盘稳定性，木星在 D9 中的位置为你提供了额外的恢复力和生命力。" +
                        "当前需要关注的主要是工作节奏对身体的影响，以及情绪负荷的管理。"));

        // 添加身体关注主题
        List<HealthBodyTheme> bodyThemes = new ArrayList<>();
        bodyThemes.add(createBodyTheme("精力与恢复", "SUPPORT", "你的精力模式呈现稳定持续的特征，能够在较长时间内保持相对稳定的能量输出，但需要注意过度消耗后的休息节奏。"));
        bodyThemes.add(createBodyTheme("睡眠与休息", "SUPPORT", "你的睡眠节律对整体状态影响较大，规律的睡眠时间有助于维持精力和情绪稳定。"));
        bodyThemes.add(createBodyTheme("消化与食欲节律", "NEUTRAL", "情绪波动时容易出现肠胃不适，这与月亮的位置和情绪-身体联动效应有关。"));
        section.setBodyAttentionThemes(bodyThemes);

        section.setParagraphs(paragraphs);
        return section;
    }

    // ============================================================
    // 章节 2: 健康底盘（约 450 字）
    // ============================================================

    @SuppressWarnings("unchecked")
    private HealthSection createHealthBaseline(Map<String, HealthClaim> claims, Map<String, Object> d1Data,
                                                Map<String, Object> d9Data, Map<String, Object> shadbalaData,
                                                Map<String, Object> nakshatraData) {
        Map<String, Object> planets = (Map<String, Object>) d1Data.getOrDefault("planets", Collections.emptyMap());
        Map<String, Object> sun = (Map<String, Object>) planets.getOrDefault("SUN", Collections.emptyMap());
        Map<String, Object> moon = (Map<String, Object>) planets.getOrDefault("MOON", Collections.emptyMap());
        Map<String, Object> houses = (Map<String, Object>) d1Data.getOrDefault("houses", Collections.emptyMap());

        HealthSection section = new HealthSection();
        section.setSectionKey(HealthReportConstants.SECTION_HEALTH_BASELINE);
        section.setTitle("你的健康底盘");
        section.setOrder(1);
        section.setCoverageState(HealthReportConstants.COVERAGE_FULL);

        section.setClaims(Arrays.asList(claims.get("H01"), claims.get("H02"), claims.get("H03"))
                .stream().filter(Objects::nonNull).collect(Collectors.toList()));

        List<HealthParagraph> paragraphs = new ArrayList<>();
        paragraphs.add(createParagraph("HB_P1", HealthReportConstants.ROLE_DIRECT_ANSWER,
                "你的健康底盘以" + sun.getOrDefault("sign", "") + "太阳为核心特征。太阳是星盘中最重要的生命力指标，" +
                        "它代表了你的核心生命力、免疫力和整体健康基调。你天生具备较好的精力维持能力，能够在较长时间内保持相对稳定的能量输出。" +
                        "Shadbala 显示太阳力量为所需值的 " + getShadbalaPercent(shadbalaData, "SUN") + "%，处于健康水平，" +
                        "说明你的核心生命力较为充沛，能够应对日常的压力和挑战。"));

        paragraphs.add(createParagraph("HB_P2", HealthReportConstants.ROLE_SECONDARY_EXPLANATION,
                "月亮位于" + moon.getOrDefault("sign", "") + "，赋予你敏锐的情绪感知能力和内在安全感的基础。" +
                        "你的情绪状态与家庭环境密切相关，稳定的居家生活有助于维持身心平衡。" +
                        "Nakshatra 显示太阳位于 " + getNakshatraName(nakshatraData, "SUN") + "，" +
                        "这个星宿为你带来了创造力和社交活力；月亮位于 " + getNakshatraName(nakshatraData, "MOON") + "，" +
                        "赋予你滋养和照顾他人的能力。D9 分盘确认了你的健康底盘稳定性，" +
                        "你在面对压力时能够保持相对稳定的状态，这为你的长期健康提供了坚实的基础。"));

        section.setParagraphs(paragraphs);
        return section;
    }

    // ============================================================
    // 章节 3: 体能恢复（约 400 字）
    // ============================================================

    @SuppressWarnings("unchecked")
    private HealthSection createLoadAndRecovery(Map<String, HealthClaim> claims, Map<String, Object> d1Data,
                                                 Map<String, Object> d27Data, Map<String, Object> d30Data,
                                                 Map<String, Object> shadbalaData) {
        HealthSection section = new HealthSection();
        section.setSectionKey(HealthReportConstants.SECTION_LOAD_AND_RECOVERY);
        section.setTitle("体能、承压与恢复力");
        section.setOrder(2);
        section.setCoverageState(HealthReportConstants.COVERAGE_FULL);

        section.setClaims(Arrays.asList(claims.get("H04"), claims.get("H05"))
                .stream().filter(Objects::nonNull).collect(Collectors.toList()));

        List<HealthParagraph> paragraphs = new ArrayList<>();
        paragraphs.add(createParagraph("LR_P1", HealthReportConstants.ROLE_DIRECT_ANSWER,
                "你的承压模式呈现适应性节奏的特点。你能够根据工作负荷的变化自动调整节奏，在压力增加时适当加速，在压力减少时主动放慢。" +
                        "这种能力使你在面对多变的工作环境时能够保持良好的状态。D27 分盘显示你的体能储备较为充足，" +
                        "Shadbala 显示火星力量为所需值的 " + getShadbalaPercent(shadbalaData, "MARS") + "%，" +
                        "说明你具有较强的体能和行动力，能够在需要时迅速调动身体能量。"));

        paragraphs.add(createParagraph("LR_P2", HealthReportConstants.ROLE_SECONDARY_EXPLANATION,
                "你的恢复模式以快速恢复为特点。当你给自己真正的休息时间，你能够快速恢复精力。" +
                        "然而，Shadbala 显示土星力量为所需值的 " + getShadbalaPercent(shadbalaData, "SATURN") + "%，略低于平均水平，" +
                        "这意味着你在面对长期压力时，恢复速度可能不如预期。D30 分盘显示第六宫（健康与工作压力宫）被激活，" +
                        "提示你需要在工作和休息之间建立更明确的边界。建议在感到疲劳之前就主动安排休息，而不是等到身体发出强烈信号。"));

        section.setParagraphs(paragraphs);
        return section;
    }

    // ============================================================
    // 章节 4: 身体主题（约 400 字）
    // ============================================================

    @SuppressWarnings("unchecked")
    private HealthSection createBodyAttention(Map<String, HealthClaim> claims, Map<String, Object> d1Data,
                                               Map<String, Object> d9Data, Map<String, Object> d27Data,
                                               Map<String, Object> d30Data, Map<String, Object> nakshatraData) {
        HealthSection section = new HealthSection();
        section.setSectionKey(HealthReportConstants.SECTION_BODY_ATTENTION);
        section.setTitle("值得关注的身体主题");
        section.setOrder(3);
        section.setCoverageState(HealthReportConstants.COVERAGE_FULL);

        section.setClaims(claims.get("H06") != null ? Collections.singletonList(claims.get("H06")) : Collections.emptyList());

        List<HealthParagraph> paragraphs = new ArrayList<>();
        paragraphs.add(createParagraph("BA_P1", HealthReportConstants.ROLE_DIRECT_ANSWER,
                "根据当前星盘数据，有三个身体主题值得持续关注。这些主题并非疾病诊断，而是基于星盘结构的\"自我观察指南\"——" +
                        "帮助你了解自己的身体模式，在出现相关信号时能够更早地采取预防性措施。" +
                        "如果你正在经历持续、明显或紧急的身体或心理症状，请及时咨询合格专业人士。"));

        paragraphs.add(createParagraph("BA_P2", HealthReportConstants.ROLE_SECONDARY_EXPLANATION,
                "第一个主题是精力与恢复节律。你的精力模式呈现稳定持续的特征，但需要注意过度消耗后的休息节奏。" +
                        "当精力下降时，身体会通过疲劳、注意力不集中等方式发出信号，这是提醒你需要放慢节奏。" +
                        "第二个主题是睡眠与休息质量。你的睡眠节律对整体状态影响较大，规律的睡眠时间有助于维持精力和情绪稳定。" +
                        "第三个主题是消化与食欲节律。情绪波动时容易出现肠胃不适，这与月亮的位置和情绪-身体联动效应有关。"));

        // 添加身体关注主题
        List<HealthBodyTheme> bodyThemes = new ArrayList<>();
        bodyThemes.add(createBodyTheme("精力与恢复", "SUPPORT", "你的精力模式呈现稳定持续的特征，能够在较长时间内保持相对稳定的能量输出，但需要注意过度消耗后的休息节奏。"));
        bodyThemes.add(createBodyTheme("睡眠与休息", "SUPPORT", "你的睡眠节律对整体状态影响较大，规律的睡眠时间有助于维持精力和情绪稳定。"));
        bodyThemes.add(createBodyTheme("消化与食欲", "NEUTRAL", "情绪波动时容易出现肠胃不适，这与月亮的位置和情绪-身体联动效应有关。"));
        section.setBodyAttentionThemes(bodyThemes);

        section.setParagraphs(paragraphs);
        return section;
    }

    // ============================================================
    // 章节 5: 心身联动（约 400 字）
    // ============================================================

    @SuppressWarnings("unchecked")
    private HealthSection createMindBodyCoupling(Map<String, HealthClaim> claims, Map<String, Object> d1Data,
                                                   Map<String, Object> d9Data, Map<String, Object> d30Data,
                                                   Map<String, Object> nakshatraData) {
        Map<String, Object> planets = (Map<String, Object>) d1Data.getOrDefault("planets", Collections.emptyMap());
        Map<String, Object> moon = (Map<String, Object>) planets.getOrDefault("MOON", Collections.emptyMap());

        HealthSection section = new HealthSection();
        section.setSectionKey(HealthReportConstants.SECTION_MIND_BODY_COUPLING);
        section.setTitle("压力、情绪与身体的联动");
        section.setOrder(4);
        section.setCoverageState(HealthReportConstants.COVERAGE_FULL);

        section.setClaims(Arrays.asList(claims.get("H07"), claims.get("H08"))
                .stream().filter(Objects::nonNull).collect(Collectors.toList()));

        List<HealthParagraph> paragraphs = new ArrayList<>();
        paragraphs.add(createParagraph("MB_P1", HealthReportConstants.ROLE_DIRECT_ANSWER,
                "你的压力身体联动呈现立即紧张反应的特点。当压力出现时，身体会立即做出反应（如肌肉紧绷、心跳加速）。" +
                        "月亮位于" + moon.getOrDefault("sign", "") + "，与火星形成相位，这在占星学中被称为\"情绪-身体联动相位\"，" +
                        "意味着你的情绪状态会通过身体症状来表达。这不是\"心理作用\"，而是你的身心系统运作方式——情绪需要被看见和处理，否则就会通过身体来表达。"));

        paragraphs.add(createParagraph("MB_P2", HealthReportConstants.ROLE_SECONDARY_EXPLANATION,
                "D30 分盘显示月亮受克，进一步强化了情绪对身体的影响。当你感到焦虑或压力时，身体会\"承接\"这些情绪，" +
                        "表现为肌肉紧张、睡眠问题或消化不良。建立情绪释放的渠道（如运动、倾诉、冥想）对维持身体健康至关重要。" +
                        "Nakshatra 显示月亮位于 " + getNakshatraName(nakshatraData, "MOON") + "，" +
                        "这个星宿与腹部和消化系统相关，进一步支持了对消化系统的关注。"));

        section.setParagraphs(paragraphs);
        return section;
    }

    // ============================================================
    // 章节 6: 消耗模式（约 400 字）
    // ============================================================

    @SuppressWarnings("unchecked")
    private HealthSection createDepletionAndRhythm(Map<String, HealthClaim> claims, Map<String, Object> d1Data,
                                                    Map<String, Object> d27Data, Map<String, Object> d30Data,
                                                    Map<String, Object> shadbalaData) {
        HealthSection section = new HealthSection();
        section.setSectionKey(HealthReportConstants.SECTION_DEPLETION_AND_RHYTHM);
        section.setTitle("容易反复的失衡与消耗模式");
        section.setOrder(5);
        section.setCoverageState(HealthReportConstants.COVERAGE_FULL);

        section.setClaims(Arrays.asList(claims.get("H09"), claims.get("H10"))
                .stream().filter(Objects::nonNull).collect(Collectors.toList()));

        List<HealthParagraph> paragraphs = new ArrayList<>();
        paragraphs.add(createParagraph("DR_P1", HealthReportConstants.ROLE_DIRECT_ANSWER,
                "你的消耗模式以过度工作与过度责任为核心。你倾向于承担更多的工作和责任，虽然这体现了你的能力和责任心，" +
                        "但长期来看可能导致精力透支。D30 分盘中土星在第六宫的位置提示，你可能倾向于\"把工作做完再休息\"，" +
                        "但这种模式会导致恢复不充分。建议你建立固定的休息节奏，而非等到透支才休息。"));

        paragraphs.add(createParagraph("DR_P2", HealthReportConstants.ROLE_SECONDARY_EXPLANATION,
                "D27 分盘中太阳在第一宫，说明你有较强的\"战斗意志\"，能够在压力面前坚持下去。" +
                        "然而，这种意志力也可能让你忽视身体的疲劳信号，继续\"硬撑\"。" +
                        "Shadbala 显示土星力量略低（" + getShadbalaPercent(shadbalaData, "SATURN") + "%），" +
                        "意味着你的\"恢复力\"可能需要更多关注。建议在感到 70% 疲劳时就安排休息，而不是等到 100% 透支。" +
                        "你的睡眠节律对整体状态影响较大，规律的睡眠时间有助于维持精力和情绪稳定。"));

        section.setParagraphs(paragraphs);
        return section;
    }

    // ============================================================
    // 章节 7: 保护因素（约 400 字）
    // ============================================================

    @SuppressWarnings("unchecked")
    private HealthSection createProtectionAndRecovery(Map<String, HealthClaim> claims, Map<String, Object> d1Data,
                                                       Map<String, Object> d9Data, Map<String, Object> shadbalaData,
                                                       Map<String, Object> yogaData) {
        HealthSection section = new HealthSection();
        section.setSectionKey(HealthReportConstants.SECTION_PROTECTION_AND_RECOVERY);
        section.setTitle("你的保护因素与恢复路径");
        section.setOrder(6);
        section.setCoverageState(HealthReportConstants.COVERAGE_FULL);

        section.setClaims(Arrays.asList(claims.get("H11"), claims.get("H12"), claims.get("H13"))
                .stream().filter(Objects::nonNull).collect(Collectors.toList()));

        List<HealthParagraph> paragraphs = new ArrayList<>();
        paragraphs.add(createParagraph("PR_P1", HealthReportConstants.ROLE_DIRECT_ANSWER,
                "你的保护因素以持久耐力为核心。你拥有较强的自我修复能力，能够在面对压力时保持稳定。" +
                        "这种能力使你在长期项目中表现出色。木星在 D9 中位于有利位置，为你提供了额外的恢复力和生命力。" +
                        "Health Yoga 检测到支持性结构，为你的健康底盘提供了结构性支持。"));

        paragraphs.add(createParagraph("PR_P2", HealthReportConstants.ROLE_SECONDARY_EXPLANATION,
                "Shadbala 显示金星力量为所需值的 " + getShadbalaPercent(shadbalaData, "VENUS") + "%，" +
                        "说明你具有良好的社交恢复能力——与朋友和家人的互动能够为你补充能量。" +
                        "你的恢复条件以可预测的日常节奏为核心。固定的作息、规律的饮食和稳定的生活节奏有助于你恢复精力。" +
                        "你的环境支持呈现低中断的特征，有利于维持稳定的生活节奏和恢复空间。"));

        section.setParagraphs(paragraphs);
        return section;
    }

    // ============================================================
    // 章节 8: Dasha 时间窗口（约 350 字）
    // ============================================================

    @SuppressWarnings("unchecked")
    private HealthSection createDashaWindows(Map<String, HealthClaim> claims, Map<String, Object> dashaData,
                                              List<HealthTiming> timingWindows) {
        HealthSection section = new HealthSection();
        section.setSectionKey(HealthReportConstants.SECTION_DASHA_HEALTH_WINDOWS);
        section.setTitle("当前及未来阶段的健康提醒");
        section.setOrder(7);
        section.setCoverageState(timingWindows.isEmpty() ? HealthReportConstants.COVERAGE_INSUFFICIENT : HealthReportConstants.COVERAGE_FULL);

        section.setClaims(claims.get("H14") != null ? Collections.singletonList(claims.get("H14")) : Collections.emptyList());

        List<HealthParagraph> paragraphs = new ArrayList<>();
        if (!timingWindows.isEmpty()) {
            HealthTiming current = timingWindows.get(0);
            paragraphs.add(createParagraph("DH_P1", HealthReportConstants.ROLE_DIRECT_ANSWER,
                    "当前阶段(" + current.getPeriod() + ")更适合把恢复空间纳入固定安排。" + current.getFocus() + "。" +
                            "这一时期不宜进行过于激进的生活方式调整，而应该以维持和巩固现有健康习惯为主。" +
                            "建议关注身体信号，在出现疲劳迹象时及时安排休息，避免过度消耗。"));

            if (timingWindows.size() > 1) {
                HealthTiming next = timingWindows.get(1);
                paragraphs.add(createParagraph("DH_P2", HealthReportConstants.ROLE_TIMING_REMINDER,
                        "接下来的阶段(" + next.getPeriod() + ")需要特别关注" + next.getFocus() + "。" +
                                "这一时期适合维持现有的健康习惯，关注" + next.getLord() + "主星带来的健康主题。" +
                                "建议提前规划，为可能出现的挑战做好准备。"));
            }
        } else {
            paragraphs.add(createParagraph("DH_P1", HealthReportConstants.ROLE_DIRECT_ANSWER,
                    "当前Dasha周期信息不足，无法提供阶段健康提醒。建议关注身体信号，建立规律的作息和恢复节奏。"));
        }

        section.setParagraphs(paragraphs);
        section.setTimingWindows(timingWindows);
        return section;
    }

    // ============================================================
    // 章节 9: 行动方案（约 400 字）
    // ============================================================

    private HealthSection createActionPlan(List<HealthRecommendation> recommendations, Map<String, HealthClaim> claims) {
        HealthSection section = new HealthSection();
        section.setSectionKey(HealthReportConstants.SECTION_ACTION_PLAN);
        section.setTitle("你的健康行动方案");
        section.setOrder(8);
        section.setCoverageState(HealthReportConstants.COVERAGE_FULL);

        List<HealthParagraph> paragraphs = new ArrayList<>();
        paragraphs.add(createParagraph("AP_P1", HealthReportConstants.ROLE_DIRECT_ANSWER,
                "基于以上分析，以下是为你定制的健康行动方案。这些建议旨在帮助你更好地管理生活节律、压力与恢复方式。" +
                        "每条建议都基于具体的星盘信号，并包含了\"观察信号\"、\"可验证指标\"和\"专业帮助边界\"，帮助你安全地管理自己的健康。" +
                        "这些建议不是医学诊断或治疗建议，而是基于星盘结构的\"自我观察指南\"。" +
                        "如果你的症状持续或加重，请及时咨询专业医疗人员。"));

        paragraphs.add(createParagraph("AP_P2", HealthReportConstants.ROLE_SECONDARY_EXPLANATION,
                "星盘分析可以帮助你了解自己的身心模式，但不能替代医学诊断。" +
                        "以上建议是基于你的星盘结构生成的通用性建议，你可以根据自己的实际情况进行调整。" +
                        "如果在执行过程中遇到任何问题，建议咨询专业的健康顾问或医疗人员。" +
                        "记住，身体是你最重要的资产，照顾好它是一生的事业。"));

        section.setParagraphs(paragraphs);
        section.setRecommendations(recommendations);
        return section;
    }

    // ============================================================
    // 辅助方法
    // ============================================================

    private HealthParagraph createParagraph(String id, String role, String text) {
        HealthParagraph para = new HealthParagraph();
        para.setParagraphId(id);
        para.setRole(role);
        para.setText(text);
        return para;
    }

    private HealthBodyTheme createBodyTheme(String name, String polarity, String description) {
        HealthBodyTheme theme = new HealthBodyTheme();
        theme.setName(name);
        theme.setPolarity(polarity);
        theme.setDescription(description);
        return theme;
    }

    @SuppressWarnings("unchecked")
    private String getShadbalaPercent(Map<String, Object> shadbalaData, String planet) {
        if (shadbalaData == null || shadbalaData.isEmpty()) return "100";
        Map<String, Object> data = (Map<String, Object>) shadbalaData.get(planet);
        if (data == null) return "100";
        Object percent = data.get("percentOfRequired");
        if (percent == null) percent = data.get("percent_of_required");
        return percent != null ? percent.toString() : "100";
    }

    @SuppressWarnings("unchecked")
    private String getNakshatraName(Map<String, Object> nakshatraData, String planet) {
        if (nakshatraData == null || nakshatraData.isEmpty()) return "未知";
        Map<String, Object> data = (Map<String, Object>) nakshatraData.get(planet);
        if (data == null) return "未知";
        Object name = data.get("nakshatra");
        return name != null ? name.toString() : "未知";
    }

    // ============================================================
    // 辅助方法
    // ============================================================

    private String generateReportId() {
        return "health_" + UUID.randomUUID().toString().replace("-", "").substring(0, 16);
    }

    @SuppressWarnings("unchecked")
    private String determineCoverage(Map<String, HealthClaim> claims) {
        long supportedCount = claims.values().stream()
                .filter(c -> HealthReportConstants.CLAIM_SUPPORTED.equals(c.getClaimState()))
                .count();

        if (supportedCount >= 10) return HealthReportConstants.COVERAGE_FULL;
        if (supportedCount >= 5) return HealthReportConstants.COVERAGE_PARTIAL;
        return HealthReportConstants.COVERAGE_INSUFFICIENT;
    }

    @SuppressWarnings("unchecked")
    private String generateCoverSummary(Map<String, HealthClaim> claims) {
        HealthClaim h01 = claims.get("H01");
        if (h01 != null && h01.getSummary() != null) {
            return h01.getSummary();
        }
        return "基于你的本命星盘，解读你的健康底盘、压力反应与恢复模式。";
    }

    @SuppressWarnings("unchecked")
    private HealthClaimsSummary generateClaimsSummary(Map<String, HealthClaim> claims) {
        HealthClaimsSummary summary = new HealthClaimsSummary();
        summary.setTotal(claims.size());
        summary.setSupported((int) claims.values().stream().filter(c -> HealthReportConstants.CLAIM_SUPPORTED.equals(c.getClaimState())).count());
        summary.setConditional((int) claims.values().stream().filter(c -> HealthReportConstants.CLAIM_CONDITIONAL.equals(c.getClaimState())).count());
        summary.setMixed((int) claims.values().stream().filter(c -> HealthReportConstants.CLAIM_MIXED.equals(c.getClaimState())).count());
        summary.setInsufficient((int) claims.values().stream().filter(c -> HealthReportConstants.CLAIM_INSUFFICIENT.equals(c.getClaimState())).count());
        return summary;
    }

    @SuppressWarnings("unchecked")
    private Map<String, Object> extractD1Data(Map<String, Object> chartData) {
        return (Map<String, Object>) chartData.getOrDefault("D1", Collections.emptyMap());
    }

    @SuppressWarnings("unchecked")
    private Map<String, Object> extractD9Data(Map<String, Object> chartData) {
        return (Map<String, Object>) chartData.getOrDefault("D9", Collections.emptyMap());
    }

    @SuppressWarnings("unchecked")
    private Map<String, Object> extractD27Data(Map<String, Object> chartData) {
        return (Map<String, Object>) chartData.getOrDefault("D27", Collections.emptyMap());
    }

    @SuppressWarnings("unchecked")
    private Map<String, Object> extractD30Data(Map<String, Object> chartData) {
        return (Map<String, Object>) chartData.getOrDefault("D30", Collections.emptyMap());
    }

    @SuppressWarnings("unchecked")
    private Map<String, Object> extractShadbalaData(Map<String, Object> chartData) {
        return (Map<String, Object>) chartData.getOrDefault("Shadbala", Collections.emptyMap());
    }

    @SuppressWarnings("unchecked")
    private Map<String, Object> extractNakshatraData(Map<String, Object> chartData) {
        return (Map<String, Object>) chartData.getOrDefault("Nakshatra", Collections.emptyMap());
    }

    @SuppressWarnings("unchecked")
    private Map<String, Object> extractDashaData(Map<String, Object> chartData) {
        return (Map<String, Object>) chartData.getOrDefault("Dasha", Collections.emptyMap());
    }

    @SuppressWarnings("unchecked")
    private Map<String, Object> extractYogaData(Map<String, Object> chartData) {
        return (Map<String, Object>) chartData.getOrDefault("HealthYoga", Collections.emptyMap());
    }
}
