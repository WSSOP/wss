package cn.dct.zpback.entity;

import com.fasterxml.jackson.annotation.JsonAlias;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.databind.PropertyNamingStrategies;
import com.fasterxml.jackson.databind.annotation.JsonNaming;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 问星渡｜主题活动
 *
 * 对应表：learning_event
 *
 * 规格来源：原始 skill 目录无字段定义，本实体字段与枚举均为实现方所拟。
 *
 * live_url 只在"已报名者查看详情"接口返回，绝不在列表接口外泄。
 */
@Data
@JsonIgnoreProperties(ignoreUnknown = true)
@JsonNaming(PropertyNamingStrategies.SnakeCaseStrategy.class)
public class LearningEvent {

    @JsonAlias("eventId")
    private String eventId;

    @JsonAlias("title")
    private String title;

    @JsonAlias("subtitle")
    private String subtitle;

    @JsonAlias("description")
    private String description;

    @JsonAlias("coverUrl")
    private String coverUrl;

    /** TALK / WORKSHOP / MEDITATION / RETREAT / QA */
    @JsonAlias("eventType")
    private String eventType;

    /** DRAFT / PUBLISHED / UNPUBLISHED / ARCHIVED */
    @JsonAlias("eventStatus")
    private String eventStatus;

    @JsonAlias("hostId")
    private String hostId;

    @JsonAlias("venueName")
    private String venueName;

    @JsonAlias("venueAddress")
    private String venueAddress;

    @JsonAlias("isOnline")
    private Boolean isOnline;

    @JsonAlias("liveUrl")
    private String liveUrl;

    @JsonAlias("durationMinutes")
    private Integer durationMinutes;

    @JsonAlias("agenda")
    private String agenda;

    @JsonAlias("createdAt")
    private LocalDateTime createdAt;

    @JsonAlias("updatedAt")
    private LocalDateTime updatedAt;
}
