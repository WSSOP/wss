package cn.dct.zpback.mapper;

import cn.dct.zpback.entity.LearningEnrollment;
import org.apache.ibatis.annotations.*;

import java.util.List;

/**
 * 问星渡｜报名记录 Mapper
 *
 * 幂等靠 (user_id, session_id) 唯一索引 + findExisting 判断，
 * 重复报名返回既有记录，绝不重复扣名额。
 */
@Mapper
public interface LearningEnrollmentMapper {

    String COLUMNS = "enrollment_id, user_id, session_id, target_type, enrollment_status, "
            + "attendance_checked_at, cancelled_at, created_at";

    @Select("SELECT " + COLUMNS + " FROM learning_enrollment "
            + "WHERE user_id = #{userId} AND session_id = #{sessionId}")
    LearningEnrollment findExisting(@Param("userId") String userId,
                                    @Param("sessionId") String sessionId);

    @Select("SELECT " + COLUMNS + " FROM learning_enrollment "
            + "WHERE user_id = #{userId} AND enrollment_status IN ('ENROLLED', 'ATTENDED') "
            + "ORDER BY created_at DESC")
    List<LearningEnrollment> findActiveByUser(@Param("userId") String userId);

    /** 某场次已报名名单（讲师点名 / 出勤核销用） */
    @Select("SELECT " + COLUMNS + " FROM learning_enrollment "
            + "WHERE session_id = #{sessionId} AND enrollment_status IN ('ENROLLED', 'ATTENDED') "
            + "ORDER BY created_at")
    List<LearningEnrollment> findBySession(@Param("sessionId") String sessionId);

    @Insert("INSERT INTO learning_enrollment (enrollment_id, user_id, session_id, target_type, "
            + "enrollment_status, attendance_checked_at) VALUES "
            + "(#{enrollmentId}, #{userId}, #{sessionId}, #{targetType}, #{enrollmentStatus}, "
            + "#{attendanceCheckedAt})")
    int insert(LearningEnrollment e);

    /**
     * 取消后重新报名：CANCELLED -> ENROLLED。
     *
     * 必须走 UPDATE 而非 INSERT——(user_id, session_id) 上有唯一索引，
     * 重新报名时直接插入会撞约束。
     */
    @Update("UPDATE learning_enrollment SET enrollment_status = 'ENROLLED', "
            + "cancelled_at = NULL, attendance_checked_at = NULL, target_type = #{targetType} "
            + "WHERE enrollment_id = #{enrollmentId}")
    int revive(@Param("enrollmentId") String enrollmentId,
               @Param("targetType") String targetType);

    /** 取消报名：ENROLLED -> CANCELLED。已核销出勤的不可取消。 */
    @Update("UPDATE learning_enrollment SET enrollment_status = 'CANCELLED', cancelled_at = NOW() "
            + "WHERE user_id = #{userId} AND session_id = #{sessionId} "
            + "  AND enrollment_status = 'ENROLLED'")
    int cancel(@Param("userId") String userId, @Param("sessionId") String sessionId);

    /** 出勤核销：ENROLLED -> ATTENDED / ABSENT */
    @Update("UPDATE learning_enrollment SET enrollment_status = #{status}, attendance_checked_at = NOW() "
            + "WHERE user_id = #{userId} AND session_id = #{sessionId} "
            + "  AND enrollment_status = 'ENROLLED'")
    int checkAttendance(@Param("userId") String userId,
                        @Param("sessionId") String sessionId,
                        @Param("status") String status);
}
