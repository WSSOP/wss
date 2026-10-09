package cn.dct.zpback.service;

import cn.dct.zpback.entity.MallAddress;
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
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

/**
 * 问星渡｜商城订单 —— 锁《商城HTML原型说明》§2 六条规则中<b>最容易做坏</b>的几条。
 *
 * <ul>
 *   <li>§2.6 现货与预售不可混结、不同预售批次不可混结</li>
 *   <li>§2.7 提交订单才锁库存；库存不足整单回滚</li>
 *   <li>§4 价格现读、token 不含价格、支付幂等</li>
 *   <li>§2.9 定制只生成 request_id，不产生订单</li>
 *   <li>§16.3 订单所有权与登录校验</li>
 * </ul>
 */
class MallOrderServiceTest {

    private MallOrderService svc;
    private MallOrderMapper orderMapper;
    private MallOrderItemMapper itemMapper;
    private MallAddressMapper addrMapper;
    private MallCustomRequestMapper customMapper;
    private MallCheckoutTokenMapper tokenMapper;
    private PlanetaryJewelrySkuMapper skuMapper;
    private PlanetaryJewelryProductMapper productMapper;
    private MallCartMapper cartMapper;

    private static final String USER = "user_u1";
    private static final String OTHER = "user_u2";

    @BeforeEach
    void setUp() {
        orderMapper = mock(MallOrderMapper.class);
        itemMapper = mock(MallOrderItemMapper.class);
        addrMapper = mock(MallAddressMapper.class);
        customMapper = mock(MallCustomRequestMapper.class);
        tokenMapper = mock(MallCheckoutTokenMapper.class);
        skuMapper = mock(PlanetaryJewelrySkuMapper.class);
        productMapper = mock(PlanetaryJewelryProductMapper.class);
        cartMapper = mock(MallCartMapper.class);
        svc = new MallOrderService(orderMapper, itemMapper, addrMapper, customMapper,
                tokenMapper, skuMapper, productMapper, cartMapper);
    }

    // ---------- helpers ----------

    private static Map<String, Object> line(String skuId, int qty) {
        return Map.of("sku_id", skuId, "quantity", qty);
    }

    private PlanetaryJewelrySku sku(String id, int qty, boolean restockEnabled) {
        PlanetaryJewelrySku s = new PlanetaryJewelrySku();
        s.setSkuId(id);
        s.setProductId("p_" + id);
        s.setPrice(new BigDecimal("399"));
        s.setInventoryQuantity(qty);
        s.setStatus("ACTIVE");
        s.setRestockWaitlistEnabled(restockEnabled);
        return s;
    }

    private PlanetaryJewelryProduct product(String pid) {
        PlanetaryJewelryProduct p = new PlanetaryJewelryProduct();
        p.setProductId(pid);
        p.setTitle("商品" + pid);
        p.setPublishStatus("PUBLISHED");
        return p;
    }

    /**
     * resolveSkus 是一次性把所有 skuId 查出来的（findByIds 批量，避免 N+1），
     * 所以 stub 必须按<b>整批</b>参数匹配，按单个 List.of(skuId) 匹配不到。
     */
    private void givenSkus(PlanetaryJewelrySku... skus) {
        // 用 Arrays.asList 而非 List.of(skus)：数组传给 varargs 会被当成「单个元素是数组」，语义不对
        List<PlanetaryJewelrySku> all = Arrays.asList(skus);
        List<String> ids = new ArrayList<>();
        for (PlanetaryJewelrySku s : skus) {
            ids.add(s.getSkuId());
            when(productMapper.findById(s.getProductId())).thenReturn(product(s.getProductId()));
        }
        when(skuMapper.findByIds(ids)).thenReturn(all);
    }

    private MallCheckoutToken validToken(String userId, String groupKey, String tradeGroup,
                                         String skuJson) {
        MallCheckoutToken t = new MallCheckoutToken();
        t.setToken("tok1");
        t.setUserId(userId);
        t.setGroupKey(groupKey);
        t.setTradeGroup(tradeGroup);
        t.setSkuList(skuJson);
        return t;
    }

    // ==========================================================
    // §2.6 现货与预售不可混结
    // ==========================================================

    @Test
    @DisplayName("现货 + 预售混在一起结算被拒绝")
    void mixedStandardAndPreorder_rejected() {
        givenSkus(sku("s1", 5, false), sku("s2", 0, true));

        List<Map<String, Object>> skus = List.of(line("s1", 1), line("s2", 1));

        LearningValidationException e = assertThrows(LearningValidationException.class,
                () -> svc.issueCheckoutToken(USER, skus));
        assertEquals("MALL_MIXED_GROUP", e.getCode());
    }

