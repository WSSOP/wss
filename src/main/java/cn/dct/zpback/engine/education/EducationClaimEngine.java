package cn.dct.zpback.engine.education;

import cn.dct.zpback.constant.EducationReportConstants;
import cn.dct.zpback.entity.education.EducationClaim;
import cn.dct.zpback.entity.education.EducationEvidenceObject;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.*;

/**
 * 学业报告 - 确定性 Claim 解析引擎
 * 符合 SEALED 文档《01_学业深度解析报告》§6 Registry、§7 最小充分条件、§14 Cross-chart Resolver
 *
 * 运行时不变量：canonical facts → eligible Evidence → deduplicated Candidates
 * → D1 sufficiency → D1×D24 resolution → derived Claims
 *
 * 解析顺序：C02-C06/C08 → C09 → C10 → C07/C11 → C12 → C01（C13 由 Timing 引擎处理）
 */
@Component
@Slf4j
public class EducationClaimEngine {

    /** 基础 Natal Claims */
    private static final List<String> BASE_CLAIMS = Arrays.asList("C02", "C03", "C04", "C05", "C06", "C08");

    /**
     * 解析全部 Claim（C01-C12；C13 由 Timing 引擎处理）
     */
    public Map<String, EducationClaim> resolveAllClaims(List<EducationEvidenceObject> evidenceList) {
        Map<String, EducationClaim> claims = new LinkedHashMap<>();

        // 1. 基础 Natal Claims（D1 充分性 → D24 解析）
        for (String claimId : BASE_CLAIMS) {
            claims.put(claimId, resolveBaseClaim(claimId, evidenceList));
        }

        // 2. C09 阻力循环（Hybrid：挑战根）
        claims.put("C09", resolveC09(evidenceList));

        // 3. C10 知识方向（Composite：2 roots from 2 families + D24）
        claims.put("C10", resolveC10(evidenceList, claims));

        // 4. C07 方法环境（Derived：1 个 C02-C04 + 1 个 C05-C06 已成立）
        claims.put("C07", deriveC07(claims));

        // 5. C11 学习路线（Composite：2 个已成立 Claim + D24 语境）
        claims.put("C11", resolveC11(evidenceList, claims));

        // 6. C12 深造路径（Derived：C06/C09/C10/C11 + D24 条件）
        claims.put("C12", deriveC12(claims));

        // 7. C01 总览（Derived：≥4 个可渲染 Claim，两组各至少一个）
        claims.put("C01", deriveC01(claims));

        log.info("Education Claims 解析完成: supported={}, mixed={}, insufficient={}",
                claims.values().stream().filter(c -> "SUPPORTED".equals(c.getState())).count(),
                claims.values().stream().filter(c -> "MIXED".equals(c.getState())).count(),
                claims.values().stream().filter(c -> "INSUFFICIENT".equals(c.getState())).count());
        return claims;
    }

    // ============================================================
    // 基础 Claim：C02-C06, C08（§7.1 / contract §5）
    // ============================================================

