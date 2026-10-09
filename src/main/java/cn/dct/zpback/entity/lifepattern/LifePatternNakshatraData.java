package cn.dct.zpback.entity.lifepattern;

import lombok.Data;
import java.io.Serializable;

/**
 * 人生课题报告 - Nakshatra 月宿数据
 */
@Data
public class LifePatternNakshatraData implements Serializable {
    private static final long serialVersionUID = 1L;

    /** 月宿名称 */
    private String name;

    /** Pada */
    private Integer pada;
}
