package cn.dct.zpback.mapper;

import cn.dct.zpback.entity.Chart;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.util.List;

/**
 * 星盘 Mapper
 */
@Mapper
public interface ChartMapper {

    /** 根据ID查询 */
    Chart selectById(@Param("chartId") String chartId);

    /** 查询用户的所有星盘 */
    List<Chart> selectByUserId(@Param("userId") String userId);

    /** 查询用户的默认星盘 */
    Chart selectDefaultByUserId(@Param("userId") String userId);

    /** 插入星盘 */
    int insert(Chart chart);

    /** 更新星盘 */
    int update(Chart chart);

    /** 删除星盘 */
    int deleteById(@Param("chartId") String chartId);

    /** 设置默认星盘（先将其他星盘设为非默认，再设置指定星盘为默认） */
    int clearDefaultByUserId(@Param("userId") String userId);

    int setDefault(@Param("chartId") String chartId, @Param("userId") String userId);
}
