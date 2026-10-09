package cn.dct.zpback.engine.children;

import cn.dct.zpback.constant.NatalChildrenConstants;
import cn.dct.zpback.entity.children.ChildrenClaim;
import cn.dct.zpback.entity.children.ChildrenEvidenceObject;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.*;

/**
 * 子女报告 - 确定性 Claim 解析引擎
 * 符合 SEALED 文档《01_子女与养育深度报告_最终开发总说明》第 8、9 节
 * 与 skill/references/claim_section_registry.md
 *
 * 解析顺序：C02–C09 → C10（派生）→ C01（派生）→ C11（Dasha 激活）
 * C01、C10 不接受原始 Evidence 投票；C11 不接受 natal vote。
 */
@Component
@Slf4j
public class ChildrenClaimEngine {

    /**
     * 解析全部 11 个 Claim
     */
    public Map<String, ChildrenClaim> resolveAllClaims(List<ChildrenEvidenceObject> evidenceList) {
        Map<String, ChildrenClaim> claims = new LinkedHashMap<>();

        // 1. 直接 Claim C02-C09
        claims.put("C02", resolveDirectClaim("C02", "FAMILY_EXPANSION_PATH", evidenceList, "CHILDREN_PATH"));
        claims.put("C03", resolveDirectClaim("C03", "PATH_PRESSURE_SOURCE", evidenceList, "PATH_SUPPORT_AND_PRESSURE"));
        claims.put("C04", resolveDirectClaim("C04", "PARENT_ROLE", evidenceList, "PARENT_ROLE"));
        claims.put("C05", resolveDirectClaim("C05", "NURTURING_STRENGTH", evidenceList, "NURTURING_STRENGTHS"));
        claims.put("C06", resolveDirectClaim("C06", "PARENTING_OVERUSE_RISK", evidenceList, "PARENTING_OVERUSE"));
        claims.put("C07", resolveDirectClaim("C07", "PARENT_CHILD_COMMUNICATION", evidenceList, "EMOTIONAL_SECURITY_AND_COMMUNICATION"));
        claims.put("C08", resolveInteractionArchetype(claims, evidenceList));
        claims.put("C09", resolveDirectClaim("C09", "COPARENTING_ENVIRONMENT", evidenceList, "COPARENTING_ENVIRONMENT"));

        // 2. 派生 C10 / C01
        claims.put("C10", deriveGrowthTask(claims));
        claims.put("C01", deriveChildrenPathTheme(claims));

        return claims;
    }

    // ============================================================
    // 直接 Claim 通用解析
    // ============================================================

    private ChildrenClaim resolveDirectClaim(String claimId, String claimKey,
                                              List<ChildrenEvidenceObject> evidenceList,
                                              String sectionOwner) {
        ChildrenClaim claim = new ChildrenClaim();
        claim.setClaimId(claimId);
        claim.setClaimKey(claimKey);
        claim.setSectionOwner(sectionOwner);
        claim.setRenderPermission("DIRECT");

        // 1. 仅保留 ELIGIBLE 且 claim 在 eligibility 内的 roots
        List<ChildrenEvidenceObject> eligible = new ArrayList<>();
        for (ChildrenEvidenceObject ev : evidenceList) {
            if ("ELIGIBLE".equals(ev.getEvidenceStatus())
                    && ev.getClaimEligibility().contains(claimId)
                    && !"CONTEXT_ONLY".equals(ev.getPolarity())) {
                eligible.add(ev);
            }
        }

        if (eligible.isEmpty()) {
            claim.setState("INSUFFICIENT");
            claim.setConfidence("NOT_APPLICABLE");
            claim.setPrimaryCandidate(defaultCandidate(claimId) + "_INSUFFICIENT");
            claim.getResolutionCodes().add("NO_ELIGIBLE_ROOTS");
            return claim;
        }

        // 2. 候选方向统计（每个 root 一个候选，candidate_match notes）
        Map<String, CandidateStats> candidates = new LinkedHashMap<>();
        for (ChildrenEvidenceObject ev : eligible) {
            String candidate = extractCandidate(ev);
            if (candidate == null) candidate = "UNSPECIFIED";
            CandidateStats stats = candidates.computeIfAbsent(candidate, k -> new CandidateStats());
            stats.rootIds.add(ev.getEvidenceId());
            stats.clusters.add(ev.getIndependenceClusterKey());
            if ("D1".equals(ev.getChartSource()) && "PRIMARY".equals(ev.getAuthorityRole())) {
                stats.d1Primary.add(ev.getEvidenceId());
            }
            if ("D7".equals(ev.getChartSource()) && "SPECIALIST".equals(ev.getAuthorityRole())) {
                stats.d7Specialist.add(ev.getEvidenceId());
            }
            if ("SUPPORT".equals(ev.getPolarity())) {
                stats.support.add(ev.getEvidenceId());
            } else if ("CONTRADICT".equals(ev.getPolarity())) {
                stats.contradict.add(ev.getEvidenceId());
            }
        }

        // 3. 候选优先级：D1+D7 一致 > cluster 数 > root 数 > 无高权威反证
        String best = null;
        CandidateStats bestStats = null;
        for (Map.Entry<String, CandidateStats> entry : candidates.entrySet()) {
            if (best == null || betterThan(entry.getValue(), bestStats)) {
                best = entry.getKey();
                bestStats = entry.getValue();
            }
        }

        claim.setPrimaryCandidate(best);
        claim.setSupportRootIds(new ArrayList<>(bestStats.support));
        claim.setContradictRootIds(new ArrayList<>(bestStats.contradict));
        claim.setD1PrimaryRootIds(new ArrayList<>(bestStats.d1Primary));
        claim.setD7SpecialistRootIds(new ArrayList<>(bestStats.d7Specialist));
        claim.setClusters(new ArrayList<>(bestStats.clusters));

        // 4. 子女报告特有门槛（8.4）
        resolveState(claim, claimId, bestStats, candidates);
        return claim;
    }

