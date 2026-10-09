package cn.dct.zpback.entity.lifepattern;

import lombok.Data;
import java.io.Serializable;

/**
 * 人生课题报告 - Shadbala 六力数据
 */
@Data
public class LifePatternShadbalaData implements Serializable {
    private static final long serialVersionUID = 1L;

    /** 执行能力 */
    private String executionCapacity;

    /** 分数 */
    private Double score;
}
