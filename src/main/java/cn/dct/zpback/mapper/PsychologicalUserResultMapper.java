package cn.dct.zpback.mapper;

import cn.dct.zpback.entity.PsychologicalUserResult;
import org.apache.ibatis.annotations.*;

import java.util.List;

/**
 * 心理测评中心｜用户结果 Mapper
 */
@Mapper
public interface PsychologicalUserResultMapper {

    String COLUMNS = "result_id, user_id, assessment_id, total_score, level, level_label, "
            + " level_color, advice_text, finish_reason, started_at, finished_at, created_at, updated_at";

    @Select("SELECT " + COLUMNS + " FROM psychological_user_result "
            + " WHERE user_id = #{userId} ORDER BY finished_at DESC, created_at DESC")
    List<PsychologicalUserResult> findByUser(@Param("userId") String userId);

    @Select("SELECT " + COLUMNS + " FROM psychological_user_result "
            + " WHERE user_id = #{userId} AND assessment_id = #{assessmentId} LIMIT 1")
    PsychologicalUserResult findLatestByAssessment(@Param("userId") String userId,
                                                   @Param("assessmentId") String assessmentId);

    @Select("SELECT " + COLUMNS + " FROM psychological_user_result WHERE result_id = #{resultId}")
    PsychologicalUserResult findById(@Param("resultId") String resultId);

    @Insert("INSERT INTO psychological_user_result "
            + "(result_id, user_id, assessment_id, total_score, level, level_label, "
            + " level_color, advice_text, finish_reason, started_at, finished_at) "
            + "VALUES (#{resultId}, #{userId}, #{assessmentId}, #{totalScore}, #{level}, "
            + " #{levelLabel}, #{levelColor}, #{adviceText}, #{finishReason}, #{startedAt}, #{finishedAt})")
    int insert(PsychologicalUserResult result);

    @Update("UPDATE psychological_user_result SET finished_at = #{finishedAt}, "
            + " updated_at = CURRENT_TIMESTAMP WHERE result_id = #{resultId}")
    int updateFinishedAt(@Param("resultId") String resultId, @Param("finishedAt") java.time.LocalDateTime finishedAt);

    @Update("UPDATE psychological_user_result SET "
            + " total_score = #{totalScore}, level = #{level}, level_label = #{levelLabel}, "
            + " level_color = #{levelColor}, advice_text = #{adviceText}, "
            + " finish_reason = #{finishReason}, finished_at = #{finishedAt}, "
            + " updated_at = CURRENT_TIMESTAMP WHERE result_id = #{resultId}")
    int updateResult(@Param("resultId") String resultId,
                     @Param("totalScore") Integer totalScore,
                     @Param("level") String level,
                     @Param("levelLabel") String levelLabel,
                     @Param("levelColor") String levelColor,
                     @Param("adviceText") String adviceText,
                     @Param("finishReason") String finishReason,
                     @Param("finishedAt") java.time.LocalDateTime finishedAt);
}