    private void resolveState(ChildrenClaim claim, String claimId, CandidateStats best,
                               Map<String, CandidateStats> candidates) {
        boolean hasD1Primary = !best.d1Primary.isEmpty();
        boolean hasD7Specialist = !best.d7Specialist.isEmpty();
        boolean crossCluster = best.clusters.size() >= 2;
        boolean noPrimaryContradict = best.contradict.isEmpty();

        // 混合候选枚举
        if (candidates.size() > 1) {
            List<CandidateStats> sorted = new ArrayList<>(candidates.values());
            sorted.sort((a, b) -> betterThan(b, a) ? 1 : -1);
            if (sorted.size() > 1) {
                claim.setSecondaryCandidate(candidates.entrySet().stream()
                        .filter(e -> e.getValue() == sorted.get(1))
                        .map(Map.Entry::getKey).findFirst().orElse(null));
            }
        }

        // C02/C03：SUPPORTED 需要 D1 Primary + D7 Specialist
        if ("C02".equals(claimId) || "C03".equals(claimId)) {
            if (hasD1Primary && hasD7Specialist && crossCluster && noPrimaryContradict) {
                claim.setState("SUPPORTED");
                claim.setConfidence("HIGH");
            } else if (!hasD1Primary && !hasD7Specialist) {
                claim.setState("INSUFFICIENT");
                claim.setConfidence("NOT_APPLICABLE");
            } else {
                claim.setState("CONDITIONAL");
                claim.setConfidence("MEDIUM");
            }
            // D1 Primary 与 D7 Specialist 直接冲突 → MIXED
            if (hasD1Primary && hasD7Specialist && hasOpposingDirections(candidates)) {
                claim.setState("MIXED");
                claim.setConfidence("LOW");
                claim.getResolutionCodes().add("D1_D7_PRIMARY_CONFLICT");
            }
            return;
        }

        // C04-C08：需要 D7 root + D1/Moon/Jupiter natal root
        if (Arrays.asList("C04", "C05", "C06", "C07", "C08").contains(claimId)) {
            boolean hasNatalCore = !best.d1Primary.isEmpty();
            if (hasD7Specialist && hasNatalCore && crossCluster && noPrimaryContradict) {
                claim.setState("SUPPORTED");
                claim.setConfidence("HIGH");
            } else if (hasD7Specialist && !hasNatalCore) {
                claim.setState("CONDITIONAL");
                claim.setConfidence("MEDIUM");
                claim.getResolutionCodes().add("D7_ONLY");
            } else if (!hasD7Specialist && hasNatalCore) {
                claim.setState("CONDITIONAL");
                claim.setConfidence("LOW");
                claim.getResolutionCodes().add("D1_ONLY");
            } else {
                claim.setState("INSUFFICIENT");
                claim.setConfidence("NOT_APPLICABLE");
            }
            return;
        }

        // C09：需要 D1 或 D7 direct bridge；仅 D9 = INSUFFICIENT
        if ("C09".equals(claimId)) {
            if (best.support.isEmpty()) {
                claim.setState("INSUFFICIENT");
                claim.setConfidence("NOT_APPLICABLE");
                claim.getResolutionCodes().add("INSUFFICIENT_COPARENTING_DATA");
            } else if (hasD1Primary && hasD7Specialist && noPrimaryContradict) {
                claim.setState("SUPPORTED");
                claim.setConfidence("HIGH");
            } else {
                claim.setState("CONDITIONAL");
                claim.setConfidence("MEDIUM");
            }
        }
    }

