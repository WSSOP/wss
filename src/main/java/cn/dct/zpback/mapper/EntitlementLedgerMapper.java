package cn.dct.zpback.mapper;

import cn.dct.zpback.entity.EntitlementLedger;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.util.List;

/**
 * 权益流水 Mapper
 */
@Mapper
public interface EntitlementLedgerMapper {

    /** 根据ID查询 */
    EntitlementLedger selectById(@Param("id") Long id);

    /** 根据请求ID查询流水列表 */
    List<EntitlementLedger> selectByRequestId(@Param("requestId") String requestId);

    /** 插入流水 */
    int insert(EntitlementLedger ledger);
}
