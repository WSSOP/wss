package cn.dct.zpback.service.relationship;

import cn.dct.zpback.constant.NatalRelationshipConstants;
import cn.dct.zpback.entity.ReportInstance;
import cn.dct.zpback.entity.relationship.*;
import cn.dct.zpback.engine.relationship.*;
import cn.dct.zpback.mapper.ReportInstanceMapper;
import cn.dct.zpback.service.VedicAstroService;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.*;

/**
 * 单人婚恋深度报告生成服务
 * 
 * 4层架构实现：
 * 1. Canonical Astrology Data
 * 2. Evidence Engine（STEP 1-2H）
 * 3. Claim Engine（STEP 3A-3D）
 * 4. Controlled Renderer（STEP 3E）
 */
@Service
@Slf4j
public class NatalRelationshipReportService {

    @Autowired
    private ReportInstanceMapper reportInstanceMapper;

    @Autowired
    private VedicAstroService vedicAstroService;

    @Autowired
    private EvidenceEngine evidenceEngine;

    @Autowired
    private ClaimEngine claimEngine;

    @Autowired
    private ControlledRenderer renderer;

    @Autowired
    private ChartDataParser chartDataParser;

    private final ObjectMapper objectMapper;

    @Autowired
    public NatalRelationshipReportService() {
        this.objectMapper = new ObjectMapper();
        this.objectMapper.registerModule(new JavaTimeModule());
        this.objectMapper.disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS);
    }

    /**
     * 生成单人婚恋深度报告
     */
    public NatalRelationshipReportInstance generateReport(String userId, Map<String, Object> chartData,
                                                           String chartProfileId, Map<String, Object> birthParams) {
        log.info("开始生成单人婚恋深度报告: userId={}, chartProfileId={}", userId, chartProfileId);

        NatalRelationshipReportInstance report = createBaseReport(userId, chartProfileId);

        try {
            // Layer 1: 获取 Canonical Astrology Data
            CanonicalChartData canonicalData = resolveCanonicalData(chartData, birthParams);

            // Layer 2: Evidence Engine - 构建 6 类 Evidence Family
            List<Evidence> evidenceList = evidenceEngine.buildEvidence(canonicalData);
            log.info("Evidence 构建完成: total={}", evidenceList.size());

            // Layer 3: Claim Engine - 解析 38 个 Canonical Claim
            Map<String, CanonicalClaim> claims = claimEngine.resolveAllClaims(evidenceList, canonicalData);
            log.info("Claim 解析完成: total={}", claims.size());

            // 生成推荐
            List<NatalRelationshipRecommendation> recommendations = generateRecommendations(claims);

            // Layer 4: Controlled Renderer - 渲染章节
            List<NatalRelationshipSection> sections = renderer.renderSections(claims, recommendations);
            log.info("章节渲染完成: total={}", sections.size());

            // 5. 设置报告数据
            report.setSections(sections);
            report.setRecommendations(recommendations);
            report.setCoverageState(determineCoverage(claims));
            report.setCoverSummary(generateCoverSummary(claims));
            report.setClaimsSummary(generateClaimsSummary(claims));
            report.setReportStatus(NatalRelationshipConstants.STATUS_COMPLETED);

            // 6. 保存到数据库
            saveReportToDatabase(report, userId);

            log.info("单人婚恋深度报告生成完成: reportId={}, status={}", report.getReportInstanceId(), report.getReportStatus());

        } catch (Exception e) {
            log.error("单人婚恋深度报告生成失败: {}", e.getMessage(), e);
            report.setReportStatus(NatalRelationshipConstants.STATUS_FAILED);
            report.setFailureReason(e.getMessage() == null ? "报告生成失败，请稍后重试" : e.getMessage());
        }

        return report;
    }

    /**
     * Layer 1: 获取 Canonical Astrology Data
     *
     * 拿不到真实星盘数据时直接抛错：用「默认数据」生成出来的报告看起来正常，
     * 但结论与用户真实星盘无关，属于静默造假。
     */
    private CanonicalChartData resolveCanonicalData(Map<String, Object> chartData, Map<String, Object> birthParams) {
        // 调用方已传入完整星盘数据时直接解析
        if (chartData != null && !chartData.isEmpty()
                && (chartData.containsKey("D1") || chartData.containsKey("d1") || chartData.containsKey("vargas"))) {
            return chartDataParser.parse(chartData);
        }

        if (birthParams == null || birthParams.isEmpty()) {
            throw new IllegalStateException("缺少出生资料，无法排盘");
        }

        // buildVedicAstroParams 会校验出生地坐标，缺失时抛出的提示直接透传给前端
        Map<String, Object> vedicParams = chartDataParser.buildVedicAstroParams(birthParams);
        log.info("调用 VedicAstro 获取真实星盘数据: {}", vedicParams);

        Map<String, Object> rawData;
        try {
            rawData = vedicAstroService.getJyotishyaData(vedicParams);
        } catch (Exception e) {
            log.error("调用 VedicAstro 获取星盘数据失败: {}", e.getMessage(), e);
            throw new IllegalStateException("排盘服务调用失败：" + e.getMessage(), e);
        }
        if (rawData == null || rawData.isEmpty()) {
            throw new IllegalStateException("排盘服务返回空数据，请稍后重试");
        }
        return chartDataParser.parse(rawData);
    }

    // ============================================================
    // 基础方法
    // ============================================================

    private NatalRelationshipReportInstance createBaseReport(String userId, String chartProfileId) {
        NatalRelationshipReportInstance report = new NatalRelationshipReportInstance();
        report.setReportInstanceId(generateReportId());
        report.setReportType(NatalRelationshipConstants.REPORT_TYPE);
        report.setChartProfileId(chartProfileId);
        report.setReportStatus(NatalRelationshipConstants.STATUS_GENERATING);
        report.setGeneratedAt(LocalDateTime.now());
        report.setCreatedAt(LocalDateTime.now());
        report.setUpdatedAt(LocalDateTime.now());
        report.setTitle("单人婚恋深度解析报告");
        report.setSubtitle("基于你的本命星盘，解读你的亲密关系模式、长期需求与伴侣画像");
        report.setReportVersion("1.0.0");
        report.setAnalysisEngine("D1 + D9 + 7 Karaka + Shadbala + Nakshatra + Dasha");
        report.setClaimRegistryVersion(NatalRelationshipConstants.CLAIM_REGISTRY_VERSION);
        report.setRenderSchemaVersion(NatalRelationshipConstants.RENDER_SCHEMA_VERSION);
        report.setEvidenceEngineVersion(NatalRelationshipConstants.EVIDENCE_ENGINE_VERSION);
        return report;
    }

    private String generateReportId() {
        return "rel_" + UUID.randomUUID().toString().replace("-", "").substring(0, 16);
    }

    // ============================================================
    // 覆盖率与摘要
    // ============================================================

    private String determineCoverage(Map<String, CanonicalClaim> claims) {
        long supportedCount = claims.values().stream()
                .filter(c -> c.getConfidence() == cn.dct.zpback.entity.relationship.ClaimConfidence.HIGH ||
                        c.getConfidence() == cn.dct.zpback.entity.relationship.ClaimConfidence.MEDIUM)
                .count();

        if (supportedCount >= 25) return NatalRelationshipConstants.COVERAGE_FULL;
        if (supportedCount >= 15) return NatalRelationshipConstants.COVERAGE_PARTIAL;
        return NatalRelationshipConstants.COVERAGE_INSUFFICIENT;
    }

    private String generateCoverSummary(Map<String, CanonicalClaim> claims) {
        CanonicalClaim lovePattern = claims.get("LOVE_PATTERN");
        if (lovePattern != null && lovePattern.getSummary() != null) {
            return lovePattern.getSummary();
        }
        return "基于你的本命星盘，解读你的亲密关系模式、长期需求与伴侣画像。";
    }

    private NatalRelationshipClaimsSummary generateClaimsSummary(Map<String, CanonicalClaim> claims) {
        NatalRelationshipClaimsSummary summary = new NatalRelationshipClaimsSummary();
        summary.setTotal(claims.size());
        summary.setSupported((int) claims.values().stream()
                .filter(c -> c.getConfidence() == cn.dct.zpback.entity.relationship.ClaimConfidence.HIGH).count());
        summary.setConditional((int) claims.values().stream()
                .filter(c -> c.getConfidence() == cn.dct.zpback.entity.relationship.ClaimConfidence.MEDIUM).count());
        summary.setMixed(0);
        summary.setInsufficient((int) claims.values().stream()
                .filter(c -> c.getConfidence() == cn.dct.zpback.entity.relationship.ClaimConfidence.LOW ||
                        c.getConfidence() == cn.dct.zpback.entity.relationship.ClaimConfidence.INSUFFICIENT).count());
        return summary;
    }

    // ============================================================
    // 推荐生成
    // ============================================================

    /**
     * 行为类 Claim → 健康练习规则
     * key = Claim ID，value = {recommendationType, triggerContext, lowRiskAction}
     */
    private static final Map<String, String[]> RECOMMENDATION_RULES = new HashMap<>();
    static {
        RECOMMENDATION_RULES.put("EMOTIONAL_SECURITY_NEED",
                new String[]{"VALIDATE_BEFORE_SOLVING", "伴侣表达困扰时", "先确认对方的感受，再提供建议"});
        RECOMMENDATION_RULES.put("LONG_TERM_RELATIONSHIP_NEED",
                new String[]{"PLAN_CONNECTION_AND_SPACE", "日常生活中", "安排固定的深度对话时间，同时保持各自的空间"});
        RECOMMENDATION_RULES.put("COMMITMENT_STYLE",
                new String[]{"STATE_NEEDS_EARLY", "感到不满或需要支持时", "直接、清晰、温和地表达自己的需求"});
        RECOMMENDATION_RULES.put("REPAIR_PATTERN",
                new String[]{"REPAIR_AFTER_COOLDOWN", "冲突发生后", "先冷静，再坐下来沟通解决问题"});
        RECOMMENDATION_RULES.put("CONFLICT_RESPONSE_PATTERN",
                new String[]{"REPAIR_AFTER_COOLDOWN", "冲突发生时", "先冷静，再坐下来沟通解决问题"});
        RECOMMENDATION_RULES.put("BOUNDARY_PATTERN",
                new String[]{"SET_LIMIT_BEFORE_SACRIFICE", "想要为伴侣牺牲时", "先确认自己的底线，再做出决定"});
        RECOMMENDATION_RULES.put("SHARED_RESPONSIBILITY_PATTERN",
                new String[]{"CHECK_EXPECTATIONS_AGAINST_BEHAVIOR", "对伴侣有期待时", "检查自己的期待是否基于对方的实际行为"});
    }

    /**
     * 由 Claim 派生行动建议
     *
     * 每条建议都必须绑定一个真实 Claim，且该 Claim 证据足够（非 INSUFFICIENT）才会出现；
     * 排序按 Claim 置信度，并把 Claim 的判定摘要作为依据一并返回。
     * 不再无条件输出固定 6 条 —— 否则会「看起来个性化、实际人人一样」。
     */
    List<NatalRelationshipRecommendation> generateRecommendations(Map<String, CanonicalClaim> claims) {
        List<NatalRelationshipRecommendation> recommendations = new ArrayList<>();
        Set<String> usedTypes = new HashSet<>();

        List<String> claimIds = new ArrayList<>(RECOMMENDATION_RULES.keySet());
        // 置信度高的 Claim 优先
        claimIds.sort(Comparator.comparingInt(id -> confidenceRank(claims.get(id))));

        int priority = 1;
        for (String claimId : claimIds) {
            CanonicalClaim claim = claims.get(claimId);
            if (claim == null || claim.getConfidence() == null
                    || claim.getConfidence() == ClaimConfidence.INSUFFICIENT) {
                continue;
            }
            String[] rule = RECOMMENDATION_RULES.get(claimId);
            // 同一类型只保留置信度最高的那条（如冲突与修复都指向「先冷静再沟通」）
            if (rule == null || !usedTypes.add(rule[0])) {
                continue;
            }
            NatalRelationshipRecommendation rec = createRecommendation(rule[0], priority, rule[1], rule[2]);
            rec.setBackingClaimId(claimId);
            rec.setEvidenceBasis(claim.getSummary());
            recommendations.add(rec);
            priority++;
        }

        if (recommendations.isEmpty()) {
            log.warn("婚恋报告未生成行动建议：所有行为类 Claim 证据均不足，已如实留空");
        }
        return recommendations;
    }

    private static int confidenceRank(CanonicalClaim claim) {
        if (claim == null || claim.getConfidence() == null) {
            return 4;
        }
        switch (claim.getConfidence()) {
            case HIGH: return 0;
            case MEDIUM: return 1;
            case LOW: return 2;
            default: return 4;
        }
    }

    private NatalRelationshipRecommendation createRecommendation(String type, int priority,
                                                                  String trigger, String action) {
        NatalRelationshipRecommendation rec = new NatalRelationshipRecommendation();
        rec.setRecommendationId("rel_rec_" + UUID.randomUUID().toString().replace("-", "").substring(0, 8));
        rec.setRecommendationType(type);
        rec.setPriority(priority);
        rec.setTriggerContext(trigger);
        rec.setSignalToNotice(trigger);
        rec.setLowRiskAction(action);
        // 可验证项要给出完整可观察的行为，不能截断成「是否先确认对」这种残句
        rec.setObservableCheck("是否做到：" + action);
        rec.setTitle(action);
        rec.setTaskCode(type);
        rec.setGoalState("关系健康");
        return rec;
    }

    // ============================================================
    // 数据库操作
    // ============================================================

    private void saveReportToDatabase(NatalRelationshipReportInstance report, String userId) {
        try {
            ReportInstance reportInstance = new ReportInstance();
            reportInstance.setReportInstanceId(report.getReportInstanceId());
            reportInstance.setUserId(userId);
            reportInstance.setReportType(report.getReportType());
            reportInstance.setReportTitle(report.getTitle());
            reportInstance.setSubjectLabel("本人");
            reportInstance.setReportVersion(report.getReportVersion());
            reportInstance.setSourceSkill(NatalRelationshipConstants.REPORT_TYPE);
            reportInstance.setSkillVersion(report.getReportVersion());
            reportInstance.setSchemaVersion(report.getClaimRegistryVersion());
            reportInstance.setRendererVersion(report.getRenderSchemaVersion());
            reportInstance.setChartId(report.getChartProfileId());
            reportInstance.setGeneratedAt(report.getGeneratedAt());
            reportInstance.setStatus(report.getReportStatus());
            reportInstance.setFollowupAvailable(true);
            reportInstance.setFollowupConfig(null);

            String finalPayload = objectMapper.writeValueAsString(report);
            log.info("婚恋报告序列化完成: reportId={}, payloadLength={}", report.getReportInstanceId(), finalPayload.length());
            reportInstance.setFinalPayload(finalPayload);

            int rows = reportInstanceMapper.insert(reportInstance);
            log.info("婚恋报告保存到数据库完成: reportId={}, rows={}", report.getReportInstanceId(), rows);
        } catch (Exception e) {
            log.error("婚恋报告保存到数据库失败: reportId={}, error={}", report.getReportInstanceId(), e.getMessage(), e);
            throw new RuntimeException("保存报告到数据库失败: " + e.getMessage(), e);
        }
    }

    public NatalRelationshipReportInstance getReportById(String reportId) {
        log.info("查询婚恋报告: reportId={}", reportId);
        try {
            ReportInstance reportInstance = reportInstanceMapper.selectById(reportId);
            if (reportInstance == null) {
                log.warn("婚恋报告不存在: reportId={}", reportId);
                return null;
            }
            log.info("查询婚恋报告成功: reportId={}, hasPayload={}", reportId, reportInstance.getFinalPayload() != null);
            return convertToNatalRelationshipReport(reportInstance);
        } catch (Exception e) {
            log.error("获取婚恋报告详情失败: reportId={}, error={}", reportId, e.getMessage(), e);
            return null;
        }
    }

    private NatalRelationshipReportInstance convertToNatalRelationshipReport(ReportInstance reportInstance) {
        try {
            if (reportInstance.getFinalPayload() != null && !reportInstance.getFinalPayload().isEmpty()) {
                return objectMapper.readValue(reportInstance.getFinalPayload(), NatalRelationshipReportInstance.class);
            } else {
                log.warn("婚恋报告finalPayload为空: reportId={}", reportInstance.getReportInstanceId());
            }
        } catch (Exception e) {
            log.error("反序列化婚恋报告失败: reportId={}, error={}", reportInstance.getReportInstanceId(), e.getMessage(), e);
        }

        // 如果反序列化失败，返回基本信息
        NatalRelationshipReportInstance report = new NatalRelationshipReportInstance();
        report.setReportInstanceId(reportInstance.getReportInstanceId());
        report.setReportType(reportInstance.getReportType());
        report.setChartProfileId(reportInstance.getChartId());
        report.setReportStatus(reportInstance.getStatus());
        report.setTitle(reportInstance.getReportTitle());
        report.setReportVersion(reportInstance.getReportVersion());
        report.setGeneratedAt(reportInstance.getGeneratedAt());
        return report;
    }

    public List<NatalRelationshipReportInstance> getUserReports(String userId) {
        try {
            List<ReportInstance> reportInstances = reportInstanceMapper.selectByUserId(userId, 0, 1000);
            return reportInstances.stream()
                    .filter(r -> NatalRelationshipConstants.REPORT_TYPE.equals(r.getReportType()))
                    .map(this::convertToNatalRelationshipReport)
                    .collect(java.util.stream.Collectors.toList());
        } catch (Exception e) {
            log.error("获取用户婚恋报告列表失败: userId={}, error={}", userId, e.getMessage(), e);
            return new ArrayList<>();
        }
    }

    public int getReportCount(String userId) {
        try {
            List<ReportInstance> reportInstances = reportInstanceMapper.selectByUserId(userId, 0, 1000);
            return (int) reportInstances.stream()
                    .filter(r -> NatalRelationshipConstants.REPORT_TYPE.equals(r.getReportType()))
                    .count();
        } catch (Exception e) {
            log.error("获取用户婚恋报告总数失败: userId={}, error={}", userId, e.getMessage(), e);
            return 0;
        }
    }
}