    private boolean hasOpposingDirections(Map<String, CandidateStats> candidates) {
        // 简化判定：存在多个候选，其中两个都有高权威根，且候选名互为对立枚举
        if (candidates.size() < 2) return false;
        int highAuthorityCount = 0;
        for (CandidateStats s : candidates.values()) {
            if (!s.d1Primary.isEmpty() || !s.d7Specialist.isEmpty()) highAuthorityCount++;
        }
        return highAuthorityCount >= 2;
    }

    private boolean betterThan(CandidateStats a, CandidateStats b) {
        int aScore = 0, bScore = 0;
        // D1 与 D7 高权威一致最高
        if (!a.d1Primary.isEmpty() && !a.d7Specialist.isEmpty()) aScore += 100;
        if (!b.d1Primary.isEmpty() && !b.d7Specialist.isEmpty()) bScore += 100;
        aScore += a.clusters.size() * 10 + a.support.size();
        bScore += b.clusters.size() * 10 + b.support.size();
        if (a.contradict.isEmpty()) aScore += 5;
        if (b.contradict.isEmpty()) bScore += 5;
        return aScore > bScore;
    }

    private String extractCandidate(ChildrenEvidenceObject ev) {
        for (String note : ev.getNotes()) {
            if (note.startsWith("candidate_match:")) {
                String[] parts = note.substring("candidate_match:".length()).split("=", 2);
                if (parts.length == 2) return parts[1];
            }
        }
        return null;
    }

    private String defaultCandidate(String claimId) {
        switch (claimId) {
            case "C02": return "INSUFFICIENT_PATH";
            case "C03": return "NO_DOMINANT_PRESSURE";
            case "C04": return "ADAPTIVE_MIXED_ROLE";
            case "C05": return "ADAPTIVE_RESOURCE_MIX";
            case "C06": return "NO_DOMINANT_OVERUSE_RISK";
            case "C07": return "ADAPTIVE_MIXED";
            case "C08": return "MIXED_INTERACTION";
            case "C09": return "INSUFFICIENT_COPARENTING_DATA";
            default: return "UNSPECIFIED";
        }
    }

    // ============================================================
    // C08：互动原型（以 C04/C07 为结构输入）
    // ============================================================

    private ChildrenClaim resolveInteractionArchetype(Map<String, ChildrenClaim> claims,
                                                       List<ChildrenEvidenceObject> evidenceList) {
        ChildrenClaim claim = resolveDirectClaim("C08", "PARENT_CHILD_INTERACTION_ARCHETYPE",
                evidenceList, "INTERACTION_ARCHETYPE");
        ChildrenClaim c04 = claims.get("C04");
        ChildrenClaim c07 = claims.get("C07");

        // C08 以 C04/C07 已解析方向为结构输入
        boolean c04ok = c04 != null && ("SUPPORTED".equals(c04.getState()) || "CONDITIONAL".equals(c04.getState()));
        boolean c07ok = c07 != null && ("SUPPORTED".equals(c07.getState()) || "CONDITIONAL".equals(c07.getState()));

        if (c04ok && c07ok && "INSUFFICIENT".equals(claim.getState())) {
            String role = c04.getPrimaryCandidate();
            String comm = c07.getPrimaryCandidate();
            String archetype = deriveArchetype(role, comm);
            claim.setPrimaryCandidate(archetype);
            claim.setState("CONDITIONAL");
            claim.setConfidence("MEDIUM");
            claim.getResolutionCodes().add("DERIVED_FROM_C04_C07");
            claim.getSupportRootIds().addAll(c04.getSupportRootIds());
            claim.getSupportRootIds().addAll(c07.getSupportRootIds());
        }
        return claim;
    }