    @Test
    @DisplayName("不同预售批次混结被拒绝")
    void differentPreorderBatches_rejected() {
        givenSkus(sku("s1", 0, true), sku("s2", 0, true));

        List<Map<String, Object>> skus = List.of(line("s1", 1), line("s2", 1));

        LearningValidationException e = assertThrows(LearningValidationException.class,
                () -> svc.issueCheckoutToken(USER, skus));
        assertEquals("MALL_MIXED_GROUP", e.getCode());
    }

    @Test
    @DisplayName("多件现货可以一起结算")
    void multipleStandard_accepted() {
        givenSkus(sku("s1", 5, false), sku("s2", 3, false));

        Map<String, Object> r = svc.issueCheckoutToken(USER,
                List.of(line("s1", 2), line("s2", 1)));

        assertEquals("STANDARD", r.get("trade_group"));
        assertEquals(new BigDecimal("1197"), r.get("payable_amount")); // 399*2 + 399*1
    }

    // ==========================================================
    // §2.7 提交订单才锁库存
    // ==========================================================

    @Test
    @DisplayName("签发结算 token 不扣库存")
    void issueToken_doesNotLockStock() {
        givenSkus(sku("s1", 5, false));
        svc.issueCheckoutToken(USER, List.of(line("s1", 1)));

        verify(skuMapper, never()).decrementInventory(anyString(), anyInt());
    }

    @Test
    @DisplayName("提交现货订单才条件扣减库存并清购物车")
    void submitStandard_locksStockAndClearsCart() {
        givenSkus(sku("s1", 5, false));
        when(skuMapper.decrementInventory("s1", 2)).thenReturn(1);
        when(tokenMapper.findValid("tok1")).thenReturn(
                validToken(USER, "STANDARD", "STANDARD",
                        "[{\"sku_id\":\"s1\",\"quantity\":2}]"));
        when(orderMapper.insert(any())).thenReturn(1);
        when(orderMapper.findById(anyString())).thenAnswer(i -> newOrder("mo_1", USER));

        svc.submitOrder(USER, "tok1", null, "WECHAT_PAY", null);

        verify(skuMapper).decrementInventory("s1", 2);
        verify(cartMapper).softRemove(eq(USER), eq("s1"));
    }

    @Test
    @DisplayName("库存不足扣不动 -> 抛错，整单不成立")
    void insufficientStock_rejectedAndNoOrder() {
        givenSkus(sku("s1", 1, false));
        when(skuMapper.decrementInventory("s1", 5)).thenReturn(0); // 扣不动
        when(tokenMapper.findValid("tok1")).thenReturn(
                validToken(USER, "STANDARD", "STANDARD",
                        "[{\"sku_id\":\"s1\",\"quantity\":5}]"));

        LearningValidationException e = assertThrows(LearningValidationException.class,
                () -> svc.submitOrder(USER, "tok1", null, "WECHAT_PAY", null));
        assertEquals("MALL_OUT_OF_STOCK", e.getCode());
        verify(orderMapper, never()).insert(any());
    }

    @Test
    @DisplayName("预售订单不扣库存（无库存可锁）")
    void submitPreorder_doesNotLockStock() {
        givenSkus(sku("s1", 0, true));
        when(tokenMapper.findValid("tok1")).thenReturn(
                validToken(USER, "PREORDER:s1", "PREORDER",
                        "[{\"sku_id\":\"s1\",\"quantity\":1}]"));
        when(orderMapper.insert(any())).thenReturn(1);
        when(orderMapper.findById(anyString())).thenAnswer(i -> newOrder("mo_1", USER));

        svc.submitOrder(USER, "tok1", null, "WECHAT_PAY", null);

        verify(skuMapper, never()).decrementInventory(anyString(), anyInt());
    }

    // ==========================================================
    // §4 token 不含价格 / 所有权 / 登录
    // ==========================================================

    @Test
    @DisplayName("token 不属于当前账号 -> 拒绝下单")
    void tokenFromOtherUser_rejected() {
        givenSkus(sku("s1", 5, false));
        when(tokenMapper.findValid("tok1")).thenReturn(
                validToken(OTHER, "STANDARD", "STANDARD",
                        "[{\"sku_id\":\"s1\",\"quantity\":1}]"));

        LearningValidationException e = assertThrows(LearningValidationException.class,
                () -> svc.submitOrder(USER, "tok1", null, "WECHAT_PAY", null));
        assertEquals("MALL_NO_PERMISSION", e.getCode());
    }

