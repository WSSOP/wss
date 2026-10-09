package cn.dct.zpback.service;

import cn.dct.zpback.entity.MallAddress;
import cn.dct.zpback.entity.MallCartItem;
import cn.dct.zpback.entity.MallCheckoutToken;
import cn.dct.zpback.entity.MallCustomRequest;
import cn.dct.zpback.entity.MallOrder;
import cn.dct.zpback.entity.MallOrderItem;
import cn.dct.zpback.entity.PlanetaryJewelryProduct;
import cn.dct.zpback.entity.PlanetaryJewelrySku;
import cn.dct.zpback.exception.LearningValidationException;
import cn.dct.zpback.mapper.MallAddressMapper;
import cn.dct.zpback.mapper.MallCartMapper;
import cn.dct.zpback.mapper.MallCheckoutTokenMapper;
import cn.dct.zpback.mapper.MallCustomRequestMapper;
import cn.dct.zpback.mapper.MallOrderItemMapper;
import cn.dct.zpback.mapper.MallOrderMapper;
import cn.dct.zpback.mapper.PlanetaryJewelryProductMapper;
import cn.dct.zpback.mapper.PlanetaryJewelrySkuMapper;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/**
 * 问星渡｜商城订单 / 地址 / 定制需求 / 结算
 *
 * 契约来源：《问星渡_商城HTML原型_程序员对照说明_V1》§2 锁定规则。
 *
 * <b>本类守住四条最容易做错的硬规则</b>：
 * <ol>
 *   <li><b>现货与预售不可混结、不同预售批次不可混结（§2.6）</b>。
 *       用 {@link #groupKeyOf} 把每个 SKU 归到一个结算分组，
 *       一单只允许一个分组，跨组直接拒绝。前端绕过也拦得住，因为校验在后端。</li>
 *   <li><b>提交订单才锁库存 15 分钟（§2.7）</b>。
 *       签发结算 token、打开结算页<b>一律不锁</b>；只有 submitOrder 才条件扣减。</li>
 *   <li><b>价格永远现读，订单存快照（§4）</b>。
 *       token 里<b>没有价格</b>，只在 submit 时从 SKU 读当前价再冻结进订单行。
 *       否则前端改个金额就能 1 块钱买下所有东西。</li>
 *   <li><b>支付成功以服务端状态为准（§4）</b>。
 *       前端结果页只读本接口，不自行改订单 / 库存事实。</li>
 * </ol>
 *
 * <p>定制商品<b>不</b>走这里下单（§2.9）：{@link #submitCustomRequest} 只生成
 * request_id，不产生订单、待付款、锁库存或应收金额。
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class MallOrderService {

    /** 现货锁库存时长（§2.7） */
    private static final int STOCK_LOCK_MINUTES = 15;
    /** 结算 token 有效期：比库存锁略长，留出「锁已释放但页面还在」的窗口用于提示 */
    private static final int TOKEN_TTL_MINUTES = 30;

    private static final String G_STANDARD = "STANDARD";
    private static final String G_PREORDER = "PREORDER";
    private static final String PREORDER_PREFIX = "PREORDER:";

    private final MallOrderMapper orderMapper;
    private final MallOrderItemMapper orderItemMapper;
    private final MallAddressMapper addressMapper;
    private final MallCustomRequestMapper customRequestMapper;
    private final MallCheckoutTokenMapper checkoutTokenMapper;
    private final PlanetaryJewelrySkuMapper skuMapper;
    private final PlanetaryJewelryProductMapper productMapper;
    private final MallCartMapper cartMapper;

    private final ObjectMapper json = new ObjectMapper();

    // ==============================================================
    // 结算分组
    // ==============================================================

    /**
     * 判定单个 SKU 属于哪个结算分组。
     *
     * <p>真实商品表没有「预售批次」字段，也没有交易类型字段。
     * 这里按可观测的库存事实推导：
     * <ul>
     *   <li>有货 → {@code STANDARD}（现货）</li>
     *   <li>缺货但允许登记到货 → {@code PREORDER:&lt;skuId&gt;}（全款预售）</li>
     *   <li>缺货且不允许预订 → 不可结算，直接拒绝</li>
     * </ul>
     *
     * <p><b>为什么预售批次用 skuId 当键</b>：§2.6 要求不同预售批次不可混结。
     * 既然真实数据没有批次概念，退而求其次把每个预售 SKU 当成独立批次——
     * 这样「不同预售混结」被<b>最严格地</b>禁止，符合规则方向；
     * 等运营真有了批次字段，只需替换本方法返回的 key，调用方无感。
     */
    private String groupKeyOf(PlanetaryJewelrySku sku) {
        int qty = sku.getInventoryQuantity() == null ? 0 : sku.getInventoryQuantity();
        if (qty > 0) {
            return G_STANDARD;
        }
        if (Boolean.TRUE.equals(sku.getRestockWaitlistEnabled())) {
            return PREORDER_PREFIX + sku.getSkuId();
        }
        throw new LearningValidationException("MALL_SKU_UNAVAILABLE",
                "该商品暂时缺货，且不支持预订");
    }

    private String tradeGroupOf(String groupKey) {
        return groupKey.startsWith(PREORDER_PREFIX) ? G_PREORDER : G_STANDARD;
    }

    // ==============================================================
    // 签发结算 token（§4：必须服务端签发）
    // ==============================================================

    /**
     * 签发结算 token。
     *
     * <p><b>此刻不锁库存</b>（§2.7）：加购、打开结算都不预留，
     * 否则用户逛十分钟不付款就把库存占死了。
     *
     * <p>token 只记录「这组 SKU / 数量 / 分组可以结算」，<b>不记录价格</b>。
     * 金额在 submitOrder 时现读并冻结。
     */
    @Transactional
    public Map<String, Object> issueCheckoutToken(String userId, List<Map<String, Object>> skus) {
        requireLogin(userId);
        if (skus == null || skus.isEmpty()) {
            throw new LearningValidationException("MALL_CART_EMPTY", "请先选择要结算的商品");
        }

        List<Resolved> resolved = resolveSkus(skus);
        String groupKey = resolved.get(0).groupKey;
        for (Resolved r : resolved) {
            if (!groupKey.equals(r.groupKey)) {
                // §2.6 的核心拦截点。给出可执行的下一步，而不是干巴巴一句「不能混结」
                throw new LearningValidationException("MALL_MIXED_GROUP",
                        "现货与预售商品不能一起结算，不同预售批次也不能一起结算，请分开结算");
            }
        }

        String token = UUID.randomUUID().toString().replace("-", "");
        MallCheckoutToken t = new MallCheckoutToken();
        t.setToken(token);
        t.setUserId(userId);
        t.setTradeGroup(tradeGroupOf(groupKey));
        t.setGroupKey(groupKey);
        t.setSkuList(writeJson(skuListPayload(resolved)));
        t.setExpiresAt(LocalDateTime.now().plusMinutes(TOKEN_TTL_MINUTES));
        checkoutTokenMapper.insert(t);

        Map<String, Object> out = new LinkedHashMap<>();
        out.put("checkout_token", token);
        out.put("trade_group", tradeGroupOf(groupKey));
        out.put("group_key", groupKey);
        out.put("goods_amount", sumGoods(resolved));
        out.put("freight_amount", BigDecimal.ZERO);
        out.put("discount_amount", BigDecimal.ZERO);
        out.put("payable_amount", sumGoods(resolved));
        out.put("expires_in_seconds", TOKEN_TTL_MINUTES * 60);
        return out;
    }

    // ==============================================================
    // 提交订单（§2.7：这里才锁库存）
    // ==============================================================

    /**
     * 提交订单。
     *
     * <p>流程固定为：校验 token → 现读价格 → 条件扣减库存 → 落订单 → 清购物车。
     * 全程一个事务，任一步失败整体回滚，<b>不会留下「扣了库存但没订单」</b>。
     */
    @Transactional
    public Map<String, Object> submitOrder(String userId, String token, String addressId,
                                           String paymentChannel, String remark) {
        requireLogin(userId);
        // 先校验支付方式，再动库存。渠道非法属于「输入错」，应在任何写操作之前就拒掉，
        // 而不是扣完库存才抛异常——虽然事务会回滚，但早失败更早暴露问题
        String channel = normalizeChannel(paymentChannel);

        MallCheckoutToken t = checkoutTokenMapper.findValid(token);
        if (t == null) {
            throw new LearningValidationException("MALL_CHECKOUT_EXPIRED",
                    "结算已超时，请返回购物车重新结算");
        }
        if (!userId.equals(t.getUserId())) {
            // token 是结算凭证，等同于一次授权；转给别人用等于让他下你的单
            throw new LearningValidationException("MALL_NO_PERMISSION", "结算凭证不属于当前账号");
        }

        List<Map<String, Object>> skuList = readSkuList(t.getSkuList());
        List<Resolved> resolved = resolveSkus(skuList);

        // 二次分组校验：token 签发后商品状态可能变化，提交时必须再确认一次
        for (Resolved r : resolved) {
            if (!t.getGroupKey().equals(r.groupKey)) {
                throw new LearningValidationException("MALL_MIXED_GROUP",
                        "商品交易类型已变化，请返回购物车重新结算");
            }
        }

        BigDecimal goods = sumGoods(resolved);
        // V1 没有运费规则表，不凭空编一个金额；预售包邮与现货同价 = 0。
        // 字段先留好，接运费规则时只改这一行。
        BigDecimal freight = BigDecimal.ZERO;
        BigDecimal discount = BigDecimal.ZERO;
        BigDecimal payable = goods.add(freight).subtract(discount);

        String addressJson = null;
        if (addressId != null && !addressId.isBlank()) {
            MallAddress addr = addressMapper.findById(userId, addressId);
            if (addr == null) {
                throw new LearningValidationException("MALL_ADDRESS_NOT_FOUND", "收货地址不存在");
            }
            addressJson = writeJson(addressView(addr));
        }

        boolean anyStandard = G_STANDARD.equals(t.getTradeGroup());
        // 现货才锁库存（§2.7）；预售等补货，没有可售库存可锁
        if (anyStandard) {
            for (Resolved r : resolved) {
                int affected = skuMapper.decrementInventory(r.skuId, r.quantity);
                if (affected == 0) {
                    // 条件扣减匹配不到行 = 库存不足。抛异常让事务回滚，前面扣的也一并还回去
                    throw new LearningValidationException("MALL_OUT_OF_STOCK",
                            "「" + r.title + "」库存不足，请调整数量后重试");
                }
            }
        }

        String orderId = "mo_" + UUID.randomUUID().toString().replace("-", "").substring(0, 20);
        MallOrder order = new MallOrder();
        order.setOrderId(orderId);
        order.setUserId(userId);
        order.setOrderNo(generateOrderNo());
        order.setTradeGroup(t.getTradeGroup());
        order.setGroupKey(t.getGroupKey());
        order.setGoodsAmount(goods);
        order.setFreightAmount(freight);
        order.setDiscountAmount(discount);
        order.setPayableAmount(payable);
        order.setAddressSnapshot(addressJson);
        order.setPaymentChannel(channel);
        order.setOrderStatus("PENDING_PAYMENT");
        order.setStockLockedUntil(anyStandard
                ? LocalDateTime.now().plusMinutes(STOCK_LOCK_MINUTES) : null);
        order.setRemark(trimOrNull(remark));
        order.setSource("MALL_CART");
        orderMapper.insert(order);

        for (Resolved r : resolved) {
            MallOrderItem item = new MallOrderItem();
            item.setItemId("oi_" + UUID.randomUUID().toString().replace("-", "").substring(0, 20));
            item.setOrderId(orderId);
            item.setSkuId(r.skuId);
            item.setProductId(r.productId);
            item.setProductTitle(r.title);
            item.setSkuVariant(r.variant);
            item.setTradeGroup(tradeGroupOf(r.groupKey));
            item.setBatch(r.groupKey.startsWith(PREORDER_PREFIX)
                    ? r.groupKey.substring(PREORDER_PREFIX.length()) : null);
            item.setPrice(r.price);
            item.setQuantity(r.quantity);
            item.setSubtotal(r.price.multiply(BigDecimal.valueOf(r.quantity)));
            orderItemMapper.insert(item);

            // 已下单的行移出购物车（软删）。不清会让用户重复下单同一件
            cartMapper.softRemove(userId, r.skuId);
        }

        return orderDetail(userId, orderId);
    }

    /**
 * 结算预览：按 token 返回本单商品与金额。
 *
 * <p><b>同样不锁库存</b>（§2.7）。金额在此现读，用于页面展示；
 * 真正落账仍以 submitOrder 的快照为准。
 */
    public Map<String, Object> preview(String userId, String token) {
        requireLogin(userId);
        MallCheckoutToken t = checkoutTokenMapper.findValid(token);
        if (t == null) {
            throw new LearningValidationException("MALL_CHECKOUT_EXPIRED",
                    "结算已超时，请返回购物车重新结算");
        }
        if (!userId.equals(t.getUserId())) {
            throw new LearningValidationException("MALL_NO_PERMISSION", "结算凭证不属于当前账号");
        }

        List<Map<String, Object>> skuList = readSkuList(t.getSkuList());
        List<Resolved> resolved = resolveSkus(skuList);
        BigDecimal goods = sumGoods(resolved);

        List<Map<String, Object>> items = new ArrayList<>();
        for (Resolved r : resolved) {
            Map<String, Object> m = new LinkedHashMap<>();
            m.put("sku_id", r.skuId);
            m.put("product_id", r.productId);
            m.put("product_title", r.title);
            m.put("sku_variant", r.variant);
            m.put("trade_group", tradeGroupOf(r.groupKey));
            m.put("price", r.price);
            m.put("quantity", r.quantity);
            m.put("subtotal", r.price.multiply(BigDecimal.valueOf(r.quantity)));
            items.add(m);
        }

        Map<String, Object> out = new LinkedHashMap<>();
        out.put("checkout_token", token);
        out.put("trade_group", t.getTradeGroup());
        out.put("group_key", t.getGroupKey());
        out.put("items", items);
        out.put("goods_amount", goods);
        out.put("freight_amount", BigDecimal.ZERO);
        out.put("discount_amount", BigDecimal.ZERO);
        out.put("payable_amount", goods);
        return out;
    }

    // ==============================================================
    // 订单查询 / 支付 / 取消
    // ==============================================================

    public Map<String, Object> listOrders(String userId, String status) {
        requireLogin(userId);
        List<MallOrder> orders = orderMapper.listByUser(userId,
                (status == null || status.isBlank()) ? null : status);
        List<Map<String, Object>> out = new ArrayList<>();
        for (MallOrder o : orders) {
            out.add(orderSummary(o));
        }
        Map<String, Object> res = new LinkedHashMap<>();
        res.put("orders", out);
        res.put("total", out.size());
        return res;
    }

    /**
     * 订单详情。
     *
     * <p>§4：前端支付结果页只能读这个接口拿服务端事实，
     * <b>不得</b>根据本地状态自行改订单 / 库存。
     */
    public Map<String, Object> orderDetail(String userId, String orderId) {
        requireLogin(userId);
        MallOrder o = ownedOrder(userId, orderId);
        Map<String, Object> out = orderSummary(o);
        List<Map<String, Object>> items = new ArrayList<>();
        for (MallOrderItem it : orderItemMapper.listByOrder(o.getOrderId())) {
            Map<String, Object> m = new LinkedHashMap<>();
            m.put("sku_id", it.getSkuId());
            m.put("product_id", it.getProductId());
            m.put("product_title", it.getProductTitle());
            m.put("sku_variant", it.getSkuVariant());
            m.put("trade_group", it.getTradeGroup());
            m.put("batch", it.getBatch());
            m.put("price", it.getPrice());
            m.put("quantity", it.getQuantity());
            m.put("subtotal", it.getSubtotal());
            items.add(m);
        }
        out.put("items", items);
        out.put("address", readJson(o.getAddressSnapshot()));
        // 锁库存是否已过期：前端据此提示「库存已释放，请重新下单」
        out.put("stock_lock_expired", o.getStockLockedUntil() != null
                && o.getStockLockedUntil().isBefore(LocalDateTime.now()));
        return out;
    }

    /**
     * 支付成功。<b>服务端状态机唯一的入口</b>。
     *
     * <p>真实接入支付渠道后，支付网关的异步通知也必须落到这一个方法，
     * 走同一套前置状态校验与幂等；前端「支付成功」页面<b>绝不能</b>直接调本方法。
     */
    @Transactional
    public Map<String, Object> pay(String userId, String orderId, String paymentChannel) {
        requireLogin(userId);
        MallOrder o = ownedOrder(userId, orderId);

        if ("PAID".equals(o.getOrderStatus()) || "SHIPPED".equals(o.getOrderStatus())
                || "COMPLETED".equals(o.getOrderStatus())) {
            // 重复回调幂等：已支付就直接返回当前事实，不报错也不重复推进
            return orderDetail(userId, orderId);
        }
        if (!"PENDING_PAYMENT".equals(o.getOrderStatus())) {
            throw new LearningValidationException("MALL_ORDER_STATE",
                    "当前订单状态不可支付：" + statusLabel(o.getOrderStatus()));
        }
        if (o.getStockLockedUntil() != null
                && o.getStockLockedUntil().isBefore(LocalDateTime.now())) {
            // 锁已过期，库存早已回补，这时收款就成了无货可发
            throw new LearningValidationException("MALL_STOCK_LOCK_EXPIRED",
                    "库存锁定已超时，请重新下单");
        }

        String channel = normalizeChannel(paymentChannel);
        int affected = orderMapper.markPaid(orderId, channel);
        if (affected == 0) {
            // 并发重复支付：另一个请求已经推进了状态，按幂等处理
            return orderDetail(userId, orderId);
        }
        return orderDetail(userId, orderId);
    }

    /**
     * 取消订单。只有待付款可取消，取消时<b>回补</b>已锁的现货库存。
     */
    @Transactional
    public Map<String, Object> cancel(String userId, String orderId) {
        requireLogin(userId);
        MallOrder o = ownedOrder(userId, orderId);
        if ("CANCELLED".equals(o.getOrderStatus())) {
            return orderDetail(userId, orderId);
        }
        if (!"PENDING_PAYMENT".equals(o.getOrderStatus())) {
            throw new LearningValidationException("MALL_ORDER_STATE",
                    "只有待付款订单可以取消，当前状态：" + statusLabel(o.getOrderStatus()));
        }

        int affected = orderMapper.cancel(orderId);
        if (affected == 0) {
            throw new LearningValidationException("MALL_ORDER_STATE", "订单状态已变化，请刷新后重试");
        }
        // 先改状态再回补：反过来会让「已回补但没取消」的订单无法重试
        releaseLockedStock(o);
        return orderDetail(userId, orderId);
    }

    /** 取消/超时后回补现货库存；预售没有锁过库存，不动 */
    private void releaseLockedStock(MallOrder o) {
        if (!G_STANDARD.equals(o.getTradeGroup())) {
            return;
        }
        for (MallOrderItem it : orderItemMapper.listByOrder(o.getOrderId())) {
            skuMapper.releaseInventory(it.getSkuId(), it.getQuantity());
        }
    }

    /** 支付成功后的两个推进动作，前台<b>无按钮</b>（§2.10） */
    public Map<String, Object> markShipped(String userId, String orderId) {
        requireLogin(userId);
        ownedOrder(userId, orderId);
        if (orderMapper.markShipped(orderId) == 0) {
            throw new LearningValidationException("MALL_ORDER_STATE", "仅已付款订单可发货");
        }
        return orderDetail(userId, orderId);
    }

    public Map<String, Object> markCompleted(String userId, String orderId) {
        requireLogin(userId);
        ownedOrder(userId, orderId);
        if (orderMapper.markCompleted(orderId) == 0) {
            throw new LearningValidationException("MALL_ORDER_STATE", "仅已发货订单可完成");
        }
        return orderDetail(userId, orderId);
    }

    // ==============================================================
    // 定制需求（§2.9：只生成 request_id）
    // ==============================================================

    @Transactional
    public Map<String, Object> submitCustomRequest(String userId, String productId,
                                                   Map<String, Object> payload) {
        requireLogin(userId);
        if (productId == null || productId.isBlank()) {
            throw new LearningValidationException("MALL_PRODUCT_REQUIRED", "缺少商品");
        }
        PlanetaryJewelryProduct p = productMapper.findById(productId);
        if (p == null || !"PUBLISHED".equals(p.getPublishStatus())) {
            throw new LearningValidationException("MALL_PRODUCT_UNAVAILABLE", "该商品已下架");
        }

        MallCustomRequest r = new MallCustomRequest();
        r.setRequestId("cr_" + UUID.randomUUID().toString().replace("-", "").substring(0, 20));
        r.setUserId(userId);
        r.setProductId(productId);
        // 字段后台动态配置，payload 原样存，不在这里写死固定列
        r.setPayload(writeJson(payload == null ? Map.of() : payload));
        r.setStatus("SUBMITTED");
        customRequestMapper.insert(r);

        Map<String, Object> out = new LinkedHashMap<>();
        out.put("custom_request_id", r.getRequestId());
        out.put("status", r.getStatus());
        // 明确回这三个「没有」，让前端不会误以为已经产生待付款
        out.put("order_id", null);
        out.put("payable_amount", BigDecimal.ZERO);
        out.put("stock_locked", false);
        return out;
    }

    public Map<String, Object> listCustomRequests(String userId) {
        requireLogin(userId);
        List<Map<String, Object>> out = new ArrayList<>();
        for (MallCustomRequest r : customRequestMapper.listByUser(userId)) {
            Map<String, Object> m = new LinkedHashMap<>();
            m.put("custom_request_id", r.getRequestId());
            m.put("product_id", r.getProductId());
            m.put("status", r.getStatus());
            m.put("payload", readJson(r.getPayload()));
            m.put("created_at", r.getCreatedAt());
            out.add(m);
        }
        Map<String, Object> res = new LinkedHashMap<>();
        res.put("requests", out);
        return res;
    }

    // ==============================================================
    // 收货地址
    // ==============================================================

    public List<Map<String, Object>> listAddresses(String userId) {
        requireLogin(userId);
        List<Map<String, Object>> out = new ArrayList<>();
        for (MallAddress a : addressMapper.listByUser(userId)) {
            out.add(addressView(a));
        }
        return out;
    }

    @Transactional
    public Map<String, Object> addAddress(String userId, Map<String, Object> body) {
        requireLogin(userId);
        String name = str(body.get("name"));
        String phone = str(body.get("phone"));
        String province = str(body.get("province"));
        String city = str(body.get("city"));
        String detail = str(body.get("detail"));
        if (name == null || phone == null || province == null || city == null || detail == null) {
            throw new LearningValidationException("MALL_ADDRESS_INCOMPLETE",
                    "收货人、手机号、省、市、详细地址都要填写");
        }
        if (!phone.matches("^1[3-9]\\d{9}$")) {
            throw new LearningValidationException("MALL_PHONE_INVALID", "请填写正确的手机号");
        }

        MallAddress a = new MallAddress();
        a.setAddressId("ad_" + UUID.randomUUID().toString().replace("-", "").substring(0, 20));
        a.setUserId(userId);
        a.setName(name);
        a.setPhone(phone);
        a.setProvince(province);
        a.setCity(city);
        a.setDistrict(str(body.get("district")));
        a.setDetail(detail);
        a.setIsDefault(Boolean.TRUE.equals(body.get("is_default"))
                || addressMapper.listByUser(userId).isEmpty());
        addressMapper.insert(a);

        if (Boolean.TRUE.equals(a.getIsDefault())) {
            addressMapper.clearDefault(userId);
            addressMapper.setDefault(userId, a.getAddressId());
        }
        return addressView(a);
    }

    public void deleteAddress(String userId, String addressId) {
        requireLogin(userId);
        // userId 同时进 WHERE，越权删除自然影响 0 行
        if (addressMapper.deleteByUserAndId(userId, addressId) == 0) {
            throw new LearningValidationException("MALL_ADDRESS_NOT_FOUND", "地址不存在");
        }
    }

    public void setDefaultAddress(String userId, String addressId) {
        requireLogin(userId);
        if (addressMapper.findById(userId, addressId) == null) {
            throw new LearningValidationException("MALL_ADDRESS_NOT_FOUND", "地址不存在");
        }
        addressMapper.clearDefault(userId);
        addressMapper.setDefault(userId, addressId);
    }

    // ==============================================================
    // 私有
    // ==============================================================

    private MallOrder ownedOrder(String userId, String orderId) {
        MallOrder o = orderMapper.findById(orderId);
        if (o == null) {
            throw new LearningValidationException("MALL_ORDER_NOT_FOUND", "订单不存在");
        }
        if (!userId.equals(o.getUserId())) {
            // 与购物车同口径：不回显「该订单属于他人」，避免泄露（§16.3）
            throw new LearningValidationException("MALL_ORDER_NOT_FOUND", "订单不存在");
        }
        return o;
    }

    private void requireLogin(String userId) {
        if (userId == null || userId.isBlank() || "anonymous".equals(userId)) {
            throw new LearningValidationException("MALL_LOGIN_REQUIRED", "请先登录");
        }
    }

    /**
     * 解析并校验传入的 [ {sku_id, quantity} ]。
     *
     * <p>顺带把价格、商品标题、变体、分组都<b>现读</b>出来，
     * submitOrder 直接拿这里的快照落订单，不二次查询避免前后不一致。
     */
    private List<Resolved> resolveSkus(List<Map<String, Object>> skus) {
        List<String> skuIds = new ArrayList<>();
        Map<String, Integer> qtyBySku = new LinkedHashMap<>();
        for (Map<String, Object> row : skus) {
            String skuId = str(row.get("sku_id"));
            if (skuId == null) {
                skuId = str(row.get("skuId"));
            }
            if (skuId == null) {
                throw new LearningValidationException("MALL_SKU_REQUIRED", "缺少 sku_id");
            }
            int qty = intOf(row.get("quantity"), 1);
            if (qty <= 0) {
                throw new LearningValidationException("MALL_QTY_INVALID", "购买数量需大于 0");
            }
            if (qty > 99) {
                throw new LearningValidationException("MALL_QTY_TOO_LARGE", "单次最多购买 99 件");
            }
            // 同一 sku 出现两次时合并，避免下游生成两行同 sku 的订单
            qtyBySku.merge(skuId, qty, Integer::sum);
        }
        for (String id : qtyBySku.keySet()) {
            skuIds.add(id);
        }
        if (skuIds.isEmpty()) {
            throw new LearningValidationException("MALL_CART_EMPTY", "请先选择要结算的商品");
        }

        Map<String, PlanetaryJewelrySku> skuMap = new LinkedHashMap<>();
        for (PlanetaryJewelrySku s : skuMapper.findByIds(skuIds)) {
            skuMap.put(s.getSkuId(), s);
        }

        List<Resolved> out = new ArrayList<>();
        for (Map.Entry<String, Integer> e : qtyBySku.entrySet()) {
            PlanetaryJewelrySku sku = skuMap.get(e.getKey());
            if (sku == null || !"ACTIVE".equals(sku.getStatus())) {
                throw new LearningValidationException("MALL_SKU_UNAVAILABLE", "该规格已停售");
            }
            PlanetaryJewelryProduct p = productMapper.findById(sku.getProductId());
            if (p == null || !"PUBLISHED".equals(p.getPublishStatus())) {
                throw new LearningValidationException("MALL_PRODUCT_UNAVAILABLE", "该商品已下架");
            }
            Resolved r = new Resolved();
            r.skuId = sku.getSkuId();
            r.productId = sku.getProductId();
            r.quantity = e.getValue();
            r.price = sku.getPrice() == null ? BigDecimal.ZERO : sku.getPrice();
            r.title = p.getTitle();
            r.variant = flattenVariant(sku.getVariantAttributes());
            r.groupKey = groupKeyOf(sku);
            out.add(r);
        }
        return out;
    }

    private List<Map<String, Object>> skuListPayload(List<Resolved> resolved) {
        List<Map<String, Object>> out = new ArrayList<>();
        for (Resolved r : resolved) {
            Map<String, Object> m = new LinkedHashMap<>();
            // 注意：这里<b>不写价格</b>。价格在 submitOrder 现读，token 只授权 SKU 与数量
            m.put("sku_id", r.skuId);
            m.put("quantity", r.quantity);
            out.add(m);
        }
        return out;
    }

    private BigDecimal sumGoods(List<Resolved> resolved) {
        BigDecimal total = BigDecimal.ZERO;
        for (Resolved r : resolved) {
            total = total.add(r.price.multiply(BigDecimal.valueOf(r.quantity)));
        }
        return total;
    }

    private Map<String, Object> orderSummary(MallOrder o) {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("order_id", o.getOrderId());
        m.put("order_no", o.getOrderNo());
        m.put("trade_group", o.getTradeGroup());
        m.put("goods_amount", o.getGoodsAmount());
        m.put("freight_amount", o.getFreightAmount());
        m.put("discount_amount", o.getDiscountAmount());
        m.put("payable_amount", o.getPayableAmount());
        m.put("order_status", o.getOrderStatus());
        m.put("order_status_label", statusLabel(o.getOrderStatus()));
        m.put("payment_channel", o.getPaymentChannel());
        m.put("created_at", o.getCreatedAt());
        m.put("paid_at", o.getPaidAt());
        m.put("shipped_at", o.getShippedAt());
        return m;
    }

    private Map<String, Object> addressView(MallAddress a) {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("address_id", a.getAddressId());
        m.put("name", a.getName());
        m.put("phone", a.getPhone());
        m.put("province", a.getProvince());
        m.put("city", a.getCity());
        m.put("district", a.getDistrict());
        m.put("detail", a.getDetail());
        m.put("is_default", Boolean.TRUE.equals(a.getIsDefault()));
        return m;
    }

    private String statusLabel(String status) {
        if (status == null) return "";
        return switch (status) {
            case "PENDING_PAYMENT" -> "待付款";
            case "PAID" -> "待发货";
            case "SHIPPED" -> "已发货";
            case "COMPLETED" -> "已完成";
            case "CANCELLED" -> "已取消";
            default -> status;
        };
    }

    /** 实体商品仅支持微信 / 支付宝（§2.8），其它一律拒绝，不做静默兜底 */
    private String normalizeChannel(String channel) {
        String c = channel == null ? null : channel.trim().toUpperCase();
        if (c == null || c.isBlank()) {
            return null;
        }
        if (!"WECHAT_PAY".equals(c) && !"ALIPAY".equals(c)) {
            throw new LearningValidationException("MALL_PAYMENT_UNSUPPORTED",
                    "实体商品仅支持微信支付或支付宝支付");
        }
        return c;
    }

    private String generateOrderNo() {
        return "WXD" + java.time.LocalDate.now().toString().replace("-", "")
                + UUID.randomUUID().toString().replace("-", "").substring(0, 8).toUpperCase();
    }

    private String flattenVariant(String json) {
        if (json == null || json.isBlank()) {
            return null;
        }
        try {
            Map<String, Object> m = readJson(json);
            if (m == null || m.isEmpty()) {
                return null;
            }
            Set<String> parts = new LinkedHashSet<>();
            for (Object v : m.values()) {
                if (v != null) {
                    parts.add(String.valueOf(v));
                }
            }
            return parts.isEmpty() ? null : String.join(" / ", parts);
        } catch (RuntimeException ex) {
            // 变体解析失败不该阻断下单，退回原始串总比丢信息好
            return json;
        }
    }

    private String writeJson(Object o) {
        try {
            return json.writeValueAsString(o);
        } catch (Exception e) {
            throw new LearningValidationException("MALL_SERIALIZE_FAILED", "数据处理失败");
        }
    }

    private Map<String, Object> readJson(String s) {
        if (s == null || s.isBlank()) {
            return new LinkedHashMap<>();
        }
        try {
            return json.readValue(s, new TypeReference<Map<String, Object>>() {
            });
        } catch (Exception e) {
            return new LinkedHashMap<>();
        }
    }

    private List<Map<String, Object>> readSkuList(String s) {
        try {
            return json.readValue(s, new TypeReference<List<Map<String, Object>>>() {
            });
        } catch (Exception e) {
            throw new LearningValidationException("MALL_CHECKOUT_EXPIRED", "结算凭证已损坏，请重新结算");
        }
    }

    private static String str(Object o) {
        return o == null ? null : String.valueOf(o);
    }

    private static int intOf(Object o, int def) {
        if (o == null) return def;
        if (o instanceof Number) return ((Number) o).intValue();
        try {
            return Integer.parseInt(String.valueOf(o));
        } catch (NumberFormatException e) {
            return def;
        }
    }

    private static String trimOrNull(String s) {
        if (s == null) return null;
        String t = s.trim();
        return t.isEmpty() ? null : (t.length() > 255 ? t.substring(0, 255) : t);
    }

    /** 内部载体：把一个 SKU 的现读事实 + 分组打包，submit 与 issue 共用 */
    private static final class Resolved {
        String skuId;
        String productId;
        int quantity;
        BigDecimal price;
        String title;
        String variant;
        String groupKey;
    }
}