    private EducationClaim resolveBaseClaim(String claimId, List<EducationEvidenceObject> evidenceList) {
        EducationClaim claim = new EducationClaim();
        claim.setClaimId(claimId);
        claim.setClaimKey(claimKeyOf(claimId));
        claim.setClaimType("Natal");
        claim.setRenderPermission("DIRECT");
        claim.setSectionOwner(sectionOwnerOf(claimId));

        // 1. 收集 vote-bearing 且 family 在 Claim 许可范围内的根
        List<String> allowedFamilies = EducationTagRegistry.getClaimFamilies(claimId);
        List<EducationEvidenceObject> voteRoots = new ArrayList<>();
        for (EducationEvidenceObject ev : evidenceList) {
            if (!"VOTE_BEARING".equals(ev.getNatalVoteEligibility())) continue;
            if (!"ELIGIBLE".equals(ev.getEvidenceStatus())) continue;
            if (!allowedFamilies.contains(ev.getFamily())) continue;
            voteRoots.add(ev);
        }

        if (voteRoots.isEmpty()) {
            return insufficient(claim, "NO_ELIGIBLE_ROOTS");
        }

        // 2. 候选方向统计
        Map<String, CandidateBucket> candidates = new LinkedHashMap<>();
        for (EducationEvidenceObject ev : voteRoots) {
            String candidate = matchCandidate(claimId, ev);
            if (candidate == null) continue;
            CandidateBucket bucket = candidates.computeIfAbsent(candidate, k -> new CandidateBucket());
            bucket.evidenceIds.add(ev.getEvidenceId());
            bucket.families.add(ev.getFamily());
            bucket.clusters.add(ev.getIndependenceClusterKey());
            bucket.roots.add(ev.getRootKey());
            if ("CHALLENGE".equals(ev.getPolarity()) || "CONSTRAINING".equals(ev.getFunctionalCondition())) {
                bucket.challenge.add(ev.getEvidenceId());
            } else {
                bucket.support.add(ev.getEvidenceId());
            }
        }

        if (candidates.isEmpty()) {
            return insufficient(claim, "NO_SEMANTIC_MATCH");
        }

        // 3. 主候选：支持根最多、family 覆盖最广
        CandidateBucket best = null;
        String bestCandidate = null;
        for (Map.Entry<String, CandidateBucket> entry : candidates.entrySet()) {
            if (best == null || entry.getValue().score() > best.score()) {
                best = entry.getValue();
                bestCandidate = entry.getKey();
            }
        }
        claim.setPrimaryCandidate(bestCandidate);

        // 次候选（qualifiers 最多 2）
        List<String> sorted = new ArrayList<>(candidates.keySet());
        sorted.sort((a, b) -> candidates.get(b).score() - candidates.get(a).score());
        for (int i = 1; i < sorted.size() && claim.getQualifierCandidates().size() < 2; i++) {
            if (!sorted.get(i).equals(bestCandidate)) {
                claim.getQualifierCandidates().add(sorted.get(i));
            }
        }

        claim.setSupportRootIds(new ArrayList<>(best.support));
        claim.setChallengeRootIds(new ArrayList<>(best.challenge));
        claim.setSupportFamilies(new ArrayList<>(best.families));

        // 4. D1 最小充分条件（§7.1）：
        //    两个独立 vote-bearing roots 来自至少两个 family；或
        //    一个高权限 root + 一个独立 supporting root
        boolean twoFamilies = best.families.size() >= 2 && best.roots.size() >= 2;
        boolean highAuthorityPlusSupport = best.support.size() >= 2 && best.roots.size() >= 2;
        boolean d1Sufficient = twoFamilies || highAuthorityPlusSupport;

        if (!d1Sufficient) {
            // D1 不足 → 无论 D24 多强都 INSUFFICIENT（§14.1）
            claim.setState(EducationReportConstants.STATE_INSUFFICIENT);
            claim.setConfidence(EducationReportConstants.CONF_LOW);
            claim.setInsufficiencyReason(EducationReportConstants.REASON_INSUFFICIENT_NATAL_ORIGIN);
            claim.getResolutionCodes().add("D1_MIN_SUFFICIENCY_NOT_MET");
            return claim;
        }

        // 5. D1×D24 解析（§14.1 矩阵）
        applyD24Resolution(claim, voteRoots, evidenceList);
        return claim;
    }

