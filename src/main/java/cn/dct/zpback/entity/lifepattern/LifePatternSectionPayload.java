package cn.dct.zpback.entity.lifepattern;

import lombok.Data;
import java.io.Serializable;
import java.util.List;

/**
 * 人生课题报告 - 章节 Payload
 */
@Data
public class LifePatternSectionPayload implements Serializable {
    private static final long serialVersionUID = 1L;

    /** 章节 Key */
    private String sectionKey;

    /** 章节标题 */
    private String title;

    /** 章节问题 */
    private String question;

    /** 使用的 Claim IDs */
    private List<String> claimIds;

    /** 章节摘要 */
    private String summary;

    /** Takeaway */
    private String takeaway;

    /** Evidence 折叠区 */
    private List<EvidenceObject> evidenceFold;
}
