package cn.dct.zpback.entity;

import com.fasterxml.jackson.databind.PropertyNamingStrategies;
import com.fasterxml.jackson.databind.annotation.JsonNaming;
import lombok.Data;

import java.io.Serializable;
import java.time.LocalDateTime;

/**
 * 心理测评中心｜用户答题记录实体
 *
 * 对应表：psychological_user_answer
 */
@Data
@JsonNaming(PropertyNamingStrategies.SnakeCaseStrategy.class)
public class PsychologicalUserAnswer implements Serializable {

    private static final long serialVersionUID = 1L;

    private String answerId;
    private String resultId;
    private String questionId;
    private String optionId;
    private Integer score;
    private LocalDateTime createdAt;
}
