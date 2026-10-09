package cn.dct.zpback.mapper;

import cn.dct.zpback.entity.MallCheckoutToken;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

/** 问星渡｜结算 token Mapper */
@Mapper
public interface MallCheckoutTokenMapper {

    int insert(MallCheckoutToken token);

    /** 取未过期的 token；过期返回 null */
    MallCheckoutToken findValid(@Param("token") String token);
}