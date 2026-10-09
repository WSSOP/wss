package cn.dct.zpback.entity.career;

import lombok.Data;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

/**
 * 事业深度报告实例
 */
@Data
public class CareerReportInstance {

    private String reportInstanceId;

    /** 报告类型: CAREER */
    private String reportType = "CAREER";

    /** 报告状态: GENERATING / COMPLETED / FAILED */
    private String reportStatus;

    /** 内容版本 */
    private String reportContentVersion;

    /** Schema版本 */
    private String reportSchemaVersion = "CAREER_V1";

    /** 生成时间 */
    private LocalDateTime generatedAt;

    /** 用户ID */
    private String userId;

    /** 主星盘ID */
    private String primaryChartId;

    /** 星盘快照ID */
    private String chartSnapshotId;

    /** Dasha快照ID */
    private String dashaSnapshotId;

    /** 用户上下文快照ID */
    private String userContextSnapshotId;

    /** 来源技能 */
    private String sourceSkill = "CAREER_REPORT_SKILL";

    /** 技能版本 */
    private String sourceSkillVersion = "V1";

    /** 分析引擎版本 */
    private String analysisEngineVersion = "V1";

    /** 渲染器版本 */
    private String rendererVersion = "V1";

    /** 报告标题 */
    private String title;

    /** 副标题 */
    private String subtitle;

    /** 主体显示名 */
    private String subjectDisplayName;

    /** 封面摘要 */
    private String coverSummary;

    /** 核心Claim IDs */
    private List<String> coreClaimIds;

    /** Section IDs */
    private List<String> sectionIds;

    /** Timing IDs */
    private List<String> timingIds;

    /** Recommendation IDs */
    private List<String> recommendationIds;

    /** PDF导出状态 */
    private String pdfExportStatus;

    /** PDF文件ID */
    private String pdfFileId;

    /** PDF生成时间 */
    private LocalDateTime pdfGeneratedAt;

    /** 派生自报告ID */
    private String derivedFromReportInstanceId;

    /** Sections */
    private List<CareerSection> sections;

    /** Core Findings */
    private List<CareerClaim> coreFindings;

    /** Timing Windows */
    private List<CareerTiming> timingWindows;

    /** Recommendations */
    private List<CareerRecommendation> recommendations;

    /** 报告内容（JSON格式，包含完整报告数据） */
    private Map<String, Object> reportContent;

    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
}
