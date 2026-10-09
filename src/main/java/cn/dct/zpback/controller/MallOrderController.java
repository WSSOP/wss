package cn.dct.zpback.controller;

import cn.dct.zpback.dto.ApiResponse;
import cn.dct.zpback.exception.LearningValidationException;
import cn.dct.zpback.security.BearerUserIdResolver;
import cn.dct.zpback.service.MallOrderService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.Map;

/**
 * 问星渡｜商城订单 / 地址 / 定制需求
 *
 * 契约来源：《问星渡_商城HTML原型_程序员对照说明_V1》§2、§4。
 *
 * <p>所有接口都靠 userId 做所有权校验（§16.3）；订单、地址、token 均不认前端传入的 userId。
 */
@RestController
@RequestMapping("/v1/mall")
@RequiredArgsConstructor
@Slf4j
public class MallOrderController {

    private final MallOrderService mall;
    private final BearerUserIdResolver bearer;

    // ---------- 结算 ----------

    /** POST /v1/mall/checkout/token 签发结算凭证。服务端签发，不锁库存（§4、§2.7） */
    @PostMapping("/checkout/token")
    public ApiResponse<Map<String, Object>> issueToken(
            @RequestHeader(value = "Authorization", required = false) String auth,
            @RequestBody Map<String, Object> body) {
        try {
            @SuppressWarnings("unchecked")
            List<Map<String, Object>> skus = (List<Map<String, Object>>) body.get("skus");
            return ApiResponse.success(mall.issueCheckoutToken(bearer.resolve(auth), skus));
        } catch (LearningValidationException e) {
            return ApiResponse.error(400, e.getMessage(), Map.of("error_code", e.getCode()));
        }
    }

    /** GET /v1/mall/checkout/preview 结算预览。服务端现读金额，同样不锁库存（§2.7） */
    @GetMapping("/checkout/preview")
    public ApiResponse<Map<String, Object>> preview(
            @RequestHeader(value = "Authorization", required = false) String auth,
            @RequestParam("token") String token) {
        try {
            return ApiResponse.success(mall.preview(bearer.resolve(auth), token));
        } catch (LearningValidationException e) {
            return ApiResponse.error(400, e.getMessage(), Map.of("error_code", e.getCode()));
        }
    }

    /** POST /v1/mall/orders 提交订单。这里才锁库存 15 分钟（§2.7） */
    @PostMapping("/orders")
    public ApiResponse<Map<String, Object>> submit(
            @RequestHeader(value = "Authorization", required = false) String auth,
            @RequestBody Map<String, Object> body) {
        try {
            return ApiResponse.success(mall.submitOrder(
                    bearer.resolve(auth),
                    str(body.get("checkout_token")),
                    str(body.get("address_id")),
                    str(body.get("payment_channel")),
                    str(body.get("remark"))));
        } catch (LearningValidationException e) {
            return ApiResponse.error(400, e.getMessage(), Map.of("error_code", e.getCode()));
        }
    }

    // ---------- 订单 ----------

    /** GET /v1/mall/orders 商品订单列表；status 可选 */
    @GetMapping("/orders")
    public ApiResponse<Map<String, Object>> list(
            @RequestHeader(value = "Authorization", required = false) String auth,
            @RequestParam(value = "status", required = false) String status) {
        try {
            return ApiResponse.success(mall.listOrders(bearer.resolve(auth), status));
        } catch (LearningValidationException e) {
            return ApiResponse.error(401, e.getMessage(), Map.of("error_code", e.getCode()));
        }
    }

    /**
     * GET /v1/mall/orders/{orderId} 订单详情。
     * 支付结果页只读这个接口拿服务端事实，不自行改订单（§4）。
     */
    @GetMapping("/orders/{orderId}")
    public ApiResponse<Map<String, Object>> detail(
            @RequestHeader(value = "Authorization", required = false) String auth,
            @PathVariable String orderId) {
        try {
            return ApiResponse.success(mall.orderDetail(bearer.resolve(auth), orderId));
        } catch (LearningValidationException e) {
            return ApiResponse.error(404, e.getMessage(), Map.of("error_code", e.getCode()));
        }
    }

    /** POST /v1/mall/orders/{orderId}/pay 服务端记录支付成功（幂等） */
    @PostMapping("/orders/{orderId}/pay")
    public ApiResponse<Map<String, Object>> pay(
            @RequestHeader(value = "Authorization", required = false) String auth,
            @PathVariable String orderId,
            @RequestBody Map<String, Object> body) {
        try {
            return ApiResponse.success(
                    mall.pay(bearer.resolve(auth), orderId, str(body.get("payment_channel"))));
        } catch (LearningValidationException e) {
            return ApiResponse.error(400, e.getMessage(), Map.of("error_code", e.getCode()));
        }
    }

