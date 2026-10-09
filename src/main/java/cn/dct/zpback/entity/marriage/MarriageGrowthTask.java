package cn.dct.zpback.entity.marriage;

import lombok.Data;
import java.io.Serializable;
import java.util.List;

/**
 * 成长任务 (S7)
 */
@Data
public class MarriageGrowthTask implements Serializable {

    private static final long serialVersionUID = 1L;

    /** 任务ID */
    private String taskId;

    /** 任务类型 */
    private String taskType;

    /** 优先级 (1-3) */
    private Integer priority;

    /** 核心适应需求 */
    private String coreAdaptationNeed;

    /** 行为机制 */
    private String mechanism;

    /** 目标状态 */
    private String goalState;

    /** 行为任务 */
    private String behavioralTask;

    /** 具体行动 */
    private String concreteAction;

    /** 行动颗粒度: MICRO, SITUATIONAL, STRUCTURAL */
    private String actionGranularity;

    /** 触发场景 */
    private String triggerContext;

    /** 可用资源 */
    private String availableResource;

    /** Why (为什么) */
    private String whyDescription;

    /** Action (行动) */
    private String actionDescription;

    /** Resource (资源) */
    private String resourceDescription;
}
