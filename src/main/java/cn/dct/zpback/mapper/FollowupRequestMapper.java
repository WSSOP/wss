package cn.dct.zpback.mapper;

import cn.dct.zpback.entity.FollowupRequest;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.util.List;

/**
 * 追问请求 Mapper
 */
@Mapper
public interface FollowupRequestMapper {

    /** 根据ID查询 */
    FollowupRequest selectById(@Param("requestId") String requestId);

    /** 根据幂等键查询 */
    FollowupRequest selectByIdempotencyKey(@Param("idempotencyKey") String idempotencyKey);

    /** 查询会话的请求列表 */
    List<FollowupRequest> selectByConversationId(@Param("conversationId") String conversationId);

    /** 插入请求 */
    int insert(FollowupRequest request);

    /** 更新请求状态 */
    int updateStatus(@Param("requestId") String requestId,
                     @Param("requestStatus") String requestStatus,
                     @Param("answerStatus") String answerStatus);

    /** 更新回答内容 */
    int updateAnswer(@Param("requestId") String requestId,
                     @Param("answerContent") String answerContent,
                     @Param("answerStatus") String answerStatus);
}
