package cn.dct.zpback.entity;

import com.fasterxml.jackson.databind.PropertyNamingStrategies;
import com.fasterxml.jackson.databind.annotation.JsonNaming;
import lombok.Data;

import java.io.Serializable;
import java.time.LocalDateTime;

/**
 * 心理测评中心｜选项实体
 *
 * 对应表：psychological_option
 */
@Data
@JsonNaming(PropertyNamingStrategies.SnakeCaseStrategy.class)
public class PsychologicalOption implements Serializable {

    private static final long serialVersionUID = 1L;

    private String optionId;
    private String questionId;
    private String text;
    private Integer score;
    private Integer orderIndex;
    private LocalDateTime createdAt;
}
