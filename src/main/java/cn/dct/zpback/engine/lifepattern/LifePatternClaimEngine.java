package cn.dct.zpback.engine.lifepattern;

import cn.dct.zpback.entity.lifepattern.*;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.*;

/**
 * 人生课题报告 - 确定性 Claim 解析引擎
 * 符合 SEALED 文档 01 第 7-9 节规范
 *
 * 职责：
 * 1. 将 Evidence 转换为 Candidate Direction
 * 2. 按确定性规则解析 13 个 Claim
 * 3. 不创建、不删除、不升级、不降级 Claim
 */
@Component
@Slf4j
public class LifePatternClaimEngine {

    /**
     * 解析所有 13 个 Claim
     */
    public Map<String, LifePatternClaim> resolveAllClaims(List<EvidenceObject> evidenceList, LifePatternChartData chartData) {
        Map<String, LifePatternClaim> claims = new LinkedHashMap<>();

        // Natal Claims (C02-C05, C07, C08, C12)
        claims.put("C02", resolveC02_CoreInnerNeed(evidenceList, chartData));
        claims.put("C03", resolveC03_SecurityRegulation(evidenceList, chartData));
        claims.put("C04", resolveC04_SelfWorthAnchor(evidenceList, chartData));
        claims.put("C05", resolveC05_ProtectiveResponse(evidenceList, chartData));
        claims.put("C07", resolveC07_OverusePattern(evidenceList, chartData));
        claims.put("C08", resolveC08_StrengthResource(evidenceList, chartData));
        claims.put("C12", resolveC12_MeaningOrientation(evidenceList, chartData));

        // Derived Claims
        claims.put("C06", resolveC06_InnerConflict(claims));
        claims.put("C09", resolveC09_RepeatingLoop(claims));
        claims.put("C10", resolveC10_GrowthChallenge(claims));
        claims.put("C11", resolveC11_MaturityCapacity(claims, evidenceList, chartData));

        // Summary Claim
        claims.put("C01", resolveC01_CoreLifeTheme(claims));

        return claims;
    }

    // ============================================================
    // C02: 核心内在需要
    // ============================================================
    private LifePatternClaim resolveC02_CoreInnerNeed(List<EvidenceObject> evidenceList, LifePatternChartData chartData) {
        LifePatternClaim claim = createClaim("C02", "CORE_LIFE_THEME", "S2");

        // 硬门槛：必须至少有一个来自 Moon、4H/4L 或 Ascendant Lord 的 Claim-bearing Root
        List<EvidenceObject> moonEvidence = filterBySemanticFamily(evidenceList, "B_EMOTIONAL_SECURITY");
        List<EvidenceObject> ascLordEvidence = filterByAuthorityRole(evidenceList, "ASCENDANT_LORD");

        int primaryCount = countPrimary(moonEvidence) + countPrimary(ascLordEvidence);
        int totalSupport = countSupport(moonEvidence) + countSupport(ascLordEvidence);

        if (primaryCount >= 2 && totalSupport >= 2) {
            claim.setState("SUPPORTED");
            claim.setConfidence("HIGH");
            claim.setCandidateDirection(inferCoreDirection(moonEvidence, ascLordEvidence));
        } else if (primaryCount >= 1 && totalSupport >= 1) {
            claim.setState("CONDITIONAL");
            claim.setConfidence("MEDIUM");
            claim.setCandidateDirection(inferCoreDirection(moonEvidence, ascLordEvidence));
        } else if (totalSupport > 0) {
            claim.setState("INSUFFICIENT");
            claim.setConfidence("NONE");
        } else {
            claim.setState("INSUFFICIENT");
            claim.setConfidence("NONE");
        }

        claim.setSupportEvidenceIds(extractIds(moonEvidence));
        claim.setRenderPermission("DIRECT");
        return claim;
    }

