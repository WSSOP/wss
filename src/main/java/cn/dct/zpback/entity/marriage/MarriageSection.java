package cn.dct.zpback.entity.marriage;

import lombok.Data;
import java.io.Serializable;
import java.util.List;

/**
 * 婚恋报告章节
 */
@Data
public class MarriageSection implements Serializable {

    private static final long serialVersionUID = 1L;

    /** 章节Key */
    private String sectionKey;

    /** 章节标题 */
    private String title;

    /** 章节副标题 */
    private String subtitle;

    /** 章节顺序 */
    private Integer order;

    /** 覆盖状态: FULL, PARTIAL, INSUFFICIENT */
    private String coverageState;

    /** 章节信息角色: ENTRY, NEED, PARTNER, EVALUATION, INTERACTION, TIMING, ACTION */
    private String chapterRole;

    /** 渲染状态: FULL, NORMAL, SHORT, MINIMAL */
    private String renderStatus;

    /** Claim列表 */
    private List<MarriageClaim> claims;

    /** 段落列表 */
    private List<MarriageParagraph> paragraphs;

    /** 推荐列表 */
    private List<MarriageRecommendation> recommendations;

    /** 时间窗口 */
    private List<MarriageTiming> timingWindows;

    /** Thesis */
    private String thesis;

    /** Synthesis 模式: SINGLE_CORE, DUAL_COMPLEMENT, TENSION_INTEGRATION, CAUSAL_CHAIN, LAYERED_PROFILE */
    private String synthesisMode;

    /** Takeaway */
    private String takeaway;
}
