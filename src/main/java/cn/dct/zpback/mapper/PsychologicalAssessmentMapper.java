package cn.dct.zpback.mapper;

import cn.dct.zpback.entity.PsychologicalAssessment;
import org.apache.ibatis.annotations.*;

import java.util.List;

/**
 * 心理测评中心｜测评目录 Mapper
 */
@Mapper
public interface PsychologicalAssessmentMapper {

    String COLUMNS = "assessment_id, slug, title, subtitle, category, icon, color, "
            + " question_count, estimated_minutes, min_score, max_score, description, "
            + " disclaimer, publish_status, sort_order, created_at, updated_at";

    @Select("SELECT " + COLUMNS + " FROM psychological_assessment "
            + " WHERE publish_status = 'PUBLISHED' ORDER BY sort_order ASC, assessment_id")
    List<PsychologicalAssessment> findAllPublished();

    @Select("SELECT " + COLUMNS + " FROM psychological_assessment "
            + " WHERE publish_status = 'PUBLISHED' AND category = #{category} "
            + " ORDER BY sort_order ASC, assessment_id")
    List<PsychologicalAssessment> findByCategory(@Param("category") String category);

    @Select("SELECT " + COLUMNS + " FROM psychological_assessment "
            + " WHERE slug = #{slug} AND publish_status = 'PUBLISHED'")
    PsychologicalAssessment findBySlug(@Param("slug") String slug);

    @Select("SELECT " + COLUMNS + " FROM psychological_assessment WHERE assessment_id = #{id}")
    PsychologicalAssessment findById(@Param("id") String id);

    @Select("SELECT COUNT(*) FROM psychological_assessment WHERE publish_status = 'PUBLISHED'")
    int countPublished();

    @Insert("INSERT INTO psychological_assessment "
            + "(assessment_id, slug, title, subtitle, category, icon, color, "
            + " question_count, estimated_minutes, min_score, max_score, description, "
            + " disclaimer, publish_status, sort_order) "
            + "VALUES (#{assessmentId}, #{slug}, #{title}, #{subtitle}, #{category}, #{icon}, #{color}, "
            + " #{questionCount}, #{estimatedMinutes}, #{minScore}, #{maxScore}, #{description}, "
            + " #{disclaimer}, #{publishStatus}, #{sortOrder})")
    int insert(PsychologicalAssessment assessment);

    @Update("UPDATE psychological_assessment SET "
            + " title = #{title}, subtitle = #{subtitle}, icon = #{icon}, color = #{color}, "
            + " question_count = #{questionCount}, estimated_minutes = #{estimatedMinutes}, "
            + " description = #{description}, disclaimer = #{disclaimer}, sort_order = #{sortOrder}, "
            + " updated_at = CURRENT_TIMESTAMP WHERE assessment_id = #{assessmentId}")
    int update(PsychologicalAssessment assessment);

    @Update("UPDATE psychological_assessment SET publish_status = #{status}, updated_at = CURRENT_TIMESTAMP "
            + " WHERE assessment_id = #{id}")
    int updatePublishStatus(@Param("id") String id, @Param("status") String status);
}
