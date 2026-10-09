package cn.dct.zpback.entity.marriage;

import lombok.Data;
import java.io.Serializable;
import java.util.List;

/**
 * 版本信息
 */
@Data
public class MarriageVersionInfo implements Serializable {

    private static final long serialVersionUID = 1L;

    /** 证据引擎版本 */
    private String evidenceEngineVersion;

    /** Claim注册版本 */
    private String claimRegistryVersion;

    /** 去重引擎版本 */
    private String dedupEngineVersion;

    /** 合成引擎版本 */
    private String synthesisEngineVersion;

    /** 推荐引擎版本 */
    private String recommendationEngineVersion;

    /** Renderer合约版本 */
    private String rendererContractVersion;

    /** Prompt版本 */
    private String promptVersion;

    /** 模型版本 */
    private String modelVersion;

    /** UL计算约定 */
    private String ulCalculationConvention;
}
