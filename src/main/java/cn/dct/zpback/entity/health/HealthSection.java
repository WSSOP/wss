package cn.dct.zpback.entity.health;

import lombok.Data;
import java.io.Serializable;
import java.util.List;

/**
 * 健康报告章节
 */
@Data
public class HealthSection implements Serializable {

    private static final long serialVersionUID = 1L;

    /** 章节Key (如 EXECUTIVE_SUMMARY) */
    private String sectionKey;

    /** 章节标题 */
    private String title;

    /** 章节顺序 */
    private Integer order;

    /** 覆盖状态: FULL, PARTIAL, INSUFFICIENT */
    private String coverageState;

    /** Claim列表 */
    private List<HealthClaim> claims;

    /** 段落列表 */
    private List<HealthParagraph> paragraphs;

    /** Evidence折叠 */
    private HealthEvidenceFold evidenceFold;

    /** 推荐列表 */
    private List<HealthRecommendation> recommendations;

    /** 时间窗口 */
    private List<HealthTiming> timingWindows;

    /** 身体关注主题 */
    private List<HealthBodyTheme> bodyAttentionThemes;
}
