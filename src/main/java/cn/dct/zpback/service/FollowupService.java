package cn.dct.zpback.service;

import cn.dct.zpback.dto.FollowupRequestDTO;
import cn.dct.zpback.dto.QuoteRequestDTO;
import cn.dct.zpback.entity.*;
import cn.dct.zpback.mapper.*;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.*;

/**
 * 报告追问核心业务服务
 * 实现交付包 REPORT_FOLLOWUP V1 的完整流程：
 * Precheck → Router → Quote → Reserve → Retrieve+Generate → Validate → Consume/Release
 */
@Service
@Slf4j
@RequiredArgsConstructor
public class FollowupService {

    private final ReportInstanceMapper reportInstanceMapper;
    private final ConversationMapper conversationMapper;
    private final FollowupRequestMapper followupRequestMapper;
    private final EntitlementLedgerMapper entitlementLedgerMapper;
    private final ReportGiftEntitlementMapper reportGiftEntitlementMapper;
    private final UserMapper userMapper;
    private final RedisService redisService;

    @Value("${wenxingdu.report-followup.quick-star-cost:19}")
    private int quickStarCost;

    @Value("${wenxingdu.report-followup.deep-star-cost:39}")
    private int deepStarCost;

    @Value("${wenxingdu.report-followup.session-hours:48}")
    private int sessionHours;

    @Value("${wenxingdu.report-followup.gift-count:1}")
    private int giftCount;

    // ============================================================
    // 1. Precheck — 预校验
    // ============================================================

    /**
     * 预校验：检查绑定、权限、问题数量、会话一致性
     * @return null=通过，否则返回阻塞原因
     */
    public String precheck(QuoteRequestDTO dto, String userId) {
        // 1. 报告是否存在
        ReportInstance report = reportInstanceMapper.selectById(dto.getReportInstanceId());
        if (report == null) {
            return "REPORT_REQUIRED";
        }

        // 2. 所有权/授权检查
        if (!userId.equals(report.getUserId())) {
            return "REPORT_ACCESS_DENIED";
        }

        // 3. 报告是否可读
        if (!Boolean.TRUE.equals(report.getFollowupAvailable())) {
            return "REPORT_UNAVAILABLE";
        }

        // 4. 空问题检查
        if (dto.getQuestion() == null || dto.getQuestion().trim().isEmpty()) {
            return "QUESTION_REQUIRED";
        }

        // 5. 会话一致性检查
        if (dto.getConversationId() != null && !dto.getConversationId().isEmpty()) {
            Conversation conv = conversationMapper.selectById(dto.getConversationId());
            if (conv != null && !dto.getReportInstanceId().equals(conv.getReportInstanceId())) {
                return "SESSION_REPORT_MISMATCH";
            }
        }

        // 6. 多问题过载检测
        if (isMultiQuestionOverload(dto.getQuestion())) {
            return "MULTI_QUESTION_OVERLOAD";
        }

        return null; // 通过
    }

    /**
     * 检测是否包含多个独立问题
     */
    private boolean isMultiQuestionOverload(String question) {
        int questionCount = 0;
        for (char c : question.toCharArray()) {
            if (c == '？' || c == '?') {
                questionCount++;
            }
        }
        return questionCount > 2 && question.length() > 100;
    }

    // ============================================================
    // 2. Router — 意图路由
    // ============================================================

    /**
     * 路由用户问题，判断意图
     * @return 意图字符串
     */
    public String routeQuestion(String question, String reportType) {
        if (isOutOfScope(question, reportType)) {
            return "OUT_OF_REPORT_SCOPE";
        }
        if (isEvidenceQuestion(question)) {
            return "REPORT_EVIDENCE";
        }
        if (isDeepAnalysisQuestion(question)) {
            return "DEEP_ANALYSIS";
        }
        return "REPORT_EXPLANATION";
    }

    private boolean isOutOfScope(String question, String reportType) {
        if ("NATAL_RELATIONSHIP".equals(reportType) &&
                (question.contains("事业") || question.contains("换工作") || question.contains("职业"))) {
            return true;
        }
        if (question.contains("我们") || question.contains("对方") || question.contains("他/她")) {
            if (!"SYNASTRY_RELATIONSHIP".equals(reportType)) {
                return true;
            }
        }
        return false;
    }

