package cn.dct.zpback.entity.career;

import lombok.Data;
import java.util.List;

/**
 * 事业报告职业示例
 */
@Data
public class CareerExample {

    /** 示例ID */
    private String exampleId;

    /** 显示名称 */
    private String displayName;

    /** 角色集群ID */
    private String roleClusterId;

    /** 必需标签(全部) */
    private List<String> requiredTagsAll;

    /** 必需标签(任一) */
    private List<String> requiredTagsAny;

    /** 首选标签 */
    private List<String> preferredTags;

    /** 禁止标签 */
    private List<String> forbiddenTags;

    /** 兼容环境标签 */
    private List<String> compatibleEnvironmentTags;

    /** 不兼容环境标签 */
    private List<String> incompatibleEnvironmentTags;

    /** 管理需求 */
    private String managementRequirement;

    /** 创业需求 */
    private String entrepreneurshipRequirement;

    /** 受监管职业 */
    private Boolean regulatedProfession = false;

    /** 工作模式需求 */
    private String workModelRequirement;

    /** 显示优先级 */
    private Integer displayPriority;
}
