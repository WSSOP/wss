package cn.dct.zpback.mapper;

import cn.dct.zpback.entity.MallCartItem;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.util.List;

/**
 * 问星渡｜商城购物车 Mapper
 *
 * 契约来源：《【我的】页面开发文档》§7、§12.1。
 */
@Mapper
public interface MallCartMapper {

    /**
     * §12.1 购物车数量：<b>有效可结算 SKU 行数</b>。
     *
     * <p>口径 = item_status=ACTIVE 且 selected=1 且 quantity>0。
     *
     * <p><b>刻意不判断库存与上架状态</b>：
     * 缺货或下架的行仍要让用户在购物车里看见并自己取消。
     * 若把它们从计数里剔掉，用户会遇到「角标显示 3，点进去只剩 1 行」，
     * 以为是数据丢了。真正结算时（§16.3 服务端鉴权）才校验并报错。
     * 这是<b>计数口径</b>与<b>结算口径</b>刻意分离，不是遗漏。
     *
     * <p>走 idx_mall_cart_user_status 索引，不回表。
     */
    int countSettleableRows(@Param("userId") String userId);

    /** 购物车列表（含未勾选行），供购物车页渲染 */
    List<MallCartItem> findActiveByUser(@Param("userId") String userId);

    /** 单行查询；所有写操作按 userId+skuId 双重校验所有权（§16.3） */
    MallCartItem findByUserAndSku(@Param("userId") String userId,
                                  @Param("skuId") String skuId);

    /**
     * 加购：同一 user+sku 累加数量。
     *
     * <p>依赖 uk_mall_cart_user_sku 唯一键，
     * 所以「重复加购只有一行」由数据库保证，
     * 而不是靠应用层先查再判断——并发加购时先查后插会出现两行。
     */
    int upsertIncrement(MallCartItem item);

    /** 改数量 */
    int updateQuantity(@Param("userId") String userId,
                       @Param("skuId") String skuId,
                       @Param("quantity") int quantity);

    /** 勾选 / 取消勾选 */
    int updateSelected(@Param("userId") String userId,
                       @Param("skuId") String skuId,
                       @Param("selected") boolean selected);

    /** 全选 / 全不选 */
    int updateSelectedAll(@Param("userId") String userId,
                          @Param("selected") boolean selected);

    /** 软删：item_status -> REMOVED，不物理删除 */
    int softRemove(@Param("userId") String userId, @Param("skuId") String skuId);

    /** 结算成功后软删已勾选行 */
    int softRemoveSelected(@Param("userId") String userId);

    /** 购物车内行数（含未勾选），用于购物车页判断是否为空 */
    int countActiveRows(@Param("userId") String userId);
}