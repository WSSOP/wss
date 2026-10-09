package cn.dct.zpback.mapper;

import cn.dct.zpback.entity.MallCustomRequest;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.util.List;

/** 问星渡｜定制需求单 Mapper */
@Mapper
public interface MallCustomRequestMapper {

    int insert(MallCustomRequest request);

    List<MallCustomRequest> listByUser(@Param("userId") String userId);
}