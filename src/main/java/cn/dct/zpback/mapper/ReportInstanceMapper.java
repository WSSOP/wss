package cn.dct.zpback.mapper;

import cn.dct.zpback.entity.ReportInstance;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.util.List;

/**
 * 报告实例 Mapper
 */
@Mapper
public interface ReportInstanceMapper {

    /** 根据ID查询 */
    ReportInstance selectById(@Param("reportInstanceId") String reportInstanceId);

    /** 查询用户可追问的报告列表（按生成时间倒序） */
    List<ReportInstance> selectFollowupAvailable(@Param("userId") String userId);

    /** 查询用户的报告列表（分页） */
    List<ReportInstance> selectByUserId(@Param("userId") String userId,
                                         @Param("offset") int offset,
                                         @Param("limit") int limit);

    /** 插入报告实例 */
    int insert(ReportInstance report);

    /** 更新报告实例 */
    int update(ReportInstance report);
}