    private boolean isEvidenceQuestion(String question) {
        return question.contains("为什么") || question.contains("依据") ||
                question.contains("证据") || question.contains("配置") ||
                question.contains("凭什么") || question.contains("根据什么");
    }

    private boolean isDeepAnalysisQuestion(String question) {
        return question.contains("方向") || question.contains("顺序") ||
                question.contains("优先") || question.contains("哪个") ||
                question.contains("应该怎样") || question.contains("如何安排") ||
                question.contains("怎么做");
    }

    public String getRouteTarget(String question) {
        if (question.contains("事业") || question.contains("职业")) return "NATAL";
        if (question.contains("关系") || question.contains("合盘")) return "SYNASTRY";
        if (question.contains("决定") || question.contains("选择")) return "PRASHNA";
        if (question.contains("概念") || question.contains("什么是")) return "KNOWLEDGE_QA";
        return "EMOTIONAL_SUPPORT";
    }

    // ============================================================
    // 3. Quote — 询价
    // ============================================================

    /**
     * 询价：确定权益来源和价格
     */
    public Map<String, Object> quote(QuoteRequestDTO dto, String userId) {
        Map<String, Object> result = new HashMap<>();

        String intent = routeQuestion(dto.getQuestion(), getReportType(dto.getReportInstanceId()));
        if ("OUT_OF_REPORT_SCOPE".equals(intent)) {
            result.put("passed", true);
            result.put("intent", "OUT_OF_REPORT_SCOPE");
            result.put("request_status", "TRANSFERRED_NO_CHARGE");
            result.put("route_target", getRouteTarget(dto.getQuestion()));
            result.put("message", "这个问题已经超出当前报告范围，更适合前往对应功能继续分析，本次不会扣除追问次数或星印。");
            result.put("entitlement", buildEntitlementMap("NONE", "NOT_REQUIRED", false, 0));
            return result;
        }

        String entitlementSource = resolveEntitlementSource(dto.getReportInstanceId(), userId);
        int starCost = calculateStarCost(entitlementSource, dto.getRequestedMode());

        result.put("passed", true);
        result.put("request_status", "QUOTED");
        result.put("intent", intent);
        result.put("requested_mode", dto.getRequestedMode());
        result.put("source", entitlementSource);
        result.put("star_cost", starCost);
        result.put("quote_token", UUID.randomUUID().toString());
        result.put("message", "询价成功");

        return result;
    }

    private String resolveEntitlementSource(String reportInstanceId, String userId) {
        ReportGiftEntitlement gift = reportGiftEntitlementMapper.selectByReportId(reportInstanceId);
        if (gift != null && !Boolean.TRUE.equals(gift.getUsed())) {
            return "REPORT_GIFT";
        }
        User user = userMapper.selectById(userId);
        if (user != null && user.getDailyFreeRemaining() != null && user.getDailyFreeRemaining() > 0) {
            return "DAILY_FREE";
        }
        return "RECHARGED_STAR";
    }

    private int calculateStarCost(String entitlementSource, String mode) {
        if ("REPORT_GIFT".equals(entitlementSource) || "DAILY_FREE".equals(entitlementSource)) {
            return 0;
        }
        return "QUICK".equals(mode) ? quickStarCost : deepStarCost;
    }

    // ============================================================
    // 4. Submit — 提交请求
    // ============================================================