    private String deriveArchetype(String role, String comm) {
        if (role == null || comm == null) return "MIXED_INTERACTION";
        if ("EMOTIONAL_COMPANION".equals(role) || "PROTECTIVE_CAREGIVER".equals(role)) {
            return "CLOSE_COLLABORATIVE";
        }
        if ("STRUCTURE_BUILDER".equals(role) || "GUIDING_TEACHER".equals(role)) {
            return "STRUCTURED_MENTORING";
        }
        if ("INDEPENDENCE_COACH".equals(role)) return "INDEPENDENT_SPACIOUS";
        if ("PROTECTIVE_CAREGIVER".equals(comm)) return "PROTECTIVE_DEPENDENT";
        if ("SPACE_FIRST".equals(comm) || "ACTION_FIRST".equals(comm)) return "INDEPENDENT_SPACIOUS";
        if ("RULE_FIRST".equals(comm)) return "STRUCTURED_MENTORING";
        return "MIXED_INTERACTION";
    }

    // ============================================================
    // C10：成长任务（由 C06/C07/C09 派生）
    // ============================================================

    private ChildrenClaim deriveGrowthTask(Map<String, ChildrenClaim> claims) {
        ChildrenClaim claim = new ChildrenClaim();
        claim.setClaimId("C10");
        claim.setClaimKey("PARENTAL_GROWTH_TASK");
        claim.setSectionOwner("PARENTAL_GROWTH");
        claim.setRenderPermission("DERIVED_ONLY");

        ChildrenClaim c06 = claims.get("C06");
        ChildrenClaim c07 = claims.get("C07");
        ChildrenClaim c09 = claims.get("C09");

        List<String> usable = new ArrayList<>();
        if (c06 != null && isUsable(c06)) usable.add("C06");
        if (c07 != null && isUsable(c07)) usable.add("C07");
        if (c09 != null && isUsable(c09)) usable.add("C09");

        // 门槛：至少两个来源可用，或一个 Supported 且另一个 Conditional
        boolean gate = usable.size() >= 2;
        if (!gate && usable.size() == 1) {
            String first = usable.get(0);
            ChildrenClaim source = claims.get(first);
            String secondId = usable.contains("C06") ? "C07" : "C06";
            ChildrenClaim second = claims.get(secondId);
            if ("SUPPORTED".equals(source.getState()) && second != null && "CONDITIONAL".equals(second.getState())) {
                gate = true;
            }
        }

        if (!gate) {
            claim.setState("INSUFFICIENT");
            claim.setConfidence("NOT_APPLICABLE");
            claim.setPrimaryCandidate("INSUFFICIENT_GROWTH_TASK");
            claim.setOmissionReason("C06/C07/C09 derivation gate not met");
            return claim;
        }

        // 派生表（claim_section_registry 12）
        String candidate;
        String overuse = c06 != null ? c06.getPrimaryCandidate() : null;
        String comm = c07 != null ? c07.getPrimaryCandidate() : null;
        String coparent = c09 != null ? c09.getPrimaryCandidate() : null;

        if ("OVERCONTROL".equals(overuse) || "RULE_FIRST".equals(comm)) {
            candidate = "FLEXIBLE_CONTROL";
        } else if ("OVERPROTECTION".equals(overuse)) {
            candidate = "TRUST_AND_RELEASE";
        } else if ("OVERRESPONSIBILITY".equals(overuse) || "OVERACCOMMODATION".equals(overuse)) {
            candidate = "BALANCE_SELF_AND_CARE";
        } else if ("NEGOTIATION_REQUIRED".equals(coparent)) {
            candidate = "EXPRESS_NEEDS_AND_LIMITS";
        } else if ("OVEREXPECTATION".equals(overuse) || "NONLINEAR_OR_UNCONVENTIONAL".equals(
                claims.get("C02") != null ? claims.get("C02").getPrimaryCandidate() : null)) {
            candidate = "ADAPT_EXPECTATIONS";
        } else if ("EMOTIONAL_WITHDRAWAL".equals(overuse)) {
            candidate = "SUSTAIN_CONNECTION_ACROSS_CHANGE";
        } else if ("TIMING_AND_PATIENCE".equals(
                claims.get("C03") != null ? claims.get("C03").getPrimaryCandidate() : null)) {
            candidate = "PATIENCE_WITH_UNCERTAINTY";
        } else {
            candidate = "INTEGRATED_GROWTH_TASK";
        }

        claim.setPrimaryCandidate(candidate);
        claim.setState("SUPPORTED");
        claim.setConfidence("MEDIUM");
        claim.setDerivedFrom(String.join(",", usable));
        return claim;
    }

