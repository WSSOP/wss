package cn.dct.zpback.entity.education;

import lombok.Data;
import java.io.Serializable;
import java.util.ArrayList;
import java.util.List;

/**
 * 学业报告 - 标准化 Evidence 对象
 * 符合 SEALED 文档《01_学业深度解析报告》第 10 节四层结构：
 * Fact → Evidence → Candidate → Claim
 */
@Data
public class EducationEvidenceObject implements Serializable {
    private static final long serialVersionUID = 1L;

    private String evidenceId;
    private String chartSource;            // D1 / D24 / DASHA / NAKSHATRA / SHADBALA / YOGA
    private String evidenceKind;           // PLACEMENT / LORD_RELATION / HOUSE_OCCUPANCY / RELATION / MODIFIER
    private String subject;
    private String target;
    private String rawFact;
    private String normalizedFeature;

    /** D1 证据 Family（COGNITION / MEANING_GUIDANCE / ...） */
    private String family;
    /** 主 Family 还是条件性 Family */
    private boolean conditionalFamily;

    private List<String> semanticTags = new ArrayList<>();
    private String authorityRole;          // NATAL_PRIMARY / DOMAIN_CONFIRMATION / STYLE_MODIFIER / THEME_AMPLIFIER / EXPRESSION_MODIFIER / TIMING_ACTIVATION
    private String anchorKey;
    /** root key: {chart}:{root_type}:{root_identity}:{relationship_scope} */
    private String rootKey;
    private String rootType;               // PLANET / HOUSE / HOUSE_LORD / RELATION / YOGA
    private String independenceClusterKey;

    private String dependencyStatus;       // INDEPENDENT / DEPENDENT / MODIFIER / AMPLIFIER
    private List<String> parentEvidenceIds = new ArrayList<>();

    private String evidenceStatus;         // ELIGIBLE / SUPPRESSED / AMBIVALENT / AUDIT_ONLY
    private String evidenceLane;
    private String natalVoteEligibility;   // VOTE_BEARING / NON_VOTE_BEARING
    private List<String> claimEligibility = new ArrayList<>();  // C02..C13

    private String polarity;               // SUPPORT / CHALLENGE / CONTEXT_ONLY
    private String functionalCondition;    // SUPPORTIVE / CONSTRAINING / AMBIVALENT / CONTEXT_ONLY

    /** Shadbala 表达修饰 */
    private String strengthModifier;       // STABLY_AVAILABLE / AVAILABLE_WITH_EFFORT / INCONSISTENT_ACCESS / UNKNOWN
    /** Nakshatra 风格修饰 */
    private String styleModifier;
    /** Yoga 增强标记 */
    private List<String> yogaAmplifiers = new ArrayList<>();

    private List<String> notes = new ArrayList<>();
}
