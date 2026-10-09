package cn.dct.zpback.service.relationship;

import cn.dct.zpback.entity.relationship.CanonicalClaim;
import cn.dct.zpback.entity.relationship.ClaimConfidence;
import cn.dct.zpback.entity.relationship.NatalRelationshipRecommendation;
import org.junit.jupiter.api.Test;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

/**
 * 行动建议必须由真实 Claim 派生，不允许固定输出同一套。
 */
class NatalRelationshipRecommendationTest {

    private final NatalRelationshipReportService service = new NatalRelationshipReportService();

    private CanonicalClaim claim(String id, ClaimConfidence confidence, String summary) {
        CanonicalClaim c = new CanonicalClaim();
        c.setClaimId(id);
        c.setCanonicalName(id);
        c.setConfidence(confidence);
        c.setSummary(summary);
        return c;
    }

    @Test
    void insufficientClaims_produceNoRecommendation() {
        Map<String, CanonicalClaim> claims = new HashMap<>();
        claims.put("EMOTIONAL_SECURITY_NEED", claim("EMOTIONAL_SECURITY_NEED", ClaimConfidence.INSUFFICIENT, "证据不足"));
        claims.put("REPAIR_PATTERN", claim("REPAIR_PATTERN", ClaimConfidence.INSUFFICIENT, "证据不足"));

        List<NatalRelationshipRecommendation> recs = service.generateRecommendations(claims);

        assertTrue(recs.isEmpty(), "证据不足的 Claim 不应产出任何建议");
    }

    @Test
    void recommendations_areDrivenByClaimConfidenceAndCarryBasis() {
        Map<String, CanonicalClaim> claims = new HashMap<>();
        claims.put("EMOTIONAL_SECURITY_NEED",
                claim("EMOTIONAL_SECURITY_NEED", ClaimConfidence.HIGH, "你高度需要被确认回应"));
        claims.put("BOUNDARY_PATTERN",
                claim("BOUNDARY_PATTERN", ClaimConfidence.MEDIUM, "你在关系中容易先牺牲边界"));

        List<NatalRelationshipRecommendation> recs = service.generateRecommendations(claims);

        assertEquals(2, recs.size());
        // 高置信度 Claim 排前面
        assertEquals("VALIDATE_BEFORE_SOLVING", recs.get(0).getRecommendationType());
        assertEquals("EMOTIONAL_SECURITY_NEED", recs.get(0).getBackingClaimId());
        assertEquals("你高度需要被确认回应", recs.get(0).getEvidenceBasis());
        assertEquals(1, recs.get(0).getPriority());

        assertEquals("SET_LIMIT_BEFORE_SACRIFICE", recs.get(1).getRecommendationType());
        assertEquals("BOUNDARY_PATTERN", recs.get(1).getBackingClaimId());
        assertEquals(2, recs.get(1).getPriority());
    }

    @Test
    void duplicateRecommendationTypes_areDedupedKeepingHighestConfidence() {
        Map<String, CanonicalClaim> claims = new HashMap<>();
        // 冲突与修复都映射到「先冷静再沟通」，应只保留置信度更高的那条
        claims.put("CONFLICT_RESPONSE_PATTERN",
                claim("CONFLICT_RESPONSE_PATTERN", ClaimConfidence.MEDIUM, "冲突时你倾向先冷处理"));
        claims.put("REPAIR_PATTERN",
                claim("REPAIR_PATTERN", ClaimConfidence.HIGH, "你具备主动修复的能力"));

        List<NatalRelationshipRecommendation> recs = service.generateRecommendations(claims);

        assertEquals(1, recs.size(), "同一建议类型只应出现一次");
        assertEquals("REPAIR_AFTER_COOLDOWN", recs.get(0).getRecommendationType());
        assertEquals("REPAIR_PATTERN", recs.get(0).getBackingClaimId(), "应保留置信度更高的 Claim");
        assertEquals("你具备主动修复的能力", recs.get(0).getEvidenceBasis());
    }

    @Test
    void missingClaims_doNotThrow() {
        List<NatalRelationshipRecommendation> recs = service.generateRecommendations(new HashMap<>());
        assertTrue(recs.isEmpty());
    }
}
