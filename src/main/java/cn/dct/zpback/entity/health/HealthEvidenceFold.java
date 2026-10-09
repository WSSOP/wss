package cn.dct.zpback.entity.health;

import lombok.Data;
import java.io.Serializable;
import java.util.List;

/**
 * Evidence 折叠
 */
@Data
public class HealthEvidenceFold implements Serializable {

    private static final long serialVersionUID = 1L;

    /** Evidence列表 */
    private List<HealthEvidence> evidences;
}
