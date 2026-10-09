package cn.dct.zpback.entity.children;

import lombok.Data;
import java.io.Serializable;
import java.util.ArrayList;
import java.util.List;

/**
 * 子女报告 - Claim 结果
 * 符合 SEALED 文档《01_子女与养育深度报告_最终开发总说明》第 8 节
 */
@Data
public class ChildrenClaim implements Serializable {
    private static final long serialVersionUID = 1L;

    private String claimId;              // C01..C11
    private String claimKey;             // CHILDREN_PATH_THEME 等
    private String state;                // SUPPORTED / CONDITIONAL / MIXED / CONTRADICTED / INSUFFICIENT / SUPPRESSED
    private String confidence;           // HIGH / MEDIUM / LOW / NOT_APPLICABLE
    private String primaryCandidate;
    private String secondaryCandidate;
    private List<String> supportRootIds = new ArrayList<>();
    private List<String> contradictRootIds = new ArrayList<>();
    private List<String> d1PrimaryRootIds = new ArrayList<>();
    private List<String> d7SpecialistRootIds = new ArrayList<>();
    private List<String> clusters = new ArrayList<>();
    private List<String> resolutionCodes = new ArrayList<>();
    private String derivedFrom;
    private String renderPermission;     // DIRECT / DERIVED_ONLY / TIMING_ONLY
    private String sectionOwner;
    private String omissionReason;
}
