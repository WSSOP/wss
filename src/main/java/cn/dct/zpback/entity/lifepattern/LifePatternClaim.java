package cn.dct.zpback.entity.lifepattern;

import lombok.Data;
import java.io.Serializable;
import java.util.List;

/**
 * 人生课题报告 - Claim 结果
 */
@Data
public class LifePatternClaim implements Serializable {
    private static final long serialVersionUID = 1L;

    /** Claim ID (C01-C13) */
    private String claimId;

    /** Claim 名称 */
    private String claimName;

    /** Claim 状态: SUPPORTED / CONDITIONAL / MIXED / CONTRADICTED / INSUFFICIENT */
    private String state;

    /** 置信度: HIGH / MEDIUM / LOW / NONE */
    private String confidence;

    /** 候选方向 */
    private String candidateDirection;

    /** 候选描述 */
    private String candidateDescription;

    /** 支持证据 IDs */
    private List<String> supportEvidenceIds;

    /** 反向证据 IDs */
    private List<String> contradictEvidenceIds;

    /** 派生来源 */
    private String derivedFrom;

    /** 渲染权限: DIRECT / CONDITIONAL / DERIVED_ONLY */
    private String renderPermission;

    /** 省略原因 */
    private String omissionReason;

    /** 章节归属 */
    private String sectionOwner;

    /** 主题（C01使用） */
    private List<String> themes;
}
