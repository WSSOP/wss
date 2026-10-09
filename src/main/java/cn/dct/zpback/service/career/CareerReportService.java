package cn.dct.zpback.service.career;

import cn.dct.zpback.constant.CareerReportConstants;
import cn.dct.zpback.entity.career.*;
import cn.dct.zpback.service.AIService;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.*;

/**
 * 事业深度报告生成服务
 * 基于星盘数据生成事业深度解析报告
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class CareerReportService {

    private final AIService aiService;
    private final CareerRoleClusterService roleClusterService;
    private final ObjectMapper objectMapper;

    /**
     * 生成事业深度报告
     * @param userId 用户ID
     * @param chartData 星盘数据
     * @return 报告实例
     */
    public CareerReportInstance generateReport(String userId, Map<String, Object> chartData) {
        log.info("开始生成事业深度报告: userId={}", userId);

        CareerReportInstance report = new CareerReportInstance();
        report.setReportInstanceId(generateReportId());
        report.setUserId(userId);
        report.setReportStatus(CareerReportConstants.STATUS_GENERATING);
        report.setGeneratedAt(LocalDateTime.now());
        report.setCreatedAt(LocalDateTime.now());
        report.setUpdatedAt(LocalDateTime.now());

        try {
            // 1. 提取星盘数据
            Map<String, Object> d1Data = extractD1Data(chartData);
            Map<String, Object> d10Data = extractD10Data(chartData);
            Map<String, Object> jaiminiData = extractJaiminiData(chartData);
            Map<String, Object> shadbalaData = extractShadbalaData(chartData);
            Map<String, Object> dashaData = extractDashaData(chartData);

            // 2. 生成核心结论
            List<CareerClaim> coreClaims = generateCoreClaims(d1Data, d10Data, jaiminiData, shadbalaData);
            report.setCoreFindings(coreClaims);

            // 3. 生成章节
            List<CareerSection> sections = generateSections(coreClaims, d1Data, d10Data, jaiminiData, dashaData);
            report.setSections(sections);

            // 4. 生成时间窗口
            List<CareerTiming> timings = generateTimingWindows(dashaData, coreClaims);
            report.setTimingWindows(timings);

            // 5. 生成建议
            List<CareerRecommendation> recommendations = generateRecommendations(coreClaims, timings);
            report.setRecommendations(recommendations);

            // 6. 设置报告元数据
            report.setTitle("事业深度解析报告");
            report.setSubtitle("基于你的本命星盘，解读你的事业驱动力、优势与发展路径");
            report.setCoverSummary(generateCoverSummary(coreClaims));
            report.setReportStatus(CareerReportConstants.STATUS_COMPLETED);

            log.info("事业深度报告生成完成: reportId={}", report.getReportInstanceId());

        } catch (Exception e) {
            log.error("事业深度报告生成失败: {}", e.getMessage(), e);
            report.setReportStatus(CareerReportConstants.STATUS_FAILED);
        }

        return report;
    }

    /**
     * 生成核心结论
     */
    private List<CareerClaim> generateCoreClaims(Map<String, Object> d1Data, Map<String, Object> d10Data,
                                                  Map<String, Object> jaiminiData, Map<String, Object> shadbalaData) {
        List<CareerClaim> claims = new ArrayList<>();

        // 使用 AI 生成核心结论
        String prompt = buildCareerAnalysisPrompt(d1Data, d10Data, jaiminiData, shadbalaData);
        String aiResponse = aiService.ask(buildSystemPrompt(), prompt);

        // 解析 AI 响应为 Claim 列表
        claims = parseClaimsFromAIResponse(aiResponse);

        // 如果没有生成足够的 Claim，使用默认 Claim
        if (claims.size() < CareerReportConstants.MIN_CORE_FINDINGS) {
            claims = generateDefaultClaims(d1Data, d10Data);
        }

        // 限制数量
        if (claims.size() > CareerReportConstants.MAX_CORE_FINDINGS) {
            claims = claims.subList(0, CareerReportConstants.MAX_CORE_FINDINGS);
        }

        // 设置 Claim 元数据
        for (int i = 0; i < claims.size(); i++) {
            CareerClaim claim = claims.get(i);
            claim.setClaimId("claim_" + UUID.randomUUID().toString().substring(0, 8));
            claim.setIsCore(true);
            claim.setIsUserVisible(true);
            if (i < 2) {
                claim.setClaimStrength(CareerReportConstants.STRENGTH_STRONG);
            } else {
                claim.setClaimStrength(CareerReportConstants.STRENGTH_MODERATE);
            }
        }

        return claims;
    }

    /**
     * 生成章节
     */
    private List<CareerSection> generateSections(List<CareerClaim> coreClaims, Map<String, Object> d1Data,
                                                  Map<String, Object> d10Data, Map<String, Object> jaiminiData,
                                                  Map<String, Object> dashaData) {
        List<CareerSection> sections = new ArrayList<>();

        // 章节1: 核心发现
        sections.add(createSection("sec_01", CareerReportConstants.SECTION_CAREER_CORE_FINDINGS,
                "先看这份报告最重要的结论", CareerReportConstants.SECTION_TYPE_OVERVIEW, 1,
                buildCoreFindingsContent(coreClaims)));

        // 章节2: 事业驱动力与工作模式
        sections.add(createSection("sec_02", CareerReportConstants.SECTION_CAREER_DRIVE_WORK_PATTERN,
                "你的事业驱动力与工作模式", CareerReportConstants.SECTION_TYPE_DEEP_ANALYSIS, 2,
                buildDriveWorkPatternContent(coreClaims, d1Data)));

        // 章节3: 职业优势
        sections.add(createSection("sec_03", CareerReportConstants.SECTION_CAREER_STRENGTHS,
                "你真正能形成优势的地方", CareerReportConstants.SECTION_TYPE_DEEP_ANALYSIS, 3,
                buildStrengthsContent(coreClaims, d10Data)));

        // 章节4: 环境与角色
        sections.add(createSection("sec_04", CareerReportConstants.SECTION_CAREER_ENVIRONMENT_ROLE,
                "什么环境和职业角色更适合你", CareerReportConstants.SECTION_TYPE_DEEP_ANALYSIS, 4,
                buildEnvironmentRoleContent(coreClaims, d1Data, d10Data)));

        // 章节5: 事业挑战
        sections.add(createSection("sec_05", CareerReportConstants.SECTION_CAREER_CHALLENGES,
                "事业最容易卡在哪里", CareerReportConstants.SECTION_TYPE_DEEP_ANALYSIS, 5,
                buildChallengesContent(coreClaims, d1Data)));

        // 章节6: 发展路径
        sections.add(createSection("sec_06", CareerReportConstants.SECTION_CAREER_GROWTH_PATH,
                "你的事业会怎样逐步发展", CareerReportConstants.SECTION_TYPE_DEEP_ANALYSIS, 6,
                buildGrowthPathContent(coreClaims, jaiminiData)));

        // 章节7: 收入关联
        sections.add(createSection("sec_07", CareerReportConstants.SECTION_CAREER_INCOME_LINK,
                "事业更容易怎样转化成收入", CareerReportConstants.SECTION_TYPE_DEEP_ANALYSIS, 7,
                buildIncomeLinkContent(coreClaims, d10Data)));

        // 章节8: 时间窗口
        sections.add(createSection("sec_08", CareerReportConstants.SECTION_CAREER_TIMING,
                "接下来值得关注的事业阶段", CareerReportConstants.SECTION_TYPE_TIMING, 8,
                buildTimingContent(dashaData)));

        // 章节9: 注意事项
        sections.add(createSection("sec_09", CareerReportConstants.SECTION_CAREER_CAUTIONS,
                "尤其值得注意的是", CareerReportConstants.SECTION_TYPE_CAUTION, 9,
                buildCautionsContent(coreClaims)));

        // 章节10: 建议
        sections.add(createSection("sec_10", CareerReportConstants.SECTION_CAREER_RECOMMENDATIONS,
                "接下来可以怎么做", CareerReportConstants.SECTION_TYPE_RECOMMENDATION, 10,
                buildRecommendationsContent(coreClaims)));

        return sections;
    }

    /**
     * 生成时间窗口
     */
    private List<CareerTiming> generateTimingWindows(Map<String, Object> dashaData, List<CareerClaim> coreClaims) {
        List<CareerTiming> timings = new ArrayList<>();

        // 从 Dasha 数据生成时间窗口
        List<Map<String, Object>> dashaPeriods = extractDashaPeriods(dashaData);

        for (int i = 0; i < Math.min(dashaPeriods.size(), CareerReportConstants.MAX_TIMING_WINDOWS); i++) {
            Map<String, Object> period = dashaPeriods.get(i);
            CareerTiming timing = new CareerTiming();
            timing.setTimingId("timing_" + UUID.randomUUID().toString().substring(0, 8));
            timing.setTimingType("DASHA_PHASE");
            timing.setMahadasaLord(getString(period, "mahadasaLord"));
            timing.setAntardashaLord(getString(period, "antardashaLord"));
            timing.setStartDate(getDate(period, "startDate"));
            timing.setEndDate(getDate(period, "endDate"));
            timing.setStageType(determineStageType(i));
            timing.setTheme(generateTimingTheme(period, coreClaims));
            timing.setDirection("SUPPORTIVE");
            timing.setActivationStrength(determineActivationStrength(i));
            timing.setUserFacingTitle(generateTimingTitle(period));
            timing.setUserFacingSummary(generateTimingSummary(period, coreClaims));
            timings.add(timing);
        }

        return timings;
    }

    /**
     * 从 Map 中安全获取 String 值
     */
    private String getString(Map<String, Object> map, String key) {
        Object value = map.get(key);
        return value != null ? value.toString() : null;
    }

    /**
     * 从 Map 中安全获取 LocalDate 值
     */
    private LocalDate getDate(Map<String, Object> map, String key) {
        Object value = map.get(key);
        if (value == null) {
            return LocalDate.now();
        }
        if (value instanceof LocalDate) {
            return (LocalDate) value;
        }
        if (value instanceof String) {
            try {
                return LocalDate.parse((String) value);
            } catch (Exception e) {
                return LocalDate.now();
            }
        }
        try {
            return LocalDate.parse(value.toString());
        } catch (Exception e) {
            return LocalDate.now();
        }
    }

    /**
     * 生成建议
     */
    private List<CareerRecommendation> generateRecommendations(List<CareerClaim> coreClaims, List<CareerTiming> timings) {
        List<CareerRecommendation> recommendations = new ArrayList<>();

        // 基于 Claim 生成建议
        int count = 0;
        for (CareerClaim claim : coreClaims) {
            if (count >= CareerReportConstants.MAX_RECOMMENDATIONS) break;

            CareerRecommendation rec = new CareerRecommendation();
            rec.setRecommendationId("rec_" + UUID.randomUUID().toString().substring(0, 8));
            rec.setRecommendationType(determineRecommendationType(claim));
            rec.setRecommendationMode(determineRecommendationMode(claim));
            rec.setActionRiskLevel(determineActionRiskLevel(claim));
            rec.setSpecificityLevel("GENERAL");
            rec.setStatement(generateRecommendationStatement(claim));
            rec.setRationale(generateRecommendationRationale(claim));
            rec.setRelatedClaimIds(Collections.singletonList(claim.getClaimId()));
            rec.setPriority(determinePriority(count));
            rec.setIsUserVisible(true);
            recommendations.add(rec);
            count++;
        }

        // 确保最少建议数量
        if (recommendations.size() < CareerReportConstants.MIN_RECOMMENDATIONS) {
            recommendations.addAll(generateDefaultRecommendations(coreClaims, recommendations.size()));
        }

        return recommendations;
    }

    // ============================================================
    // 辅助方法
    // ============================================================

    private String generateReportId() {
        return "career_" + UUID.randomUUID().toString().replace("-", "").substring(0, 16);
    }

    private String buildSystemPrompt() {
        return """
                你是星灵AI，专业的吠陀占星师。你正在为用户生成一份【事业深度解析报告】。

                报告要求：
                1. 全文控制在 3000-4000 字（不含标点空格），各章篇幅均匀，不重复填充
                2. 使用清晰、专业但不晦涩的语言
                3. 每条结论都要有占星依据
                4. 避免使用"一定"、"注定"、"必然"等绝对化语言
                5. 将条件性结论标注为"在...情况下"
                6. 职业示例来自白名单池，不做行星→职业的机械映射
                7. 建议必须是低风险、可执行的，禁止建议辞职、创业等高影响行动

                报告结构：
                01｜先看这份报告最重要的结论（3-5条）
                02｜你的事业驱动力与工作模式
                03｜你真正能形成优势的地方
                04｜什么环境和职业角色更适合你
                05｜事业最容易卡在哪里
                06｜你的事业会怎样逐步发展
                07｜事业更容易怎样转化成收入
                08｜接下来值得关注的事业阶段（2-5个窗口）
                09｜尤其值得注意的是（2-4条）
                10｜接下来可以怎么做（3-5条）
                """;
    }

    private String buildCareerAnalysisPrompt(Map<String, Object> d1Data, Map<String, Object> d10Data,
                                              Map<String, Object> jaiminiData, Map<String, Object> shadbalaData) {
        StringBuilder sb = new StringBuilder();
        sb.append("请基于以下星盘数据生成事业深度分析报告：\n\n");

        if (d1Data != null && !d1Data.isEmpty()) {
            sb.append("## D1 本命盘数据\n");
            sb.append(d1Data.toString()).append("\n\n");
        }

        if (d10Data != null && !d10Data.isEmpty()) {
            sb.append("## D10 事业宫数据\n");
            sb.append(d10Data.toString()).append("\n\n");
        }

        if (jaiminiData != null && !jaiminiData.isEmpty()) {
            sb.append("## Jaimini 数据\n");
            sb.append(jaiminiData.toString()).append("\n\n");
        }

        if (shadbalaData != null && !shadbalaData.isEmpty()) {
            sb.append("## Shadbala 力量数据\n");
            sb.append(shadbalaData.toString()).append("\n\n");
        }

        return sb.toString();
    }

    private List<CareerClaim> parseClaimsFromAIResponse(String aiResponse) {
        List<CareerClaim> claims = new ArrayList<>();
        // 简单解析：按行分割，每行作为一个 Claim
        String[] lines = aiResponse.split("\n");
        for (String line : lines) {
            line = line.trim();
            if (line.length() > 10 && !line.startsWith("#") && !line.startsWith("##")) {
                CareerClaim claim = new CareerClaim();
                claim.setStatement(line);
                claim.setClaimStrength(CareerReportConstants.STRENGTH_MODERATE);
                claims.add(claim);
            }
        }
        return claims;
    }

    private List<CareerClaim> generateDefaultClaims(Map<String, Object> d1Data, Map<String, Object> d10Data) {
        List<CareerClaim> claims = new ArrayList<>();

        // 基于 D1 10H 生成默认 Claim
        String d10HSign = d1Data != null ? (String) d10Data.get("10HSign") : null;
        if (d10HSign != null) {
            CareerClaim claim1 = new CareerClaim();
            claim1.setClaimType(CareerReportConstants.CLAIM_TYPE_CAREER_MOTIVATION);
            claim1.setClaimScope(CareerReportConstants.SCOPE_MOTIVATION);
            claim1.setStatement("你的事业驱动力与" + d10HSign + "座的能量密切相关，表现出" +
                    getSignExpression(d10HSign) + "的特质。");
            claim1.setClaimStrength(CareerReportConstants.STRENGTH_MODERATE);
            claims.add(claim1);
        }

        // 默认 Claim 2
        CareerClaim claim2 = new CareerClaim();
        claim2.setClaimType(CareerReportConstants.CLAIM_TYPE_WORK_STYLE);
        claim2.setClaimScope(CareerReportConstants.SCOPE_WORK_METHOD);
        claim2.setStatement("你在工作中更倾向于通过积累和沉淀来建立专业优势，而非频繁转换方向。");
        claim2.setClaimStrength(CareerReportConstants.STRENGTH_MODERATE);
        claims.add(claim2);

        // 默认 Claim 3
        CareerClaim claim3 = new CareerClaim();
        claim3.setClaimType(CareerReportConstants.CLAIM_TYPE_PROFESSIONAL_STRENGTH);
        claim3.setClaimScope(CareerReportConstants.SCOPE_ROLE);
        claim3.setStatement("你的核心职业优势在于能够将深度思考转化为可执行的方法和价值输出。");
        claim3.setClaimStrength(CareerReportConstants.STRENGTH_MODERATE);
        claims.add(claim3);

        return claims;
    }

    private String getSignExpression(String sign) {
        List<String> tags = CareerReportConstants.D1_10H_SIGN_TAGS.get(sign);
        if (tags != null && !tags.isEmpty()) {
            return String.join("、", tags.subList(0, Math.min(3, tags.size())));
        }
        return "独特";
    }

    private CareerSection createSection(String id, String key, String title, String type, int order, String content) {
        CareerSection section = new CareerSection();
        section.setSectionId(id);
        section.setSectionKey(key);
        section.setTitle(title);
        section.setSectionType(type);
        section.setOrderNum(order);
        section.setBody(content);
        section.setSummary(content.substring(0, Math.min(100, content.length())));
        section.setIsVisible(true);
        return section;
    }

    private String buildCoreFindingsContent(List<CareerClaim> claims) {
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < claims.size(); i++) {
            sb.append(i + 1).append(". ").append(claims.get(i).getStatement()).append("\n\n");
        }
        return sb.toString();
    }

    private String buildDriveWorkPatternContent(List<CareerClaim> claims, Map<String, Object> d1Data) {
        return "基于你的星盘配置，你的事业驱动力主要来自于内心深处对专业成就的追求。" +
                "你倾向于在一个稳定的框架内发挥自己的创造力和专业能力，" +
                "而不是频繁地转换方向。你的工作模式更适合深度思考和系统性推进，" +
                "而非快速切换和短期冲刺。";
    }

    private String buildStrengthsContent(List<CareerClaim> claims, Map<String, Object> d10Data) {
        return "你在职业环境中展现出的核心优势包括：\n" +
                "1. 将复杂信息转化为清晰判断的能力\n" +
                "2. 在专业领域持续深耕的耐心和毅力\n" +
                "3. 协调资源、推动项目落地的执行力\n\n" +
                "这些优势使你在专业道路上更容易建立可识别度和信任。";
    }

    private String buildEnvironmentRoleContent(List<CareerClaim> claims, Map<String, Object> d1Data, Map<String, Object> d10Data) {
        return "更适合你的工作环境通常具备以下特征：\n" +
                "1. 有清晰的责任边界和专业自主空间\n" +
                "2. 重视专业能力和长期积累\n" +
                "3. 提供持续学习和成长的机会\n\n" +
                "在角色方面，你更适合承担需要独立判断和专业深度的岗位。";
    }

    private String buildChallengesContent(List<CareerClaim> claims, Map<String, Object> d1Data) {
        return "事业上可能遇到的挑战包括：\n" +
                "1. 高责任与边界模糊并存时，容易产生压力\n" +
                "2. 专业深度与商业现实之间的平衡需要持续调整\n" +
                "3. 在快速变化的环境中，需要保持专业定力\n\n" +
                "这些挑战并非不适合，而是需要培养的重要能力。";
    }

    private String buildGrowthPathContent(List<CareerClaim> claims, Map<String, Object> jaiminiData) {
        return "你的事业发展路径通常呈现以下阶段：\n" +
                "1. 先建立专业可识别度和核心能力\n" +
                "2. 逐渐进入更高权责的角色\n" +
                "3. 在成熟阶段承担更大的决策和协调责任\n\n" +
                "每个阶段都有其独特的价值和挑战。";
    }

    private String buildIncomeLinkContent(List<CareerClaim> claims, Map<String, Object> d10Data) {
        return "你的事业价值通常通过以下方式转化为收入：\n" +
                "1. 把专业能力沉淀成可识别、可定价的服务或岗位价值\n" +
                "2. 建立稳定的客户关系与可重复交付\n" +
                "3. 提升承担的责任复杂度，而非只增加工作量\n\n" +
                "收入的增长通常与专业积累和责任提升同步。";
    }

    private String buildTimingContent(Map<String, Object> dashaData) {
        return "接下来值得关注的事业阶段包括：\n" +
                "1. 当前 Mahadasha 阶段的主题和机会\n" +
                "2. 未来 2-3 年的重要事业窗口\n" +
                "3. 需要提前准备的关键时间节点\n\n" +
                "每个阶段都有其独特的主题和可以把握的机会。";
    }

    private String buildCautionsContent(List<CareerClaim> claims) {
        return "尤其值得注意的是：\n" +
                "1. 避免因过度追求完美而错失机会窗口\n" +
                "2. 在承担更大责任时，同步确认职责边界和资源\n" +
                "3. 保持专业定力，不被短期波动影响长期方向";
    }

    private String buildRecommendationsContent(List<CareerClaim> claims) {
        return "接下来可以怎么做：\n" +
                "1. 把核心专业能力沉淀成可复用的方法、案例或专业输出\n" +
                "2. 主动争取能够独立判断和负责结果的工作内容\n" +
                "3. 建立长期合作网络和专业影响力\n" +
                "4. 为未来更高权责角色做好能力和资源准备";
    }

    private String generateCoverSummary(List<CareerClaim> claims) {
        if (claims == null || claims.isEmpty()) {
            return "基于你的本命星盘，解读你的事业驱动力、优势与发展路径。";
        }
        return claims.get(0).getStatement();
    }

    private String determineStageType(int index) {
        String[] stages = {
                CareerReportConstants.STAGE_FOUNDATION,
                CareerReportConstants.STAGE_ACCUMULATION,
                CareerReportConstants.STAGE_EXPANSION,
                CareerReportConstants.STAGE_VISIBILITY,
                CareerReportConstants.STAGE_LEADERSHIP
        };
        return stages[Math.min(index, stages.length - 1)];
    }

    private String generateTimingTheme(Map<String, Object> period, List<CareerClaim> claims) {
        return "事业主题激活期";
    }

    private String determineActivationStrength(int index) {
        if (index == 0) return CareerReportConstants.ACTIVATION_STRONG;
        if (index == 1) return CareerReportConstants.ACTIVATION_MODERATE;
        return CareerReportConstants.ACTIVATION_WEAK;
    }

    private String generateTimingTitle(Map<String, Object> period) {
        return (String) period.getOrDefault("mahadasaLord", "当前") + "大运期";
    }

    private String generateTimingSummary(Map<String, Object> period, List<CareerClaim> claims) {
        return "这一时期事业主题被激活，适合在已验证能力基础上适度增加项目规模、客户范围或责任复杂度。";
    }

    private String determineRecommendationType(CareerClaim claim) {
        String type = claim.getClaimType();
        if (type != null) {
            switch (type) {
                case "PROFESSIONAL_STRENGTH":
                case "DEVELOPMENT_PATH":
                    return CareerReportConstants.REC_TYPE_BUILD;
                case "PROFESSIONAL_CHALLENGE":
                case "CAREER_IDENTITY_TENSION":
                    return CareerReportConstants.REC_TYPE_ADJUST;
                case "CAREER_TIMING":
                    return CareerReportConstants.REC_TYPE_PREPARE;
            }
        }
        return CareerReportConstants.REC_TYPE_BUILD;
    }

    private String determineRecommendationMode(CareerClaim claim) {
        String strength = claim.getClaimStrength();
        if (CareerReportConstants.STRENGTH_STRONG.equals(strength)) {
            return CareerReportConstants.REC_MODE_DIRECT;
        }
        return CareerReportConstants.REC_MODE_CONDITIONAL;
    }

    private String determineActionRiskLevel(CareerClaim claim) {
        return CareerReportConstants.RISK_LOW;
    }

    private String generateRecommendationStatement(CareerClaim claim) {
        return "基于你的星盘配置，建议你在" + claim.getClaimScope() + "方面：" + claim.getStatement();
    }

    private String generateRecommendationRationale(CareerClaim claim) {
        return "这一建议基于你的星盘" + claim.getClaimType() + "配置。";
    }

    private String determinePriority(int index) {
        if (index == 0) return "HIGH";
        if (index == 1) return "MEDIUM";
        return "LOW";
    }

    private List<CareerRecommendation> generateDefaultRecommendations(List<CareerClaim> claims, int startIndex) {
        List<CareerRecommendation> recs = new ArrayList<>();
        String[] statements = {
                "把核心专业能力沉淀成可复用的方法、案例或专业输出",
                "主动争取能够独立判断和负责结果的工作内容",
                "建立长期合作网络和专业影响力"
        };
        for (int i = 0; i < statements.length && (startIndex + i) < CareerReportConstants.MAX_RECOMMENDATIONS; i++) {
            CareerRecommendation rec = new CareerRecommendation();
            rec.setRecommendationId("rec_default_" + UUID.randomUUID().toString().substring(0, 8));
            rec.setRecommendationType(CareerReportConstants.REC_TYPE_BUILD);
            rec.setRecommendationMode(CareerReportConstants.REC_MODE_DIRECT);
            rec.setActionRiskLevel(CareerReportConstants.RISK_LOW);
            rec.setSpecificityLevel("GENERAL");
            rec.setStatement(statements[i]);
            rec.setRationale("基于你的星盘配置");
            rec.setPriority(i == 0 ? "HIGH" : "MEDIUM");
            rec.setIsUserVisible(true);
            recs.add(rec);
        }
        return recs;
    }

    // ============================================================
    // 数据提取方法
    // ============================================================

    @SuppressWarnings("unchecked")
    private Map<String, Object> extractD1Data(Map<String, Object> chartData) {
        return (Map<String, Object>) chartData.getOrDefault("d1", Collections.emptyMap());
    }

    @SuppressWarnings("unchecked")
    private Map<String, Object> extractD10Data(Map<String, Object> chartData) {
        return (Map<String, Object>) chartData.getOrDefault("d10", Collections.emptyMap());
    }

    @SuppressWarnings("unchecked")
    private Map<String, Object> extractJaiminiData(Map<String, Object> chartData) {
        return (Map<String, Object>) chartData.getOrDefault("jaimini", Collections.emptyMap());
    }

    @SuppressWarnings("unchecked")
    private Map<String, Object> extractShadbalaData(Map<String, Object> chartData) {
        return (Map<String, Object>) chartData.getOrDefault("shadbala", Collections.emptyMap());
    }

    @SuppressWarnings("unchecked")
    private Map<String, Object> extractDashaData(Map<String, Object> chartData) {
        return (Map<String, Object>) chartData.getOrDefault("dasha", Collections.emptyMap());
    }

    @SuppressWarnings("unchecked")
    private List<Map<String, Object>> extractDashaPeriods(Map<String, Object> dashaData) {
        return (List<Map<String, Object>>) dashaData.getOrDefault("periods", Collections.emptyList());
    }
}
