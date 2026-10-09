package cn.dct.zpback.entity.health;

import lombok.Data;
import java.io.Serializable;
import java.util.List;

/**
 * 健康 Claim 解析结果
 */
@Data
public class HealthClaim implements Serializable {

    private static final long serialVersionUID = 1L;

    /** Claim ID (H01-H14) */
    private String claimId;

    /** Claim 类型 */
    private String claimType;

    /** Claim 状态: SUPPORTED, CONDITIONAL, MIXED, CONTRADICTED, INSUFFICIENT, SUPPRESSED */
    private String claimState;

    /** 置信度: HIGH, MEDIUM, LOW, NONE */
    private String claimConfidence;

    /** 主要候选ID */
    private String primaryCandidateId;

    /** 次要候选ID列表 */
    private List<String> secondaryCandidateIds;

    /** 支持根ID列表 */
    private List<String> supportRootIds;

    /** 矛盾根ID列表 */
    private List<String> contradictRootIds;

    /** 缓解根ID列表 */
    private List<String> mitigationRootIds;

    /** 中性根ID列表 */
    private List<String> neutralRootIds;

    /** Nakshatra 限定符 */
    private String nakshatraQualifier;

    /** 渲染可见性: FULL, PARTIAL, NONE */
    private String rendererVisibility;

    /** Claim 摘要文本 */
    private String summary;
}
