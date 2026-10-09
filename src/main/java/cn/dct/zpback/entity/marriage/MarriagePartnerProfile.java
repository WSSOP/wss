package cn.dct.zpback.entity.marriage;

import lombok.Data;
import java.io.Serializable;
import java.util.List;

/**
 * 伴侣画像 (S3)
 */
@Data
public class MarriagePartnerProfile implements Serializable {

    private static final long serialVersionUID = 1L;

    /** 核心性格 */
    private String corePersonality;

    /** 关系角色 */
    private String relationalRole;

    /** 成熟风格 */
    private String maturityStyle;

    /** 社会/职业倾向 */
    private String socialCareerTendency;

    /** 背景倾向 */
    private String backgroundTendency;

    /** 外表/气质 */
    private String appearancePresence;

    /** 距离/文化倾向 */
    private String distanceCultureTendency;

    /** 年龄差倾向 */
    private String ageGapTendency;

    /** 需求原型匹配 */
    private String needArchetypeAlignment;

    /** 伴侣关键词 */
    private List<String> keywords;

    /** 综合画像描述 */
    private String profileDescription;
}
