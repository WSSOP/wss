package cn.dct.zpback.entity.career;

import lombok.Data;
import java.util.List;

/**
 * 事业报告角色集群
 */
@Data
public class CareerRoleCluster {

    /** 角色集群ID */
    private String roleClusterId;

    /** 显示名称 */
    private String displayName;

    /** 核心标签 */
    private List<String> coreTags;

    /** 支持标签 */
    private List<String> supportingTags;

    /** 兼容的Claim类型 */
    private List<String> compatibleClaimTypes;

    /** 最低Claim强度 */
    private String minimumClaimStrength;

    /** 是否需要管理解析器 */
    private Boolean requiresManagementResolver = false;

    /** 是否需要创业解析器 */
    private Boolean requiresEntrepreneurshipResolver = false;

    /** 排名: PRIMARY / SECONDARY / OPTIONAL / SUPPRESSED */
    private String rank;

    /** 职业示例 */
    private List<CareerExample> examples;
}
