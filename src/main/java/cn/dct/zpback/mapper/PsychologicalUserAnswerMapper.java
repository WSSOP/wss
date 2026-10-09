package cn.dct.zpback.mapper;

import cn.dct.zpback.entity.PsychologicalUserAnswer;
import org.apache.ibatis.annotations.*;

import java.util.List;

/**
 * 心理测评中心｜用户答题 Mapper
 */
@Mapper
public interface PsychologicalUserAnswerMapper {

    String COLUMNS = "answer_id, result_id, question_id, option_id, score, created_at";

    @Select("SELECT " + COLUMNS + " FROM psychological_user_answer WHERE result_id = #{resultId}")
    List<PsychologicalUserAnswer> findByResultId(@Param("resultId") String resultId);

    @Select("SELECT " + COLUMNS + " FROM psychological_user_answer WHERE answer_id = #{id}")
    PsychologicalUserAnswer findById(@Param("id") String id);

    @Select("SELECT COUNT(*) FROM psychological_user_answer WHERE result_id = #{resultId}")
    int countByResultId(@Param("resultId") String resultId);

    @Insert("INSERT INTO psychological_user_answer "
            + "(answer_id, result_id, question_id, option_id, score) "
            + "VALUES (#{answerId}, #{resultId}, #{questionId}, #{optionId}, #{score})")
    int insert(PsychologicalUserAnswer answer);
}
