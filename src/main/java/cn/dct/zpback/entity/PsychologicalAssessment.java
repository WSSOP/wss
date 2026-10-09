package cn.dct.zpback.entity;

import com.fasterxml.jackson.databind.PropertyNamingStrategies;
import com.fasterxml.jackson.databind.annotation.JsonNaming;
import lombok.Data;

import java.io.Serializable;
import java.time.LocalDateTime;

/**
 * 心理测评中心｜测评目录实体
 *
 * 对应表：psychological_assessment
 */
@Data
@JsonNaming(PropertyNamingStrategies.SnakeCaseStrategy.class)
public class PsychologicalAssessment implements Serializable {

    private static final long serialVersionUID = 1L;

    private String assessmentId;
    private String slug;
    private String title;
    private String subtitle;
    private String category;
    private String icon;
    private String color;
    private Integer questionCount;
    private Integer estimatedMinutes;
    private Integer minScore;
    private Integer maxScore;
    private String description;
    private String disclaimer;
    private String publishStatus;
    private Integer sortOrder;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
}
