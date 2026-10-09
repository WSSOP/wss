package cn.dct.zpback.entity;

import lombok.Data;

import java.io.Serializable;
import java.time.LocalDateTime;

/**
 * 报告实例实体
 * 记录用户购买并生成的每一份深度报告
 */
@Data
public class ReportInstance implements Serializable {

    private static final long serialVersionUID = 1L;

    /** 报告实例ID (如 rp_career_001) */
    private String reportInstanceId;

    /** 用户ID */
    private String userId;

    /** 报告类型 (CAREER/LIFEPATTERN_GROWTH/NATAL_RELATIONSHIP/...) */
    private String reportType;

    /** 前台报告名称 */
    private String reportTitle;

    /** 报告对象标签 (如 "本人" 或 "张三 vs 李四") */
    private String subjectLabel;

    /** 报告版本 (如 1.0.0) */
    private String reportVersion;

    /** 来源 Skill ID */
    private String sourceSkill;

    /** Skill 版本 */
    private String skillVersion;

    /** Schema 版本 */
    private String schemaVersion;

    /** Renderer 版本 */
    private String rendererVersion;

    /** 数据快照ID */
    private String dataSnapshotId;

    /** 关联星盘ID */
    private String chartId;

    /** 第二人星盘ID（双人报告） */
    private String secondaryChartId;

    /** 报告生成时间 */
    private LocalDateTime generatedAt;

    /** 报告状态：GENERATING/COMPLETED/FAILED */
    private String status;

    /** 是否允许追问 */
    private Boolean followupAvailable;

    /** 追问配置（JSON，含sections/claims/evidence等） */
    private String followupConfig;

    /** FinalReportPayload（JSON） */
    private String finalPayload;
}