    /**
     * D1×D24 解析矩阵（§14.1）
     */
    private void applyD24Resolution(EducationClaim claim, List<EducationEvidenceObject> d1Roots,
                                     List<EducationEvidenceObject> evidenceList) {
        // 收集相关 D24 确认证据
        List<EducationEvidenceObject> d24Confirm = new ArrayList<>();
        List<EducationEvidenceObject> d24Challenge = new ArrayList<>();
        for (EducationEvidenceObject ev : evidenceList) {
            if (!"D24".equals(ev.getChartSource())) continue;
            if (!claim.getClaimId().matches("C0[2-9]|C1[0-2]")) continue;
            // D24 与 D1 同 family 相关
            boolean relevant = d1Roots.stream().anyMatch(d1 ->
                    d1.getFamily() != null && d1.getFamily().equals(ev.getFamily()));
            if (!relevant) continue;
            if ("SUPPORT".equals(ev.getPolarity())) d24Confirm.add(ev);
            else if ("CHALLENGE".equals(ev.getPolarity())) d24Challenge.add(ev);
        }

        if (d24Confirm.isEmpty() && d24Challenge.isEmpty()) {
            // D24 缺失相关确认 → SUPPORTED with LOW/MEDIUM confidence
            claim.setState(EducationReportConstants.STATE_SUPPORTED);
            claim.setConfidence(EducationReportConstants.CONF_LOW);
            claim.getResolutionCodes().add("NO_D24_CONFIRMATION");
            return;
        }

        boolean d1AllChallenge = claim.getSupportRootIds().isEmpty();
        if (d1AllChallenge) {
            // D1 挑战成立 + D24 挑战确认 → 学业领域实际阻力，不译为"没有学习能力"
            claim.setState(EducationReportConstants.STATE_MIXED);
            claim.setConfidence(EducationReportConstants.CONF_MEDIUM);
            claim.setD24Action(EducationReportConstants.D24_CONTRADICTS);
            claim.getResolutionCodes().add("D1_CHALLENGE_D24_CHALLENGE");
            return;
        }

        if (!d24Challenge.isEmpty()) {
            // D24 对 D1 增加条件或同级冲突
            claim.setState(EducationReportConstants.STATE_MIXED);
            claim.setConfidence(EducationReportConstants.CONF_MEDIUM);
            claim.setD24Action(d24Confirm.isEmpty()
                    ? EducationReportConstants.D24_CONTRADICTS
                    : EducationReportConstants.D24_QUALIFIES);
            claim.getResolutionCodes().add("D24_QUALIFIES_OR_CONTRADICTS");
            return;
        }

        // D24 同向确认 → SUPPORTED
        claim.setState(EducationReportConstants.STATE_SUPPORTED);
        claim.setD24Action(EducationReportConstants.D24_CONFIRMS);
        claim.setConfidence(claim.getSupportFamilies().size() >= 2 && claim.getChallengeRootIds().isEmpty()
                ? EducationReportConstants.CONF_HIGH
                : EducationReportConstants.CONF_MEDIUM);
    }

    // ============================================================
    // C09 阻力循环（§7.3）
    // ============================================================

    private EducationClaim resolveC09(List<EducationEvidenceObject> evidenceList) {
        EducationClaim claim = new EducationClaim();
        claim.setClaimId("C09");
        claim.setClaimKey("FRICTION_AND_INTERRUPTION_PATTERN");
        claim.setClaimType("Hybrid");
        claim.setRenderPermission("DIRECT");
        claim.setSectionOwner("FRICTION_LOOPS");

        List<String> allowedFamilies = EducationTagRegistry.getClaimFamilies("C09");
        List<EducationEvidenceObject> challengeRoots = new ArrayList<>();
        for (EducationEvidenceObject ev : evidenceList) {
            if (!"VOTE_BEARING".equals(ev.getNatalVoteEligibility())) continue;
            if (!"ELIGIBLE".equals(ev.getEvidenceStatus())) continue;
            if (!allowedFamilies.contains(ev.getFamily())) continue;
            if ("CHALLENGE".equals(ev.getPolarity()) || "CONSTRAINING".equals(ev.getFunctionalCondition())) {
                challengeRoots.add(ev);
            }
        }

        // 门槛（§7.3）：一个直接挑战 Root + 独立影响链；或两个独立挑战 Roots
        Map<String, CandidateBucket> candidates = new LinkedHashMap<>();
        for (EducationEvidenceObject ev : challengeRoots) {
            String candidate = matchCandidate("C09", ev);
            if (candidate == null) continue;
            CandidateBucket bucket = candidates.computeIfAbsent(candidate, k -> new CandidateBucket());
            bucket.evidenceIds.add(ev.getEvidenceId());
            bucket.families.add(ev.getFamily());
            bucket.roots.add(ev.getRootKey());
            bucket.clusters.add(ev.getIndependenceClusterKey());
            bucket.challenge.add(ev.getEvidenceId());
        }

        // 选挑战根最多且跨 family 的方向
        String best = null;
        CandidateBucket bestBucket = null;
        for (Map.Entry<String, CandidateBucket> entry : candidates.entrySet()) {
            if (best == null || entry.getValue().score() > bestBucket.score()) {
                best = entry.getKey();
                bestBucket = entry.getValue();
            }
        }

        boolean sufficient = bestBucket != null
                && (bestBucket.roots.size() >= 2 || (bestBucket.roots.size() >= 1 && bestBucket.families.size() >= 2));
        // 只有单个不利修饰项时必须 INSUFFICIENT
        if (!sufficient) {
            return insufficient(claim, "SINGLE_CHALLENGE_MODIFIER_ONLY");
        }

        claim.setPrimaryCandidate(best);
        claim.setChallengeRootIds(new ArrayList<>(bestBucket.challenge));
        claim.setSupportFamilies(new ArrayList<>(bestBucket.families));
        claim.setState(EducationReportConstants.STATE_SUPPORTED);
        claim.setConfidence(bestBucket.families.size() >= 2
                ? EducationReportConstants.CONF_MEDIUM : EducationReportConstants.CONF_LOW);
        return claim;
    }

