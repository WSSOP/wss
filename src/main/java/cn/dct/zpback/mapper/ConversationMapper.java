package cn.dct.zpback.mapper;

import cn.dct.zpback.entity.Conversation;
import org.apache.ibatis.annotations.*;

import java.util.List;

/**
 * 报告追问会话 Mapper
 */
@Mapper
public interface ConversationMapper {

    /** 根据ID查询 */
    @Select("SELECT * FROM conversation WHERE conversation_id = #{conversationId}")
    Conversation selectById(@Param("conversationId") String conversationId);

    /** 查询用户活跃会话列表 */
    @Select("SELECT * FROM conversation WHERE user_id = #{userId} AND status = 'ACTIVE' ORDER BY last_active_at DESC")
    List<Conversation> selectActiveByUserId(@Param("userId") String userId);

    /** 查询报告对应的活跃会话 */
    @Select("SELECT * FROM conversation WHERE report_instance_id = #{reportInstanceId} AND status = 'ACTIVE' ORDER BY last_active_at DESC LIMIT 1")
    Conversation selectActiveByReportId(@Param("reportInstanceId") String reportInstanceId);

    /** 插入会话 */
    @Insert("INSERT INTO conversation (conversation_id, user_id, report_instance_id, status, turn_count, " +
            "last_question, last_active_at, expire_at) " +
            "VALUES (#{conversationId}, #{userId}, #{reportInstanceId}, #{status}, #{turnCount}, " +
            "#{lastQuestion}, #{lastActiveAt}, #{expireAt})")
    int insert(Conversation conversation);

    /** 更新会话 */
    @Update("UPDATE conversation SET " +
            "status = #{status}, " +
            "turn_count = #{turnCount}, " +
            "last_question = #{lastQuestion}, " +
            "last_active_at = #{lastActiveAt}, " +
            "expire_at = #{expireAt} " +
            "WHERE conversation_id = #{conversationId}")
    int update(Conversation conversation);

    /** 增加追问轮数 */
    @Update("UPDATE conversation SET turn_count = turn_count + 1 WHERE conversation_id = #{conversationId}")
    int incrementTurnCount(@Param("conversationId") String conversationId);

    /** 更新最后活跃时间 */
    @Update("UPDATE conversation SET last_active_at = NOW() WHERE conversation_id = #{conversationId}")
    int updateLastActive(@Param("conversationId") String conversationId);
}
