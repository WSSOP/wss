package cn.dct.zpback.entity.career;

import lombok.Data;
import java.util.List;

/**
 * 事业报告证据
 */
@Data
public class CareerEvidence {

    private String evidenceId;

    /** 报告实例ID */
    private String reportInstanceId;

    /** 占星事实ID */
    private String astrologicalFactId;

    /** 去重组ID */
    private String dedupGroupId;

    /** 证据家族: D1_STRUCTURAL / D10_PROFESSIONAL / JAIMINI_ROLE / PLANETARY_STRENGTH / NAKSHATRA_EXPRESSION / YOGA_CONFIGURATION / DASHA_ACTIVATION */
    private String evidenceFamily;

    /** 证据优先级: PRIMARY / SECONDARY / CONTEXTUAL / MODIFIER */
    private String evidencePriority;

    /** 证据类型 */
    private String evidenceType;

    /** 来源星盘: D1 / D10 / JAIMINI */
    private String sourceChart;

    /** 来源系统 */
    private String sourceSystem;

    /** 来源对象 */
    private String sourceObject;

    /** 原始配置 */
    private String rawConfiguration;

    /** 解读含义 */
    private String interpretedMeaning;

    /** Claim支持标签 */
    private List<String> claimSupportTags;

    /** 支持方向: SUPPORT / COUNTER / REFINE / QUALIFY */
    private String supportDirection;

    /** 支持强度: STRONG / MODERATE / WEAK */
    private String supportStrength;

    /** 条件标志 */
    private List<String> conditionFlags;

    /** 规则ID */
    private String ruleId;

    /** 来源技能 */
    private String sourceSkill;

    /** 是否用户可见 */
    private Boolean isUserVisible = true;

    /** 用户端摘要 */
    private String userFacingSummary;

    /** 是否对Claim独立 */
    private Boolean independentForClaim = true;
}
