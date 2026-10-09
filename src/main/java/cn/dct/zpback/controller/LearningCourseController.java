package cn.dct.zpback.controller;

import cn.dct.zpback.dto.ApiResponse;
import cn.dct.zpback.entity.LearningCourse;
import cn.dct.zpback.entity.LearningEnrollment;
import cn.dct.zpback.entity.LearningEvent;
import cn.dct.zpback.entity.LearningOrder;
import cn.dct.zpback.entity.MyLearningRow;
import cn.dct.zpback.exception.LearningValidationException;
import cn.dct.zpback.security.BearerUserIdResolver;
import cn.dct.zpback.security.LearningRole;
import cn.dct.zpback.security.LearningRoleResolver;
import cn.dct.zpback.service.LearningCommerceService;
import cn.dct.zpback.service.LearningCourseService;
import cn.dct.zpback.service.LearningEventService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.*;

import java.math.BigDecimal;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 问星渡｜学习课程 与 主题活动
 * 前缀：/v1/learning
 *
 * 对应文档 14_API_Contract：「API按Service分组，Service对应Provider接入层」。
 * Controller 只做<b>参数解析 + 权限判定 + 异常翻译</b>，
 * 不含任何业务规则——业务规则全在三个 Domain Service 里。
 *
 * 三段式分派：
 *   /courses/** → LearningCourseService   （Domain Service 独立）
 *   /events/**  → LearningEventService
 *   /orders/**  → LearningCommerceService （Commerce Core 统一处理交易）
 *
 * 权限按 16_Backend_Menu_Mapping 的四角色判定，见 {@link LearningRoleResolver}。
 */
@RestController
@RequestMapping("/v1/learning")
@RequiredArgsConstructor
@Slf4j
public class LearningCourseController {

    private final LearningCourseService courseService;
    private final LearningEventService eventService;
    private final LearningCommerceService commerce;
    private final BearerUserIdResolver bearer;
    private final LearningRoleResolver roles;

    // ============================================================
    // 课程浏览
    // ============================================================

    @GetMapping("/courses")
    public ApiResponse<List<LearningCourse>> listCourses(
            @RequestParam(required = false) String category,
            @RequestParam(required = false) String level,
            @RequestHeader(value = "Authorization", required = false) String auth) {
        return ApiResponse.success(courseService.listCourses(
                blankToNull(category), blankToNull(level), uid(auth)));
    }

    @GetMapping("/courses/{courseId}")
    public ApiResponse<Map<String, Object>> courseDetail(
            @PathVariable String courseId,
            @RequestHeader(value = "Authorization", required = false) String auth) {
        return run(() -> courseService.courseDetail(courseId, uid(auth)));
    }

    /** 讲师端「我的课程」：Teacher 与 Assistant Teacher 都可见 */
    @GetMapping("/teacher/courses")
    public ApiResponse<List<LearningCourse>> myCourses(
            @RequestHeader(value = "Authorization", required = false) String auth) {
        String userId = uid(auth);
        if (!roles.hasPermission(userId, LearningRole.P_ROSTER_VIEW)) {
            return denied("仅讲师、助教与运营可查看");
        }
        return ApiResponse.success(courseService.listMyCourses(userId));
    }

    // ============================================================
    // 活动浏览
    // ============================================================

    @GetMapping("/events")
    public ApiResponse<List<LearningEvent>> listEvents(
            @RequestParam(name = "event_type", required = false) String eventType) {
        return ApiResponse.success(eventService.listEvents(blankToNull(eventType)));
    }

    @GetMapping("/events/{eventId}")
    public ApiResponse<Map<String, Object>> eventDetail(
            @PathVariable String eventId,
            @RequestHeader(value = "Authorization", required = false) String auth) {
        return run(() -> eventService.eventDetail(eventId, uid(auth)));
    }

    /** 主持人端「我的活动」 */
    @GetMapping("/host/events")
    public ApiResponse<List<LearningEvent>> myEvents(
            @RequestHeader(value = "Authorization", required = false) String auth) {
        String userId = uid(auth);
        if (!roles.hasPermission(userId, LearningRole.P_ROSTER_VIEW)) {
            return denied("仅主持人、讲师与运营可查看");
        }
        return ApiResponse.success(eventService.listMyEvents(userId));
    }

    // ============================================================
    // 报名
    // ============================================================

    /** 免费课程报名。付费课程返回 LEARNING_REQUIRES_ORDER，前端应引导下单 */
    @PostMapping("/courses/{courseId}/enroll")
    public ApiResponse<Map<String, Object>> enrollCourse(
            @PathVariable String courseId,
            @RequestBody Map<String, Object> req,
            @RequestHeader(value = "Authorization", required = false) String auth) {
        return run(() -> courseService.enrollFree(courseId, str(req, "session_id"), uid(auth)));
    }

    /** 免费活动报名 */
    @PostMapping("/events/{eventId}/enroll")
    public ApiResponse<Map<String, Object>> enrollEvent(
            @PathVariable String eventId,
            @RequestBody Map<String, Object> req,
            @RequestHeader(value = "Authorization", required = false) String auth) {
        return run(() -> eventService.enrollFree(eventId, str(req, "session_id"), uid(auth)));
    }

