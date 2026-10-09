package cn.dct.zpback.entity.relationship;

import lombok.Data;
import java.io.Serializable;
import java.util.List;

/**
 * Canonical Claim - 规范声明
 * Claim Engine 的输出
 */
@Data
public class CanonicalClaim implements Serializable {

    private static final long serialVersionUID = 1L;

    /** Claim ID */
    private String claimId;

    /** 规范名称 */
    private String canonicalName;

    /** Claim 类别 */
    private ClaimClass claimClass;

    /** 置信度 */
    private ClaimConfidence confidence;

    /** 敏感度 */
    private ClaimSensitivity sensitivity;

    /** 主体 */
    private String subject;

    /** 关系阶段 */
    private String relationshipStage;

    /** 主要章节归属 */
    private String primaryChapterOwner;

    /** 摘要 */
    private String summary;

    /** 支持证据列表 */
    private List<Evidence> supportingEvidence;

    /** D1×D9 关系 */
    private D1D9Relation d1d9Relation;

    /** 保留核心（MATURED_ADAPTED 时） */
    private Boolean basePreserved;

    /** 成熟表达 */
    private String matureExpression;

    /** 适应要求 */
    private String adaptationRequirement;

    /** 压力变化 */
    private String pressureChange;

    /** 保护变化 */
    private String protectiveChange;

    /** 婚姻相关性 */
    private String marriageRelevance;
}
