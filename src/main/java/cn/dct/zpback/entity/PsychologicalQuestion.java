package cn.dct.zpback.entity;

import com.fasterxml.jackson.databind.PropertyNamingStrategies;
import com.fasterxml.jackson.databind.annotation.JsonNaming;
import lombok.Data;

import java.io.Serializable;
import java.time.LocalDateTime;

/**
 * 心理测评中心｜题目实体
 *
 * 对应表：psychological_question
 */
@Data
@JsonNaming(PropertyNamingStrategies.SnakeCaseStrategy.class)
public class PsychologicalQuestion implements Serializable {

    private static final long serialVersionUID = 1L;

    private String questionId;
    private String assessmentId;
    private String text;
    private Integer orderIndex;
    private Boolean reverseScore;
    private LocalDateTime createdAt;
}
