package cn.dct.zpback.entity;

import com.fasterxml.jackson.annotation.JsonAlias;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.databind.PropertyNamingStrategies;
import com.fasterxml.jackson.databind.annotation.JsonNaming;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 问星渡｜学习课程
 *
 * 对应表：learning_course
 *
 * 规格来源：原始 skill 目录无字段定义（只有 205~469 字节的提纲），
 * 本实体的字段与枚举均为实现方所拟，不是原始规格。
 *
 * course_status 状态机：
 *   DRAFT -> PUBLISHED -> UNPUBLISHED -> PUBLISHED
 *   PUBLISHED/UNPUBLISHED -> ARCHIVED（终态）
 * ARCHIVED 不物理删除：历史订单与学习记录必须保留。
 */
@Data
@JsonIgnoreProperties(ignoreUnknown = true)
@JsonNaming(PropertyNamingStrategies.SnakeCaseStrategy.class)
public class LearningCourse {

    @JsonAlias("courseId")
    private String courseId;

    @JsonAlias("title")
    private String title;

    @JsonAlias("subtitle")
    private String subtitle;

    @JsonAlias("description")
    private String description;

    @JsonAlias("coverUrl")
    private String coverUrl;

    /** ASTROLOGY / VEDIC / INTERPRETATION / MEDITATION */
    @JsonAlias("category")
    private String category;

    /** BEGINNER / INTERMEDIATE / ADVANCED */
    @JsonAlias("level")
    private String level;

    /** DRAFT / PUBLISHED / UNPUBLISHED / ARCHIVED */
    @JsonAlias("courseStatus")
    private String courseStatus;

    @JsonAlias("teacherId")
    private String teacherId;

    @JsonAlias("assistantTeacherId")
    private String assistantTeacherId;

    @JsonAlias("sessionCount")
    private Integer sessionCount;

    @JsonAlias("totalMinutes")
    private Integer totalMinutes;

    @JsonAlias("lessonCount")
    private Integer lessonCount;

    @JsonAlias("outline")
    private String outline;

    @JsonAlias("tags")
    private String tags;

    @JsonAlias("createdAt")
    private LocalDateTime createdAt;

    @JsonAlias("updatedAt")
    private LocalDateTime updatedAt;
}