    /**
     * 取消报名并释放名额。幂等：重复取消不会二次减名额。
     *
     * 场次 ID 全局唯一（课程场次与活动场次各自生成），因此单一路由即可，
     * 无需再区分 target_type。
     */
    @DeleteMapping("/enrollments/{sessionId}")
    public ApiResponse<Map<String, Object>> cancelEnrollment(
            @PathVariable String sessionId,
            @RequestHeader(value = "Authorization", required = false) String auth) {
        String userId = uid(auth);
        // 报名记录里带 target_type，据此回到正确的 Domain Service
        return run(() -> isEventSession(userId, sessionId)
                ? eventService.cancelEnrollment(userId, sessionId)
                : courseService.cancelEnrollment(userId, sessionId));
    }

    // ============================================================
    // 直播入口（仅已报名者）
    // ============================================================

    @GetMapping("/sessions/{sessionId}/live")
    public ApiResponse<Map<String, Object>> liveSession(
            @PathVariable String sessionId,
            @RequestParam(name = "target_type", defaultValue = "COURSE") String targetType,
            @RequestHeader(value = "Authorization", required = false) String auth) {
        String userId = uid(auth);
        if (!roles.isLoggedIn(userId)) {
            return denied("请先登录");
        }
        try {
            return ApiResponse.success("EVENT".equals(targetType)
                    ? eventService.enrolledSession(sessionId, userId)
                    : courseService.enrolledSession(sessionId, userId));
        } catch (LearningValidationException e) {
            return ApiResponse.error(403, e.getMessage(),
                    Map.of("error_code", e.getCode(), "details", e.getDetails()));
        }
    }

    // ============================================================
    // 讲师 / 主持人侧
    // ============================================================

    /** 某场次报名名单（讲师点名）。不含手机号等联系方式 */
    @GetMapping("/sessions/{sessionId}/roster")
    public ApiResponse<List<LearningEnrollment>> roster(
            @PathVariable String sessionId,
            @RequestParam(name = "target_type", defaultValue = "COURSE") String targetType,
            @RequestHeader(value = "Authorization", required = false) String auth) {
        String userId = uid(auth);
        if (!roles.hasPermission(userId, LearningRole.P_ROSTER_VIEW)) {
            return deniedRoster("无权查看报名名单");
        }
        return ApiResponse.success("EVENT".equals(targetType)
                ? eventService.sessionRoster(sessionId)
                : courseService.sessionRoster(sessionId));
    }

    /** 出勤核销。归属校验在 Service 内完成（讲师只能核自己的课） */
    @PostMapping("/sessions/{sessionId}/attendance")
    public ApiResponse<Map<String, Object>> checkAttendance(
            @PathVariable String sessionId,
            @RequestBody Map<String, Object> req,
            @RequestHeader(value = "Authorization", required = false) String auth) {
        String userId = uid(auth);
        if (!roles.hasPermission(userId, LearningRole.P_ATTENDANCE)) {
            return denied("仅讲师、助教与运营可核验出勤");
        }
        boolean isAdmin = roles.isAdmin(userId);
        String status = str(req, "status", "ATTENDED");
        String student = str(req, "user_id");
        return run(() -> isEventSession(null, sessionId)
                ? eventService.checkAttendance(sessionId, student, status, userId, isAdmin)
                : courseService.checkAttendance(sessionId, student, status, userId, isAdmin));
    }

    // ============================================================
    // Commerce Core
    // ============================================================

    @PostMapping("/orders")
    public ApiResponse<Map<String, Object>> createOrder(
            @RequestBody Map<String, Object> req,
            @RequestHeader(value = "Authorization", required = false) String auth) {
        return run(() -> commerce.createOrder(
                str(req, "target_type"),
                str(req, "target_id"),
                str(req, "session_id"),
                uid(auth),
                str(req, "source", "COURSE_LIST")));
    }

    @DeleteMapping("/orders/{orderId}")
    public ApiResponse<Map<String, Object>> cancelOrder(
            @PathVariable String orderId,
            @RequestHeader(value = "Authorization", required = false) String auth) {
        return run(() -> commerce.cancelOrder(orderId, uid(auth)));
    }

    /**
     * 支付回调。仅 CUSTOMER_SERVICE / ADMIN。
     *
     * <b>本接口只把订单标记为 PAID，不会发放学习权限</b>
     * ——权限由独立的 Fulfillment 步骤发放（原始文档：Payment 不直接创建业务权限）。
     *
     * 第三方回调签名校验待渠道接入后补充；当前用 MANUAL 通道供后台确认。
     */
    @PostMapping("/orders/{orderId}/pay-callback")
    public ApiResponse<Map<String, Object>> payCallback(
            @PathVariable String orderId,
            @RequestBody Map<String, Object> req,
            @RequestHeader(value = "Authorization", required = false) String auth) {
        String userId = uid(auth);
        if (!roles.hasPermission(userId, LearningRole.P_PAYMENT_CONFIRM)) {
            return denied("支付确认仅限客服与运营");
        }
        try {
            BigDecimal paid = req.get("paid_amount") == null
                    ? null : new BigDecimal(String.valueOf(req.get("paid_amount")));
            return ApiResponse.success(
                    commerce.markPaid(orderId, str(req, "payment_channel", "MANUAL"), paid));
        } catch (LearningValidationException e) {
            return err(400, e);
        }
    }

