package cn.dct.zpback.entity.lifepattern;

import lombok.Data;
import java.io.Serializable;

/**
 * 人生课题报告 - 建议 Payload
 */
@Data
public class LifePatternRecommendationPayload implements Serializable {
    private static final long serialVersionUID = 1L;

    /** 建议类型 */
    private String recommendationType;

    /** 标题 */
    private String title;

    /** 低风险行动 */
    private String lowRiskAction;

    /** 可观察检查 */
    private String observableCheck;

    /** 专业帮助边界 */
    private String professionalHelpBoundary;

    /** 触发情境 */
    private String triggerContext;

    /** 信号识别 */
    private String signalRecognition;

    /** 暂停动作 */
    private String pauseAction;

    /** 替代行为 */
    private String alternativeBehavior;
}