    // ============================================================
    // C10 知识方向（§7.4）
    // ============================================================

    private EducationClaim resolveC10(List<EducationEvidenceObject> evidenceList,
                                       Map<String, EducationClaim> claims) {
        EducationClaim claim = new EducationClaim();
        claim.setClaimId("C10");
        claim.setClaimKey("SUBJECT_MAJOR_FIT");
        claim.setClaimType("Composite");
        claim.setRenderPermission("DIRECT");
        claim.setSectionOwner("SUBJECT_DIRECTIONS");

        List<String> allowedFamilies = EducationTagRegistry.getClaimFamilies("C10");
        Map<String, CandidateBucket> candidates = new LinkedHashMap<>();
        for (EducationEvidenceObject ev : evidenceList) {
            if (!"VOTE_BEARING".equals(ev.getNatalVoteEligibility())) continue;
            if (!"ELIGIBLE".equals(ev.getEvidenceStatus())) continue;
            if (!allowedFamilies.contains(ev.getFamily())) continue;
            String candidate = matchCandidate("C10", ev);
            if (candidate == null) continue;
            CandidateBucket bucket = candidates.computeIfAbsent(candidate, k -> new CandidateBucket());
            bucket.evidenceIds.add(ev.getEvidenceId());
            bucket.families.add(ev.getFamily());
            bucket.roots.add(ev.getRootKey());
            bucket.clusters.add(ev.getIndependenceClusterKey());
            if ("CHALLENGE".equals(ev.getPolarity())) {
                bucket.challenge.add(ev.getEvidenceId());
            } else {
                bucket.support.add(ev.getEvidenceId());
            }
        }

        // 门槛：每个方向簇至少两个独立 D1 Roots 来自两个 Family + D24 确认
        List<String> qualified = new ArrayList<>();
        for (Map.Entry<String, CandidateBucket> entry : candidates.entrySet()) {
            CandidateBucket b = entry.getValue();
            if (b.families.size() >= 2 && b.roots.size() >= 2 && b.support.size() >= 2) {
                qualified.add(entry.getKey());
            }
        }
        qualified.sort((a, b) -> candidates.get(b).score() - candidates.get(a).score());

        // 只输出排名最高且彼此有区分度的 2-4 项（§7.4）
        List<String> selected = new ArrayList<>();
        for (String q : qualified) {
            if (selected.size() >= EducationReportConstants.MAX_DIRECTIONS) break;
            selected.add(q);
        }

        if (selected.size() < EducationReportConstants.MIN_DIRECTIONS) {
            return insufficient(claim, "INSUFFICIENT_NATAL_ORIGIN");
        }

        claim.setPrimaryCandidate(selected.get(0));
        for (int i = 1; i < selected.size(); i++) {
            claim.getQualifierCandidates().add(selected.get(i));
        }
        claim.setSupportRootIds(candidates.get(selected.get(0)).support);
        claim.setSupportFamilies(new ArrayList<>(candidates.get(selected.get(0)).families));

        // D24 确认
        boolean hasD24 = evidenceList.stream().anyMatch(e ->
                "D24".equals(e.getChartSource()) && "SUPPORT".equals(e.getPolarity()));
        claim.setState(EducationReportConstants.STATE_SUPPORTED);
        claim.setD24Action(hasD24 ? EducationReportConstants.D24_CONFIRMS : null);
        claim.setConfidence(hasD24 ? EducationReportConstants.CONF_HIGH : EducationReportConstants.CONF_MEDIUM);
        if (!hasD24) {
            claim.getResolutionCodes().add("NO_D24_CONFIRMATION");
        }
        return claim;
    }