    // ============================================================
    // C03: 安全感调节方式
    // ============================================================
    private LifePatternClaim resolveC03_SecurityRegulation(List<EvidenceObject> evidenceList, LifePatternChartData chartData) {
        LifePatternClaim claim = createClaim("C03", "SECURITY_REGULATION_PATTERN", "S2");

        // 硬门槛：必须有 Moon 或 4H/4L 的安全调节 Root
        List<EvidenceObject> moonEvidence = filterBySemanticFamily(evidenceList, "B_EMOTIONAL_SECURITY");
        List<EvidenceObject> moonSupport = filterByPolarity(moonEvidence, "SUPPORT");

        if (countPrimary(moonEvidence) >= 1 && moonSupport.size() >= 2) {
            claim.setState("SUPPORTED");
            claim.setConfidence("HIGH");
        } else if (countPrimary(moonEvidence) >= 1 && moonSupport.size() >= 1) {
            claim.setState("CONDITIONAL");
            claim.setConfidence("MEDIUM");
        } else {
            claim.setState("INSUFFICIENT");
            claim.setConfidence("NONE");
        }

        claim.setSupportEvidenceIds(extractIds(moonEvidence));
        claim.setRenderPermission("DIRECT");
        return claim;
    }

    // ============================================================
    // C04: 自我价值锚点
    // ============================================================
    private LifePatternClaim resolveC04_SelfWorthAnchor(List<EvidenceObject> evidenceList, LifePatternChartData chartData) {
        LifePatternClaim claim = createClaim("C04", "SELF_WORTH_ANCHOR", "S2");

        // 硬门槛：必须有 Ascendant / Ascendant Lord / Sun / 1H 功能 Root
        List<EvidenceObject> selfEvidence = filterBySemanticFamily(evidenceList, "A_SELF_IDENTITY");
        List<EvidenceObject> sunEvidence = filterBySubject(evidenceList, "Sun");

        int primaryCount = countPrimary(selfEvidence) + countPrimary(sunEvidence);

        if (primaryCount >= 2) {
            claim.setState("SUPPORTED");
            claim.setConfidence("HIGH");
        } else if (primaryCount >= 1) {
            claim.setState("CONDITIONAL");
            claim.setConfidence("MEDIUM");
        } else {
            claim.setState("INSUFFICIENT");
            claim.setConfidence("NONE");
        }

        claim.setSupportEvidenceIds(extractIds(selfEvidence));
        claim.setRenderPermission("DIRECT");
        return claim;
    }

    // ============================================================
    // C05: 压力下自动保护方式
    // ============================================================
    private LifePatternClaim resolveC05_ProtectiveResponse(List<EvidenceObject> evidenceList, LifePatternChartData chartData) {
        LifePatternClaim claim = createClaim("C05", "PROTECTIVE_RESPONSE_PATTERN", "S3");

        // 硬门槛：必须同时有"情绪/安全触发 Root"与"保护动作 Root"
        List<EvidenceObject> triggerEvidence = filterBySemanticFamily(evidenceList, "B_EMOTIONAL_SECURITY");
        List<EvidenceObject> protectionEvidence = filterBySemanticFamily(evidenceList, "C_ACTION_PROTECTION");

        boolean hasTrigger = countPrimary(triggerEvidence) >= 1;
        boolean hasProtection = countPrimary(protectionEvidence) >= 1;

        if (hasTrigger && hasProtection) {
            claim.setState("SUPPORTED");
            claim.setConfidence("HIGH");
            claim.setCandidateDirection("PROTECTIVE_RESPONSE");
        } else if (hasTrigger || hasProtection) {
            claim.setState("CONDITIONAL");
            claim.setConfidence("MEDIUM");
        } else {
            claim.setState("INSUFFICIENT");
            claim.setConfidence("NONE");
        }

        claim.setSupportEvidenceIds(extractIds(protectionEvidence));
        claim.setRenderPermission("DIRECT");
        return claim;
    }

    // ============================================================
    // C06: 内在拉扯（派生 Claim）
    // ============================================================
    private LifePatternClaim resolveC06_InnerConflict(Map<String, LifePatternClaim> claims) {
        LifePatternClaim claim = createClaim("C06", "INNER_CONFLICT", "S4");

        // 生成条件：两个 canonical opposite 方向分别至少达到 CONDITIONAL
        List<LifePatternClaim> supportedClaims = new ArrayList<>();
        for (LifePatternClaim c : claims.values()) {
            if ("SUPPORTED".equals(c.getState()) || "CONDITIONAL".equals(c.getState())) {
                supportedClaims.add(c);
            }
        }

        if (supportedClaims.size() >= 2) {
            claim.setState("SUPPORTED");
            claim.setConfidence("MEDIUM");
            claim.setCandidateDirection("DUAL_TENSION");
            claim.setDerivedFrom("C02_C04_C05");
            claim.setRenderPermission("DERIVED_ONLY");
        } else {
            claim.setState("INSUFFICIENT");
            claim.setConfidence("NONE");
            claim.setRenderPermission("DERIVED_ONLY");
            claim.setOmissionReason("Supported claims < 2");
        }

        return claim;
    }

