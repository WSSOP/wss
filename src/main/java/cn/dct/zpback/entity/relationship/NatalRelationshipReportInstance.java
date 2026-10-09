package cn.dct.zpback.entity.relationship;

import lombok.Data;
import java.io.Serializable;
import java.time.LocalDateTime;
import java.util.List;

/**
 * 单人婚恋深度报告实例
 * 报告类型：NATAL_RELATIONSHIP_REPORT
 */
@Data
public class NatalRelationshipReportInstance implements Serializable {

    private static final long serialVersionUID = 1L;

    /** 报告实例ID */
    private String reportInstanceId;

    /** 报告类型：NATAL_RELATIONSHIP_REPORT */
    private String reportType;

    /** 星盘档案ID */
    private String chartProfileId;

    /** 报告状态：GENERATING, COMPLETED, FAILED */
    private String reportStatus;

    /** 失败原因（reportStatus=FAILED 时给出可展示的说明） */
    private String failureReason;

    /** 覆盖状态：FULL, PARTIAL, INSUFFICIENT */
    private String coverageState;

    /** 生成时间 */
    private LocalDateTime generatedAt;

    /** 报告版本 */
    private String reportVersion;

    /** 分析引擎描述 */
    private String analysisEngine;

    /** 报告标题 */
    private String title;

    /** 报告副标题 */
    private String subtitle;

    /** 封面摘要 */
    private String coverSummary;

    /** 创建时间 */
    private LocalDateTime createdAt;

    /** 更新时间 */
    private LocalDateTime updatedAt;

    /** 章节列表（S0-S7） */
    private List<NatalRelationshipSection> sections;

    /** 推荐列表 */
    private List<NatalRelationshipRecommendation> recommendations;

    /** Claim摘要 */
    private NatalRelationshipClaimsSummary claimsSummary;

    /** 报告版本信息 */
    private String claimRegistryVersion;
    private String renderSchemaVersion;
    private String evidenceEngineVersion;
}