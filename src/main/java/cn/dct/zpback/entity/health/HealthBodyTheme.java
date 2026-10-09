package cn.dct.zpback.entity.health;

import lombok.Data;
import java.io.Serializable;

/**
 * 身体关注主题
 */
@Data
public class HealthBodyTheme implements Serializable {

    private static final long serialVersionUID = 1L;

    /** 主题名称: ENERGY_AND_RECOVERY, SLEEP_AND_REST, etc. */
    private String name;

    /** 极性: SUPPORT, CONTRADICT, MITIGATE, NEUTRAL */
    private String polarity;

    /** 描述 */
    private String description;
}
