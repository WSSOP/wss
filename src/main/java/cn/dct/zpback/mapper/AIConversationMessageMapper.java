package cn.dct.zpback.mapper;

import cn.dct.zpback.entity.AIConversationMessage;
import org.apache.ibatis.annotations.*;

import java.util.List;

/**
 * AI 会话消息 Mapper
 */
@Mapper
public interface AIConversationMessageMapper {

    @Insert("INSERT INTO ai_conversation_message (id, conversation_id, role, content, " +
            "message_type, structured_content_json, tokens_used, sort_order, created_at) " +
            "VALUES (#{id}, #{conversationId}, #{role}, #{content}, " +
            "#{messageType}, #{structuredContentJson}, #{tokensUsed}, #{sortOrder}, #{createdAt})")
    int insert(AIConversationMessage message);

    @Select("SELECT * FROM ai_conversation_message WHERE conversation_id = #{conversationId} " +
            "ORDER BY sort_order ASC")
    List<AIConversationMessage> findByConversationId(@Param("conversationId") String conversationId);

    @Select("SELECT COUNT(*) FROM ai_conversation_message WHERE conversation_id = #{conversationId}")
    int countByConversationId(@Param("conversationId") String conversationId);

    @Delete("DELETE FROM ai_conversation_message WHERE conversation_id = #{conversationId}")
    int deleteByConversationId(@Param("conversationId") String conversationId);
}
