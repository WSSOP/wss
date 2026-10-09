package cn.dct.zpback.mapper;

import cn.dct.zpback.entity.LearningCourse;
import org.apache.ibatis.annotations.*;

import java.util.List;

/**
 * 问星渡｜课程 Mapper
 *
 * 规格来源：原始 skill 无字段定义，查询条件为实现方拟定。
 *
 * 前台只暴露 publish_status='PUBLISHED'；
 * ARCHIVED 为终态但<b>不删除</b>，历史订单与学习记录必须能查到。
 */
@Mapper
public interface LearningCourseMapper {

    String COLUMNS = "course_id, title, subtitle, description, cover_url, category, level, "
            + "course_status, teacher_id, assistant_teacher_id, session_count, total_minutes, "
            + "lesson_count, outline, tags, created_at, updated_at";

    /** 已发布课程列表。category / level 可空；按最新在前。 */
    @Select("<script>"
            + "SELECT " + COLUMNS + " FROM learning_course WHERE course_status = 'PUBLISHED' "
            + " <if test='category != null'> AND category = #{category} </if>"
            + " <if test='level != null'> AND level = #{level} </if>"
            + " ORDER BY created_at DESC, course_id"
            + "</script>")
    List<LearningCourse> findPublished(@Param("category") String category,
                                       @Param("level") String level);

    /**
     * 按 ID 取课程，<b>不过滤状态</b>。
     * 详情接口需要据此区分：不存在 / 已下架 / 已归档，给用户不同文案。
     */
    @Select("SELECT " + COLUMNS + " FROM learning_course WHERE course_id = #{courseId}")
    LearningCourse findById(@Param("courseId") String courseId);

    /**
     * 讲师名下的课程。
     *
     * 同时覆盖主讲（teacher_id）与助教（assistant_teacher_id），
     * 对应文档「Teacher：被分配课程和班期」与
     * 「Assistant Teacher：辅助学习运营」。
     *
     * 含未发布状态：讲师需要看到自己筹备中的课，
     * 否则新讲师建完课在「我的课程」里找不到。
     */
    @Select("SELECT " + COLUMNS + " FROM learning_course "
            + "WHERE teacher_id = #{userId} OR assistant_teacher_id = #{userId} "
            + "ORDER BY course_status, created_at DESC")
    List<LearningCourse> findByTeacher(@Param("userId") String userId);

    @Insert("INSERT INTO learning_course (course_id, title, subtitle, description, cover_url, "
            + "category, level, course_status, teacher_id, session_count, total_minutes, "
            + "lesson_count, outline, tags) VALUES "
            + "(#{courseId}, #{title}, #{subtitle}, #{description}, #{coverUrl}, "
            + "#{category}, #{level}, #{courseStatus}, #{teacherId}, #{sessionCount}, "
            + "#{totalMinutes}, #{lessonCount}, #{outline}, #{tags})")
    int insert(LearningCourse course);
}
