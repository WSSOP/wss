package cn.dct.zpback.service.career;

import cn.dct.zpback.constant.LifePatternGrowthConstants;
import cn.dct.zpback.entity.career.*;
import cn.dct.zpback.entity.lifepattern.*;
import cn.dct.zpback.engine.lifepattern.*;
import cn.dct.zpback.engine.relationship.ChartDataParser;
import cn.dct.zpback.service.AIService;
import cn.dct.zpback.service.VedicAstroService;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.*;

/**
 * 人生课题深度报告生成服务
 * 符合 SEALED 文档 01 规范
 *
 * 架构（确定性规则引擎 + LLM Renderer）：
 * Canonical Data → Data Eligibility Gate → Evidence Engine →
 * Claim Engine → Dasha Activator → Final Payload → LLM Renderer
 *
 * LLM 只负责排版措辞，不做任何判断
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class LifePatternGrowthReportService {

    private final AIService aiService;
    private final ObjectMapper objectMapper;
    private final DataEligibilityGate eligibilityGate;
    private final LifePatternEvidenceEngine evidenceEngine;
    private final LifePatternClaimEngine claimEngine;
    private final DashaActivationResolver dashaResolver;
    private final VedicAstroService vedicAstroService;
    private final ChartDataParser chartDataParser;

    /**
     * 生成人生课题深度报告
     */
    public CareerReportInstance generateReport(String userId, Map<String, Object> chartDataRaw,
                                                String chartProfileId, Map<String, Object> birthParams) {
        log.info("开始生成人生课题深度报告: userId={}, chartProfileId={}, hasBirthParams={}",
                userId, chartProfileId, birthParams != null && !birthParams.isEmpty());

        CareerReportInstance report = new CareerReportInstance();
        report.setReportInstanceId(generateReportId());
        report.setUserId(userId);
        report.setReportType(LifePatternGrowthConstants.REPORT_TYPE);
        report.setReportStatus(LifePatternGrowthConstants.STATUS_GENERATING);
        report.setGeneratedAt(LocalDateTime.now());
        report.setCreatedAt(LocalDateTime.now());
        report.setUpdatedAt(LocalDateTime.now());

        try {
            // 1. 解析原始星盘数据（优先使用 birth_params 获取真实数据）
            Map<String, Object> resolvedRawData = resolveRawChartData(chartDataRaw, birthParams);
            LifePatternChartData chartData = parseChartData(resolvedRawData);

            // 2. Data Eligibility Gate - 阻断级数据验证
            List<String> eligibilityErrors = eligibilityGate.validate(chartData);
            if (!eligibilityErrors.isEmpty()) {
                log.error("数据验证失败: {}", eligibilityErrors);
                report.setReportStatus(LifePatternGrowthConstants.STATUS_FAILED);
                return report;
            }

            // 3. 检查可降级数据
            List<String> degradationNotes = eligibilityGate.checkDegradation(chartData);

            // 4. Evidence Engine - 构建标准化证据
            List<EvidenceObject> evidenceList = evidenceEngine.buildEvidence(chartData);
            log.info("Evidence 构建完成: total={}", evidenceList.size());

            // 5. Claim Engine - 确定性解析 13 个 Claim
            Map<String, LifePatternClaim> claims = claimEngine.resolveAllClaims(evidenceList, chartData);
            log.info("Claim 解析完成: total={}", claims.size());

            // 6. Dasha Activator - 激活已成立 Claim
            LifePatternClaim c13 = dashaResolver.resolveC13(claims, chartData.getDashaList());
            claims.put("C13", c13);

            // 7. 生成时间窗口
            List<LifePatternTimingPayload> timingWindows = dashaResolver.generateTimingWindows(chartData.getDashaList());

            // 8. 生成建议
            List<LifePatternRecommendationPayload> recommendations = generateRecommendations(claims);

            // 9. 构建 Final Payload
            LifePatternFinalPayload payload = buildFinalPayload(claims, evidenceList, timingWindows, recommendations, degradationNotes);

            // 10. LLM Renderer - 仅排版措辞
            String renderedContent = renderWithLLM(payload);

            // 11. 设置报告内容
            report.setTitle("人生课题深度报告");
            report.setSubtitle("基于你的本命星盘，解读你的内在运行系统、保护方式与成长路径");
            report.setReportContent(parseRenderedContent(renderedContent));
            report.setReportStatus(LifePatternGrowthConstants.STATUS_COMPLETED);

            // 12. 生成核心发现
            List<CareerClaim> coreClaims = convertClaims(claims);
            report.setCoreFindings(coreClaims);

            // 13. 生成章节
            List<CareerSection> sections = buildSections(claims, evidenceList);
            report.setSections(sections);

            // 14. 生成时间窗口
            report.setTimingWindows(convertTimings(timingWindows));

            // 15. 生成建议
            report.setRecommendations(convertRecommendations(recommendations));

            // 16. 设置封面摘要
            report.setCoverSummary(generateCoverSummary(claims));

            log.info("人生课题深度报告生成完成: reportId={}, claims={}, evidence={}",
                    report.getReportInstanceId(), claims.size(), evidenceList.size());

        } catch (Exception e) {
            log.error("人生课题深度报告生成失败: {}", e.getMessage(), e);
            report.setReportStatus(LifePatternGrowthConstants.STATUS_FAILED);
        }

        report.setUpdatedAt(LocalDateTime.now());
        return report;
    }

    // ============================================================
    // 数据解析
    // ============================================================

    /**
     * 获取原始星盘数据：
     * 1. 如果有 birth_params，调用 VedicAstro 获取真实星盘数据
     * 2. 否则使用传入的 chart_data
     */
    private Map<String, Object> resolveRawChartData(Map<String, Object> chartDataRaw, Map<String, Object> birthParams) {
        if (birthParams != null && !birthParams.isEmpty()) {
            try {
                Map<String, Object> vedicParams = chartDataParser.buildVedicAstroParams(birthParams);
                log.info("调用 VedicAstro 获取真实星盘数据: {}", vedicParams);
                Map<String, Object> rawData = vedicAstroService.getJyotishyaData(vedicParams);
                if (rawData != null && !rawData.isEmpty()) {
                    return rawData;
                }
            } catch (Exception e) {
                log.error("调用 VedicAstro 获取星盘数据失败: {}", e.getMessage(), e);
            }
        }
        return chartDataRaw != null ? chartDataRaw : Collections.emptyMap();
    }

    /**
     * 解析原始星盘数据为 LifePatternChartData
     */
    @SuppressWarnings("unchecked")
    private LifePatternChartData parseChartData(Map<String, Object> rawData) {
        LifePatternChartData chartData = new LifePatternChartData();

        // 解析 D1 / D9（VedicAstro 响应含 vargas 包装层）
        Object vargasObj = rawData.get("vargas");
        Map<String, Object> vargas = vargasObj instanceof Map ? (Map<String, Object>) vargasObj : rawData;

        Object d1Raw = vargas.get("D1");
        if (d1Raw instanceof Map) {
            chartData.setD1(parseNatalChart((Map<String, Object>) d1Raw));
        }

        Object d7Raw = vargas.get("D7");
        if (d7Raw instanceof Map) {
            chartData.setD7(parseNatalChart((Map<String, Object>) d7Raw));
        }

        Object d9Raw = vargas.get("D9");
        if (d9Raw instanceof Map) {
            chartData.setD9(parseNatalChart((Map<String, Object>) d9Raw));
        }

        // 解析 Jaimini / AK - 三级回退：
        // 1. karaka 字段（VedicAstro 响应）
        // 2. jaimini 字段
        // 3. 从 D1 行星度数计算（AK = 度数最高行星，7 Karaka: Sun-Saturn）
        if (chartData.getAkPlanet() == null || chartData.getAkPlanet().isEmpty()) {
            Object karakaObj = rawData.get("karaka");
            if (karakaObj instanceof Map) {
                Map<String, Object> karaka = (Map<String, Object>) karakaObj;
                Object ak = karaka.get("AK");
                if (ak instanceof String) {
                    chartData.setAkPlanet((String) ak);
                }
            }
        }
        if (chartData.getAkPlanet() == null || chartData.getAkPlanet().isEmpty()) {
            Object jaiminiObj = rawData.get("jaimini");
            if (jaiminiObj instanceof Map) {
                Map<String, Object> jaimini = (Map<String, Object>) jaiminiObj;
                Object ak = jaimini.get("AK");
                if (ak instanceof String) {
                    chartData.setAkPlanet((String) ak);
                }
            }
        }
        if ((chartData.getAkPlanet() == null || chartData.getAkPlanet().isEmpty())
                && chartData.getD1() != null && chartData.getD1().getPlanets() != null) {
            chartData.setAkPlanet(calculateAKFromD1(chartData.getD1()));
        }

        // 解析 Shadbala
        Object shadbalaObj = rawData.get("shadbala");
        Map<String, LifePatternShadbalaData> shadbala = new HashMap<>();
        if (shadbalaObj instanceof Map) {
            Map<String, Object> shadbalaRaw = (Map<String, Object>) shadbalaObj;
            for (Map.Entry<String, Object> entry : shadbalaRaw.entrySet()) {
                if (entry.getValue() instanceof Map) {
                    Map<String, Object> sdMap = (Map<String, Object>) entry.getValue();
                    LifePatternShadbalaData sd = new LifePatternShadbalaData();
                    sd.setExecutionCapacity((String) sdMap.get("execution_capacity"));
                    if (sdMap.get("score") instanceof Number) {
                        sd.setScore(((Number) sdMap.get("score")).doubleValue());
                    }
                    shadbala.put(entry.getKey(), sd);
                }
            }
        }
        chartData.setShadbala(shadbala);

        // 解析 Dasha
        Object dashaObj = rawData.get("dasha");
        List<LifePatternDashaPeriod> dashaList = new ArrayList<>();
        if (dashaObj instanceof List) {
            for (Object item : (List<?>) dashaObj) {
                if (item instanceof Map) {
                    Map<String, Object> d = (Map<String, Object>) item;
                    LifePatternDashaPeriod dp = new LifePatternDashaPeriod();
                    dp.setMahadasha((String) d.get("mahadasha"));
                    dp.setAntardasha((String) d.get("antardasha"));
                    dp.setStartDate((String) d.get("start_date"));
                    dp.setEndDate((String) d.get("end_date"));
                    dp.setIsCurrent(Boolean.TRUE.equals(d.get("is_current")));
                    dashaList.add(dp);
                }
            }
        }
        chartData.setDashaList(dashaList);

        return chartData;
    }

    @SuppressWarnings("unchecked")
    private LifePatternNatalChart parseNatalChart(Map<String, Object> raw) {
        LifePatternNatalChart chart = new LifePatternNatalChart();

        // 上升
        Object ascendantRaw = raw.get("ascendant");
        if (ascendantRaw instanceof String) {
            chart.setAscendantSign((String) ascendantRaw);
        } else if (ascendantRaw instanceof Map) {
            chart.setAscendantSign((String) ((Map<String, Object>) ascendantRaw).get("sign"));
            Object deg = ((Map<String, Object>) ascendantRaw).get("degree");
            if (deg instanceof Number) {
                chart.setAscendant(((Number) deg).doubleValue());
            }
        }

        // 行星 - VedicAstro 返回数组格式: [ { "name": "Sun", "sign": "Leo", ... }, ... ]
        Object planetsObj = raw.get("planets");
        Map<String, LifePatternPlanetPosition> planets = new HashMap<>();

        if (planetsObj instanceof List) {
            for (Object item : (List<?>) planetsObj) {
                if (item instanceof Map) {
                    Map<String, Object> planetMap = (Map<String, Object>) item;
                    String planetName = (String) planetMap.get("name");
                    if (planetName != null) {
                        planets.put(planetName, parsePlanetPosition(planetMap));
                    }
                }
            }
        } else if (planetsObj instanceof Map) {
            // Map 格式: { "Sun": { "sign": "Leo", ... }, ... }
            Map<String, Object> planetsRaw = (Map<String, Object>) planetsObj;
            for (Map.Entry<String, Object> entry : planetsRaw.entrySet()) {
                if (entry.getValue() instanceof Map) {
                    planets.put(entry.getKey(), parsePlanetPosition((Map<String, Object>) entry.getValue()));
                }
            }
        }
        chart.setPlanets(planets);

        // 宫位 - 兼容数组和 Map 两种格式
        Object housesObj = raw.get("houses");
        Map<String, LifePatternHouseInfo> houses = new HashMap<>();

        if (housesObj instanceof List) {
            List<?> housesList = (List<?>) housesObj;
            for (int i = 0; i < housesList.size(); i++) {
                Object item = housesList.get(i);
                if (item instanceof Map) {
                    Map<String, Object> houseMap = (Map<String, Object>) item;
                    LifePatternHouseInfo hi = new LifePatternHouseInfo();
                    hi.setSign((String) houseMap.get("sign"));
                    hi.setLord((String) houseMap.get("lord"));
                    Object deg = houseMap.get("degree");
                    if (deg instanceof Number) {
                        hi.setDegree(((Number) deg).doubleValue());
                    }
                    houses.put(String.valueOf(i + 1), hi);
                }
            }
        } else if (housesObj instanceof Map) {
            Map<String, Object> housesRaw = (Map<String, Object>) housesObj;
            for (Map.Entry<String, Object> entry : housesRaw.entrySet()) {
                if (entry.getValue() instanceof Map) {
                    Map<String, Object> houseMap = (Map<String, Object>) entry.getValue();
                    LifePatternHouseInfo hi = new LifePatternHouseInfo();
                    hi.setSign((String) houseMap.get("sign"));
                    hi.setLord((String) houseMap.get("lord"));
                    Object deg = houseMap.get("degree");
                    if (deg instanceof Number) {
                        hi.setDegree(((Number) deg).doubleValue());
                    }
                    houses.put(entry.getKey(), hi);
                }
            }
        }
        chart.setHouses(houses);

        return chart;
    }

    private LifePatternPlanetPosition parsePlanetPosition(Map<String, Object> raw) {
        LifePatternPlanetPosition pos = new LifePatternPlanetPosition();
        pos.setSign((String) raw.get("sign"));
        Object house = raw.get("house");
        if (house instanceof Number) {
            pos.setHouse(((Number) house).intValue());
        }
        pos.setDegree(extractDegree(raw));
        pos.setRetrograde(Boolean.TRUE.equals(raw.get("retrograde")));
        pos.setDignity((String) raw.get("dignity"));
        pos.setVargottama(Boolean.TRUE.equals(raw.get("vargottama")));
        return pos;
    }

    /**
     * 从多种结构中提取行星度数
     * 支持: degree 字段 / pos.dec_deg 嵌套结构
     */
    private Double extractDegree(Map<String, Object> raw) {
        Object degree = raw.get("degree");
        if (degree instanceof Number) {
            return ((Number) degree).doubleValue();
        }
        Object posObj = raw.get("pos");
        if (posObj instanceof Map) {
            Object decDeg = ((Map<String, Object>) posObj).get("dec_deg");
            if (decDeg instanceof Number) {
                return ((Number) decDeg).doubleValue();
            }
        }
        return null;
    }

    /**
     * 从 D1 行星计算 AK（度数最高行星）
     * 7 Karaka 体系: Sun / Moon / Mars / Mercury / Jupiter / Venus / Saturn
     * 符合 SEALED 文档: AK = 度数最高；DK = 度数最低
     */
    private String calculateAKFromD1(LifePatternNatalChart d1) {
        if (d1 == null || d1.getPlanets() == null) return null;

        Set<String> sevenKarakas = new HashSet<>(Arrays.asList(
                "Sun", "Moon", "Mars", "Mercury", "Jupiter", "Venus", "Saturn"));

        String akPlanet = null;
        double maxDegree = -1;

        for (Map.Entry<String, LifePatternPlanetPosition> entry : d1.getPlanets().entrySet()) {
            if (!sevenKarakas.contains(entry.getKey())) continue;
            Double deg = entry.getValue().getDegree();
            if (deg != null && deg > maxDegree) {
                maxDegree = deg;
                akPlanet = entry.getKey();
            }
        }

        if (akPlanet != null) {
            log.info("从 D1 行星度数计算 AK: {} (degree={})", akPlanet, maxDegree);
        }
        return akPlanet;
    }

    // ============================================================
    // Final Payload 构建
    // ============================================================

    private LifePatternFinalPayload buildFinalPayload(Map<String, LifePatternClaim> claims,
                                                       List<EvidenceObject> evidenceList,
                                                       List<LifePatternTimingPayload> timingWindows,
                                                       List<LifePatternRecommendationPayload> recommendations,
                                                       List<String> degradationNotes) {
        LifePatternFinalPayload payload = new LifePatternFinalPayload();
        payload.setClaims(claims);
        payload.setEvidenceList(evidenceList);
        payload.setTimingWindows(timingWindows);
        payload.setRecommendations(recommendations);
        payload.setDegradationNotes(degradationNotes);

        // 构建章节
        List<LifePatternSectionPayload> sections = new ArrayList<>();
        sections.add(buildSection("EXECUTIVE_SUMMARY", "你的个人模式总览", "C01,C02,C05,C11", claims, evidenceList));
        sections.add(buildSection("CORE_THEME_AND_NEED", "你真正反复在意的是什么", "C01,C02", claims, evidenceList));
        sections.add(buildSection("SECURITY_SELF_WORTH_RESOURCES", "你如何建立安全感与价值感", "C03,C04,C08", claims, evidenceList));
        sections.add(buildSection("PROTECTION_AND_OVERUSE", "压力下，你会怎样保护自己", "C05,C07", claims, evidenceList));
        sections.add(buildSection("INNER_TENSION", "你容易在哪两种需要之间拉扯", "C06", claims, evidenceList));
        sections.add(buildSection("REPEATING_LOOP", "你容易重复的人生循环", "C09", claims, evidenceList));
        sections.add(buildSection("GROWTH_AND_MATURITY", "你真正需要练习的能力", "C10,C11", claims, evidenceList));
        sections.add(buildSection("MEANING_ORIENTATION", "什么会让你觉得这件事值得", "C12", claims, evidenceList));
        sections.add(buildSection("CURRENT_ACTIVATION", "现阶段更容易被触发的成长主题", "C13", claims, evidenceList));
        sections.add(buildSection("PERSONAL_ACTION_GUIDE", "你的个人使用说明", "RECOMMENDATIONS", claims, evidenceList));
        payload.setSections(sections);

        return payload;
    }

    private LifePatternSectionPayload buildSection(String key, String title, String claimIds,
                                                    Map<String, LifePatternClaim> claims,
                                                    List<EvidenceObject> evidenceList) {
        LifePatternSectionPayload section = new LifePatternSectionPayload();
        section.setSectionKey(key);
        section.setTitle(title);
        section.setClaimIds(Arrays.asList(claimIds.split(",")));

        // 收集相关 Evidence
        List<EvidenceObject> sectionEvidence = new ArrayList<>();
        for (String claimId : section.getClaimIds()) {
            LifePatternClaim claim = claims.get(claimId);
            if (claim != null && claim.getSupportEvidenceIds() != null) {
                for (String evId : claim.getSupportEvidenceIds()) {
                    for (EvidenceObject ev : evidenceList) {
                        if (evId.equals(ev.getEvidenceId())) {
                            sectionEvidence.add(ev);
                        }
                    }
                }
            }
        }
        section.setEvidenceFold(sectionEvidence);

        return section;
    }

    // ============================================================
    // LLM Renderer（仅排版措辞）
    // ============================================================

    private String renderWithLLM(LifePatternFinalPayload payload) {
        try {
            String payloadJson = objectMapper.writeValueAsString(payload);
            String systemPrompt = "你是问星渡《人生课题深度报告》的AI排版引擎。" +
                    "你的唯一职责是将已结构化的报告数据排版为通顺的中文报告。" +
                    "禁止：计算星盘、创造Claim、改变Claim状态、补写证据、决定当前运势。" +
                    "只负责：将已有的Claim、Evidence、Recommendation排版为3000-4000字的中文报告。" +
                    "必须输出JSON，字段：title, subtitle, executive_summary(headline,keywords,body), " +
                    "sections(display_title,body,evidence_cards), action_guide, method_note。" +
                    "写作风格：温和、清醒、具体、有理解感。";
            String userPrompt = "请将以下结构化数据排版为中文报告：\n\n" + payloadJson;

            String llmResponse = aiService.generateLifePatternReport(systemPrompt, userPrompt);
            Map<String, Object> parsed = parseRenderedContent(llmResponse);
            if (isValidReportContent(parsed)) {
                return llmResponse;
            }
            log.warn("LLM 输出结构不完整，使用确定性内容");
            return generateDefaultContent(payload);
        } catch (Exception e) {
            log.warn("LLM 渲染失败，使用默认内容: {}", e.getMessage());
            return generateDefaultContent(payload);
        }
    }

    /**
     * 校验报告内容结构是否完整（前端渲染所需字段）
     */
    private boolean isValidReportContent(Map<String, Object> content) {
        if (content == null || content.isEmpty()) return false;
        Object sections = content.get("sections");
        if (!(sections instanceof List) || ((List<?>) sections).isEmpty()) return false;
        for (Object s : (List<?>) sections) {
            if (s instanceof Map) {
                Object body = ((Map<?, ?>) s).get("body");
                if (body instanceof String && !((String) body).trim().isEmpty()) {
                    return true;
                }
            }
        }
        return false;
    }

    private String generateDefaultContent(LifePatternFinalPayload payload) {
        try {
            Map<String, Object> content = buildStructuredReportContent(payload);
            return objectMapper.writeValueAsString(content);
        } catch (Exception e) {
            log.error("生成默认报告内容失败: {}", e.getMessage(), e);
            return "{\"title\":\"人生课题深度报告\",\"sections\":[]}";
        }
    }

    /**
     * 从确定性 Claim 结果构建完整报告结构（前端渲染所需格式）
     */
    private Map<String, Object> buildStructuredReportContent(LifePatternFinalPayload payload) {
        Map<String, LifePatternClaim> claims = payload.getClaims() != null
                ? payload.getClaims() : Collections.emptyMap();
        List<EvidenceObject> evidenceList = payload.getEvidenceList() != null
                ? payload.getEvidenceList() : Collections.emptyList();

        Map<String, Object> content = new LinkedHashMap<>();
        content.put("title", "人生课题深度报告");
        content.put("subtitle", "基于你的本命星盘，解读你的内在运行系统、保护方式与成长路径");

        // 执行摘要
        Map<String, Object> summary = new LinkedHashMap<>();
        LifePatternClaim c01 = claims.get("C01");
        LifePatternClaim c02 = claims.get("C02");
        LifePatternClaim c05 = claims.get("C05");
        LifePatternClaim c11 = claims.get("C11");

        boolean c01Ok = c01 != null && ("SUPPORTED".equals(c01.getState()) || "CONDITIONAL".equals(c01.getState()));
        summary.put("headline", c01Ok ? "你的核心人生主题已经清晰" : "你的个人模式总览");
        List<String> keywords = new ArrayList<>();
        if (c02 != null && !"INSUFFICIENT".equals(c02.getState())) keywords.add(claimDirectionText(c02));
        if (c05 != null && !"INSUFFICIENT".equals(c05.getState())) keywords.add(claimDirectionText(c05));
        if (c11 != null && !"INSUFFICIENT".equals(c11.getState())) keywords.add(claimDirectionText(c11));
        if (keywords.isEmpty()) keywords.add("自我认识");
        summary.put("keywords", keywords);

        StringBuilder summaryBody = new StringBuilder();
        summaryBody.append("这份报告基于你的本命星盘（D1），从内在需要、安全感、价值感、保护方式与成长路径等角度，");
        summaryBody.append("解读你反复出现的人生模式。");
        if (c02 != null && !"INSUFFICIENT".equals(c02.getState())) {
            summaryBody.append("你最核心的内在需要围绕「").append(claimDirectionText(c02)).append("」展开。");
        }
        if (c05 != null && "SUPPORTED".equals(c05.getState())) {
            summaryBody.append("在压力之下，你有一套熟悉的自动保护方式，它在特定时刻保护过你，也可能带来重复的代价。");
        }
        if (c11 != null && !"INSUFFICIENT".equals(c11.getState())) {
            summaryBody.append("接下来最重要的，是练习「").append(claimDirectionText(c11)).append("」。");
        }
        summary.put("body", summaryBody.toString());
        content.put("executive_summary", summary);

        // 各章节
        List<Map<String, Object>> sections = new ArrayList<>();
        addSection(sections, "你真正反复在意的是什么", "C01,C02", claims, evidenceList,
                "本章回答：抛开外界期待，你真正反复在意、反复回到的原点是什么。");
        addSection(sections, "你如何建立安全感与价值感", "C03,C04,C08", claims, evidenceList,
                "本章回答：你在什么条件下会感到安心，又把价值感建立在什么上面。");
        addSection(sections, "压力下，你会怎样保护自己", "C05,C07", claims, evidenceList,
                "本章回答：当压力出现时，你会启动怎样的自动保护方式，以及使用过头的代价。");
        addSection(sections, "你容易在哪两种需要之间拉扯", "C06", claims, evidenceList,
                "本章回答：你内在同时存在的两种合法需要，以及它们之间的张力。");
        addSection(sections, "你容易重复的人生循环", "C09", claims, evidenceList,
                "本章回答：需要—触发—保护—短期收益—长期代价的完整循环。");
        addSection(sections, "你真正需要练习的能力", "C10,C11", claims, evidenceList,
                "本章回答：旧策略不够用时，你需要学习的调节能力。");
        addSection(sections, "什么会让你觉得这件事值得", "C12", claims, evidenceList,
                "本章回答：你为什么觉得一件事值得长期投入。");
        addSection(sections, "现阶段更容易被触发的成长主题", "C13", claims, evidenceList,
                "本章回答：当前阶段更容易被注意、练习或感受到的既有主题。");
        content.put("sections", sections);

        // 个人使用说明
        List<Map<String, Object>> actionGuide = new ArrayList<>();
        if (payload.getRecommendations() != null) {
            for (LifePatternRecommendationPayload rec : payload.getRecommendations()) {
                Map<String, Object> action = new LinkedHashMap<>();
                action.put("title", rec.getTitle());
                action.put("trigger_context", rec.getTriggerContext() != null ? rec.getTriggerContext() : "日常压力情境");
                action.put("signal_to_notice", rec.getSignalRecognition() != null ? rec.getSignalRecognition() : "熟悉的保护模式自动启动");
                action.put("pause_action", rec.getPauseAction() != null ? rec.getPauseAction() : "暂停三秒，先回到呼吸");
                action.put("replacement_behavior", rec.getAlternativeBehavior() != null ? rec.getAlternativeBehavior() : rec.getLowRiskAction());
                action.put("observable_check", rec.getObservableCheck() != null ? rec.getObservableCheck() : "能够识别自己的模式并尝试不同回应");
                actionGuide.add(action);
            }
        }
        content.put("action_guide", actionGuide);

        // 方法论说明
        content.put("method_note", "本报告基于吠陀占星 D1 本命盘，由确定性规则引擎解析 13 个核心维度生成；" +
                "AK 提供成长支持方向，D9 仅做成熟度验证，Shadbala 只修饰表达强弱，Dasha 只激活已存在的主题。" +
                "报告内容不构成医疗、心理诊断或重大人生决策建议。");

        return content;
    }

    /**
     * 添加一个章节（由相关 Claims 驱动内容）
     */
    private void addSection(List<Map<String, Object>> sections, String displayTitle, String claimIds,
                             Map<String, LifePatternClaim> claims, List<EvidenceObject> evidenceList,
                             String chapterQuestion) {
        Map<String, Object> section = new LinkedHashMap<>();
        section.put("display_title", displayTitle);
        section.put("section_key", claimIds);

        StringBuilder body = new StringBuilder();
        body.append(chapterQuestion).append("\n\n");

        boolean hasContent = false;
        List<Map<String, Object>> evidenceCards = new ArrayList<>();

        for (String claimId : claimIds.split(",")) {
            LifePatternClaim claim = claims.get(claimId.trim());
            if (claim == null) continue;
            if ("INSUFFICIENT".equals(claim.getState())) continue;

            hasContent = true;
            String dirText = claimDirectionText(claim);
            String stateText = "SUPPORTED".equals(claim.getState()) ? "在你的星盘中有清晰的结构支持"
                    : "在你的星盘中有一定倾向";

            body.append("• ").append(claimNameText(claim.getClaimId()))
                .append("：").append(dirText).append("——这一方向").append(stateText).append("。\n");

            // 证据卡片（每章最多3条）
            if (evidenceCards.size() < 3 && claim.getSupportEvidenceIds() != null) {
                for (String evId : claim.getSupportEvidenceIds()) {
                    if (evidenceCards.size() >= 3) break;
                    for (EvidenceObject ev : evidenceList) {
                        if (evId.equals(ev.getEvidenceId())) {
                            Map<String, Object> card = new LinkedHashMap<>();
                            card.put("plain_language", evidencePlainLanguage(ev));
                            card.put("technical_summary", ev.getRawFact());
                            evidenceCards.add(card);
                            break;
                        }
                    }
                }
            }
        }

        if (!hasContent) {
            body.append("本章相关的星盘证据尚不充分，为避免武断解读，此处不做展开。\n");
        }

        section.put("body", body.toString());
        section.put("evidence_cards", evidenceCards);
        sections.add(section);
    }

    private String claimDirectionText(LifePatternClaim claim) {
        String dir = claim.getCandidateDirection();
        if (dir == null || dir.isEmpty() || "UNDETERMINED".equals(dir)) {
            return claim.getClaimName();
        }
        switch (dir) {
            case "EMOTIONAL_SECURITY": return "情感安全与归属";
            case "IDENTITY_EXPRESSION": return "自我身份的表达与确认";
            case "PROTECTIVE_RESPONSE": return "压力下的自我保护";
            case "OVERUSE": return "优势策略使用过头的模式";
            case "DUAL_TENSION": return "两种需要之间的张力";
            case "INTEGRATED_THEME": return "自我确认与关系连结的平衡";
            case "GROWTH_PATH": return "长期成长与成熟练习";
            default: return dir;
        }
    }

    private String claimNameText(String claimId) {
        switch (claimId) {
            case "C02": return "核心内在需要";
            case "C03": return "安全感调节方式";
            case "C04": return "自我价值锚点";
            case "C05": return "压力下自动保护方式";
            case "C06": return "内在拉扯";
            case "C07": return "容易使用过头的模式";
            case "C08": return "已有优势资源";
            case "C09": return "重复人生循环";
            case "C10": return "主要成长难点";
            case "C11": return "需要发展的成熟能力";
            case "C12": return "意义感取向";
            case "C13": return "当前阶段激活主题";
            default: return claimId;
        }
    }

    private String evidencePlainLanguage(EvidenceObject ev) {
        String subject = ev.getSubject() != null ? ev.getSubject() : "";
        String target = ev.getTarget() != null ? ev.getTarget() : "";
        return "星盘中「" + subject + "」的结构（" + target + "）支持本章解读";
    }

    // ============================================================
    // 建议生成
    // ============================================================

    private List<LifePatternRecommendationPayload> generateRecommendations(Map<String, LifePatternClaim> claims) {
        List<LifePatternRecommendationPayload> recommendations = new ArrayList<>();

        // 基于已成立的 Claim 生成建议
        LifePatternClaim c10 = claims.get("C10");
        if (c10 != null && ("SUPPORTED".equals(c10.getState()) || "CONDITIONAL".equals(c10.getState()))) {
            LifePatternRecommendationPayload rec = new LifePatternRecommendationPayload();
            rec.setRecommendationType("GROWTH_TASK");
            rec.setTitle("觉察并调整过度使用的保护模式");
            rec.setLowRiskAction("当发现自己进入熟悉的保护模式时，先暂停3秒，问自己：此刻我真正需要什么？");
            rec.setObservableCheck("能够识别自己何时进入保护模式，并尝试不同的回应方式");
            rec.setTriggerContext("压力情境、冲突触发时");
            rec.setSignalRecognition("熟悉的保护模式自动启动");
            rec.setPauseAction("暂停3秒，深呼吸");
            rec.setAlternativeBehavior("尝试直接表达需要，而非自动保护");
            recommendations.add(rec);
        }

        LifePatternClaim c06 = claims.get("C06");
        if (c06 != null && "SUPPORTED".equals(c06.getState())) {
            LifePatternRecommendationPayload rec = new LifePatternRecommendationPayload();
            rec.setRecommendationType("GROWTH_TASK");
            rec.setTitle("接纳内在两种需要的合法性");
            rec.setLowRiskAction("当两种需要同时出现时，不急于选择一边，先承认两种需要都是合理的。");
            rec.setObservableCheck("能够在内在张力出现时，不急于反应，先观察");
            recommendations.add(rec);
        }

        // 默认建议
        if (recommendations.isEmpty()) {
            LifePatternRecommendationPayload rec = new LifePatternRecommendationPayload();
            rec.setRecommendationType("SELF_OBSERVATION");
            rec.setTitle("观察自己的关系模式");
            rec.setLowRiskAction("每天花5分钟回顾当天的互动，观察自己何时感到安全，何时感到威胁。");
            rec.setObservableCheck("能够识别自己的情绪触发点");
            recommendations.add(rec);
        }

        return recommendations;
    }

    // ============================================================
    // 转换方法
    // ============================================================

    private List<CareerClaim> convertClaims(Map<String, LifePatternClaim> claims) {
        List<CareerClaim> result = new ArrayList<>();
        for (LifePatternClaim claim : claims.values()) {
            CareerClaim cc = new CareerClaim();
            cc.setClaimId(claim.getClaimId());
            cc.setClaimType(claim.getClaimName());
            cc.setStatement(claim.getCandidateDirection());
            cc.setClaimStrength(claim.getState());
            cc.setImportance(claim.getConfidence());
            cc.setSupportingEvidenceIds(claim.getSupportEvidenceIds());
            cc.setCounterEvidenceIds(claim.getContradictEvidenceIds());
            result.add(cc);
        }
        return result;
    }

    private List<CareerSection> buildSections(Map<String, LifePatternClaim> claims, List<EvidenceObject> evidenceList) {
        List<CareerSection> sections = new ArrayList<>();

        String[][] sectionDefs = {
            {"EXECUTIVE_SUMMARY", "OVERVIEW", "你的个人模式总览", "C01,C02,C05,C11"},
            {"CORE_THEME_AND_NEED", "DEEP_ANALYSIS", "你真正反复在意的是什么", "C01,C02"},
            {"SECURITY_SELF_WORTH_RESOURCES", "DEEP_ANALYSIS", "你如何建立安全感与价值感", "C03,C04,C08"},
            {"PROTECTION_AND_OVERUSE", "DEEP_ANALYSIS", "压力下，你会怎样保护自己", "C05,C07"},
            {"INNER_TENSION", "TENSION", "你容易在哪两种需要之间拉扯", "C06"},
            {"REPEATING_LOOP", "DEEP_ANALYSIS", "你容易重复的人生循环", "C09"},
            {"GROWTH_AND_MATURITY", "DEEP_ANALYSIS", "你真正需要练习的能力", "C10,C11"},
            {"MEANING_ORIENTATION", "DEEP_ANALYSIS", "什么会让你觉得这件事值得", "C12"},
            {"CURRENT_ACTIVATION", "TIMING", "现阶段更容易被触发的成长主题", "C13"},
            {"PERSONAL_ACTION_GUIDE", "RECOMMENDATION", "你的个人使用说明", "RECOMMENDATIONS"}
        };

        int order = 1;
        for (String[] def : sectionDefs) {
            CareerSection section = new CareerSection();
            section.setSectionKey(def[0]);
            section.setSectionType(def[1]);
            section.setTitle(def[2]);
            section.setOrderNum(order++);

            // 检查相关 Claim 是否足够
            boolean hasContent = false;
            for (String claimId : def[3].split(",")) {
                LifePatternClaim claim = claims.get(claimId);
                if (claim != null && ("SUPPORTED".equals(claim.getState()) || "CONDITIONAL".equals(claim.getState()))) {
                    hasContent = true;
                    break;
                }
            }

            if (hasContent) {
                section.setSummary("COMPLETE");
            } else {
                section.setSummary("INSUFFICIENT");
            }

            sections.add(section);
        }

        return sections;
    }

    private List<CareerTiming> convertTimings(List<LifePatternTimingPayload> timings) {
        List<CareerTiming> result = new ArrayList<>();
        for (LifePatternTimingPayload timing : timings) {
            CareerTiming ct = new CareerTiming();
            try {
                if (timing.getStartDate() != null) {
                    ct.setStartDate(java.time.LocalDate.parse(timing.getStartDate()));
                }
                if (timing.getEndDate() != null) {
                    ct.setEndDate(java.time.LocalDate.parse(timing.getEndDate()));
                }
            } catch (Exception e) {
                // ignore date parse errors
            }
            result.add(ct);
        }
        return result;
    }

    private List<CareerRecommendation> convertRecommendations(List<LifePatternRecommendationPayload> recommendations) {
        List<CareerRecommendation> result = new ArrayList<>();
        for (LifePatternRecommendationPayload rec : recommendations) {
            CareerRecommendation cr = new CareerRecommendation();
            cr.setRecommendationType(rec.getRecommendationType());
            cr.setRecommendationMode("DIRECT");
            cr.setActionRiskLevel("LOW");
            cr.setSpecificityLevel("CONTEXTUAL");
            cr.setStatement(rec.getTitle());
            cr.setRationale(rec.getLowRiskAction());
            cr.setPriority("MEDIUM");
            cr.setIsUserVisible(true);
            result.add(cr);
        }
        return result;
    }

    private String generateCoverSummary(Map<String, LifePatternClaim> claims) {
        LifePatternClaim c01 = claims.get("C01");
        if (c01 != null && "SUPPORTED".equals(c01.getState())) {
            return "基于你的本命星盘，你的核心人生主题是" + c01.getCandidateDirection();
        }
        return "基于你的本命星盘，解读你的内在运行系统、保护方式与成长路径";
    }

    @SuppressWarnings("unchecked")
    private Map<String, Object> parseRenderedContent(String content) {
        try {
            if (content.trim().startsWith("{")) {
                return objectMapper.readValue(content, Map.class);
            }
        } catch (Exception e) {
            log.warn("解析 LLM 响应失败: {}", e.getMessage());
        }
        Map<String, Object> fallback = new HashMap<>();
        fallback.put("raw_content", content);
        return fallback;
    }

    private String generateReportId() {
        return "lpg_" + UUID.randomUUID().toString().replace("-", "").substring(0, 16);
    }
}
