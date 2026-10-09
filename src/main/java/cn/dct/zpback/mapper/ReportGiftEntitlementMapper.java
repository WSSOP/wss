package cn.dct.zpback.mapper;

import cn.dct.zpback.entity.ReportGiftEntitlement;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

/**
 * 报告赠送追问权益 Mapper
 */
@Mapper
public interface ReportGiftEntitlementMapper {

    /** 根据报告ID查询 */
    ReportGiftEntitlement selectByReportId(@Param("reportInstanceId") String reportInstanceId);

    /** 插入权益 */
    int insert(ReportGiftEntitlement entitlement);

    /** 标记为已使用 */
    int markUsed(@Param("id") Long id, @Param("requestId") String requestId);
}
