package cn.dct.zpback.entity.career;

import lombok.Data;
import java.util.List;

/**
 * 事业报告Yoga配置
 */
@Data
public class CareerYoga {

    /** Yoga ID */
    private String yogaId;

    /** Yoga家族 */
    private String yogaFamily;

    /** 规则集版本 */
    private String rulesetVersion;

    /** 允许的星盘范围 */
    private List<String> allowedChartScope;

    /** 符合条件的行星 */
    private List<String> eligiblePlanets;

    /** 符合条件的宫位主星 */
    private List<String> eligibleHouseLords;

    /** 形成规则 */
    private List<String> formationRules;

    /** 形成子类型 */
    private List<String> formationSubtypes;

    /** 必需条件 */
    private List<String> requiredConditions;

    /** 削弱条件 */
    private List<String> weakeningConditions;

    /** 无效条件 */
    private List<String> invalidatingConditions;

    /** 参与者 */
    private List<String> participants;

    /** 核心事实IDs */
    private List<String> coreFactIds;

    /** 形成状态: NOT_FORMED / FORMED */
    private String formationStatus;

    /** 兑现质量: STRONG / FUNCTIONAL / QUALIFIED / WEAK */
    private String deliveryQuality;

    /** 事业相关性门槛 */
    private String careerRelevanceGate;

    /** 事业使用状态: NOT_CAREER_RELEVANT / CAREER_RELEVANT / PROFESSIONALLY_CONFIRMED */
    private String careerUsageStatus;

    /** Claim支持标签 */
    private List<String> claimSupportTags;

    /** 独立证据 */
    private Boolean standaloneEvidence = false;

    /** 对Claim独立 */
    private Boolean independentForClaim = true;

    /** D10确认策略 */
    private String d10ConfirmationPolicy;

    /** Dasha激活行星 */
    private List<String> dashaActivationPlanets;

    /** Yoga动作: AMPLIFY / CONFIRM / QUALIFY / RECOVERY_MODIFY */
    private String yogaAction;

    /** 父Yoga ID */
    private String parentYogaId;

    /** 去重组ID */
    private String dedupGroupId;
}
