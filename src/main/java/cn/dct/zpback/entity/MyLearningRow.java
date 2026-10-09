package cn.dct.zpback.entity;

import com.fasterxml.jackson.annotation.JsonAlias;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * 问星渡｜我的学习（一行 = 一次报名）
 *
 * 由 learning_enrollment ⋈ 场次 ⋈ 课程/活动 JOIN 得出，
 * 目的是让「我的学习」列表一次取全，不必前端逐条回查标题。
 *
 * <b>安全</b>：本行<b>不含 live_url</b>。
 * 直播地址只能通过 enrolledSession（带报名校验）单独获取，
 * 否则用户取消报名后列表里仍会残留直播入口。
 */
@Data
@JsonIgnoreProperties(ignoreUnknown = true)
public class MyLearningRow {

    @JsonAlias("enrollmentId")
    private String enrollmentId;

    @JsonAlias("sessionId")
    private String sessionId;

    /** COURSE / EVENT */
    @JsonAlias("targetType")
    private String targetType;

    @JsonAlias("targetId")
    private String targetId;

    @JsonAlias("targetTitle")
    private String targetTitle;

    @JsonAlias("sessionTitle")
    private String sessionTitle;

    @JsonAlias("sessionNo")
    private Integer sessionNo;

    @JsonAlias("startAt")
    private LocalDateTime startAt;

    @JsonAlias("endAt")
    private LocalDateTime endAt;

    /** SCHEDULED / ONGOING / DONE / CANCELLED */
    @JsonAlias("sessionStatus")
    private String sessionStatus;

    @JsonAlias("isLive")
    private Boolean isLive;

    /** 回放地址：课后公开，不敏感 */
    @JsonAlias("recordingUrl")
    private String recordingUrl;

    @JsonAlias("capacity")
    private Integer capacity;

    @JsonAlias("price")
    private BigDecimal price;

    @JsonAlias("enrolledCount")
    private Integer enrolledCount;

    /** ENROLLED / ATTENDED */
    @JsonAlias("enrollmentStatus")
    private String enrollmentStatus;

    @JsonAlias("attendanceCheckedAt")
    private LocalDateTime attendanceCheckedAt;

    @JsonAlias("createdAt")
    private LocalDateTime createdAt;
}