    /** Fulfillment：交付成可用权限。与支付回调分开的独立步骤 */
    @PostMapping("/orders/{orderId}/fulfill")
    public ApiResponse<Map<String, Object>> fulfill(
            @PathVariable String orderId,
            @RequestHeader(value = "Authorization", required = false) String auth) {
        String userId = uid(auth);
        if (!roles.hasPermission(userId, LearningRole.P_FULFILL)) {
            return denied("交付操作仅限客服与运营");
        }
        return run(() -> commerce.fulfillOrder(orderId));
    }

    /** 补发「已支付未交付」的订单。支付与交付是两个步骤，中间可能中断 */
    @PostMapping("/fulfill/retry")
    public ApiResponse<Map<String, Object>> retryFulfill(
            @RequestParam(defaultValue = "100") int limit,
            @RequestHeader(value = "Authorization", required = false) String auth) {
        String userId = uid(auth);
        if (!roles.hasPermission(userId, LearningRole.P_FULFILL)) {
            return denied("仅限客服与运营");
        }
        Map<String, Object> data = new LinkedHashMap<>();
        data.put("fulfilled", commerce.reFulfillPending(Math.min(limit, 500)));
        return ApiResponse.success(data);
    }

    /** 退款：撤销订单并收回权限。仅 CUSTOMER_SERVICE / ADMIN */
    @PostMapping("/orders/{orderId}/refund")
    public ApiResponse<Map<String, Object>> refund(
            @PathVariable String orderId,
            @RequestBody(required = false) Map<String, Object> req,
            @RequestHeader(value = "Authorization", required = false) String auth) {
        String userId = uid(auth);
        if (!roles.hasPermission(userId, LearningRole.P_REFUND)) {
            return denied("退款仅限客服与运营");
        }
        return run(() -> commerce.refundOrder(orderId, str(req, "reason")));
    }

    // ============================================================
    // 我的
    // ============================================================

    @GetMapping("/orders/mine")
    public ApiResponse<List<LearningOrder>> myOrders(
            @RequestParam(defaultValue = "50") int limit,
            @RequestHeader(value = "Authorization", required = false) String auth) {
        return ApiResponse.success(commerce.myOrders(uid(auth), Math.min(limit, 200)));
    }

    @GetMapping("/enrollments/mine")
    public ApiResponse<List<LearningEnrollment>> myEnrollments(
            @RequestHeader(value = "Authorization", required = false) String auth) {
        return ApiResponse.success(courseService.myEnrollments(uid(auth)));
    }

    /**
     * GET /v1/learning/my-learning
     *
     * 对应文档 15_Frontend_Page_Mapping 的用户端「学习页」：
     * 课程报名与活动报名合并成一张列表，最近的场次在最前。
     *
     * 聚合查询放在 Commerce Service 而非 Course Service——
     * 它跨了两个 Domain，而文档要求 Commerce Core 统一编排交易与跨域聚合。
     */
    @GetMapping("/my-learning")
    public ApiResponse<List<MyLearningRow>> myLearning(
            @RequestHeader(value = "Authorization", required = false) String auth) {
        return ApiResponse.success(commerce.myLearning(uid(auth)));
    }

    // ============================================================
    // 工具
    // ============================================================

    private String uid(String authHeader) {
        return bearer.resolve(authHeader);
    }

    /**
     * 判断场次属于活动还是课程。
     *
     * 场次 ID 全局唯一，活动场次前缀固定，因此这里按命名判定即可；
     * userId 为 null 时（教师侧核验）也成立。
     */
    private boolean isEventSession(String userId, String sessionId) {
        return sessionId != null && sessionId.startsWith("ES_");
    }

    private ApiResponse<Map<String, Object>> run(java.util.function.Supplier<Map<String, Object>> f) {
        try {
            return ApiResponse.success(f.get());
        } catch (LearningValidationException e) {
            return err(400, e);
        }
    }

    private static ApiResponse<Map<String, Object>> err(int code, LearningValidationException e) {
        return ApiResponse.error(code, e.getMessage(),
                Map.of("error_code", e.getCode(), "details", e.getDetails()));
    }

    private static <T> ApiResponse<T> denied(String msg) {
        ApiResponse<T> r = new ApiResponse<>();
        r.setCode(403);
        r.setMessage(msg);
        return r;
    }

    private static <T> ApiResponse<T> deniedRoster(String msg) {
        return denied(msg);
    }

    private static String str(Map<String, Object> m, String k) {
        return str(m, k, null);
    }

    private static String str(Map<String, Object> m, String k, String def) {
        Object v = m == null ? null : m.get(k);
        if (v == null) return def;
        String s = String.valueOf(v);
        return s.isBlank() ? def : s;
    }

    private static String blankToNull(String s) {
        return (s == null || s.isBlank()) ? null : s;
    }
}