    // ============================================================
    // C07 方法环境（§7.2）
    // ============================================================

    private EducationClaim deriveC07(Map<String, EducationClaim> claims) {
        EducationClaim claim = new EducationClaim();
        claim.setClaimId("C07");
        claim.setClaimKey("METHOD_ENVIRONMENT_FIT");
        claim.setClaimType("Derived");
        claim.setRenderPermission("DERIVED_ONLY");
        claim.setSectionOwner("METHOD_ENVIRONMENT");

        boolean hasCognitive = false;
        String cognitiveSource = null;
        for (String id : Arrays.asList("C02", "C03", "C04")) {
            EducationClaim c = claims.get(id);
            if (c != null && isRenderable(c)) {
                hasCognitive = true;
                cognitiveSource = c.getPrimaryCandidate();
                break;
            }
        }
        boolean hasMotivation = false;
        String motivationSource = null;
        for (String id : Arrays.asList("C05", "C06")) {
            EducationClaim c = claims.get(id);
            if (c != null && isRenderable(c)) {
                hasMotivation = true;
                motivationSource = c.getPrimaryCandidate();
                break;
            }
        }

        if (!hasCognitive || !hasMotivation) {
            return insufficient(claim, "DERIVATION_GATE_NOT_MET");
        }

        // 方法环境由认知风格 + 动机结构合成
        String candidate = synthesizeMethod(cognitiveSource, motivationSource);
        claim.setPrimaryCandidate(candidate);
        claim.setState(EducationReportConstants.STATE_SUPPORTED);
        claim.setConfidence(EducationReportConstants.CONF_MEDIUM);
        claim.getNotes().add("derived from " + cognitiveSource + " + " + motivationSource);
        return claim;
    }

    private String synthesizeMethod(String cognitive, String motivation) {
        if (cognitive == null || motivation == null) return "STRUCTURED_CURRICULUM";
        if ("PATTERN_SYNTHESIS".equals(cognitive) || "DEEP_DIVE_SEQUENTIAL".equals(cognitive)) {
            return "IMMERSIVE_INTENSIVE";
        }
        if ("DIALOGUE_ITERATIVE".equals(cognitive)) return "COLLABORATIVE_DIALOGUE";
        if ("EXPERIMENTAL_APPLIED".equals(cognitive)) return "PROJECT_BASED";
        if ("CURIOSITY_MASTERY".equals(motivation) || "AUTONOMY_EXPLORATION".equals(motivation)) {
            return "FLEXIBLE_MODULAR";
        }
        if ("RESPONSIBILITY_SECURITY".equals(motivation)) return "GUIDED_MENTOR";
        if ("COMMUNITY_COLEARNING".equals(motivation)) return "COLLABORATIVE_DIALOGUE";
        if ("CONTEXT_ASSOCIATION".equals(cognitive) || "STATE_SENSITIVE".equals(cognitive)) {
            return "QUIET_INDEPENDENT";
        }
        return "STRUCTURED_CURRICULUM";
    }

    // ============================================================
    // C11 学习路线（§7.5）
    // ============================================================

