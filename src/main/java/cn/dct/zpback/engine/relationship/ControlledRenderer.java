package cn.dct.zpback.engine.relationship;

import cn.dct.zpback.constant.NatalRelationshipConstants;
import cn.dct.zpback.entity.relationship.*;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.*;

/**
 * Controlled Renderer - 受控渲染器 (STEP 3E)
 * 
 * 核心原则：Renderer may phrase; Renderer may not decide.
 * 
 * 渲染器只负责将 Claim 转换为最终输出格式：
 * - 根据 Claim 数据生成段落
 * - 组装章节结构
 * - 不得创建新 Claim 或修改 Claim 内容
 */
@Component
@Slf4j
public class ControlledRenderer {

    /**
     * 主入口：将 Claim Map 渲染为报告章节
     */
    public List<NatalRelationshipSection> renderSections(
            Map<String, CanonicalClaim> claims,
            List<NatalRelationshipRecommendation> recommendations) {

        List<NatalRelationshipSection> sections = new ArrayList<>();

        // S0: 婚恋核心画像
        sections.add(renderS0(claims));

        // S1: 你是怎样进入一段感情的
        sections.add(renderS1(claims));

        // S2: 你真正需要怎样的长期关系
        sections.add(renderS2(claims));

        // S3: 未来伴侣画像
        sections.add(renderS3(claims));

        // S4: 婚姻里的优势与容易卡住的地方
        sections.add(renderS4(claims));

        // S5: 长期相处与婚后关系模式
        sections.add(renderS5(claims));

        // S6: 未来3-5年的婚恋时间窗口
        sections.add(renderS6(claims));

        // S7: 最值得调整的三个关系课题
        sections.add(renderS7(claims, recommendations));

        log.info("章节渲染完成: total={}", sections.size());
        return sections;
    }

    /**
     * S0: 婚恋核心画像 - 只允许 HIGH major
     */
    private NatalRelationshipSection renderS0(Map<String, CanonicalClaim> claims) {
        NatalRelationshipSection section = createSection("S0", "婚恋核心画像", 0);

        List<NatalRelationshipParagraph> paragraphs = new ArrayList<>();

        // Thesis: 使用 LOVE_PATTERN 作为核心
        CanonicalClaim lovePattern = claims.get("LOVE_PATTERN");
        if (lovePattern != null && lovePattern.getSummary() != null) {
            paragraphs.add(createParagraph("S0_P1", "THESIS",
                    "根据D1本命盘、D9婚姻盘、7 Karaka、Shadbala、Nakshatra和Dasha大运的综合分析，" + lovePattern.getSummary(), null));
        }

        section.setParagraphs(paragraphs);
        section.setCoverageState(lovePattern != null && lovePattern.getConfidence() == ClaimConfidence.HIGH ?
                NatalRelationshipConstants.COVERAGE_FULL : NatalRelationshipConstants.COVERAGE_PARTIAL);

        return section;
    }

    /**
     * S1: 你是怎样进入一段感情的
     */
    private NatalRelationshipSection renderS1(Map<String, CanonicalClaim> claims) {
        NatalRelationshipSection section = createSection("S1", "你是怎样进入一段感情的", 1);

        List<NatalRelationshipParagraph> paragraphs = new ArrayList<>();
        List<NatalRelationshipClaim> sectionClaims = new ArrayList<>();

        CanonicalClaim lovePattern = claims.get("LOVE_PATTERN");
        if (lovePattern != null && shouldInclude(lovePattern)) {
            paragraphs.add(createParagraph("S1_P1", "THESIS", lovePattern.getSummary(), null));
            sectionClaims.add(toOutputClaim(lovePattern));
        }

        CanonicalClaim attraction = claims.get("ATTRACTION_PATTERN");
        if (attraction != null && shouldInclude(attraction)) {
            paragraphs.add(createParagraph("S1_P2", "DIRECT_ANSWER", attraction.getSummary(), null));
            sectionClaims.add(toOutputClaim(attraction));
        }

        CanonicalClaim initiation = claims.get("ROMANTIC_INITIATION_STYLE");
        if (initiation != null && shouldInclude(initiation)) {
            paragraphs.add(createParagraph("S1_P3", "SECONDARY_EXPLANATION", initiation.getSummary(), null));
            sectionClaims.add(toOutputClaim(initiation));
        }

        section.setParagraphs(paragraphs);
        section.setClaims(sectionClaims);
        section.setCoverageState(calculateCoverage(claims, "S1"));

        return section;
    }

