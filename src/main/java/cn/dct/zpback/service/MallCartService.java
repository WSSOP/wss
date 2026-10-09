package cn.dct.zpback.service;

import cn.dct.zpback.entity.MallCartItem;
import cn.dct.zpback.entity.PlanetaryJewelryProduct;
import cn.dct.zpback.entity.PlanetaryJewelrySku;
import cn.dct.zpback.exception.LearningValidationException;
import cn.dct.zpback.mapper.MallCartMapper;
import cn.dct.zpback.mapper.PlanetaryJewelryProductMapper;
import cn.dct.zpback.mapper.PlanetaryJewelrySkuMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/**
 * 问星渡｜商城购物车
 *
 * 契约来源：《【我的】页面开发文档》§7（购物车 /mall/cart）、§12.1（数量口径）。
 *
 * <b>三条硬规则</b>：
 * <ol>
 *   <li><b>价格永远从 SKU 现读</b>（§16.3 资金与价格以服务端为准）。
 *       购物车不存价格快照——用户可能停留几天才下单，
 *       快照必然导致「购物车 99、结算 109」，而前端无从解释。</li>
 *   <li><b>计数口径 ≠ 结算口径</b>（§12.1）。
 *       角标数的是「有效可结算行」，但列表要显示全部行。
 *       两者刻意分离，理由见 {@link MallCartMapper#countSettleableRows}。</li>
 *   <li><b>不物理删除</b>，软删保留历史引用，与商品 ARCHIVED 终态一致。</li>
 * </ol>
 *
 * <p>本服务只管购物车，<b>不创建订单</b>。
 * 商城下单属支付域，与课程的 LearningOrder 是不同交易对象，
 * 强行复用会把两套状态机搅在一起。
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class MallCartService {

    private final MallCartMapper cartMapper;
    private final PlanetaryJewelryProductMapper productMapper;
    private final PlanetaryJewelrySkuMapper skuMapper;

    /** 单行数量上限；防止误触连点把数量加到几千 */
    private static final int MAX_QUANTITY_PER_LINE = 99;

    /**
     * §12.1 购物车数量，供【我的】一级页聚合与购物车角标使用。
     *
     * <p>未登录返回 0 而不是报错——§8.1 要求未登录时一级页入口保持可见，
     * 此时没有购物车是正常状态，不该弹错误。
     */
    public int countSettleableRows(String userId) {
        if (userId == null || userId.isBlank()) return 0;
        try {
            return cartMapper.countSettleableRows(userId);
        } catch (RuntimeException ex) {
            // 单个摘要失败不阻塞整页（§19.5）
            log.warn("[商城] 购物车计数失败：{}", ex.getMessage());
            return 0;
        }
    }

    /**
     * 购物车列表。
     *
     * <p>返回<b>不可结算</b>的行（缺货/停售/商品归档），
     * 但用 available=false 标出，由前端置灰。
     * 直接滤掉会让用户以为购物车丢了东西。
     */
    public Map<String, Object> list(String userId) {
        requireLogin(userId);
        List<MallCartItem> rows = cartMapper.findActiveByUser(userId);
        if (rows.isEmpty()) {
            return Map.of("items", List.of(), "totalRows", 0,
                    "selectedRows", 0, "totalAmount", BigDecimal.ZERO);
        }

        List<String> skuIds = new ArrayList<>();
        Set<String> productIds = new HashSet<>();
        for (MallCartItem r : rows) {
            skuIds.add(r.getSkuId());
            productIds.add(r.getProductId());
        }

        Map<String, PlanetaryJewelrySku> skuMap = indexBySku(skuIds);
        Map<String, PlanetaryJewelryProduct> prodMap = indexByProduct(productIds);

        List<Map<String, Object>> items = new ArrayList<>();
        BigDecimal total = BigDecimal.ZERO;
        int selectedRows = 0;

        for (MallCartItem r : rows) {
            PlanetaryJewelrySku sku = skuMap.get(r.getSkuId());
            PlanetaryJewelryProduct prod = prodMap.get(r.getProductId());

            // 商品不存在只可能是被物理删了；
            // 正常下架走 publish_status='ARCHIVED'，商品行仍在。
            boolean productLive = prod != null && "PUBLISHED".equals(prod.getPublishStatus());
            boolean skuLive = sku != null && "ACTIVE".equals(sku.getStatus());
            boolean inStock = skuLive && sku.getInventoryQuantity() != null
                    && sku.getInventoryQuantity() > 0;

            BigDecimal price = sku == null ? null : sku.getPrice();
            BigDecimal amount = price == null ? null
                    : price.multiply(BigDecimal.valueOf(r.getQuantity()));

            // 只有「商品在售 + SKU 在售 + 勾选」才计入合计。
            // 缺货行不计入，否则用户会看到一个算不出账的总额。
            boolean selected = Boolean.TRUE.equals(r.getSelected());
            boolean sellable = productLive && skuLive && inStock;
            boolean countable = selected && sellable;

            if (countable) {
                total = total.add(amount);
                selectedRows++;
            }

            Map<String, Object> item = new LinkedHashMap<>();
            item.put("cart_item_id", r.getCartItemId());
            item.put("sku_id", r.getSkuId());
            item.put("product_id", r.getProductId());
            item.put("title", prod == null ? "商品已下架" : prod.getTitle());
            item.put("subtitle", prod == null ? null : prod.getSubtitle());
            item.put("image", firstImage(prod));
            item.put("variant_attributes", sku == null ? null : sku.getVariantAttributes());
            item.put("price", price);
            item.put("quantity", r.getQuantity());
            item.put("amount", amount);
            item.put("selected", selected);
            item.put("available", sellable);
            item.put("unavailable_reason", unavailableReason(productLive, skuLive, inStock));
            items.add(item);
        }

        Map<String, Object> out = new LinkedHashMap<>();
        out.put("items", items);
        out.put("totalRows", rows.size());
        out.put("selectedRows", selectedRows);
        out.put("totalAmount", total);
        return out;
    }

    /**
     * 加购。
     *
     * <p>下单前校验 SKU 可售与商品在售，但<b>不因库存不足而拒绝</b>：
     * 加购时商品可能正被抢购，直接失败会让人空手离开。
     * 允许加购、把「库存不足」交给结算环节报错，体验更顺。
     * 真正要拦的是「SKU 不存在/已停售」——那说明商品已经没了。
     */
    public MallCartItem add(String userId, String skuId, int quantity) {
        requireLogin(userId);
        if (skuId == null || skuId.isBlank()) {
            throw new LearningValidationException("MALL_SKU_REQUIRED", "请选择商品规格");
        }
        if (quantity <= 0) {
            throw new LearningValidationException("MALL_QTY_INVALID", "购买数量需大于 0");
        }
        if (quantity > MAX_QUANTITY_PER_LINE) {
            throw new LearningValidationException("MALL_QTY_TOO_LARGE",
                    "单次最多购买 " + MAX_QUANTITY_PER_LINE + " 件");
        }

        PlanetaryJewelrySku sku = skuMapper.findById(skuId);
        if (sku == null || !"ACTIVE".equals(sku.getStatus())) {
            throw new LearningValidationException("MALL_SKU_UNAVAILABLE", "该规格已停售");
        }
        PlanetaryJewelryProduct prod = productMapper.findById(sku.getProductId());
        if (prod == null || !"PUBLISHED".equals(prod.getPublishStatus())) {
            throw new LearningValidationException("MALL_PRODUCT_UNAVAILABLE", "该商品已下架");
        }

        MallCartItem row = new MallCartItem();
        row.setCartItemId("ci_" + UUID.randomUUID().toString().replace("-", "").substring(0, 16));
        row.setUserId(userId);
        row.setSkuId(skuId);
        row.setProductId(sku.getProductId());
        row.setQuantity(quantity);
        row.setSelected(true);
        row.setItemStatus("ACTIVE");
        cartMapper.upsertIncrement(row);
        return row;
    }

    /** 改数量。§16.3：userId + skuId 双重条件，防越权改他人购物车 */
    public void updateQuantity(String userId, String skuId, int quantity) {
        requireLogin(userId);
        if (quantity <= 0) {
            throw new LearningValidationException("MALL_QTY_INVALID", "购买数量需大于 0");
        }
        if (quantity > MAX_QUANTITY_PER_LINE) {
            throw new LearningValidationException("MALL_QTY_TOO_LARGE",
                    "最多 " + MAX_QUANTITY_PER_LINE + " 件");
        }
        assertOwned(userId, skuId, "MALL_ITEM_NOT_FOUND", "购物车里没有这件商品");
        cartMapper.updateQuantity(userId, skuId, quantity);
    }

    /** 勾选 / 取消 */
    public void setSelected(String userId, String skuId, boolean selected) {
        requireLogin(userId);
        assertOwned(userId, skuId, "MALL_ITEM_NOT_FOUND", "购物车里没有这件商品");
        cartMapper.updateSelected(userId, skuId, selected);
    }

    /** 全选 / 全不选 */
    public void setSelectedAll(String userId, boolean selected) {
        requireLogin(userId);
        cartMapper.updateSelectedAll(userId, selected);
    }

    /** 软删。§16.3 所有权校验同 updateQuantity */
    public void remove(String userId, String skuId) {
        requireLogin(userId);
        assertOwned(userId, skuId, "MALL_ITEM_NOT_FOUND", "购物车里没有这件商品");
        cartMapper.softRemove(userId, skuId);
    }

    // ==============================================================
    // 私有
    // ==============================================================

    private void requireLogin(String userId) {
        if (userId == null || userId.isBlank() || "anonymous".equals(userId)) {
            throw new LearningValidationException("MALL_LOGIN_REQUIRED", "请先登录");
        }
    }

    private void assertOwned(String userId, String skuId, String code, String msg) {
        MallCartItem row = cartMapper.findByUserAndSku(userId, skuId);
        if (row == null) {
            // 不回显「该商品属于他人」，避免泄露他人购物车内容（§16.3）
            throw new LearningValidationException(code, msg);
        }
    }

    private Map<String, PlanetaryJewelrySku> indexBySku(List<String> skuIds) {
        Map<String, PlanetaryJewelrySku> m = new LinkedHashMap<>();
        for (PlanetaryJewelrySku s : skuMapper.findByIds(skuIds)) {
            m.put(s.getSkuId(), s);
        }
        return m;
    }

    private Map<String, PlanetaryJewelryProduct> indexByProduct(Set<String> productIds) {
        Map<String, PlanetaryJewelryProduct> m = new LinkedHashMap<>();
        if (productIds.isEmpty()) return m;
        for (PlanetaryJewelryProduct p : productMapper.browse(null, null)) {
            if (productIds.contains(p.getProductId())) {
                m.put(p.getProductId(), p);
            }
        }
        return m;
    }

    /** 缺货 / 停售 / 下架的可读原因，前端据此显示行内文案 */
    private String unavailableReason(boolean productLive, boolean skuLive, boolean inStock) {
        if (!productLive) return "商品已下架";
        if (!skuLive) return "该规格已停售";
        if (!inStock) return "暂时缺货，可到货提醒我";
        return null;
    }

    /** images 是 JSON 数组，取第一张；解析失败返回 null 而不是抛异常 */
    private String firstImage(PlanetaryJewelryProduct p) {
        if (p == null || p.getImages() == null || p.getImages().isBlank()) return null;
        try {
            String s = p.getImages().trim();
            int start = s.indexOf('"');
            int end = s.indexOf('"', start + 1);
            return (start >= 0 && end > start) ? s.substring(start + 1, end) : null;
        } catch (RuntimeException ex) {
            return null;
        }
    }
}