    // ============================================================
    // C07: 容易使用过头的模式
    // ============================================================
    private LifePatternClaim resolveC07_OverusePattern(List<EvidenceObject> evidenceList, LifePatternChartData chartData) {
        LifePatternClaim claim = createClaim("C07", "OVERUSE_PATTERN", "S3");

        // 硬门槛：必须能连接到已成立的 C05 或 C08
        List<EvidenceObject> protectionEvidence = filterBySemanticFamily(evidenceList, "C_ACTION_PROTECTION");
        List<EvidenceObject> strengthEvidence = filterBySemanticFamily(evidenceList, "D_VALUE_RELATIONAL_REGULATION");

        int primaryCount = countPrimary(protectionEvidence) + countPrimary(strengthEvidence);

        if (primaryCount >= 2) {
            claim.setState("SUPPORTED");
            claim.setConfidence("MEDIUM");
            claim.setCandidateDirection("OVERUSE");
        } else if (primaryCount >= 1) {
            claim.setState("CONDITIONAL");
            claim.setConfidence("LOW");
        } else {
            claim.setState("INSUFFICIENT");
            claim.setConfidence("NONE");
        }

        claim.setSupportEvidenceIds(extractIds(protectionEvidence));
        claim.setRenderPermission("DIRECT");
        return claim;
    }

    // ============================================================
    // C08: 已有优势资源
    // ============================================================
    private LifePatternClaim resolveC08_StrengthResource(List<EvidenceObject> evidenceList, LifePatternChartData chartData) {
        LifePatternClaim claim = createClaim("C08", "STRENGTH_RESOURCE", "S2");

        // 硬门槛：必须有至少 2 个独立 D1 Root；Shadbala、dignity 不能 Seed 优势
        List<EvidenceObject> d1Evidence = filterByChartSource(evidenceList, "D1");
        long independentRoots = d1Evidence.stream()
                .filter(e -> "INDEPENDENT".equals(e.getDependencyStatus()))
                .map(EvidenceObject::getIndependenceClusterKey)
                .distinct()
                .count();

        if (independentRoots >= 2 && countPrimary(d1Evidence) >= 2) {
            claim.setState("SUPPORTED");
            claim.setConfidence("HIGH");
        } else if (independentRoots >= 1) {
            claim.setState("CONDITIONAL");
            claim.setConfidence("MEDIUM");
        } else {
            claim.setState("INSUFFICIENT");
            claim.setConfidence("NONE");
        }

        claim.setSupportEvidenceIds(extractIds(d1Evidence));
        claim.setRenderPermission("DIRECT");
        return claim;
    }

    // ============================================================
    // C09: 重复人生循环（派生 Claim）
    // ============================================================
    private LifePatternClaim resolveC09_RepeatingLoop(Map<String, LifePatternClaim> claims) {
        LifePatternClaim claim = createClaim("C09", "REPEATING_LIFE_LOOP", "S5");

        // 硬门槛：C02 与 C05 必须 SUPPORTED；C03/C04 至少一项 SUPPORTED；C07 至少 CONDITIONAL
        LifePatternClaim c02 = claims.get("C02");
        LifePatternClaim c05 = claims.get("C05");
        LifePatternClaim c03 = claims.get("C03");
        LifePatternClaim c04 = claims.get("C04");
        LifePatternClaim c07 = claims.get("C07");

        boolean c02Ok = c02 != null && "SUPPORTED".equals(c02.getState());
        boolean c05Ok = c05 != null && "SUPPORTED".equals(c05.getState());
        boolean c03c04Ok = (c03 != null && "SUPPORTED".equals(c03.getState())) ||
                          (c04 != null && "SUPPORTED".equals(c04.getState()));
        boolean c07Ok = c07 != null && ("SUPPORTED".equals(c07.getState()) || "CONDITIONAL".equals(c07.getState()));

        if (c02Ok && c05Ok && c03c04Ok && c07Ok) {
            claim.setState("SUPPORTED");
            claim.setConfidence("HIGH");
            claim.setDerivedFrom("C02_C03_C04_C05_C07");
        } else if (c02Ok && c05Ok) {
            claim.setState("CONDITIONAL");
            claim.setConfidence("MEDIUM");
            claim.setDerivedFrom("C02_C05");
        } else {
            claim.setState("INSUFFICIENT");
            claim.setConfidence("NONE");
            claim.setOmissionReason("C02 or C05 not supported");
        }

        claim.setRenderPermission("DERIVED_ONLY");
        return claim;
    }

