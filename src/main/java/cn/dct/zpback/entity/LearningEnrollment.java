package cn.dct.zpback.entity;

import com.fasterxml.jackson.annotation.JsonAlias;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.databind.PropertyNamingStrategies;
import com.fasterxml.jackson.databind.annotation.JsonNaming;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 问星渡｜报名与参与记录
 *
 * 对应表：learning_enrollment
 *
 * 报名（Enrollment）与权限（Entitlement）刻意分离：
 *   有 ACTIVE 权限才能报名；报名成功才占用名额。
 *   幂等：同一 user + 同一 session 重复报名返回既有记录，名额不重复扣减。
 *
 * enrollment_status 状态机（实现方拟定）：
 *   ENROLLED -> ATTENDED / ABSENT
 *   ENROLLED -> CANCELLED（释放名额）
 */
@Data
@JsonIgnoreProperties(ignoreUnknown = true)
@JsonNaming(PropertyNamingStrategies.SnakeCaseStrategy.class)
public class LearningEnrollment {

    @JsonAlias("enrollmentId")
    private String enrollmentId;

    @JsonAlias("userId")
    private String userId;

    @JsonAlias("sessionId")
    private String sessionId;

    /** COURSE / EVENT */
    @JsonAlias("targetType")
    private String targetType;

    /** ENROLLED / ATTENDED / ABSENT / CANCELLED */
    @JsonAlias("enrollmentStatus")
    private String enrollmentStatus;

    @JsonAlias("attendanceCheckedAt")
    private LocalDateTime attendanceCheckedAt;

    @JsonAlias("cancelledAt")
    private LocalDateTime cancelledAt;

    @JsonAlias("createdAt")
    private LocalDateTime createdAt;
}
