package cn.dct.zpback.entity.career;

import lombok.Data;
import java.util.List;

/**
 * 事业报告建议
 */
@Data
public class CareerRecommendation {

    private String recommendationId;

    /** 报告实例ID */
    private String reportInstanceId;

    /** Section ID */
    private String sectionId;

    /** 建议类型: BUILD / ADJUST / PREPARE */
    private String recommendationType;

    /** 建议模式: DIRECT / CONDITIONAL / VALIDATION_ONLY / PROHIBITED */
    private String recommendationMode;

    /** 行动风险级别: LOW / MEDIUM / HIGH */
    private String actionRiskLevel;

    /** 具体性级别: GENERAL / CONTEXTUAL / SPECIFIC */
    private String specificityLevel;

    /** 建议陈述 */
    private String statement;

    /** 理由 */
    private String rationale;

    /** 相关Claim IDs */
    private List<String> relatedClaimIds;

    /** 相关Timing IDs */
    private List<String> relatedTimingIds;

    /** 所需条件 */
    private List<String> requiredConditions;

    /** 反对条件 */
    private List<String> counterConditions;

    /** 所需用户上下文字段 */
    private List<String> requiredUserContextFields;

    /** 优先级: HIGH / MEDIUM / LOW */
    private String priority;

    /** 是否用户可见 */
    private Boolean isUserVisible = true;

    /** 禁止行动IDs */
    private List<String> prohibitedActionIds;
}
