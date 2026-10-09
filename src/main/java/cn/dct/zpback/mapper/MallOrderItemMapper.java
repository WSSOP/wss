package cn.dct.zpback.mapper;

import cn.dct.zpback.entity.MallOrderItem;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.util.List;

/** 问星渡｜订单行 Mapper */
@Mapper
public interface MallOrderItemMapper {

    int insert(MallOrderItem item);

    List<MallOrderItem> listByOrder(@Param("orderId") String orderId);
}