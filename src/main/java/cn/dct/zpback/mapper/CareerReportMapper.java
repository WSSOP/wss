package cn.dct.zpback.mapper;

import cn.dct.zpback.entity.career.CareerReportInstance;
import org.apache.ibatis.annotations.*;

import java.util.List;

/**
 * 事业深度报告 Mapper
 */
@Mapper
public interface CareerReportMapper {

    /** 插入报告实例 */
    @Insert("INSERT INTO career_report_instance (report_instance_id, report_type, report_status, " +
            "generated_at, user_id, title, subtitle, cover_summary, source_skill_version) " +
            "VALUES (#{reportInstanceId}, #{reportType}, #{reportStatus}, " +
            "#{generatedAt}, #{userId}, #{title}, #{subtitle}, #{coverSummary}, #{sourceSkillVersion})")
    int insertReportInstance(CareerReportInstance report);

    /** 更新报告状态 */
    @Update("UPDATE career_report_instance SET report_status = #{reportStatus}, " +
            "updated_at = NOW() WHERE report_instance_id = #{reportInstanceId}")
    int updateReportStatus(@Param("reportInstanceId") String reportInstanceId,
                           @Param("reportStatus") String reportStatus);

    /** 根据ID查询报告 */
    @Select("SELECT * FROM career_report_instance WHERE report_instance_id = #{reportInstanceId}")
    CareerReportInstance findById(@Param("reportInstanceId") String reportInstanceId);

    /** 查询用户报告列表 */
    @Select("SELECT * FROM career_report_instance WHERE user_id = #{userId} " +
            "ORDER BY generated_at DESC LIMIT #{limit} OFFSET #{offset}")
    List<CareerReportInstance> findByUserId(@Param("userId") String userId,
                                             @Param("offset") int offset,
                                             @Param("limit") int limit);

    /** 统计用户报告数量 */
    @Select("SELECT COUNT(*) FROM career_report_instance WHERE user_id = #{userId}")
    int countByUserId(@Param("userId") String userId);
}
