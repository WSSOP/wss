package cn.dct.zpback.controller;

import cn.dct.zpback.dto.ApiResponse;
import cn.dct.zpback.entity.MallCartItem;
import cn.dct.zpback.exception.LearningValidationException;
import cn.dct.zpback.security.BearerUserIdResolver;
import cn.dct.zpback.service.MallCartService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

/**
 * 问星渡｜商城购物车
 *
 * 契约来源：《【我的】页面开发文档》§7（购物车 /mall/cart）、§12.1（数量口径）。
 *
 * <b>鉴权</b>：每个操作都带 userId 做所有权校验（§16.3）。
 * 只凭 skuId 就能改动的接口，等于把他人购物车敞开。
 */
@RestController
@RequestMapping("/v1/mall/cart")
@RequiredArgsConstructor
@Slf4j
public class MallCartController {

    private final MallCartService cart;
    private final BearerUserIdResolver bearer;

    /** GET /v1/mall/cart 购物车列表。价格全部来自 SKU 现读（§16.3） */
    @GetMapping
    public ApiResponse<Map<String, Object>> list(
            @RequestHeader(value = "Authorization", required = false) String auth) {
        try {
            return ApiResponse.success(cart.list(bearer.resolve(auth)));
        } catch (LearningValidationException e) {
            return ApiResponse.error(401, e.getMessage(), Map.of("error_code", e.getCode()));
        }
    }

    /**
     * GET /v1/mall/cart/count
     *
     * <p>§12.1 指定的计数口径，供【我的】一级页购物车角标使用。
     *
     * <p><b>未登录返回 0 而不是 401</b>：一级页要求入口始终可见（§8.1），
     * 未登录时没有购物车是正常状态，不该报错。
     */
    @GetMapping("/count")
    public ApiResponse<Map<String, Object>> count(
            @RequestHeader(value = "Authorization", required = false) String auth) {
        int n = cart.countSettleableRows(bearer.resolve(auth));
        return ApiResponse.success(Map.of("settleable_rows", n));
    }

    /** POST /v1/mall/cart/items 加购。重复加购由唯一键累加，不产生重复行 */
    @PostMapping("/items")
    public ApiResponse<Map<String, Object>> add(
            @RequestHeader(value = "Authorization", required = false) String auth,
            @RequestBody Map<String, Object> body) {
        try {
            String skuId = str(body.get("sku_id"));
            int qty = num(body.get("quantity"), 1);
            MallCartItem row = cart.add(bearer.resolve(auth), skuId, qty);
            // 包成 Map 而不直接返回 MallCartItem：
            // 错误分支要带 error_code，成功与失败的数据类型必须一致，
            // 否则同一个方法里两套泛型推不出来。
            return ApiResponse.success(Map.of("sku_id", row.getSkuId(),
                    "product_id", row.getProductId(),
                    "quantity", row.getQuantity()));
        } catch (LearningValidationException e) {
            return ApiResponse.error(400, e.getMessage(), Map.of("error_code", e.getCode()));
        }
    }

    /**
     * POST /v1/mall/cart/items/update 改数量或勾选状态。
     *
     * <p>用子路径而不是与加购同映射：
     * 两个方法都叫 POST /items 时 Spring 启动就报 Ambiguous mapping，
     * 那是启动期失败，不是运行时才发现。
     *
     * <p>返回 {@code ApiResponse<Map<String,Object>>} 而非 {@code ApiResponse<Void>}：
     * 错误时要带 error_code 透传给前端，
     * 而 {@code ApiResponse<Void>} 无法承载带 data 的 error()。
     */
    @PostMapping("/items/update")
    public ApiResponse<Map<String, Object>> update(
            @RequestHeader(value = "Authorization", required = false) String auth,
            @RequestBody Map<String, Object> body) {
        try {
            String userId = bearer.resolve(auth);
            String skuId = str(body.get("sku_id"));
            if (skuId == null) {
                return ApiResponse.error(400, "缺少 sku_id", Map.of());
            }
            // 数量与勾选可只传其一：前端切换复选框时不必带上数量
            if (body.containsKey("quantity")) {
                cart.updateQuantity(userId, skuId, num(body.get("quantity"), 1));
            }
            if (body.containsKey("selected")) {
                cart.setSelected(userId, skuId, bool(body.get("selected")));
            }
            return ApiResponse.success();
        } catch (LearningValidationException e) {
            return ApiResponse.error(400, e.getMessage(), Map.of("error_code", e.getCode()));
        }
    }

    /** POST /v1/mall/cart/select-all 全选 / 全不选 */
    @PostMapping("/select-all")
    public ApiResponse<Map<String, Object>> selectAll(
            @RequestHeader(value = "Authorization", required = false) String auth,
            @RequestBody Map<String, Object> body) {
        try {
            cart.setSelectedAll(bearer.resolve(auth), bool(body.get("selected")));
            return ApiResponse.success();
        } catch (LearningValidationException e) {
            return ApiResponse.error(401, e.getMessage(), Map.of("error_code", e.getCode()));
        }
    }

    /** DELETE /v1/mall/cart/items 软删，不物理删除 */
    @DeleteMapping("/items")
    public ApiResponse<Map<String, Object>> remove(
            @RequestHeader(value = "Authorization", required = false) String auth,
            @RequestParam("sku_id") String skuId) {
        try {
            cart.remove(bearer.resolve(auth), skuId);
            return ApiResponse.success();
        } catch (LearningValidationException e) {
            return ApiResponse.error(400, e.getMessage(), Map.of("error_code", e.getCode()));
        }
    }

    // ==============================================================
    // 取参容错：JSON 里的数字可能是 Integer / Double / String
    // ==============================================================

    private static String str(Object o) {
        return o == null ? null : String.valueOf(o);
    }

    private static int num(Object o, int def) {
        if (o == null) return def;
        if (o instanceof Number) return ((Number) o).intValue();
        try {
            return Integer.parseInt(String.valueOf(o));
        } catch (NumberFormatException e) {
            return def;
        }
    }

    private static boolean bool(Object o) {
        if (o == null) return false;
        if (o instanceof Boolean) return (Boolean) o;
        String s = String.valueOf(o);
        return "true".equalsIgnoreCase(s) || "1".equals(s);
    }
}