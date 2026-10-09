package cn.dct.zpback.entity;

import lombok.Data;

import java.io.Serializable;
import java.time.LocalDateTime;

/**
 * 问星渡｜商城购物车行
 *
 * 契约来源：《【我的】页面开发文档》§7、§12.1。
 *
 * <b>不含商品标题与价格</b>——价格永远从 SKU 现读。
 * 存快照价格必然出现「购物车显示 99、结算变 109」，
 * 因为用户可能在购物车里停留好几天才下单。
 */
@Data
public class MallCartItem implements Serializable {

    private static final long serialVersionUID = 1L;

    private String cartItemId;
    private String userId;
    private String skuId;

    /** 冗余快照：商品归档后仍能显示占位，而不是变成空白行 */
    private String productId;

    private Integer quantity;
    private Boolean selected;

    /** ACTIVE / REMOVED；软删，不物理删除 */
    private String itemStatus;

    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
}