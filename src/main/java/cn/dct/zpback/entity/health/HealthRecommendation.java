package cn.dct.zpback.entity.health;

import lombok.Data;
import java.io.Serializable;
import java.util.List;

/**
 * 推荐
 */
@Data
public class HealthRecommendation implements Serializable {

    private static final long serialVersionUID = 1L;

    /** 推荐ID */
    private String recommendationId;

    /** 推荐类型: REGULAR_SLEEP_WINDOW, WORK_REST_BOUNDARY, GENTLE_SUSTAINABLE_MOVEMENT, etc. */
    private String recommendationType;

    /** 优先级 */
    private Integer priority;

    /** 触发上下文 */
    private String triggerContext;

    /** 观察信号 */
    private String signalToNotice;

    /** 低风险行动 */
    private String lowRiskAction;

    /** 恢复边界 */
    private String recoveryBoundary;

    /** 可验证检查 */
    private String observableCheck;

    /** 专业帮助边界 */
    private String professionalHelpBoundary;

    /** 来源Claim ID列表 */
    private List<String> sourceClaimIds;

    /** 推荐标题 */
    private String title;
}