    private EducationClaim resolveC11(List<EducationEvidenceObject> evidenceList,
                                       Map<String, EducationClaim> claims) {
        EducationClaim claim = new EducationClaim();
        claim.setClaimId("C11");
        claim.setClaimKey("LEARNING_ROUTE_ORIENTATION");
        claim.setClaimType("Composite");
        claim.setRenderPermission("DIRECT");
        claim.setSectionOwner("LEARNING_ROUTES");

        List<String> allowedFamilies = EducationTagRegistry.getClaimFamilies("C11");
        Map<String, CandidateBucket> candidates = new LinkedHashMap<>();
        for (EducationEvidenceObject ev : evidenceList) {
            if (!"VOTE_BEARING".equals(ev.getNatalVoteEligibility())) continue;
            if (!"ELIGIBLE".equals(ev.getEvidenceStatus())) continue;
            if (!allowedFamilies.contains(ev.getFamily())) continue;
            String candidate = matchCandidate("C11", ev);
            if (candidate == null) continue;
            CandidateBucket bucket = candidates.computeIfAbsent(candidate, k -> new CandidateBucket());
            bucket.evidenceIds.add(ev.getEvidenceId());
            bucket.families.add(ev.getFamily());
            bucket.roots.add(ev.getRootKey());
            bucket.clusters.add(ev.getIndependenceClusterKey());
            bucket.support.add(ev.getEvidenceId());
        }

        List<String> sorted = new ArrayList<>(candidates.keySet());
        sorted.sort((a, b) -> candidates.get(b).score() - candidates.get(a).score());

        // 门槛：每条路线需要至少两个已成立的基础/复合 Claim（§7.5）
        long establishedClaims = claims.values().stream()
                .filter(c -> c.getClaimId().matches("C0[2-9]|C10") && isRenderable(c)).count();
        if (establishedClaims < 2 || sorted.isEmpty()) {
            return insufficient(claim, "ESTABLISHED_CLAIMS_LT_2");
        }

        // 主路线 1 个 + 次路线最多 2 个（§5 第 6 章）
        claim.setPrimaryCandidate(sorted.get(0));
        for (int i = 1; i < sorted.size() && claim.getQualifierCandidates().size() < EducationReportConstants.MAX_SECONDARY_ROUTES; i++) {
            claim.getQualifierCandidates().add(sorted.get(i));
        }
        claim.setSupportRootIds(new ArrayList<>(candidates.get(sorted.get(0)).support));
        claim.setSupportFamilies(new ArrayList<>(candidates.get(sorted.get(0)).families));

        boolean hasD24 = evidenceList.stream().anyMatch(e -> "D24".equals(e.getChartSource()));
        claim.setState(EducationReportConstants.STATE_SUPPORTED);
        claim.setConfidence(hasD24 ? EducationReportConstants.CONF_MEDIUM : EducationReportConstants.CONF_LOW);
        return claim;
    }

    // ============================================================
    // C12 深造路径（§7.6）
    // ============================================================

    private EducationClaim deriveC12(Map<String, EducationClaim> claims) {
        EducationClaim claim = new EducationClaim();
        claim.setClaimId("C12");
        claim.setClaimKey("ADVANCED_STUDY_PATH");
        claim.setClaimType("Derived");
        claim.setRenderPermission("DERIVED_ONLY");
        claim.setSectionOwner("LEARNING_ROUTES");

        EducationClaim c06 = claims.get("C06");
        EducationClaim c09 = claims.get("C09");
        EducationClaim c10 = claims.get("C10");
        EducationClaim c11 = claims.get("C11");

        List<String> usable = new ArrayList<>();
        if (c06 != null && isRenderable(c06)) usable.add("C06");
        if (c09 != null && isRenderable(c09)) usable.add("C09");
        if (c10 != null && isRenderable(c10)) usable.add("C10");
        if (c11 != null && isRenderable(c11)) usable.add("C11");

        if (usable.size() < 2) {
            return insufficient(claim, "DERIVATION_GATE_NOT_MET");
        }

        // 由路线 → 深造方式（C12_BRIDGE）
        String candidate;
        String c11Candidate = c11 != null && isRenderable(c11) ? c11.getPrimaryCandidate() : null;
        if (c11Candidate != null && EducationTagRegistry.C12_BRIDGE.containsKey(c11Candidate)) {
            candidate = EducationTagRegistry.C12_BRIDGE.get(c11Candidate);
        } else if (c10 != null && isRenderable(c10)) {
            candidate = "STAGED_FOUNDATION_BEFORE_ADVANCEMENT";
        } else {
            candidate = "MODULAR_LIFELONG_LEARNING";
        }

        claim.setPrimaryCandidate(candidate);
        claim.setState(EducationReportConstants.STATE_SUPPORTED);
        claim.setConfidence(EducationReportConstants.CONF_MEDIUM);
        claim.setInsufficiencyReason(null);
        claim.getNotes().add("derived from " + String.join(",", usable));
        // 成立条件与反条件必须同时给出（§7.6）——由 Renderer 内容层表达
        claim.getNotes().add("must_render_conditions_and_counterconditions");
        return claim;
    }

