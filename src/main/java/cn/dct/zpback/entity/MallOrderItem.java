package cn.dct.zpback.entity;

import lombok.Data;

import java.io.Serializable;
import java.math.BigDecimal;

/** 订单行：SKU 快照。运营改价 / 下架 / 改名不影响历史订单展示。 */
@Data
public class MallOrderItem implements Serializable {

    private static final long serialVersionUID = 1L;

    private String itemId;
    private String orderId;
    private String skuId;
    private String productId;

    private String productTitle;
    private String skuVariant;

    /** STANDARD / PREORDER */
    private String tradeGroup;
    /** 预售批次；STANDARD 恒 null */
    private String batch;

    private BigDecimal price;
    private Integer quantity;
    private BigDecimal subtotal;
}