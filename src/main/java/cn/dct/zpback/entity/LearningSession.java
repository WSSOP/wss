package cn.dct.zpback.entity;

import com.fasterxml.jackson.annotation.JsonAlias;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.databind.PropertyNamingStrategies;
import com.fasterxml.jackson.databind.annotation.JsonNaming;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * 问星渡｜场次（课程场次与活动场次共用同一结构）
 *
 * 对应表：course_session / learning_event_session
 * 两者字段一致，只是外键指向 learning_course 或 learning_event，
 * 因此用一张实体承载，业务层按 targetType 区分。
 *
 * session_status 状态机（实现方拟定）：
 *   SCHEDULED -> ONGOING -> DONE
 *   SCHEDULED/ONGOING -> CANCELLED（保留已报名记录）
 *
 * live_url / recording_url 只在已报名者视角返回。
 */
@Data
@JsonIgnoreProperties(ignoreUnknown = true)
@JsonNaming(PropertyNamingStrategies.SnakeCaseStrategy.class)
public class LearningSession {

    @JsonAlias("sessionId")
    private String sessionId;

    /** COURSE 指向 learning_course.course_id；EVENT 指向 learning_event.event_id */
    @JsonAlias("targetId")
    private String targetId;

    /** COURSE / EVENT */
    @JsonAlias("targetType")
    private String targetType;

    @JsonAlias("sessionNo")
    private Integer sessionNo;

    @JsonAlias("title")
    private String title;

    @JsonAlias("startAt")
    private LocalDateTime startAt;

    @JsonAlias("endAt")
    private LocalDateTime endAt;

    @JsonAlias("durationMinutes")
    private Integer durationMinutes;

    @JsonAlias("isLive")
    private Boolean isLive;

    @JsonAlias("liveUrl")
    private String liveUrl;

    @JsonAlias("recordingUrl")
    private String recordingUrl;

    /** SCHEDULED / ONGOING / DONE / CANCELLED */
    @JsonAlias("sessionStatus")
    private String sessionStatus;

    /** 0 = 不限 */
    @JsonAlias("capacity")
    private Integer capacity;

    /** 0 = 免费 */
    @JsonAlias("price")
    private BigDecimal price;

    /** 已报名人数 */
    @JsonAlias("enrolledCount")
    private Integer enrolledCount;

    @JsonAlias("createdAt")
    private LocalDateTime createdAt;

    @JsonAlias("updatedAt")
    private LocalDateTime updatedAt;
}