    @Transactional
    public Map<String, Object> submit(FollowupRequestDTO dto, String userId) {
        // 幂等检查
        FollowupRequest existing = followupRequestMapper.selectByIdempotencyKey(dto.getIdempotencyKey());
        if (existing != null) {
            return buildResponse(existing);
        }

        ReportInstance report = reportInstanceMapper.selectById(dto.getReportInstanceId());
        if (report == null) {
            throw new IllegalArgumentException("REPORT_REQUIRED");
        }

        String intent = routeQuestion(dto.getQuestion(), report.getReportType());

        if ("OUT_OF_REPORT_SCOPE".equals(intent)) {
            return buildOutOfScopeResponse(dto, userId);
        }

        Conversation conversation = getOrCreateConversation(dto, userId, report);

        String entitlementSource = resolveEntitlementSource(dto.getReportInstanceId(), userId);
        int starCost = calculateStarCost(entitlementSource, dto.getRequestedMode());

        boolean reserved = reserveEntitlement(userId, entitlementSource, starCost, dto.getRequestId());
        if (!reserved) {
            return buildBlockedResponse(dto, "ENTITLEMENT_INSUFFICIENT", "当前次数或星印不足");
        }

        FollowupRequest request = new FollowupRequest();
        request.setRequestId(dto.getRequestId());
        request.setIdempotencyKey(dto.getIdempotencyKey());
        request.setUserId(userId);
        request.setConversationId(conversation.getConversationId());
        request.setSourceContext(dto.getSourceContext());
        request.setReportInstanceId(dto.getReportInstanceId());
        request.setQuestion(dto.getQuestion());
        request.setRequestedMode(dto.getRequestedMode());
        request.setAnswerMode(dto.getRequestedMode());
        request.setIntent(intent);
        request.setRequestStatus("GENERATING");
        request.setEntitlementSource(entitlementSource);
        request.setLedgerStatus("RESERVED");
        request.setConsumed(false);
        request.setStarCost(starCost);
        request.setCreatedAt(LocalDateTime.now());
        followupRequestMapper.insert(request);

        Map<String, Object> answer = generateAnswer(dto, report, intent);
        boolean valid = validateAnswer(answer, dto.getRequestedMode());

        if (valid) {
            request.setAnswerContent(toJson(answer));
            request.setAnswerStatus("COMPLETED");
            request.setRequestStatus("COMPLETED");
            request.setCompletedAt(LocalDateTime.now());
            request.setConsumed(true);
            request.setLedgerStatus("CONSUMED");
            followupRequestMapper.updateStatus(request.getRequestId(), "COMPLETED", "COMPLETED");
            followupRequestMapper.updateAnswer(request.getRequestId(), toJson(answer), "COMPLETED");

            consumeEntitlement(userId, entitlementSource, starCost, dto.getReportInstanceId(), dto.getRequestId());

            conversation.setTurnCount(conversation.getTurnCount() + 1);
            conversation.setLastQuestion(dto.getQuestion());
            conversation.setLastActiveAt(LocalDateTime.now());
            conversationMapper.update(conversation);

            return buildResponse(request, answer);
        } else {
            releaseEntitlement(userId, entitlementSource, starCost, dto.getRequestId());
            request.setRequestStatus("FAILED_VALIDATION");
            request.setAnswerStatus("FAILED_VALIDATION");
            request.setLedgerStatus("RELEASED");
            followupRequestMapper.updateStatus(request.getRequestId(), "FAILED_VALIDATION", "FAILED_VALIDATION");

            Map<String, Object> failResult = new HashMap<>();
            failResult.put("message", "本次回答未通过质量检查，已为你保留次数或返还星印");
            return buildResponse(request, failResult);
        }
    }

    // ============================================================
    // 5. 生成回答
    // ============================================================

    private Map<String, Object> generateAnswer(FollowupRequestDTO dto, ReportInstance report, String intent) {
        Map<String, Object> answer = new HashMap<>();
        answer.put("intent", intent);
        answer.put("answer_mode", dto.getRequestedMode());
        answer.put("answer_status", "COMPLETED");

        switch (intent) {
            case "REPORT_EXPLANATION":
                answer.put("sections", generateExplanationSections(dto, report));
                break;
            case "REPORT_EVIDENCE":
                answer.put("sections", generateEvidenceSections(dto, report));
                break;
            case "DEEP_ANALYSIS":
                answer.put("sections", generateDeepAnalysisSections(dto, report));
                answer.put("answer_claims", generateAnswerClaims(dto, report));
                break;
            default:
                answer.put("sections", generateExplanationSections(dto, report));
        }

        answer.put("source_refs", generateSourceRefs(report));

        Map<String, Object> version = new HashMap<>();
        version.put("report_version", report.getReportVersion());
        version.put("source_report_skill_version", report.getSkillVersion());
        version.put("followup_skill_version", "1.0.0");
        version.put("model_version", "wenxingdu-ai-v1");
        answer.put("version", version);

        return answer;
    }

