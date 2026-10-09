package cn.dct.zpback.entity.relationship;

import lombok.Data;
import java.io.Serializable;

/**
 * 推荐/成长任务
 */
@Data
public class NatalRelationshipRecommendation implements Serializable {

    private static final long serialVersionUID = 1L;

    /** 推荐ID */
    private String recommendationId;

    /** 推荐类型 */
    private String recommendationType;

    /** 优先级 */
    private Integer priority;

    /** 触发上下文 */
    private String triggerContext;

    /** 观察信号 */
    private String signalToNotice;

    /** 低风险行动 */
    private String lowRiskAction;

    /** 可验证检查 */
    private String observableCheck;

    /** 专业帮助边界 */
    private String professionalHelpBoundary;

    /** 推荐标题 */
    private String title;

    /** 成长任务代码 */
    private String taskCode;

    /** 目标状态 */
    private String goalState;

    /** 触发该建议的 Claim ID（建议必须由真实 Claim 派生，不允许凭空生成） */
    private String backingClaimId;

    /** 该建议的 Claim 依据（来自 Claim 的判定摘要） */
    private String evidenceBasis;
}