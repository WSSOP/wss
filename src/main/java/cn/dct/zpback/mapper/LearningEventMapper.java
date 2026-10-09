package cn.dct.zpback.mapper;

import cn.dct.zpback.entity.LearningEvent;
import org.apache.ibatis.annotations.*;

import java.util.List;

/**
 * 问星渡｜活动 Mapper
 *
 * <b>安全约束</b>：本 Mapper 的 findPublished 与 findById 都不返回 live_url，
 * 直播地址只在 LearningSessionMapper.findEnrolledSessionForUser 这条
 * "确认报名后"的路径上暴露，避免未报名用户抓到直播入口。
 */
@Mapper
public interface LearningEventMapper {

    /** 不含 live_url 的列清单 */
    String COLUMNS_SAFE = "event_id, title, subtitle, description, cover_url, event_type, "
            + "event_status, host_id, venue_name, venue_address, is_online, "
            + "duration_minutes, agenda, created_at, updated_at";

    String COLUMNS_ALL = COLUMNS_SAFE + ", live_url";

    @Select("<script>"
            + "SELECT " + COLUMNS_SAFE + " FROM learning_event WHERE event_status = 'PUBLISHED' "
            + " <if test='eventType != null'> AND event_type = #{eventType} </if>"
            + " ORDER BY created_at DESC, event_id"
            + "</script>")
    List<LearningEvent> findPublished(@Param("eventType") String eventType);

    /** 前台详情：不含直播地址 */
    @Select("SELECT " + COLUMNS_SAFE + " FROM learning_event WHERE event_id = #{eventId}")
    LearningEvent findById(@Param("eventId") String eventId);

    /** 后台用：含直播地址 */
    @Select("SELECT " + COLUMNS_ALL + " FROM learning_event WHERE event_id = #{eventId}")
    LearningEvent findByIdWithLiveUrl(@Param("eventId") String eventId);

    /** 主持人名下的活动（含未发布：主持人需要看到自己筹备中的） */
    @Select("SELECT " + COLUMNS_ALL + " FROM learning_event WHERE host_id = #{hostId} "
            + "ORDER BY event_status, created_at DESC")
    List<LearningEvent> findByHost(@Param("hostId") String hostId);

    @Insert("INSERT INTO learning_event (event_id, title, subtitle, description, cover_url, "
            + "event_type, event_status, host_id, venue_name, venue_address, is_online, "
            + "live_url, duration_minutes, agenda) VALUES "
            + "(#{eventId}, #{title}, #{subtitle}, #{description}, #{coverUrl}, "
            + "#{eventType}, #{eventStatus}, #{hostId}, #{venueName}, #{venueAddress}, "
            + "#{isOnline}, #{liveUrl}, #{durationMinutes}, #{agenda})")
    int insert(LearningEvent event);
}