    private List<Map<String, Object>> generateExplanationSections(FollowupRequestDTO dto, ReportInstance report) {
        Map<String, Object> section = new HashMap<>();
        section.put("section_key", "DIRECT_ANSWER");
        section.put("title", "直接回答");
        section.put("content", generateMockExplanation(dto.getQuestion(), report.getReportType()));
        section.put("order", 1);
        List<Map<String, Object>> list = new ArrayList<>();
        list.add(section);
        return list;
    }

    private List<Map<String, Object>> generateEvidenceSections(FollowupRequestDTO dto, ReportInstance report) {
        List<Map<String, Object>> list = new ArrayList<>();

        Map<String, Object> s1 = new HashMap<>();
        s1.put("section_key", "DIRECT_ANSWER");
        s1.put("title", "证据说明");
        s1.put("content", "这一判断主要基于原报告已注册的证据链路，包括本命盘中相关行星配置、宫位影响以及已经记录的支持/限定关系。");
        s1.put("order", 1);
        list.add(s1);

        Map<String, Object> s2 = new HashMap<>();
        s2.put("section_key", "EVIDENCE_DETAIL");
        s2.put("title", "已注册证据");
        s2.put("content", "具体配置属于受控专业信息，这里只展示与你直接相关的结论摘要。");
        s2.put("order", 2);
        list.add(s2);

        return list;
    }

    private List<Map<String, Object>> generateDeepAnalysisSections(FollowupRequestDTO dto, ReportInstance report) {
        List<Map<String, Object>> list = new ArrayList<>();

        Map<String, Object> s1 = new HashMap<>();
        s1.put("section_key", "DIRECT_ANSWER");
        s1.put("title", "直接回答");
        s1.put("content", "在这份报告已经确认的能力结构下，更适合先用内容咨询继续验证需求，再逐步沉淀为课程产品。");
        s1.put("order", 1);
        list.add(s1);

        Map<String, Object> s2 = new HashMap<>();
        s2.put("section_key", "EVIDENCE_AND_CONDITIONS");
        s2.put("title", "为什么这样安排");
        s2.put("content", "原报告支持你通过表达、拆解和持续积累建立专业影响力；本次进一步分析显示，先保留一对一反馈回路更有利于确认用户真正愿意付费的问题。");
        s2.put("order", 2);
        list.add(s2);

        Map<String, Object> s3 = new HashMap<>();
        s3.put("section_key", "ACTION");
        s3.put("title", "可以怎么做");
        s3.put("content", "先用一个限定主题的咨询或小型工作坊运行四至六周，记录高频问题和有效方法；当内容重复度与结果稳定性提高后，再把它整理成课程骨架。");
        s3.put("order", 3);
        list.add(s3);

        return list;
    }

    private List<Map<String, Object>> generateAnswerClaims(FollowupRequestDTO dto, ReportInstance report) {
        Map<String, Object> claim = new HashMap<>();
        claim.put("answer_claim_id", "FAC_" + System.currentTimeMillis());
        claim.put("source_report_instance_id", report.getReportInstanceId());
        claim.put("topic", "深度追问主题");
        claim.put("status", "CONDITIONAL");
        claim.put("confidence", "MEDIUM");
        claim.put("summary", "先咨询验证需求，再课程化放大更稳妥");
        claim.put("qualifiers", Collections.singletonList("这是优先顺序，不代表课程不适合"));
        claim.put("source_skill_version_used", report.getSkillVersion());
        claim.put("input_snapshot_id", report.getDataSnapshotId());
        List<Map<String, Object>> list = new ArrayList<>();
        list.add(claim);
        return list;
    }

    private List<Map<String, Object>> generateSourceRefs(ReportInstance report) {
        List<Map<String, Object>> list = new ArrayList<>();

        Map<String, Object> r1 = new HashMap<>();
        r1.put("type", "REPORT_CLAIM");
        r1.put("ref_id", "C02");
        r1.put("section_id", "S01");
        r1.put("display_title", "核心人生课题");
        list.add(r1);

        Map<String, Object> r2 = new HashMap<>();
        r2.put("type", "REPORT_RECOMMENDATION");
        r2.put("ref_id", "R03");
        r2.put("section_id", "S09");
        r2.put("display_title", "成长建议");
        list.add(r2);

        return list;
    }