    /** POST /v1/mall/orders/{orderId}/cancel 取消待付款订单并回补库存 */
    @PostMapping("/orders/{orderId}/cancel")
    public ApiResponse<Map<String, Object>> cancel(
            @RequestHeader(value = "Authorization", required = false) String auth,
            @PathVariable String orderId) {
        try {
            return ApiResponse.success(mall.cancel(bearer.resolve(auth), orderId));
        } catch (LearningValidationException e) {
            return ApiResponse.error(400, e.getMessage(), Map.of("error_code", e.getCode()));
        }
    }

    /** POST /v1/mall/orders/{orderId}/ship 发货（服务端/运营推进，非用户点击） */
    @PostMapping("/orders/{orderId}/ship")
    public ApiResponse<Map<String, Object>> ship(
            @RequestHeader(value = "Authorization", required = false) String auth,
            @PathVariable String orderId) {
        try {
            return ApiResponse.success(mall.markShipped(bearer.resolve(auth), orderId));
        } catch (LearningValidationException e) {
            return ApiResponse.error(400, e.getMessage(), Map.of("error_code", e.getCode()));
        }
    }

    /** POST /v1/mall/orders/{orderId}/complete 完成（发货后由服务端推进，前台无按钮） */
    @PostMapping("/orders/{orderId}/complete")
    public ApiResponse<Map<String, Object>> complete(
            @RequestHeader(value = "Authorization", required = false) String auth,
            @PathVariable String orderId) {
        try {
            return ApiResponse.success(mall.markCompleted(bearer.resolve(auth), orderId));
        } catch (LearningValidationException e) {
            return ApiResponse.error(400, e.getMessage(), Map.of("error_code", e.getCode()));
        }
    }

    // ---------- 收货地址 ----------

    @GetMapping("/addresses")
    public ApiResponse<Map<String, Object>> addresses(
            @RequestHeader(value = "Authorization", required = false) String auth) {
        try {
            // 用 Map 包装而不是直接返回 List：这样成功与错误分支返回类型一致，
            // 错误分支才能统一带上 error_code（ApiResponse.error 的 T 就是外层类型）
            return ApiResponse.success(Map.of("addresses", mall.listAddresses(bearer.resolve(auth))));
        } catch (LearningValidationException e) {
            return ApiResponse.error(401, e.getMessage(), Map.of("error_code", e.getCode()));
        }
    }

    @PostMapping("/addresses")
    public ApiResponse<Map<String, Object>> addAddress(
            @RequestHeader(value = "Authorization", required = false) String auth,
            @RequestBody Map<String, Object> body) {
        try {
            return ApiResponse.success(mall.addAddress(bearer.resolve(auth), body));
        } catch (LearningValidationException e) {
            return ApiResponse.error(400, e.getMessage(), Map.of("error_code", e.getCode()));
        }
    }

    @DeleteMapping("/addresses/{addressId}")
    public ApiResponse<Map<String, Object>> deleteAddress(
            @RequestHeader(value = "Authorization", required = false) String auth,
            @PathVariable String addressId) {
        try {
            mall.deleteAddress(bearer.resolve(auth), addressId);
            return ApiResponse.success();
        } catch (LearningValidationException e) {
            return ApiResponse.error(400, e.getMessage(), Map.of("error_code", e.getCode()));
        }
    }

    @PostMapping("/addresses/{addressId}/default")
    public ApiResponse<Map<String, Object>> setDefaultAddress(
            @RequestHeader(value = "Authorization", required = false) String auth,
            @PathVariable String addressId) {
        try {
            mall.setDefaultAddress(bearer.resolve(auth), addressId);
            return ApiResponse.success();
        } catch (LearningValidationException e) {
            return ApiResponse.error(400, e.getMessage(), Map.of("error_code", e.getCode()));
        }
    }

    // ---------- 定制需求（§2.9） ----------

    /**
     * POST /v1/mall/custom/{productId}/request 提交定制需求。
     * 只生成 custom_request_id，不生成订单 / 待付款 / 锁库存。
     */
    @PostMapping("/custom/{productId}/request")
    public ApiResponse<Map<String, Object>> submitCustom(
            @RequestHeader(value = "Authorization", required = false) String auth,
            @PathVariable String productId,
            @RequestBody Map<String, Object> body) {
        try {
            return ApiResponse.success(mall.submitCustomRequest(
                    bearer.resolve(auth), productId, body));
        } catch (LearningValidationException e) {
            return ApiResponse.error(400, e.getMessage(), Map.of("error_code", e.getCode()));
        }
    }

    @GetMapping("/custom/requests")
    public ApiResponse<Map<String, Object>> listCustom(
            @RequestHeader(value = "Authorization", required = false) String auth) {
        try {
            return ApiResponse.success(mall.listCustomRequests(bearer.resolve(auth)));
        } catch (LearningValidationException e) {
            return ApiResponse.error(401, e.getMessage(), Map.of("error_code", e.getCode()));
        }
    }

    private static String str(Object o) {
        return o == null ? null : String.valueOf(o);
    }
}