    // ============================================================
    // C01 总览（§7.7）
    // ============================================================

    private EducationClaim deriveC01(Map<String, EducationClaim> claims) {
        EducationClaim claim = new EducationClaim();
        claim.setClaimId("C01");
        claim.setClaimKey("CORE_LEARNING_PROFILE");
        claim.setClaimType("Derived");
        claim.setRenderPermission("DERIVED_ONLY");
        claim.setSectionOwner("SUMMARY");

        List<String> renderable = new ArrayList<>();
        boolean hasCognitive = false;
        boolean hasMotivational = false;
        for (Map.Entry<String, EducationClaim> entry : claims.entrySet()) {
            if (!entry.getKey().matches("C0[2-9]")) continue;
            if (isRenderable(entry.getValue())) {
                renderable.add(entry.getKey());
                if (entry.getKey().matches("C0[2-4]")) hasCognitive = true;
                else hasMotivational = true;
            }
        }

        // 门槛：至少 4 个可渲染 Claim，C02-C04 与 C05-C09 两组各至少一个
        if (renderable.size() < 4 || !hasCognitive || !hasMotivational) {
            return insufficient(claim, "RENDERABLE_CLAIMS_LT_4_OR_GROUP_MISSING");
        }

        claim.setPrimaryCandidate("PROFILE_AVAILABLE");
        claim.setState(EducationReportConstants.STATE_SUPPORTED);
        claim.setConfidence(EducationReportConstants.CONF_MEDIUM);
        claim.getNotes().add("renderable: " + String.join(",", renderable));
        return claim;
    }

    // ============================================================
    // 辅助
    // ============================================================

    private EducationClaim insufficient(EducationClaim claim, String reason) {
        claim.setState(EducationReportConstants.STATE_INSUFFICIENT);
        claim.setConfidence(EducationReportConstants.CONF_LOW);
        claim.setInsufficiencyReason(reason);
        return claim;
    }

    private boolean isRenderable(EducationClaim c) {
        return c != null && (EducationReportConstants.STATE_SUPPORTED.equals(c.getState())
                || EducationReportConstants.STATE_MIXED.equals(c.getState()));
    }

    private String matchCandidate(String claimId, EducationEvidenceObject ev) {
        Map<String, String> bridge = EducationTagRegistry.getBridge(claimId);
        for (String tag : ev.getSemanticTags()) {
            String candidate = bridge.get(tag);
            if (candidate != null) return candidate;
        }
        return null;
    }

    private String claimKeyOf(String claimId) {
        switch (claimId) {
            case "C02": return "COGNITIVE_PROCESSING_STYLE";
            case "C03": return "MEMORY_FOCUS_REGULATION";
            case "C04": return "KNOWLEDGE_EXPRESSION_STYLE";
            case "C05": return "LEARNING_MOTIVATION";
            case "C06": return "DISCIPLINE_AND_COMPLETION";
            case "C08": return "ACADEMIC_STRENGTH_RESOURCE";
            default: return claimId;
        }
    }

    private String sectionOwnerOf(String claimId) {
        switch (claimId) {
            case "C02": case "C03": case "C04": return "LEARNING_MECHANISM";
            case "C05": case "C06": return "FRICTION_LOOPS";
            case "C08": return "STRENGTHS";
            default: return claimId;
        }
    }

    /** 候选统计桶 */
    private static class CandidateBucket {
        final List<String> evidenceIds = new ArrayList<>();
        final Set<String> families = new LinkedHashSet<>();
        final Set<String> clusters = new LinkedHashSet<>();
        final Set<String> roots = new LinkedHashSet<>();
        final List<String> support = new ArrayList<>();
        final List<String> challenge = new ArrayList<>();

        int score() {
            return families.size() * 10 + support.size() * 2 - challenge.size();
        }
    }
}
