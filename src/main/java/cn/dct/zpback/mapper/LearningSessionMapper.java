package cn.dct.zpback.mapper;

import cn.dct.zpback.entity.LearningSession;
import cn.dct.zpback.entity.MyLearningRow;
import org.apache.ibatis.annotations.*;

import java.util.List;

/**
 * 问星渡｜场次 Mapper（课程场次与活动场次各一张表）
 *
 * <b>列别名约定</b>：课程场的 `course_id`、活动场的 `event_id`，
 * 在 SQL 里统一 AS 成 `target_id`，把两套外键收敛到实体的同一个字段。
 * 活动场的 `registered_count` 同样 AS 成 `enrolled_count`。
 * 漏掉别名会导致 targetId 为 null，进而被服务层的
 * 「场次与内容不匹配」校验全部拒绝。
 *
 * <b>安全约束</b>：
 * - COLUMNS_SAFE 不含 live_url / recording_url，供未报名者使用；
 * - findEnrolled* 走 JOIN learning_enrollment，
 *   只有确认报名后才返回直播/回放地址，从 SQL 层杜绝越权。
 *
 * 名额扣减用带条件的 UPDATE（capacity = 0 或 enrolled_count &lt; capacity），
 * 不做「先查后写」，避免并发超卖。
 */
@Mapper
public interface LearningSessionMapper {

    /**
     * 未报名者视角的公共列（不含直播/回放地址）。
     *
     * <b>刻意不含人数列</b>：课程场次叫 enrolled_count，
     * 活动场次叫 registered_count。把人数移出公共片段，
     * 由各查询自己决定写原名还是 AS 成 enrolled_count，
     * 避免「公共片段带 enrolled_count + 追加别名」造成重复列名，
     * 那会让活动详情直接报 Unknown column 500。
     */
    String COLUMNS_SAFE = "session_id, session_no, title, start_at, end_at, duration_minutes, "
            + "is_live, session_status, capacity, price";

    /** 课程场次（含直播地址，带表别名的常量形式） */
    String COLUMNS_COURSE_ALL = "s.session_id, s.course_id AS target_id, 'COURSE' AS target_type, "
            + "s.session_no, s.title, s.start_at, s.end_at, s.duration_minutes, s.is_live, "
            + "s.live_url, s.recording_url, s.session_status, s.capacity, s.price, s.enrolled_count";

    /** 活动场次：没有 recording_url 列，人数字段名也不同 */
    String COLUMNS_EVENT_ALL = "s.session_id, s.event_id AS target_id, 'EVENT' AS target_type, "
            + "s.session_no, s.title, s.start_at, s.end_at, s.duration_minutes, s.is_live, "
            + "s.live_url, s.session_status, s.capacity, s.price, "
            + "s.registered_count AS enrolled_count";

    // ============================================================
    // 公开视角（未报名者）：无直播地址
    // ============================================================

    @Select("SELECT " + COLUMNS_SAFE + ", course_id AS target_id, 'COURSE' AS target_type, "
            + "enrolled_count FROM course_session WHERE course_id = #{targetId} ORDER BY session_no")
    List<LearningSession> findCourseSessions(@Param("targetId") String targetId);

    @Select("SELECT " + COLUMNS_SAFE + ", event_id AS target_id, 'EVENT' AS target_type, "
            + "registered_count AS enrolled_count "
            + "FROM learning_event_session WHERE event_id = #{targetId} ORDER BY session_no")
    List<LearningSession> findEventSessions(@Param("targetId") String targetId);

    // ============================================================
    // 含直播地址（后台 / 已报名场景）
    // ============================================================

    @Select("SELECT " + COLUMNS_COURSE_ALL + " FROM course_session s WHERE s.session_id = #{sessionId}")
    LearningSession findCourseSessionById(@Param("sessionId") String sessionId);

    @Select("SELECT " + COLUMNS_EVENT_ALL + " FROM learning_event_session s WHERE s.session_id = #{sessionId}")
    LearningSession findEventSessionById(@Param("sessionId") String sessionId);

    /**
     * 已报名用户的场次详情：JOIN 报名表后才给直播/回放地址。
     *
     * 只有 ENROLLED / ATTENDED 算有效报名；
     * CANCELLED 已释放名额，因此<b>不能</b>再拿到直播地址。
     */
    @Select("SELECT " + COLUMNS_COURSE_ALL + " FROM course_session s "
            + "JOIN learning_enrollment e ON e.session_id = s.session_id "
            + "WHERE s.session_id = #{sessionId} AND e.user_id = #{userId} "
            + "  AND e.enrollment_status IN ('ENROLLED', 'ATTENDED')")
    LearningSession findEnrolledCourseSession(@Param("sessionId") String sessionId,
                                               @Param("userId") String userId);

    @Select("SELECT " + COLUMNS_EVENT_ALL + " FROM learning_event_session s "
            + "JOIN learning_enrollment e ON e.session_id = s.session_id "
            + "WHERE s.session_id = #{sessionId} AND e.user_id = #{userId} "
            + "  AND e.enrollment_status IN ('ENROLLED', 'ATTENDED')")
    LearningSession findEnrolledEventSession(@Param("sessionId") String sessionId,
                                             @Param("userId") String userId);

