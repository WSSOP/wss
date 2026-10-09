package cn.dct.zpback.entity.health;

import lombok.Data;
import java.io.Serializable;
import java.time.LocalDateTime;
import java.util.List;

/**
 * 健康深度报告实例
 * 对应前端 FinalReportPayload 格式
 */
@Data
public class HealthReportInstance implements Serializable {

    private static final long serialVersionUID = 1L;

    /** 报告实例ID */
    private String reportInstanceId;

    /** 报告类型 */
    private String reportType;

    /** 星盘档案ID */
    private String chartProfileId;

    /** 报告状态: GENERATING, COMPLETED, FAILED */
    private String reportStatus;

    /** 覆盖状态: FULL, PARTIAL, INSUFFICIENT */
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

    /** 章节列表 */
    private List<HealthSection> sections;

    /** 推荐列表 */
    private List<HealthRecommendation> recommendations;

    /** Claim摘要 */
    private HealthClaimsSummary claimsSummary;
}