    @Test
    @DisplayName("token 过期 -> 拒绝下单")
    void expiredToken_rejected() {
        when(tokenMapper.findValid("tok1")).thenReturn(null); // 过期查不到

        LearningValidationException e = assertThrows(LearningValidationException.class,
                () -> svc.submitOrder(USER, "tok1", null, "WECHAT_PAY", null));
        assertEquals("MALL_CHECKOUT_EXPIRED", e.getCode());
    }

    @Test
    @DisplayName("未登录不能下单")
    void anonymous_cannotOrder() {
        LearningValidationException e = assertThrows(LearningValidationException.class,
                () -> svc.submitOrder("anonymous", "tok1", null, "WECHAT_PAY", null));
        assertEquals("MALL_LOGIN_REQUIRED", e.getCode());
    }

    @Test
    @DisplayName("实体商品不支持星印支付")
    void starSealPayment_rejected() {
        givenSkus(sku("s1", 5, false));
        when(tokenMapper.findValid("tok1")).thenReturn(
                validToken(USER, "STANDARD", "STANDARD",
                        "[{\"sku_id\":\"s1\",\"quantity\":1}]"));

        LearningValidationException e = assertThrows(LearningValidationException.class,
                () -> svc.submitOrder(USER, "tok1", null, "STAR_SEAL", null));
        assertEquals("MALL_PAYMENT_UNSUPPORTED", e.getCode());
    }

    // ==========================================================
    // 订单所有权 / 支付幂等 / 取消回补
    // ==========================================================

    @Test
    @DisplayName("看别人的订单 -> 报「订单不存在」，不泄露归属")
    void orderOfOtherUser_hidden() {
        when(orderMapper.findById("mo_1")).thenReturn(newOrder("mo_1", OTHER));

        LearningValidationException e = assertThrows(LearningValidationException.class,
                () -> svc.orderDetail(USER, "mo_1"));
        assertEquals("MALL_ORDER_NOT_FOUND", e.getCode());
    }

    @Test
    @DisplayName("重复支付幂等：已支付直接返回，不报错也不重复推进")
    void pay_isIdempotent() {
        MallOrder paid = newOrder("mo_1", USER);
        paid.setOrderStatus("PAID");
        when(orderMapper.findById("mo_1")).thenReturn(paid);
        when(itemMapper.listByOrder("mo_1")).thenReturn(List.of());

        svc.pay(USER, "mo_1", "WECHAT_PAY");

        verify(orderMapper, never()).markPaid(anyString(), anyString());
    }

    @Test
    @DisplayName("取消待付款订单会回补已锁库存")
    void cancel_releasesStock() {
        MallOrder o = newOrder("mo_1", USER);
        o.setOrderStatus("PENDING_PAYMENT");
        o.setTradeGroup("STANDARD");
        when(orderMapper.findById("mo_1")).thenReturn(o);
        when(orderMapper.cancel("mo_1")).thenReturn(1);

        MallOrderItem item = new MallOrderItem();
        item.setSkuId("s1");
        item.setQuantity(2);
        when(itemMapper.listByOrder("mo_1")).thenReturn(List.of(item));

        svc.cancel(USER, "mo_1");

        verify(skuMapper).releaseInventory("s1", 2);
    }

    // ==========================================================
    // §2.9 定制需求
    // ==========================================================

    @Test
    @DisplayName("提交定制需求只生成 request_id，不产生订单/锁库存")
    void customRequest_createsNoOrder() {
        when(productMapper.findById("p1")).thenReturn(product("p1"));

        Map<String, Object> r = svc.submitCustomRequest(USER, "p1", Map.of("size", "30cm"));

        assertNotNull(r.get("custom_request_id"));
        assertNull(r.get("order_id"));
        assertEquals(BigDecimal.ZERO, r.get("payable_amount"));
        assertEquals(false, r.get("stock_locked"));
        verify(orderMapper, never()).insert(any());
        verify(skuMapper, never()).decrementInventory(anyString(), anyInt());
        verify(customMapper).insert(any(MallCustomRequest.class));
    }

    private static MallOrder newOrder(String id, String userId) {
        MallOrder o = new MallOrder();
        o.setOrderId(id);
        o.setOrderNo("WXD1");
        o.setUserId(userId);
        o.setOrderStatus("PENDING_PAYMENT");
        o.setTradeGroup("STANDARD");
        o.setGroupKey("STANDARD");
        o.setGoodsAmount(new BigDecimal("399"));
        o.setFreightAmount(BigDecimal.ZERO);
        o.setDiscountAmount(BigDecimal.ZERO);
        o.setPayableAmount(new BigDecimal("399"));
        return o;
    }
}