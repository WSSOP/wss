package cn.dct.zpback.mapper;

import cn.dct.zpback.entity.MallAddress;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.util.List;

/** 问星渡｜商城收货地址 Mapper。增删查都按 userId 校验所有权。 */
@Mapper
public interface MallAddressMapper {

    int insert(MallAddress address);

    List<MallAddress> listByUser(@Param("userId") String userId);

    /**
     * 按 userId + addressId 双重校验：只能查到自己的地址（§16.3 所有权）。
     * 与 {@link #deleteByUserAndId} 同口径，越权访问自然查不到。
     */
    MallAddress findById(@Param("userId") String userId, @Param("addressId") String addressId);

    int deleteByUserAndId(@Param("userId") String userId, @Param("addressId") String addressId);

    /** 设默认：先清掉该用户旧的默认，再置新的一条 */
    int clearDefault(@Param("userId") String userId);

    int setDefault(@Param("userId") String userId, @Param("addressId") String addressId);
}