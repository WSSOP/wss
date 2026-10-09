package cn.dct.zpback.mapper;

import cn.dct.zpback.entity.AIConversation;
import org.apache.ibatis.annotations.*;

import java.time.LocalDateTime;
import java.util.List;

/**
 * AI 会话 Mapper
 */
@Mapper
public interface AIConversationMapper {

    @Insert("INSERT INTO ai_conversation (id, user_id, mode, first_question, response_style, " +
            "context_json, source_context, status, message_count, last_active_at, created_at, updated_at) " +
            "VALUES (#{id}, #{userId}, #{mode}, #{firstQuestion}, #{responseStyle}, " +
            "#{contextJson}, #{sourceContext}, #{status}, #{messageCount}, #{lastActiveAt}, #{createdAt}, #{updatedAt})")
    int insert(AIConversation conversation);

    @Select("SELECT * FROM ai_conversation WHERE id = #{id}")
    AIConversation findById(@Param("id") String id);

    @Select("SELECT * FROM ai_conversation WHERE user_id = #{userId} AND status = #{status} " +
            "ORDER BY last_active_at DESC LIMIT #{limit} OFFSET #{offset}")
    List<AIConversation> findByUserId(@Param("userId") String userId,
                                       @Param("status") String status,
                                       @Param("limit") int limit,
                                       @Param("offset") int offset);

    @Select("SELECT COUNT(*) FROM ai_conversation WHERE user_id = #{userId} AND status = #{status}")
    int countByUserId(@Param("userId") String userId, @Param("status") String status);

    @Update("UPDATE ai_conversation SET status = #{status}, updated_at = #{updatedAt} WHERE id = #{id}")
    int updateStatus(@Param("id") String id, @Param("status") String status, @Param("updatedAt") LocalDateTime updatedAt);

    @Update("UPDATE ai_conversation SET response_style = #{responseStyle}, updated_at = #{updatedAt} WHERE id = #{id}")
    int updateResponseStyle(@Param("id") String id, @Param("responseStyle") String responseStyle, @Param("updatedAt") LocalDateTime updatedAt);

    @Update("UPDATE ai_conversation SET message_count = message_count + 1, last_active_at = #{now}, updated_at = #{now} WHERE id = #{id}")
    int incrementMessageCount(@Param("id") String id, @Param("now") LocalDateTime now);

    @Delete("DELETE FROM ai_conversation WHERE id = #{id}")
    int deleteById(@Param("id") String id);
}