    private String generateMockExplanation(String question, String reportType) {
        return "报告里的\"稳定\"指关系或生活需要有可预期的基础，\"空间\"则指在这个基础上仍保留自主决定和调整节奏的余地。真正容易形成拉扯的，不是你同时需要两者，而是当稳定被理解成控制，或空间被误解成疏离。";
    }

    // ============================================================
    // 6. 校验
    // ============================================================

    private boolean validateAnswer(Map<String, Object> answer, String mode) {
        if (answer == null || answer.isEmpty()) return false;

        List<Map<String, Object>> sections = (List<Map<String, Object>>) answer.get("sections");
        if (sections == null || sections.isEmpty()) return false;

        int minSections = "QUICK".equals(mode) ? 1 : 3;
        return sections.size() >= minSections;
    }

    // ============================================================
    // 7. 权益管理
    // ============================================================

    private boolean reserveEntitlement(String userId, String source, int cost, String requestId) {
        if ("REPORT_GIFT".equals(source) || "DAILY_FREE".equals(source)) return true;

        User user = userMapper.selectById(userId);
        if (user == null || user.getStarBalance() == null || user.getStarBalance() < cost) {
            return false;
        }

        userMapper.updateStarBalance(userId, -cost);

        EntitlementLedger ledger = new EntitlementLedger();
        ledger.setRequestId(requestId);
        ledger.setUserId(userId);
        ledger.setLedgerActionType("RESERVE");
        ledger.setEntitlementSource(source);
        ledger.setLedgerStatus("RESERVED");
        ledger.setStarAmount(cost);
        ledger.setBalanceBefore(user.getStarBalance());
        ledger.setBalanceAfter(user.getStarBalance() - cost);
        ledger.setRemark("预占星印" + cost);
        entitlementLedgerMapper.insert(ledger);

        return true;
    }

    private void consumeEntitlement(String userId, String source, int cost, String reportInstanceId, String requestId) {
        if ("REPORT_GIFT".equals(source)) {
            ReportGiftEntitlement gift = reportGiftEntitlementMapper.selectByReportId(reportInstanceId);
            if (gift != null) {
                reportGiftEntitlementMapper.markUsed(gift.getId(), requestId);
            }
        } else if ("DAILY_FREE".equals(source)) {
            User user = userMapper.selectById(userId);
            if (user != null && user.getDailyFreeRemaining() != null && user.getDailyFreeRemaining() > 0) {
                userMapper.updateDailyFree(userId, user.getDailyFreeRemaining() - 1);
            }
        }

        EntitlementLedger ledger = new EntitlementLedger();
        ledger.setRequestId(requestId);
        ledger.setUserId(userId);
        ledger.setReportInstanceId(reportInstanceId);
        ledger.setLedgerActionType("CONSUME");
        ledger.setEntitlementSource(source);
        ledger.setLedgerStatus("CONSUMED");
        ledger.setStarAmount(cost);
        ledger.setRemark("核销");
        entitlementLedgerMapper.insert(ledger);
    }

    private void releaseEntitlement(String userId, String source, int cost, String requestId) {
        if ("RECHARGED_STAR".equals(source) && cost > 0) {
            User user = userMapper.selectById(userId);
            if (user != null) {
                userMapper.updateStarBalance(userId, cost);
            }
        }

        EntitlementLedger ledger = new EntitlementLedger();
        ledger.setRequestId(requestId);
        ledger.setUserId(userId);
        ledger.setLedgerActionType("RELEASE");
        ledger.setEntitlementSource(source);
        ledger.setLedgerStatus("RELEASED");
        ledger.setStarAmount(cost);
        ledger.setRemark("释放/返还");
        entitlementLedgerMapper.insert(ledger);
    }

    // ============================================================
    // 8. 会话管理
    // ============================================================