    /**
     * S2: 你真正需要怎样的长期关系
     */
    private NatalRelationshipSection renderS2(Map<String, CanonicalClaim> claims) {
        NatalRelationshipSection section = createSection("S2", "你真正需要怎样的长期关系", 2);

        List<NatalRelationshipParagraph> paragraphs = new ArrayList<>();
        List<NatalRelationshipClaim> sectionClaims = new ArrayList<>();

        CanonicalClaim marriage = claims.get("MARRIAGE_FOUNDATION");
        if (marriage != null && shouldInclude(marriage)) {
            paragraphs.add(createParagraph("S2_P1", "THESIS", marriage.getSummary(), null));
            sectionClaims.add(toOutputClaim(marriage));
        }

        CanonicalClaim longTerm = claims.get("LONG_TERM_RELATIONSHIP_NEED");
        if (longTerm != null && shouldInclude(longTerm)) {
            paragraphs.add(createParagraph("S2_P2", "DIRECT_ANSWER", longTerm.getSummary(), null));
            sectionClaims.add(toOutputClaim(longTerm));
        }

        CanonicalClaim security = claims.get("EMOTIONAL_SECURITY_NEED");
        if (security != null && shouldInclude(security)) {
            paragraphs.add(createParagraph("S2_P3", "SECONDARY_EXPLANATION", security.getSummary(), null));
            sectionClaims.add(toOutputClaim(security));
        }

        section.setParagraphs(paragraphs);
        section.setClaims(sectionClaims);
        section.setCoverageState(calculateCoverage(claims, "S2"));

        return section;
    }

    /**
     * S3: 未来伴侣画像
     */
    private NatalRelationshipSection renderS3(Map<String, CanonicalClaim> claims) {
        NatalRelationshipSection section = createSection("S3", "未来伴侣画像", 3);

        List<NatalRelationshipParagraph> paragraphs = new ArrayList<>();
        List<NatalRelationshipClaim> sectionClaims = new ArrayList<>();

        CanonicalClaim personality = claims.get("PARTNER_CORE_PERSONALITY");
        if (personality != null && shouldInclude(personality)) {
            paragraphs.add(createParagraph("S3_P1", "THESIS", personality.getSummary(), null));
            sectionClaims.add(toOutputClaim(personality));
        }

        CanonicalClaim role = claims.get("PARTNER_RELATIONAL_ROLE");
        if (role != null && shouldInclude(role)) {
            paragraphs.add(createParagraph("S3_P2", "DIRECT_ANSWER", role.getSummary(), null));
            sectionClaims.add(toOutputClaim(role));
        }

        CanonicalClaim alignment = claims.get("PARTNER_NEED_ARCHETYPE_ALIGNMENT");
        if (alignment != null && shouldInclude(alignment)) {
            paragraphs.add(createParagraph("S3_P3", "SECONDARY_EXPLANATION", alignment.getSummary(), null));
            sectionClaims.add(toOutputClaim(alignment));
        }

        section.setParagraphs(paragraphs);
        section.setClaims(sectionClaims);
        section.setCoverageState(calculateCoverage(claims, "S3"));

        return section;
    }