    // ============================================================
    // C10: 主要成长难点（派生 Claim）
    // ============================================================
    private LifePatternClaim resolveC10_GrowthChallenge(Map<String, LifePatternClaim> claims) {
        LifePatternClaim claim = createClaim("C10", "GROWTH_CHALLENGE", "S6");

        // 优先由 C09 的长期代价反推；若 C09 不足，可由已成立 C05 + C07 合成
        LifePatternClaim c09 = claims.get("C09");
        LifePatternClaim c05 = claims.get("C05");
        LifePatternClaim c07 = claims.get("C07");

        if (c09 != null && "SUPPORTED".equals(c09.getState())) {
            claim.setState("SUPPORTED");
            claim.setConfidence("MEDIUM");
            claim.setDerivedFrom("C09");
        } else if (c05 != null && c07 != null &&
                   "SUPPORTED".equals(c05.getState()) &&
                   ("SUPPORTED".equals(c07.getState()) || "CONDITIONAL".equals(c07.getState()))) {
            claim.setState("CONDITIONAL");
            claim.setConfidence("MEDIUM");
            claim.setDerivedFrom("C05_C07");
        } else {
            claim.setState("INSUFFICIENT");
            claim.setConfidence("NONE");
            claim.setOmissionReason("C09 insufficient and C05/C07 not available");
        }

        claim.setRenderPermission("DERIVED_ONLY");
        return claim;
    }

    // ============================================================
    // C11: 需要发展的成熟能力（派生 Claim）
    // ============================================================
    private LifePatternClaim resolveC11_MaturityCapacity(Map<String, LifePatternClaim> claims, List<EvidenceObject> evidenceList, LifePatternChartData chartData) {
        LifePatternClaim claim = createClaim("C11", "MATURITY_CAPACITY", "S6");

        // 生成顺序：C10 challenge → AK developmental support → D9 validation
        LifePatternClaim c10 = claims.get("C10");

        if (c10 != null && ("SUPPORTED".equals(c10.getState()) || "CONDITIONAL".equals(c10.getState()))) {
            claim.setState("CONDITIONAL");
            claim.setConfidence("MEDIUM");
            claim.setDerivedFrom("C10_AK");
        } else {
            claim.setState("INSUFFICIENT");
            claim.setConfidence("NONE");
            claim.setOmissionReason("C10 not available");
        }

        claim.setRenderPermission("DERIVED_ONLY");
        return claim;
    }

    // ============================================================
    // C12: 意义感取向
    // ============================================================
    private LifePatternClaim resolveC12_MeaningOrientation(List<EvidenceObject> evidenceList, LifePatternChartData chartData) {
        LifePatternClaim claim = createClaim("C12", "MEANING_ORIENTATION", "S7");

        // 硬门槛：必须有 Jupiter、9H/9L、AK 或等价意义功能的非 Node D1 Root
        List<EvidenceObject> jupiterEvidence = filterBySubject(evidenceList, "Jupiter");
        List<EvidenceObject> meaningEvidence = filterBySemanticFamily(evidenceList, "E_BELIEF_MEANING");

        int primaryCount = countPrimary(jupiterEvidence) + countPrimary(meaningEvidence);

        if (primaryCount >= 2) {
            claim.setState("SUPPORTED");
            claim.setConfidence("HIGH");
        } else if (primaryCount >= 1) {
            claim.setState("CONDITIONAL");
            claim.setConfidence("MEDIUM");
        } else {
            claim.setState("INSUFFICIENT");
            claim.setConfidence("NONE");
        }

        claim.setSupportEvidenceIds(extractIds(meaningEvidence));
        claim.setRenderPermission("DIRECT");
        return claim;
    }