    private boolean isUsable(ChildrenClaim c) {
        return "SUPPORTED".equals(c.getState()) || "CONDITIONAL".equals(c.getState()) || "MIXED".equals(c.getState());
    }

    // ============================================================
    // C01：核心主题（由 C02/C03/C04/C10 派生）
    // ============================================================

    private ChildrenClaim deriveChildrenPathTheme(Map<String, ChildrenClaim> claims) {
        ChildrenClaim claim = new ChildrenClaim();
        claim.setClaimId("C01");
        claim.setClaimKey("CHILDREN_PATH_THEME");
        claim.setSectionOwner("EXECUTIVE_SUMMARY");
        claim.setRenderPermission("DERIVED_ONLY");

        ChildrenClaim c02 = claims.get("C02");
        ChildrenClaim c03 = claims.get("C03");
        ChildrenClaim c04 = claims.get("C04");
        ChildrenClaim c10 = claims.get("C10");

        List<String> available = new ArrayList<>();
        if (c02 != null && isUsable(c02)) available.add("C02");
        if (c03 != null && isUsable(c03)) available.add("C03");
        if (c04 != null && isUsable(c04)) available.add("C04");
        if (c10 != null && isUsable(c10)) available.add("C10");

        if (available.size() < 2) {
            claim.setState("INSUFFICIENT");
            claim.setConfidence("NOT_APPLICABLE");
            claim.setPrimaryCandidate("INSUFFICIENT_THEME");
            claim.setOmissionReason("C02/C03/C04/C10 available < 2");
            return claim;
        }

        String c02Cand = c02 != null ? c02.getPrimaryCandidate() : null;
        String c03Cand = c03 != null ? c03.getPrimaryCandidate() : null;
        String c10Cand = c10 != null ? c10.getPrimaryCandidate() : null;

        String candidate;
        if ("RELATIVELY_SUPPORTED".equals(c02Cand)
                && ("PROTECTIVE_CAREGIVER".equals(c04 != null ? c04.getPrimaryCandidate() : null)
                    || "BALANCE_SELF_AND_CARE".equals(c10Cand))) {
            candidate = "GROWTH_THROUGH_CARE_AND_CONTINUITY";
        } else if ("SUPPORTED_WITH_RESPONSIBILITY".equals(c02Cand)
                || "RESPONSIBILITY_OVERLOAD".equals(c03Cand)) {
            candidate = "GROWTH_THROUGH_RESPONSIBILITY_AND_PATIENCE";
        } else if ("NONLINEAR_OR_UNCONVENTIONAL".equals(c02Cand)
                || "CHANGE_OR_UNPREDICTABILITY".equals(c03Cand)) {
            candidate = "GROWTH_THROUGH_FLEXIBILITY_AND_NONLINEARITY";
        } else if ("PARTNERSHIP_COORDINATION".equals(c03Cand)) {
            candidate = "GROWTH_THROUGH_RELATIONSHIP_COORDINATION";
        } else if ("BALANCE_SELF_AND_CARE".equals(c10Cand) || "RECEIVE_SUPPORT".equals(c10Cand)) {
            candidate = "GROWTH_THROUGH_BALANCING_CARE_AND_SELF";
        } else {
            candidate = "COMPLEX_MIXED_THEME";
        }

        claim.setPrimaryCandidate(candidate);
        claim.setState("SUPPORTED");
        claim.setConfidence("MEDIUM");
        claim.setDerivedFrom(String.join(",", available));
        return claim;
    }

    // ============================================================
    // 候选统计
    // ============================================================

    private static class CandidateStats {
        final List<String> rootIds = new ArrayList<>();
        final Set<String> clusters = new LinkedHashSet<>();
        final List<String> d1Primary = new ArrayList<>();
        final List<String> d7Specialist = new ArrayList<>();
        final List<String> support = new ArrayList<>();
        final List<String> contradict = new ArrayList<>();
    }
}