    /**
     * S4: 婚姻里的优势与容易卡住的地方
     */
    private NatalRelationshipSection renderS4(Map<String, CanonicalClaim> claims) {
        NatalRelationshipSection section = createSection("S4", "婚姻里的优势与容易卡住的地方", 4);

        List<NatalRelationshipParagraph> paragraphs = new ArrayList<>();
        List<NatalRelationshipClaim> sectionClaims = new ArrayList<>();

        CanonicalClaim strength = claims.get("CORE_MARRIAGE_STRENGTH");
        if (strength != null && shouldInclude(strength)) {
            paragraphs.add(createParagraph("S4_P1", "THESIS", strength.getSummary(), null));
            sectionClaims.add(toOutputClaim(strength));
        }

        CanonicalClaim adaptation = claims.get("CORE_ADAPTATION_NEED");
        if (adaptation != null && shouldInclude(adaptation)) {
            paragraphs.add(createParagraph("S4_P2", "DIRECT_ANSWER", adaptation.getSummary(), null));
            sectionClaims.add(toOutputClaim(adaptation));
        }

        section.setParagraphs(paragraphs);
        section.setClaims(sectionClaims);
        section.setCoverageState(calculateCoverage(claims, "S4"));

        return section;
    }

    /**
     * S5: 长期相处与婚后关系模式
     */
    private NatalRelationshipSection renderS5(Map<String, CanonicalClaim> claims) {
        NatalRelationshipSection section = createSection("S5", "长期相处与婚后关系模式", 5);

        List<NatalRelationshipParagraph> paragraphs = new ArrayList<>();
        List<NatalRelationshipClaim> sectionClaims = new ArrayList<>();

        CanonicalClaim dynamic = claims.get("MARRIAGE_DYNAMIC");
        if (dynamic != null && shouldInclude(dynamic)) {
            paragraphs.add(createParagraph("S5_P1", "THESIS", dynamic.getSummary(), null));
            sectionClaims.add(toOutputClaim(dynamic));
        }

        CanonicalClaim conflict = claims.get("CONFLICT_RESPONSE_PATTERN");
        if (conflict != null && shouldInclude(conflict)) {
            paragraphs.add(createParagraph("S5_P2", "DIRECT_ANSWER", conflict.getSummary(), null));
            sectionClaims.add(toOutputClaim(conflict));
        }

        section.setParagraphs(paragraphs);
        section.setClaims(sectionClaims);
        section.setCoverageState(calculateCoverage(claims, "S5"));

        return section;
    }

    /**
     * S6: 未来3-5年的婚恋时间窗口
     */
    private NatalRelationshipSection renderS6(Map<String, CanonicalClaim> claims) {
        NatalRelationshipSection section = createSection("S6", "未来3-5年的婚恋时间窗口", 6);

        List<NatalRelationshipParagraph> paragraphs = new ArrayList<>();
        List<NatalRelationshipClaim> sectionClaims = new ArrayList<>();

        String[] windowClaims = {"MEETING_WINDOW", "RELATIONSHIP_START_WINDOW", "COMMITMENT_WINDOW",
                "MARRIAGE_WINDOW", "RELATIONSHIP_REASSESSMENT_WINDOW", "RELATIONSHIP_PRESSURE_WINDOW"};

        List<String> windows = new ArrayList<>();
        for (String claimName : windowClaims) {
            CanonicalClaim claim = claims.get(claimName);
            if (claim != null && shouldInclude(claim)) {
                windows.add(claim.getSummary());
                sectionClaims.add(toOutputClaim(claim));
            }
        }

        if (!windows.isEmpty()) {
            paragraphs.add(createParagraph("S6_P1", "THESIS", String.join(" ", windows), null));
        } else {
            // 时间窗口依赖 Dasha 大运与行运证据，证据不足时如实说明，不留空白卡片
            paragraphs.add(createParagraph("S6_P1", "THESIS",
                    "本期分析未能从大运与行运证据中确认出足够可靠的婚恋时间窗口。"
                    + "时间窗口需要较强的 Dasha 与 Transit 证据支撑，证据不足时不做提前预测。"
                    + "更实际的做法是：在确定关系、订婚、结婚这类重要节点前，"
                    + "以当下的实际相处状态作为判断依据，而不是等待某个「注定发生」的时间点。", null));
        }

        section.setParagraphs(paragraphs);
        section.setClaims(sectionClaims);
        section.setCoverageState(calculateCoverage(claims, "S6"));

        return section;
    }