    private Conversation getOrCreateConversation(FollowupRequestDTO dto, String userId, ReportInstance report) {
        if (dto.getConversationId() != null && !dto.getConversationId().isEmpty()) {
            Conversation conv = conversationMapper.selectById(dto.getConversationId());
            if (conv != null && "ACTIVE".equals(conv.getStatus())) {
                return conv;
            }
        }

        Conversation activeConv = conversationMapper.selectActiveByReportId(dto.getReportInstanceId());
        if (activeConv != null) {
            return activeConv;
        }

        Conversation newConv = new Conversation();
        newConv.setConversationId("conv_rf_" + System.currentTimeMillis());
        newConv.setUserId(userId);
        newConv.setReportInstanceId(dto.getReportInstanceId());
        newConv.setStatus("ACTIVE");
        newConv.setTurnCount(0);
        newConv.setLastActiveAt(LocalDateTime.now());
        newConv.setExpireAt(LocalDateTime.now().plusHours(sessionHours));
        newConv.setCreatedAt(LocalDateTime.now());
        conversationMapper.insert(newConv);
        return newConv;
    }

    // ============================================================
    // 9. 响应构建
    // ============================================================

    private Map<String, Object> buildResponse(FollowupRequest request) {
        return buildResponse(request, null);
    }

    private Map<String, Object> buildResponse(FollowupRequest request, Map<String, Object> answer) {
        Map<String, Object> resp = new HashMap<>();
        resp.put("request_id", request.getRequestId());
        resp.put("request_status", request.getRequestStatus());
        resp.put("answer_status", request.getAnswerStatus());
        resp.put("intent", request.getIntent());
        resp.put("answer_mode", request.getAnswerMode());

        if (answer != null) {
            resp.put("sections", answer.get("sections"));
            resp.put("source_refs", answer.get("source_refs"));
            resp.put("answer_claims", answer.get("answer_claims"));
            resp.put("version", answer.get("version"));
        }

        resp.put("entitlement", buildEntitlementMap(
                request.getEntitlementSource(),
                request.getLedgerStatus(),
                request.getConsumed(),
                request.getStarCost()
        ));

        return resp;
    }

    private Map<String, Object> buildOutOfScopeResponse(FollowupRequestDTO dto, String userId) {
        Map<String, Object> resp = new HashMap<>();
        resp.put("request_status", "TRANSFERRED_NO_CHARGE");
        resp.put("intent", "OUT_OF_REPORT_SCOPE");
        resp.put("answer_mode", "NO_CHARGE_TRANSFER");
        resp.put("route_target", getRouteTarget(dto.getQuestion()));
        resp.put("message", "这个问题已经超出当前报告范围，更适合前往对应功能继续分析，本次不会扣除追问次数或星印。");
        resp.put("entitlement", buildEntitlementMap("NONE", "NOT_REQUIRED", false, 0));
        return resp;
    }

    private Map<String, Object> buildBlockedResponse(FollowupRequestDTO dto, String reason, String message) {
        Map<String, Object> resp = new HashMap<>();
        resp.put("request_status", "BLOCKED_PRECHARGE");
        resp.put("block_reason", reason);
        resp.put("message", message);
        resp.put("entitlement", buildEntitlementMap("NONE", "NOT_REQUIRED", false, 0));
        return resp;
    }

    /**
     * 构建权益信息 Map（替代 Map.of 以支持 null 值）
     */
    private Map<String, Object> buildEntitlementMap(String source, String ledgerStatus, Boolean consumed, Integer starCost) {
        Map<String, Object> map = new HashMap<>();
        map.put("source", source != null ? source : "NONE");
        map.put("ledger_status", ledgerStatus != null ? ledgerStatus : "NOT_REQUIRED");
        map.put("consumed", consumed != null ? consumed : false);
        map.put("star_cost", starCost != null ? starCost : 0);
        return map;
    }

    // ============================================================
    // 工具方法
    // ============================================================

    private String getReportType(String reportInstanceId) {
        ReportInstance report = reportInstanceMapper.selectById(reportInstanceId);
        return report != null ? report.getReportType() : "UNKNOWN";
    }

    private String toJson(Object obj) {
        try {
            return new ObjectMapper().writeValueAsString(obj);
        } catch (Exception e) {
            return "{}";
        }
    }
}
