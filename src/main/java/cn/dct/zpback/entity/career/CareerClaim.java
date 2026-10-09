package cn.dct.zpback.entity.career;

import lombok.Data;
import java.util.List;

/**
 * 事业报告Claim（核心结论）
 */
@Data
public class CareerClaim {

    private String claimId;

    /** 报告实例ID */
    private String reportInstanceId;

    /** Section ID */
    private String sectionId;

    /** Claim类型 */
    private String claimType;

    /** Claim范围 */
    private String claimScope;

    /** 结论陈述 */
    private String statement;

    /** Claim强度: STRONG / MODERATE / CONDITIONAL / SUPPRESSED */
    private String claimStrength;

    /** 重要性 */
    private String importance;

    /** 支持证据IDs */
    private List<String> supportingEvidenceIds;

    /** 反对证据IDs */
    private List<String> counterEvidenceIds;

    /** 结构性证据家族 */
    private List<String> structuralFamilies;

    /** 修饰性证据家族 */
    private List<String> modifierFamilies;

    /** 跨系统状态 */
    private String crossSystemState;

    /** 显现能力 */
    private String manifestationCapacity;

    /** 摩擦级别 */
    private String frictionLevel;

    /** 条件 */
    private List<String> conditions;

    /** 细化标签 */
    private List<String> refinementTags;

    /** 相关Claim IDs */
    private List<String> relatedClaimIds;

    /** 相关Yoga IDs */
    private List<String> relatedYogaIds;

    /** 相关Timing IDs */
    private List<String> relatedTimingIds;

    /** 是否核心发现 */
    private Boolean isCore = false;

    /** 是否用户可见 */
    private Boolean isUserVisible = true;

    /** 抑制原因 */
    private String suppressionReason;

    /** 解析器备注 */
    private String resolverNotes;
}
