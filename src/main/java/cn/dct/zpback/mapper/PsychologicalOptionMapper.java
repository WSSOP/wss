package cn.dct.zpback.mapper;

import cn.dct.zpback.entity.PsychologicalOption;
import org.apache.ibatis.annotations.*;

import java.util.List;

/**
 * 心理测评中心｜选项 Mapper
 */
@Mapper
public interface PsychologicalOptionMapper {

    String COLUMNS = "option_id, question_id, text, score, order_index, created_at";

    @Select("SELECT " + COLUMNS + " FROM psychological_option "
            + " WHERE question_id = #{questionId} ORDER BY order_index ASC")
    List<PsychologicalOption> findByQuestionId(@Param("questionId") String questionId);

    @Select("SELECT " + COLUMNS + " FROM psychological_option WHERE option_id = #{id}")
    PsychologicalOption findById(@Param("id") String id);

    @Insert("INSERT INTO psychological_option (option_id, question_id, text, score, order_index) "
            + "VALUES (#{optionId}, #{questionId}, #{text}, #{score}, #{orderIndex})")
    int insert(PsychologicalOption option);
}