    /**
     * S7: 最值得调整的三个关系课题
     */
    private NatalRelationshipSection renderS7(Map<String, CanonicalClaim> claims,
                                               List<NatalRelationshipRecommendation> recommendations) {
        NatalRelationshipSection section = createSection("S7", "最值得调整的三个关系课题", 7);

        List<NatalRelationshipParagraph> paragraphs = new ArrayList<>();
        List<NatalRelationshipClaim> sectionClaims = new ArrayList<>();

        CanonicalClaim growth = claims.get("GROWTH_TASK");
        if (growth != null && shouldInclude(growth)) {
            paragraphs.add(createParagraph("S7_P1", "THESIS", growth.getSummary(), null));
            sectionClaims.add(toOutputClaim(growth));
        }

        section.setParagraphs(paragraphs);
        section.setClaims(sectionClaims);
        section.setRecommendations(recommendations);
        section.setCoverageState(calculateCoverage(claims, "S7"));

        return section;
    }

    // ============================================================
    // 辅助方法
    // ============================================================

    private NatalRelationshipSection createSection(String id, String title, int order) {
        NatalRelationshipSection section = new NatalRelationshipSection();
        section.setSectionId(id);
        section.setTitle(title);
        section.setOrder(order);
        return section;
    }

    private NatalRelationshipParagraph createParagraph(String id, String role, String text, List<String> sourceClaimRefs) {
        NatalRelationshipParagraph p = new NatalRelationshipParagraph();
        p.setParagraphId(id);
        p.setRole(role);
        p.setText(text);
        p.setSourceClaimRefs(sourceClaimRefs != null ? sourceClaimRefs.toArray(new String[0]) : null);
        return p;
    }

    private NatalRelationshipClaim toOutputClaim(CanonicalClaim claim) {
        NatalRelationshipClaim output = new NatalRelationshipClaim();
        output.setClaimId(claim.getClaimId());
        output.setCanonicalName(claim.getCanonicalName());
        output.setClaimClass(claim.getClaimClass() != null ? claim.getClaimClass().name() : "CORE");
        output.setConfidence(claim.getConfidence() != null ? claim.getConfidence().name() : "MEDIUM");
        output.setSensitivity(claim.getSensitivity() != null ? claim.getSensitivity().name() : "STANDARD");
        output.setSubject(claim.getSubject());
        output.setRelationshipStage(claim.getRelationshipStage());
        output.setSummary(claim.getSummary());
        output.setPrimaryChapterOwner(claim.getPrimaryChapterOwner());
        return output;
    }

    /**
     * 判断 Claim 是否应该包含在用户可见正文中
     * LOW/INSUFFICIENT 不进入用户正文
     */
    private boolean shouldInclude(CanonicalClaim claim) {
        if (claim == null || claim.getConfidence() == null) return false;
        ClaimConfidence confidence = claim.getConfidence();
        return confidence == ClaimConfidence.HIGH || confidence == ClaimConfidence.MEDIUM;
    }

    private String calculateCoverage(Map<String, CanonicalClaim> claims, String sectionPrefix) {
        long total = claims.values().stream()
                .filter(c -> c.getPrimaryChapterOwner().equals(sectionPrefix))
                .count();
        long supported = claims.values().stream()
                .filter(c -> c.getPrimaryChapterOwner().equals(sectionPrefix))
                .filter(c -> c.getConfidence() == ClaimConfidence.HIGH || c.getConfidence() == ClaimConfidence.MEDIUM)
                .count();

        if (total == 0) return NatalRelationshipConstants.COVERAGE_INSUFFICIENT;
        double ratio = (double) supported / total;
        if (ratio >= 0.7) return NatalRelationshipConstants.COVERAGE_FULL;
        if (ratio >= 0.4) return NatalRelationshipConstants.COVERAGE_PARTIAL;
        return NatalRelationshipConstants.COVERAGE_INSUFFICIENT;
    }
}
