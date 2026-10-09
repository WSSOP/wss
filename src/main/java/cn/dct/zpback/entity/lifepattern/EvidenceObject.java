package cn.dct.zpback.entity.lifepattern;

import lombok.Data;
import java.io.Serializable;
import java.util.List;

/**
 * 人生课题报告 - 标准化 Evidence 对象
 * 符合 SEALED 文档 01 第 6.1 节规范
 */
@Data
public class EvidenceObject implements Serializable {
    private static final long serialVersionUID = 1L;

    private String evidenceId;
    private String chartSource;          // D1 / D9 / JAIMINI_ROLE / SHADBALA / DASHA / DERIVED
    private String evidenceKind;         // PLANET_PLACEMENT / HOUSE_LORD_PLACEMENT / ...
    private String subject;
    private String target;
    private String rawFact;
    private String normalizedFeature;
    private List<String> semanticTags;
    private String authorityRole;
    private String anchorKey;
    private String independenceClusterKey;
    private String semanticCorrelationKey;
    private String dependencyStatus;     // INDEPENDENT / DERIVED
    private List<String> parentEvidenceIds;
    private String evidenceStatus;       // ACTIVE / SUPPRESSED
    private String evidenceLane;         // NATAL_CLAIM / DEVELOPMENTAL_SUPPORT / MATURITY_VALIDATION / EXPRESSION_MODIFIER / TIMING_ACTIVATION
    private String natalVoteEligibility; // P / S / I / X
    private String claimEligibility;     // P / S / I / V / M / X
    private String polarity;             // SUPPORT / CONTRADICT / NEUTRAL / INSUFFICIENT
    private String strengthModifier;     // STRONG / MODERATE / CONDITIONAL / UNKNOWN
    private String notes;
    private String semanticFamily;       // A_SELF_IDENTITY / B_EMOTIONAL_SECURITY / ...
}
