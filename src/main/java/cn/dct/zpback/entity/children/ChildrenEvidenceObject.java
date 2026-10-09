package cn.dct.zpback.entity.children;

import lombok.Data;
import java.io.Serializable;
import java.util.ArrayList;
import java.util.List;

/**
 * 子女报告 - 标准化 Evidence 对象
 * 符合 SEALED 文档《01_子女与养育深度报告_最终开发总说明》第 6 节
 */
@Data
public class ChildrenEvidenceObject implements Serializable {
    private static final long serialVersionUID = 1L;

    private String evidenceId;
    private String chartSource;            // D1 / D7 / D9 / JAIMINI_ROLE / YOGA / SHADBALA / DASHA
    private String evidenceKind;           // PLACEMENT / LORD_RELATION / HOUSE_OCCUPANCY / DIRECT_RELATION / ...
    private String subject;
    private String target;
    private String rawFact;
    private String normalizedFeature;
    private List<String> semanticTags = new ArrayList<>();
    private String authorityRole;          // PRIMARY / SPECIALIST / CORROBORATIVE / VALIDATION_ONLY / QUALIFIER / MODIFIER
    private String anchorKey;
    private String rootKey;
    private String independenceClusterKey;
    private String semanticCorrelationKey;
    private String dependencyStatus;       // INDEPENDENT / DEPENDENT / ALIAS / COMPOSITE_REPLACEMENT
    private List<String> parentEvidenceIds = new ArrayList<>();
    private List<String> roleAliases = new ArrayList<>();
    private String evidenceStatus;         // ELIGIBLE / SUPPRESSED / REPLACED_BY_COMPOSITE / AMBIGUOUS_SUPPRESSED
    private String evidenceLane;           // NATAL_PRIMARY / CHILDREN_SPECIALIST / MATURITY_VALIDATION / ...
    private String natalVoteEligibility;   // ELIGIBLE / INELIGIBLE_VALIDATION / INELIGIBLE_QUALIFIER / INELIGIBLE_MODIFIER
    private List<String> claimEligibility = new ArrayList<>();  // C02..C09
    private String polarity;               // SUPPORT / CONTRADICT / CONTEXT_ONLY
    private String functionalCondition;    // SUPPORTIVE / CONSTRAINING / AMBIVALENT / CONTEXT_ONLY
    private String strengthModifier;       // Shadbala: STRONG / ABOVE_REQUIRED / NEAR_REQUIRED / BELOW_REQUIRED / NOT_APPLICABLE
    private List<String> notes = new ArrayList<>();
}
