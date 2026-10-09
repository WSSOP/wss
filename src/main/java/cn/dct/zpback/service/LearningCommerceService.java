package cn.dct.zpback.service;

import cn.dct.zpback.entity.*;
import cn.dct.zpback.exception.LearningValidationException;
import cn.dct.zpback.mapper.LearningEntitlementMapper;
import cn.dct.zpback.mapper.LearningEnrollmentMapper;
import cn.dct.zpback.mapper.LearningOrderMapper;
import cn.dct.zpback.mapper.LearningSessionMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;

/**
 * 问星渡｜Commerce Core
 *
 * 对应文档 01_Developer_Package：「Commerce Core统一处理交易」，
 * 14_API_Contract：「Commerce Core提供交易接口」，
 * 04_Final_System_Map 的链路：Order → Payment → Fulfillment。
 *
 * <b>核心边界</b>：Payment 不直接创建业务权限。
 * markPaid 只把订单推到 PAID；权限由 fulfillOrder 单独发放。
 * 两者是<b>两个方法、两个可观测步骤</b>，谁都不能合并。
 * 好处：交付中断可一键补发，且用户「付了钱没权限」时能定位是哪一步坏了。
 *
 * 订单状态机：
 *   CREATED → PAID → FULFILLED
 *   CREATED → CANCELLED
 *   FULFILLED → REFUNDED（同时撤销权限）
 *
 * <b>规格来源</b>：原始 skill 目录共 3993 字节，
 * 与本类相关的只有「Commerce Core统一处理交易」与
 * 「Payment成功后通过Fulfillment触发业务交付」两条。
 * 状态机命名为实现方所拟，<b>不是原始规格</b>。
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class LearningCommerceService {

    private final LearningOrderMapper orderMapper;
    private final LearningSessionMapper sessionMapper;
    private final LearningEntitlementMapper entitlementMapper;
    private final LearningEnrollmentMapper enrollmentMapper;
    private final LearningCourseService courseService;
    private final LearningEventService eventService;

    public static final String E_NOT_FOUND = "LEARNING_TARGET_NOT_FOUND";
    public static final String E_NO_PERMISSION = "LEARNING_NO_ENTITLEMENT";
    public static final String E_ORDER_STATE = "LEARNING_ORDER_STATE_INVALID";
    public static final String E_REQUIRES_ORDER = "LEARNING_REQUIRES_ORDER";
    public static final String E_FREE_ITEM = "LEARNING_FREE_ITEM";

    private static final String TYPE_COURSE = "COURSE";
    private static final String TYPE_EVENT = "EVENT";

    // ==============================================================
    // Order
    // ==============================================================

    /**
     * 创建订单。幂等：同一用户对同一目标/场次已有未关闭订单时返回既有订单，
     * 用户连点两次不会看到两笔待付。
     */
    @Transactional
    public Map<String, Object> createOrder(String targetType, String targetId,
                                           String sessionId, String userId, String source) {
        if (userId == null || userId.isBlank() || "anonymous".equals(userId)) {
            throw new LearningValidationException(E_NO_PERMISSION, "请先登录后再下单");
        }
        LearningSession session = resolveSession(targetType, targetId, sessionId);

        if (!isPaid(session)) {
            throw new LearningValidationException(E_FREE_ITEM, "该内容免费，直接报名即可，无需下单");
        }

        LearningOrder open = orderMapper.findOpenOrder(userId, targetType, targetId, sessionId);
        if (open != null) return orderView(open, true);

        LearningOrder o = new LearningOrder();
        o.setOrderId(UUID.randomUUID().toString().replace("-", ""));
        o.setOrderNo(generateOrderNo());
        o.setUserId(userId);
        o.setOrderType(targetType);
        o.setTargetId(targetId);
        o.setSessionId(sessionId);
        // 标题快照：运营后续改价不影响历史订单展示
        o.setTargetTitle(resolveTitle(targetType, targetId, session));
        o.setAmount(session.getPrice());
        o.setPaidAmount(BigDecimal.ZERO);
        o.setCurrency("CNY");
        o.setOrderStatus("CREATED");
        o.setPaymentChannel(null);
        o.setSource(source == null ? "COURSE_LIST" : source);
        orderMapper.insert(o);

        return orderView(o, false);
    }

    @Transactional
    public Map<String, Object> cancelOrder(String orderId, String userId) {
        LearningOrder o = orderMapper.findById(orderId);
        if (o == null) throw new LearningValidationException(E_NOT_FOUND, "订单不存在");
        if (!Objects.equals(o.getUserId(), userId)) {
            throw new LearningValidationException(E_NO_PERMISSION, "只能操作自己的订单");
        }
        if ("CANCELLED".equals(o.getOrderStatus())) return orderView(o, true);
        if (!"CREATED".equals(o.getOrderStatus())) {
            throw new LearningValidationException(E_ORDER_STATE,
                    "只有未支付订单可以取消，当前状态：" + o.getOrderStatus());
        }
        orderMapper.cancel(orderId);
        return orderView(orderMapper.findById(orderId), false);
    }

    // ==============================================================
    // Payment —— 只改状态，绝不发权限
    // ==============================================================

    /**
     * 支付成功回调。
     *
     * <b>关键边界</b>：本方法只把订单推到 PAID，<b>绝不</b>发权限。
     * 权限由 {@link #fulfillOrder} 单独发放（原始文档明确要求
     * "Payment 不直接创建业务权限"）。
     *
     * 幂等：markPaid 带 order_status='CREATED' 条件，
     * 第三方平台重试回调只会生效一次。
     */
    @Transactional
    public Map<String, Object> markPaid(String orderId, String channel, BigDecimal paidAmount) {
        LearningOrder o = orderMapper.findById(orderId);
        if (o == null) throw new LearningValidationException(E_NOT_FOUND, "订单不存在");

        if ("PAID".equals(o.getOrderStatus()) || "FULFILLED".equals(o.getOrderStatus())) {
            return orderView(o, true);   // 已支付过，不重复发货
        }
        if (!"CREATED".equals(o.getOrderStatus())) {
            throw new LearningValidationException(E_ORDER_STATE,
                    "订单当前状态无法完成支付：" + o.getOrderStatus());
        }

        int n = orderMapper.markPaid(orderId, channel,
                paidAmount == null ? o.getAmount() : paidAmount);
        if (n == 0) {
            // 并发下被另一笔回调抢先，属正常竞态
            return orderView(orderMapper.findById(orderId), true);
        }
        return orderView(orderMapper.findById(orderId), false);
    }

    // ==============================================================
    // Fulfillment —— 独立的交付步骤
    // ==============================================================

    /**
     * Fulfillment：把 PAID 订单交付成可用权限。
     *
     * 与 markPaid <b>分开</b>是原始文档的硬要求，理由见上。
     * 幂等：先按 order_id 查是否已发过权限，已发则只补订单状态。
     */
    @Transactional
    public Map<String, Object> fulfillOrder(String orderId) {
        LearningOrder o = orderMapper.findById(orderId);
        if (o == null) throw new LearningValidationException(E_NOT_FOUND, "订单不存在");

        if ("FULFILLED".equals(o.getOrderStatus())) return orderView(o, true);
        if (!"PAID".equals(o.getOrderStatus())) {
            throw new LearningValidationException(E_ORDER_STATE,
                    "只有已支付订单才能发放权限，当前状态：" + o.getOrderStatus());
        }

        grantEntitlement(o);
        autoEnroll(o);
        orderMapper.markFulfilled(orderId);
        return orderView(orderMapper.findById(orderId), true);
    }

    /**
     * Fulfillment 巡检：补发「已支付但未交付」的订单。
     *
     * 支付与交付是两个独立步骤，中间可能因进程重启、DB 抖动中断。
     * 让运维能一键补齐，不必人工逐单核对。
     */
    @Transactional
    public int reFulfillPending(int limit) {
        List<LearningOrder> pending = orderMapper.findPendingFulfillment(limit);
        int done = 0;
        for (LearningOrder o : pending) {
            try {
                grantEntitlement(o);
                autoEnroll(o);
                orderMapper.markFulfilled(o.getOrderId());
                done++;
            } catch (RuntimeException ex) {
                // 单单失败不阻断整批：坏订单留下日志，其余继续处理
                log.warn("Fulfillment 补发失败 order={}: {}", o.getOrderId(), ex.getMessage());
            }
        }
        return done;
    }

    // ==============================================================
    // Refund
    // ==============================================================

    /**
     * 退款：订单转 REFUNDED，并撤销该订单发放的权限。
     *
     * 权限<b>只改状态不删除</b>——财务对账与客服查询都需要看到
     * 「这笔订单曾发过权限，后因退款被收回」。
     *
     * 未支付订单不能退款；重复退款幂等。
     */
    @Transactional
    public Map<String, Object> refundOrder(String orderId, String reason) {
        LearningOrder o = orderMapper.findById(orderId);
        if (o == null) throw new LearningValidationException(E_NOT_FOUND, "订单不存在");

        if ("REFUNDED".equals(o.getOrderStatus())) {
            return refundView(o, 0, true);   // 幂等
        }
        if (!"PAID".equals(o.getOrderStatus()) && !"FULFILLED".equals(o.getOrderStatus())) {
            throw new LearningValidationException(E_ORDER_STATE,
                    "只有已支付订单才能退款，当前状态：" + o.getOrderStatus());
        }

        // 先撤权限再改订单状态：任一步失败都回滚，不会出现「已退款但权限还在」
        int revoked = entitlementMapper.revokeByOrderId(orderId);
        releaseEnrollment(o);
        if (orderMapper.refund(orderId) == 0) {
            throw new LearningValidationException(E_ORDER_STATE, "退款失败，订单状态已变更");
        }
        o.setRemark(reason);
        return refundView(orderMapper.findById(orderId), revoked, false);
    }

    /**
     * 退款同时取消报名并释放名额。
     *
     * <b>不撤销的话会漏</b>：直播入口校验的是报名关系而不是权限，
     * 只把 entitlement 置 REVOKED，用户退完照样能进直播间。
     *
     * 幂等：只处理 ENROLLED；已取消或已出勤的不动。
     * 场次若已结束就不释放名额——那时候名额已不参与竞争，
     * 再减会把历史数据改错。
     */
    private void releaseEnrollment(LearningOrder o) {
        try {
            if (o.getSessionId() == null) return;
            LearningEnrollment e = enrollmentMapper.findExisting(o.getUserId(), o.getSessionId());
            if (e == null || !"ENROLLED".equals(e.getEnrollmentStatus())) return;

            if (enrollmentMapper.cancel(o.getUserId(), o.getSessionId()) == 0) return;

            LearningSession s = TYPE_COURSE.equals(o.getOrderType())
                    ? sessionMapper.findCourseSessionById(o.getSessionId())
                    : sessionMapper.findEventSessionById(o.getSessionId());
            // 场次已结束/取消：名额早已冻结，不再变动
            if (s == null || (!"SCHEDULED".equals(s.getSessionStatus())
                    && !"ONGOING".equals(s.getSessionStatus()))) {
                return;
            }
            if (TYPE_COURSE.equals(o.getOrderType())) {
                sessionMapper.releaseCourseSeat(o.getSessionId());
            } else {
                sessionMapper.releaseEventSeat(o.getSessionId());
            }
        } catch (RuntimeException ex) {
            log.warn("退款后取消报名失败 order={}: {}", o.getOrderId(), ex.getMessage());
        }
    }

    // ==============================================================
    // 我的订单
    // ==============================================================

    public List<LearningOrder> myOrders(String userId, int limit) {
        return userId == null ? List.of() : orderMapper.findByUser(userId, limit);
    }

    /**
     * 我的学习：课程报名 + 活动报名合并成一张列表。
     *
     * 两条查询<b>在 Java 侧合并</b>，不在 SQL 侧 JOIN——
     * 文档要求 Domain 隔离，跨 Domain 的 JOIN 会把两个域耦在一起，
     * 将来任一域改表结构都会牵连另一边。
     *
     * 排序最近的场次在最前；start_at 为 null 的排到最后且不抛 NPE。
     */
    public List<MyLearningRow> myLearning(String userId) {
        if (userId == null || userId.isBlank() || "anonymous".equals(userId)) {
            return List.of();
        }
        List<MyLearningRow> all = new ArrayList<>();
        all.addAll(sessionMapper.findMyCourseEnrollments(userId));
        all.addAll(sessionMapper.findMyEventEnrollments(userId));

        // 最近的场次排在最前 → 降序；null 恒排最后。
        //
        // 两个顺序不能写反：先 nullsLast 再 reversed 会把 null 甩到最前
        // （reverse 作用在整个比较器上，nullsLast 的判断也被一并反转）。
        // 正确写法是 reversed 包在内层——内层只反转"有值的那些"的比较结果，
        // 外层 nullsLast 仍然保证 null 落底。
        all.sort(Comparator.comparing(MyLearningRow::getStartAt,
                Comparator.nullsLast(Comparator.<LocalDateTime>naturalOrder().reversed())));
        return all;
    }

    // ==============================================================
    // 内部
    // ==============================================================

    /** 发放权限（幂等：同一订单只发一次） */
    private void grantEntitlement(LearningOrder o) {
        if (entitlementMapper.findByOrderId(o.getOrderId()) != null) return;
        LearningEntitlement e = new LearningEntitlement();
        e.setEntitlementId(UUID.randomUUID().toString().replace("-", ""));
        e.setUserId(o.getUserId());
        e.setTargetType(o.getOrderType());
        e.setTargetId(o.getTargetId());
        e.setSessionId(o.getSessionId());
        e.setEntitlementStatus("ACTIVE");
        e.setSource("ORDER_PAID");
        e.setOrderId(o.getOrderId());
        e.setGrantedAt(LocalDateTime.now());
        try {
            entitlementMapper.insert(e);
        } catch (RuntimeException ex) {
            // 唯一索引冲突：同用户已持有该权限，只补订单状态即可
            log.debug("权限已存在（并发），仅补订单状态：order={}", o.getOrderId());
        }
    }

    /**
     * 交付时自动占位：付费购买后必须同时有报名记录。
     *
     * <b>为什么必须有</b>：直播入口 /sessions/{id}/live 校验的是
     * <b>报名关系</b>（JOIN learning_enrollment），不是 entitlement。
     * 只发权限不建报名，用户付了钱却仍然打不开直播间，
     * 还会在客服侧变成「已支付但无法上课」的疑难单。
     *
     * 名额已满或场次已结束时<b>不阻断交付</b>：
     * 用户已经付了钱，权限必须给；报名占位失败只记日志，
     * 由运营人工安排。宁可「有权限没报名」，也不能「没权限」。
     *
     * 幂等：已有报名记录时直接返回。
     */
    private void autoEnroll(LearningOrder o) {
        try {
            if (enrollmentMapper.findExisting(o.getUserId(), o.getSessionId()) != null) return;

            int occupied = TYPE_COURSE.equals(o.getOrderType())
                    ? sessionMapper.occupyCourseSeat(o.getSessionId())
                    : sessionMapper.occupyEventSeat(o.getSessionId());
            if (occupied == 0) {
                log.warn("交付已发放权限但名额已满，需人工安排：order={} session={}",
                        o.getOrderId(), o.getSessionId());
                return;
            }

            LearningEnrollment e = new LearningEnrollment();
            e.setEnrollmentId(UUID.randomUUID().toString().replace("-", ""));
            e.setUserId(o.getUserId());
            e.setSessionId(o.getSessionId());
            e.setTargetType(o.getOrderType());
            e.setEnrollmentStatus("ENROLLED");
            enrollmentMapper.insert(e);
        } catch (RuntimeException ex) {
            // 交付链路的硬要求是「权限已发放」；报名失败不推翻交付结果
            log.warn("交付后自动报名失败 order={}: {}", o.getOrderId(), ex.getMessage());
        }
    }

    /** 按类型分发到对应 Domain Service 校验场次归属 */
    private LearningSession resolveSession(String targetType, String targetId, String sessionId) {
        if (TYPE_COURSE.equals(targetType)) {
            return courseService.requireSessionForOrder(targetId, sessionId);
        }
        if (TYPE_EVENT.equals(targetType)) {
            return eventService.requireSessionForOrder(targetId, sessionId);
        }
        throw new LearningValidationException(E_NOT_FOUND, "内容类型不合法");
    }

    private String resolveTitle(String targetType, String targetId, LearningSession session) {
        return TYPE_COURSE.equals(targetType)
                ? courseService.courseTitle(targetId, session)
                : eventService.eventTitle(targetId, session);
    }

    private static boolean isPaid(LearningSession s) {
        return s.getPrice() != null && s.getPrice().compareTo(BigDecimal.ZERO) > 0;
    }

    private Map<String, Object> orderView(LearningOrder o, boolean reused) {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("order_id", o.getOrderId());
        m.put("order_no", o.getOrderNo());
        m.put("order_type", o.getOrderType());
        m.put("target_id", o.getTargetId());
        m.put("session_id", o.getSessionId());
        m.put("target_title", o.getTargetTitle());
        m.put("amount", o.getAmount());
        m.put("currency", o.getCurrency());
        m.put("order_status", o.getOrderStatus());
        // 支付渠道未接入前不给假支付链接，只告诉前端当前状态
        m.put("payment_required", "CREATED".equals(o.getOrderStatus()));
        m.put("reused", reused);
        return m;
    }

    private Map<String, Object> refundView(LearningOrder o, int revoked, boolean already) {
        Map<String, Object> m = new LinkedHashMap<>(orderView(o, false));
        m.put("entitlements_revoked", revoked);
        m.put("already_refunded", already);
        return m;
    }

    /** 订单号：yyyyMMddHHmmss + 6 位随机 */
    private static String generateOrderNo() {
        return DateTimeFormatter.ofPattern("yyyyMMddHHmmss").format(LocalDateTime.now())
                + String.format("%06d", (int) (Math.random() * 1_000_000));
    }
}