    // ============================================================
    // 名额
    // ============================================================

    /**
     * 占一个名额。条件写在 WHERE 里做乐观并发控制：
     * capacity = 0 表示不限名额，否则必须还有余额。
     * 返回 0 说明名额已满或场次不可报名。
     */
    @Update("UPDATE course_session SET enrolled_count = enrolled_count + 1 "
            + "WHERE session_id = #{sessionId} AND session_status IN ('SCHEDULED', 'ONGOING') "
            + "  AND (capacity = 0 OR enrolled_count < capacity)")
    int occupyCourseSeat(@Param("sessionId") String sessionId);

    @Update("UPDATE learning_event_session SET registered_count = registered_count + 1 "
            + "WHERE session_id = #{sessionId} AND session_status IN ('SCHEDULED', 'ONGOING') "
            + "  AND (capacity = 0 OR registered_count < capacity)")
    int occupyEventSeat(@Param("sessionId") String sessionId);

    /** 释放名额：取消报名时调用。GREATEST 兜底防止负数。 */
    @Update("UPDATE course_session SET enrolled_count = GREATEST(enrolled_count - 1, 0) "
            + "WHERE session_id = #{sessionId}")
    int releaseCourseSeat(@Param("sessionId") String sessionId);

    @Update("UPDATE learning_event_session SET registered_count = GREATEST(registered_count - 1, 0) "
            + "WHERE session_id = #{sessionId}")
    int releaseEventSeat(@Param("sessionId") String sessionId);

    // ============================================================
    // 运营写入
    // ============================================================

    @Insert("INSERT INTO course_session (session_id, course_id, session_no, title, start_at, "
            + "end_at, duration_minutes, is_live, live_url, recording_url, session_status, "
            + "capacity, price, enrolled_count) VALUES "
            + "(#{sessionId}, #{targetId}, #{sessionNo}, #{title}, #{startAt}, #{endAt}, "
            + "#{durationMinutes}, #{isLive}, #{liveUrl}, #{recordingUrl}, #{sessionStatus}, "
            + "#{capacity}, #{price}, 0)")
    int insertCourseSession(LearningSession session);

    @Insert("INSERT INTO learning_event_session (session_id, event_id, session_no, title, "
            + "start_at, end_at, duration_minutes, is_live, live_url, session_status, "
            + "capacity, price, registered_count) VALUES "
            + "(#{sessionId}, #{targetId}, #{sessionNo}, #{title}, #{startAt}, #{endAt}, "
            + "#{durationMinutes}, #{isLive}, #{liveUrl}, #{sessionStatus}, "
            + "#{capacity}, #{price}, 0)")
    int insertEventSession(LearningSession session);

    /**
     * 我的课程报名（含课程标题与场次时间）。
     *
     * <b>为什么不让前端自己回查</b>：learning_enrollment 只存 session_id，
     * 不存课程 ID。前端要显示标题就得先拿报名列表、再逐条拉课程详情，
     * 变成 N+1 请求，且课程被下架时整条记录就取不到标题。
     * 这里一次 JOIN 出去，前端拿到的就是可直接渲染的行。
     */
    @Select("SELECT e.enrollment_id, e.user_id, e.session_id, e.target_type, "
            + "e.enrollment_status, e.attendance_checked_at, e.cancelled_at, e.created_at, "
            + "c.course_id AS target_id, c.title AS target_title, "
            + "s.title AS session_title, s.session_no, s.start_at, s.end_at, s.session_status, "
            + "s.is_live, s.recording_url, s.capacity, s.price, s.enrolled_count "
            + "FROM learning_enrollment e "
            + "JOIN course_session s ON s.session_id = e.session_id "
            + "JOIN learning_course c ON c.course_id = s.course_id "
            + "WHERE e.user_id = #{userId} AND e.enrollment_status IN ('ENROLLED','ATTENDED') "
            + "ORDER BY s.start_at DESC")
    List<MyLearningRow> findMyCourseEnrollments(@Param("userId") String userId);

    /** 我的活动报名（含活动标题与场次时间） */
    @Select("SELECT e.enrollment_id, e.user_id, e.session_id, e.target_type, "
            + "e.enrollment_status, e.attendance_checked_at, e.cancelled_at, e.created_at, "
            + "ev.event_id AS target_id, ev.title AS target_title, "
            + "s.title AS session_title, s.session_no, s.start_at, s.end_at, s.session_status, "
            + "s.is_live, s.capacity, s.price, s.registered_count AS enrolled_count "
            + "FROM learning_enrollment e "
            + "JOIN learning_event_session s ON s.session_id = e.session_id "
            + "JOIN learning_event ev ON ev.event_id = s.event_id "
            + "WHERE e.user_id = #{userId} AND e.enrollment_status IN ('ENROLLED','ATTENDED') "
            + "ORDER BY s.start_at DESC")
    List<MyLearningRow> findMyEventEnrollments(@Param("userId") String userId);}
