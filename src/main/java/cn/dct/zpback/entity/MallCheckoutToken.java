package cn.dct.zpback.entity;

import lombok.Data;

import java.io.Serializable;
import java.time.LocalDateTime;

/**
 * 结算 token。§4：必须由服务端签发，前端不得用商品 ID / 价格拼接。
 * 只承载「这组 SKU / 数量可结算」，<b>不含价格</b>——价格永远在提交时现读。
 */
@Data
public class MallCheckoutToken implements Serializable {

    private static final long serialVersionUID = 1L;

    private String token;
    private String userId;

    private String tradeGroup;
    private String groupKey;

    /** [ {sku_id, quantity} ] 的 JSON 字符串 */
    private String skuList;

    private LocalDateTime expiresAt;
    private LocalDateTime createdAt;
}