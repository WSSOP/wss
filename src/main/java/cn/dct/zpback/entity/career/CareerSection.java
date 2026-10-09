package cn.dct.zpback.entity.career;

import lombok.Data;
import java.util.List;

/**
 * 事业报告章节
 */
@Data
public class CareerSection {

    private String sectionId;

    /** 报告实例ID */
    private String reportInstanceId;

    /** 章节类型: OVERVIEW / DEEP_ANALYSIS / TIMING / CAUTION / RECOMMENDATION */
    private String sectionType;

    /** 章节Key */
    private String sectionKey;

    /** 标题 */
    private String title;

    /** 排序 */
    private Integer orderNum;

    /** 摘要 */
    private String summary;

    /** 正文 */
    private String body;

    /** Claim IDs */
    private List<String> claimIds;

    /** Timing IDs */
    private List<String> timingIds;

    /** Recommendation IDs */
    private List<String> recommendationIds;

    /** Claims */
    private List<CareerClaim> claims;

    /** 是否可见 */
    private Boolean isVisible = true;
}
