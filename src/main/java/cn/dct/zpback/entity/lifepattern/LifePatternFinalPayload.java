package cn.dct.zpback.entity.lifepattern;

import lombok.Data;
import java.io.Serializable;
import java.util.List;
import java.util.Map;

/**
 * 人生课题报告 - 最终报告 Payload
 * 传给 LLM Renderer 的结构化数据
 */
@Data
public class LifePatternFinalPayload implements Serializable {
    private static final long serialVersionUID = 1L;

    /** 报告类型 */
    private String reportType = "LIFE_PATTERN_GROWTH_REPORT";

    /** 规则版本 */
    private String ruleVersion = "life_pattern_growth.v1.0.0";

    /** 13 Claims */
    private Map<String, LifePatternClaim> claims;

    /** Evidence 列表 */
    private List<EvidenceObject> evidenceList;

    /** 章节列表 */
    private List<LifePatternSectionPayload> sections;

    /** 时间窗口 */
    private List<LifePatternTimingPayload> timingWindows;

    /** 建议列表 */
    private List<LifePatternRecommendationPayload> recommendations;

    /** 封面摘要 */
    private String coverSummary;

    /** 数据降级说明 */
    private List<String> degradationNotes;
}