    // ============================================================
    // C01: 核心人生主题（综合 Claim）
    // ============================================================
    private LifePatternClaim resolveC01_CoreLifeTheme(Map<String, LifePatternClaim> claims) {
        LifePatternClaim claim = createClaim("C01", "CORE_LIFE_THEME", "S0");

        // 生成条件：C02–C12 中至少 3 个 Natal Claim 为 SUPPORTED
        long supportedCount = claims.values().stream()
                .filter(c -> "SUPPORTED".equals(c.getState()))
                .count();

        boolean hasC02orC04 = claims.get("C02") != null && "SUPPORTED".equals(claims.get("C02").getState()) ||
                             claims.get("C04") != null && "SUPPORTED".equals(claims.get("C04").getState());

        if (supportedCount >= 3 && hasC02orC04) {
            claim.setState("SUPPORTED");
            claim.setConfidence("HIGH");
            claim.setCandidateDirection("INTEGRATED_THEME");
            claim.setThemes(Arrays.asList("CORE_IDENTITY", "RELATIONSHIP_PATTERN", "GROWTH_PATH"));
        } else if (supportedCount >= 2) {
            claim.setState("CONDITIONAL");
            claim.setConfidence("MEDIUM");
            claim.setThemes(Arrays.asList("CORE_IDENTITY"));
        } else {
            claim.setState("INSUFFICIENT");
            claim.setConfidence("NONE");
            claim.setOmissionReason("Supported claims < 3");
        }

        claim.setRenderPermission("DERIVED_ONLY");
        return claim;
    }

    // ============================================================
    // 辅助方法
    // ============================================================

    private LifePatternClaim createClaim(String id, String name, String section) {
        LifePatternClaim claim = new LifePatternClaim();
        claim.setClaimId(id);
        claim.setClaimName(name);
        claim.setSectionOwner(section);
        claim.setSupportEvidenceIds(new ArrayList<>());
        claim.setContradictEvidenceIds(new ArrayList<>());
        return claim;
    }

    private List<EvidenceObject> filterBySemanticFamily(List<EvidenceObject> evidenceList, String family) {
        List<EvidenceObject> result = new ArrayList<>();
        for (EvidenceObject ev : evidenceList) {
            if (family.equals(ev.getSemanticFamily())) {
                result.add(ev);
            }
        }
        return result;
    }

    private List<EvidenceObject> filterBySubject(List<EvidenceObject> evidenceList, String subject) {
        List<EvidenceObject> result = new ArrayList<>();
        for (EvidenceObject ev : evidenceList) {
            if (subject.equals(ev.getSubject())) {
                result.add(ev);
            }
        }
        return result;
    }

    private List<EvidenceObject> filterByPolarity(List<EvidenceObject> evidenceList, String polarity) {
        List<EvidenceObject> result = new ArrayList<>();
        for (EvidenceObject ev : evidenceList) {
            if (polarity.equals(ev.getPolarity())) {
                result.add(ev);
            }
        }
        return result;
    }

    private List<EvidenceObject> filterByChartSource(List<EvidenceObject> evidenceList, String chartSource) {
        List<EvidenceObject> result = new ArrayList<>();
        for (EvidenceObject ev : evidenceList) {
            if (chartSource.equals(ev.getChartSource())) {
                result.add(ev);
            }
        }
        return result;
    }

    private List<EvidenceObject> filterByAuthorityRole(List<EvidenceObject> evidenceList, String role) {
        List<EvidenceObject> result = new ArrayList<>();
        for (EvidenceObject ev : evidenceList) {
            if (role.equals(ev.getAuthorityRole())) {
                result.add(ev);
            }
        }
        return result;
    }

    private int countPrimary(List<EvidenceObject> evidenceList) {
        int count = 0;
        for (EvidenceObject ev : evidenceList) {
            if ("P".equals(ev.getNatalVoteEligibility())) {
                count++;
            }
        }
        return count;
    }

    private int countSupport(List<EvidenceObject> evidenceList) {
        int count = 0;
        for (EvidenceObject ev : evidenceList) {
            if ("SUPPORT".equals(ev.getPolarity()) || "NEUTRAL".equals(ev.getPolarity())) {
                count++;
            }
        }
        return count;
    }

    private List<String> extractIds(List<EvidenceObject> evidenceList) {
        List<String> ids = new ArrayList<>();
        for (EvidenceObject ev : evidenceList) {
            ids.add(ev.getEvidenceId());
        }
        return ids;
    }

    private String inferCoreDirection(List<EvidenceObject> moonEvidence, List<EvidenceObject> ascLordEvidence) {
        if (!moonEvidence.isEmpty()) {
            return "EMOTIONAL_SECURITY";
        } else if (!ascLordEvidence.isEmpty()) {
            return "IDENTITY_EXPRESSION";
        }
        return "UNDETERMINED";
    }
}
