package cn.dct.zpback.entity.marriage;

import lombok.Data;
import java.io.Serializable;
import java.util.List;

/**
 * 婚恋 Claim 解析结果
 */
@Data
public class MarriageClaim implements Serializable {

    private static final long serialVersionUID = 1L;

    /** Claim ID */
    private String claimId;

    /** Canonical Claim 名称 */
    private String canonicalName;

    /** Claim 版本 */
    private String claimRegistryVersion;

    /** Claim 类别: CORE, COMPOSITE, FACTOR, TIMING, DERIVED */
    private String claimClass;

    /** 主体: USER, PARTNER, RELATIONSHIP, MARRIAGE, FUTURE_EVENT */
    private String subject;

    /** 关系阶段: EARLY_ROMANCE, LONG_TERM_RELATIONSHIP, MARRIAGE, FUTURE_PARTNER, TIMING, CROSS_STAGE */
    private String relationshipStage;

    /** 语义角色: NEED, TRAIT, STYLE, ROLE, MECHANISM, DYNAMIC, RESOURCE, PRESSURE, ALIGNMENT, EVENT, GROWTH, ACTION */
    private String semanticRole;

    /** 语义主题 */
    private String semanticTheme;

    /** 因果机制 */
    private String causalMechanism;

    /** 置信度: HIGH, MEDIUM, LOW, INSUFFICIENT */
    private String confidence;

    /** 敏感度: STANDARD, HIGH, VERY_HIGH */
    private String sensitivity;

    /** 级别: MAJOR, SUPPORTING, REFINEMENT */
    private String claimLevel;

    /** 主要章节所有者 */
    private String primaryChapterOwner;

    /** 允许的次要章节 */
    private List<String> allowedSecondaryChapters;

    /** 渲染权限: DIRECT, CONDITIONAL, DERIVED_ONLY */
    private String rendererPermission;

    /** 证据引用 */
    private List<String> evidenceRefs;

    /** 支持 Claim 引用 */
    private List<String> supportingClaimRefs;

    /** 矛盾 Claim 引用 */
    private List<String> contradictingClaimRefs;

    /** Claim 摘要 */
    private String summary;

    /** 主要候选 ID */
    private String primaryCandidateId;

    /** 支持根 ID 列表 */
    private List<String> supportRootIds;

    /** 矛盾根 ID 列表 */
    private List<String> contradictRootIds;

    /** 缓解根 ID 列表 */
    private List<String> mitigationRootIds;
}
