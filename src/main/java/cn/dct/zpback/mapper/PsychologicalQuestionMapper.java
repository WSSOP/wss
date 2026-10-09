package cn.dct.zpback.mapper;

import cn.dct.zpback.entity.PsychologicalQuestion;
import org.apache.ibatis.annotations.*;

import java.util.List;

/**
 * 心理测评中心｜题目 Mapper
 */
@Mapper
public interface PsychologicalQuestionMapper {

    String COLUMNS = "question_id, assessment_id, text, order_index, reverse_score, created_at";

    @Select("SELECT " + COLUMNS + " FROM psychological_question "
            + " WHERE assessment_id = #{assessmentId} ORDER BY order_index ASC")
    List<PsychologicalQuestion> findByAssessmentId(@Param("assessmentId") String assessmentId);

    @Select("SELECT " + COLUMNS + " FROM psychological_question WHERE question_id = #{id}")
    PsychologicalQuestion findById(@Param("id") String id);

    @Insert("INSERT INTO psychological_question (question_id, assessment_id, text, order_index, reverse_score) "
            + "VALUES (#{questionId}, #{assessmentId}, #{text}, #{orderIndex}, #{reverseScore})")
    int insert(PsychologicalQuestion question);
}
