package cn.dct.zpback.entity.education;

import lombok.Data;
import java.io.Serializable;
import java.util.ArrayList;
import java.util.List;

/**
 * 学业报告 - Claim 对象
 * 状态：SUPPORTED / MIXED / INSUFFICIENT / SUPPRESSED（§6）
 * 置信度：HIGH / MEDIUM / LOW
 */
@Data
public class EducationClaim implements Serializable {
    private static final long serialVersionUID = 1L;

    private String claimId;
    private String claimKey;
    private String claimType;              // Natal / Derived / Hybrid / Composite / Timing

    private String state;
    private String confidence;

    private String primaryCandidate;
    private List<String> qualifierCandidates = new ArrayList<>();

    private List<String> supportRootIds = new ArrayList<>();
    private List<String> challengeRootIds = new ArrayList<>();
    private List<String> supportFamilies = new ArrayList<>();

    /** D24 解析动作：CONFIRMS / QUALIFIES / CONTRADICTS / null */
    private String d24Action;
    /** 不足原因 */
    private String insufficiencyReason;

    private String sectionOwner;
    private String renderPermission;       // DIRECT / DERIVED_ONLY
    private String sectionMode;            // FULL / SHORTEN_ON_LOW_EVIDENCE

    private List<String> resolutionCodes = new ArrayList<>();
    private List<String> notes = new ArrayList<>();
}
