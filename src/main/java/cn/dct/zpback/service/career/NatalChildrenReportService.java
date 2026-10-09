package cn.dct.zpback.service.career;

import cn.dct.zpback.constant.NatalChildrenConstants;
import cn.dct.zpback.entity.career.CareerReportInstance;
import cn.dct.zpback.engine.children.ChildrenClaimEngine;
import cn.dct.zpback.engine.children.ChildrenDataGate;
import cn.dct.zpback.engine.children.ChildrenEvidenceEngine;
import cn.dct.zpback.engine.children.ChildrenTimingEngine;
import cn.dct.zpback.engine.relationship.ChartDataParser;
import cn.dct.zpback.entity.children.ChildrenClaim;
import cn.dct.zpback.entity.children.ChildrenEvidenceObject;
import cn.dct.zpback.entity.lifepattern.LifePatternChartData;
import cn.dct.zpback.service.AIService;
import cn.dct.zpback.service.VedicAstroService;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.*;

/**
 * 子女与养育深度报告生成服务
 * 符合 SEALED 文档《01_子女与养育深度报告_最终开发总说明_程序员唯一执行版.md》
 *
 * 架构（确定性规则引擎 + LLM Renderer）：
 * Canonical Data → Data Gate → Evidence Normalizer → Root Dedup →
 * Claim Resolver → Timing Engine → Final Payload → LLM Renderer
 *
 * 关键约束：
 * - 固定 12 章，不收集 life_stage_context，中性表达
 * - D7 为核心盘，缺失阻断（FAILED_DATA）
 * - LLM 只排版措辞，不做任何判断
 * - 禁止生育能力、子女性别数量、孩子本人命运等表述
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class NatalChildrenReportService {

    private final AIService aiService;
    private final ObjectMapper objectMapper;
    private final ChildrenDataGate dataGate;
    private final ChildrenEvidenceEngine evidenceEngine;
    private final ChildrenClaimEngine claimEngine;
    private final ChildrenTimingEngine timingEngine;
    private final VedicAstroService vedicAstroService;
    private final ChartDataParser chartDataParser;

    // ============================================================
    // 固定 12 章定义
    // ============================================================

    private static final String[][] SECTION_DEFS = {
        {NatalChildrenConstants.SECTION_EXECUTIVE_SUMMARY, "核心结论"},
        {NatalChildrenConstants.SECTION_CHILDREN_PATH, "子女缘与家庭扩展路径"},
        {NatalChildrenConstants.SECTION_PATH_SUPPORT_AND_PRESSURE, "家庭扩展中的支持、阻力与节奏"},
        {NatalChildrenConstants.SECTION_PARENT_ROLE, "你会成为怎样的父母"},
        {NatalChildrenConstants.SECTION_NURTURING_STRENGTHS, "养育优势与天然资源"},
        {NatalChildrenConstants.SECTION_PARENTING_OVERUSE, "容易过度使用的养育模式"},
        {NatalChildrenConstants.SECTION_EMOTIONAL_SECURITY_AND_COMMUNICATION, "亲子之间的情绪、安全感与沟通"},
        {NatalChildrenConstants.SECTION_INTERACTION_ARCHETYPE, "更容易经历怎样的亲子互动"},
        {NatalChildrenConstants.SECTION_COPARENTING_ENVIRONMENT, "伴侣、家庭与共同养育环境"},
        {NatalChildrenConstants.SECTION_PARENTAL_GROWTH, "子女议题带来的人生成长"},
        {NatalChildrenConstants.SECTION_FIVE_YEAR_TIMING, "当前与未来五年的阶段变化"},
        {NatalChildrenConstants.SECTION_ACTION_GUIDE, "养育与家庭行动建议"},
    };

    /**
     * 生成子女与养育深度报告
     */
    public CareerReportInstance generateReport(String userId, Map<String, Object> chartDataRaw,
                                                String chartProfileId, Map<String, Object> birthParams) {
        log.info("开始生成子女与养育深度报告: userId={}, chartProfileId={}, hasBirthParams={}",
                userId, chartProfileId, birthParams != null && !birthParams.isEmpty());

        CareerReportInstance report = new CareerReportInstance();
        report.setReportInstanceId("NCR_" + UUID.randomUUID().toString().replace("-", "").substring(0, 16).toUpperCase());
        report.setUserId(userId);
        report.setReportType(NatalChildrenConstants.REPORT_TYPE);
        report.setReportStatus(NatalChildrenConstants.STATUS_DATA_VALIDATING);
        report.setGeneratedAt(LocalDateTime.now());
        report.setCreatedAt(LocalDateTime.now());
        report.setUpdatedAt(LocalDateTime.now());

        try {
            // 1. 获取原始星盘数据（含 D7）
            Map<String, Object> rawData = resolveRawChartData(chartDataRaw, birthParams);
            LifePatternChartData chartData = parseChartData(rawData);

            // 2. Data Gate：D7 缺失即阻断
            boolean birthTimeOk = dataGate.isBirthTimeReliable(birthParams);
            List<String> blockErrors = dataGate.validate(chartData, birthTimeOk);
            if (!blockErrors.isEmpty()) {
                log.error("子女报告数据门槛阻断: {}", blockErrors);
                report.setReportStatus(NatalChildrenConstants.STATUS_FAILED_DATA);
                Map<String, Object> err = new LinkedHashMap<>();
                err.put("status", "FAILED_DATA");
                err.put("reasons", blockErrors);
                err.put("message", "需要完整出生时间以计算 D1/D7 分盘，请校正出生时间后重试");
                report.setReportContent(err);
                return report;
            }

            List<String> degradationFlags = dataGate.collectDegradationFlags(
                    chartData, chartData.getD9() != null);
            String akPlanet = chartData.getAkPlanet();

            // 3. Evidence 构建与去重
            report.setReportStatus(NatalChildrenConstants.STATUS_EVIDENCE_RESOLVING);
            List<ChildrenEvidenceObject> evidenceList = evidenceEngine.buildEvidence(chartData, akPlanet);

            // 4. Claim 解析（C02-C09 → C10 → C01）
            report.setReportStatus(NatalChildrenConstants.STATUS_CLAIM_RESOLVING);
            Map<String, ChildrenClaim> claims = claimEngine.resolveAllClaims(evidenceList);

            // 5. 五年时序（C11）
            List<Map<String, Object>> windows = timingEngine.resolveFiveYearWindows(chartData, claims, evidenceList);
            claims.put("C11", timingEngine.summarizeTiming(windows));

            // 6. LLM Renderer（仅排版）
            report.setReportStatus(NatalChildrenConstants.STATUS_RENDERING);
            String rendered = renderWithLLM(claims, evidenceList, windows, degradationFlags, akPlanet);
            Map<String, Object> content = parseRenderedContent(rendered);
            if (!isValidReportContent(content)) {
                content = buildStructuredReportContent(claims, evidenceList, windows, degradationFlags);
            }

            report.setReportStatus(NatalChildrenConstants.STATUS_COMPLETED);
            report.setTitle(NatalChildrenConstants.DISPLAY_NAME);
            report.setSubtitle("基于你的本命星盘，理解子女与家庭扩展议题的路径、支持与成长空间");
            report.setReportContent(content);
            report.setCoverSummary(generateCoverSummary(claims));

            log.info("子女与养育深度报告生成完成: reportId={}, claims={}, evidence={}",
                    report.getReportInstanceId(), claims.size(), evidenceList.size());

        } catch (Exception e) {
            log.error("子女与养育深度报告生成失败: {}", e.getMessage(), e);
            report.setReportStatus(NatalChildrenConstants.STATUS_FAILED_RULE_ENGINE);
        }

        report.setUpdatedAt(LocalDateTime.now());
        return report;
    }

    // ============================================================
    // 数据获取
    // ============================================================

    private Map<String, Object> resolveRawChartData(Map<String, Object> chartDataRaw, Map<String, Object> birthParams) {
        if (birthParams != null && !birthParams.isEmpty()) {
            try {
                Map<String, Object> vedicParams = chartDataParser.buildVedicAstroParams(birthParams);
                log.info("调用 VedicAstro 获取星盘数据（含 D7）");
                Map<String, Object> rawData = vedicAstroService.getJyotishyaData(vedicParams);
                if (rawData != null && !rawData.isEmpty()) {
                    return rawData;
                }
            } catch (Exception e) {
                log.error("调用 VedicAstro 失败: {}", e.getMessage(), e);
            }
        }
        return chartDataRaw != null ? chartDataRaw : Collections.emptyMap();
    }

    @SuppressWarnings("unchecked")
    private LifePatternChartData parseChartData(Map<String, Object> rawData) {
        LifePatternChartData chartData = new LifePatternChartData();

        Object vargasObj = rawData.get("vargas");
        Map<String, Object> vargas = vargasObj instanceof Map ? (Map<String, Object>) vargasObj : rawData;

        Object d1Raw = vargas.get("D1");
        if (d1Raw instanceof Map) {
            chartData.setD1(parseNatalChart((Map<String, Object>) d1Raw));
        }
        Object d7Raw = vargas.get("D7");
        if (d7Raw instanceof Map) {
            chartData.setD7(parseNatalChart((Map<String, Object>) d7Raw));
        } else {
            log.warn("VedicAstro 响应未包含 D7 分盘，尝试轻量分盘接口");
            chartData.setD7(null);
        }
        Object d9Raw = vargas.get("D9");
        if (d9Raw instanceof Map) {
            chartData.setD9(parseNatalChart((Map<String, Object>) d9Raw));
        }

        // AK 解析：karaka → jaimini → D1 最高度数
        chartData.setAkPlanet(resolveAK(rawData, chartData));

        // Shadbala
        Object shadbalaObj = rawData.get("shadbala");
        if (shadbalaObj instanceof Map) {
            Map<String, cn.dct.zpback.entity.lifepattern.LifePatternShadbalaData> shadbala = new LinkedHashMap<>();
            for (Map.Entry<String, Object> entry : ((Map<String, Object>) shadbalaObj).entrySet()) {
                if (entry.getValue() instanceof Map) {
                    Map<String, Object> sdMap = (Map<String, Object>) entry.getValue();
                    cn.dct.zpback.entity.lifepattern.LifePatternShadbalaData sd = new cn.dct.zpback.entity.lifepattern.LifePatternShadbalaData();
                    Object score = sdMap.get("score");
                    if (score instanceof Number) {
                        sd.setScore(((Number) score).doubleValue());
                    } else {
                        // percent_of_required 兼容
                        Object por = sdMap.get("percent_of_required");
                        if (por instanceof Number) {
                            sd.setScore(((Number) por).doubleValue() / 100.0);
                        }
                    }
                    sd.setExecutionCapacity((String) sdMap.get("execution_capacity"));
                    shadbala.put(entry.getKey(), sd);
                }
            }
            chartData.setShadbala(shadbala);
        }

        // Dasha
        Object dashaObj = rawData.get("dasha");
        List<cn.dct.zpback.entity.lifepattern.LifePatternDashaPeriod> dashaList = new ArrayList<>();
        if (dashaObj instanceof List) {
            for (Object item : (List<?>) dashaObj) {
                if (item instanceof Map) {
                    Map<String, Object> d = (Map<String, Object>) item;
                    cn.dct.zpback.entity.lifepattern.LifePatternDashaPeriod dp = new cn.dct.zpback.entity.lifepattern.LifePatternDashaPeriod();
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
    private cn.dct.zpback.entity.lifepattern.LifePatternNatalChart parseNatalChart(Map<String, Object> raw) {
        cn.dct.zpback.entity.lifepattern.LifePatternNatalChart chart = new cn.dct.zpback.entity.lifepattern.LifePatternNatalChart();

        Object ascRaw = raw.get("ascendant");
        if (ascRaw instanceof String) {
            chart.setAscendantSign((String) ascRaw);
        } else if (ascRaw instanceof Map) {
            chart.setAscendantSign((String) ((Map<String, Object>) ascRaw).get("sign"));
        }

        Object planetsObj = raw.get("planets");
        Map<String, cn.dct.zpback.entity.lifepattern.LifePatternPlanetPosition> planets = new LinkedHashMap<>();
        if (planetsObj instanceof List) {
            for (Object item : (List<?>) planetsObj) {
                if (item instanceof Map) {
                    Map<String, Object> pm = (Map<String, Object>) item;
                    String name = (String) pm.get("name");
                    if (name != null) {
                        planets.put(name, parsePlanet(pm));
                    }
                }
            }
        } else if (planetsObj instanceof Map) {
            for (Map.Entry<String, Object> entry : ((Map<String, Object>) planetsObj).entrySet()) {
                if (entry.getValue() instanceof Map) {
                    planets.put(entry.getKey(), parsePlanet((Map<String, Object>) entry.getValue()));
                }
            }
        }
        chart.setPlanets(planets);
        return chart;
    }

    @SuppressWarnings("unchecked")
    private cn.dct.zpback.entity.lifepattern.LifePatternPlanetPosition parsePlanet(Map<String, Object> raw) {
        cn.dct.zpback.entity.lifepattern.LifePatternPlanetPosition pos = new cn.dct.zpback.entity.lifepattern.LifePatternPlanetPosition();
        Object sign = raw.get("sign");
        pos.setSign(sign != null ? String.valueOf(sign) : null);
        Object house = raw.get("house");
        if (house instanceof Number) {
            pos.setHouse(((Number) house).intValue());
        } else if (raw.get("pos") instanceof Map) {
            Object h = ((Map<String, Object>) raw.get("pos")).get("house");
            if (h instanceof Number) {
                pos.setHouse(((Number) h).intValue());
            }
            Object dd = ((Map<String, Object>) raw.get("pos")).get("dec_deg");
            if (dd instanceof Number) {
                pos.setDegree(((Number) dd).doubleValue());
            }
        }
        return pos;
    }

    private String resolveAK(Map<String, Object> rawData, LifePatternChartData chartData) {
        Object karakaObj = rawData.get("karaka");
        if (karakaObj instanceof Map) {
            Object ak = ((Map<String, Object>) karakaObj).get("AK");
            if (ak instanceof String) return (String) ak;
        }
        Object jaiminiObj = rawData.get("jaimini");
        if (jaiminiObj instanceof Map) {
            Object ak = ((Map<String, Object>) jaiminiObj).get("AK");
            if (ak instanceof String) return (String) ak;
        }
        // D1 最高度数行星（7 Karaka: Sun-Saturn）
        if (chartData.getD1() != null && chartData.getD1().getPlanets() != null) {
            String[] seven = {"Sun", "Moon", "Mars", "Mercury", "Jupiter", "Venus", "Saturn"};
            double maxDeg = -1;
            String ak = null;
            for (String p : seven) {
                cn.dct.zpback.entity.lifepattern.LifePatternPlanetPosition pos = chartData.getD1().getPlanets().get(p);
                if (pos != null && pos.getDegree() != null && pos.getDegree() > maxDeg) {
                    maxDeg = pos.getDegree();
                    ak = p;
                }
            }
            return ak;
        }
        return null;
    }

    // ============================================================
    // LLM Renderer
    // ============================================================

    private String renderWithLLM(Map<String, ChildrenClaim> claims, List<ChildrenEvidenceObject> evidenceList,
                                  List<Map<String, Object>> windows, List<String> degradationFlags, String akPlanet) {
        try {
            Map<String, Object> payload = buildLLMPayload(claims, evidenceList, windows, degradationFlags);
            String payloadJson = objectMapper.writeValueAsString(payload);

            String systemPrompt = "你是问星渡《子女与养育深度报告》的AI排版引擎，rule_version=" +
                    NatalChildrenConstants.RULE_VERSION + "。" +
                    "你的唯一职责是将已结构化的报告数据排版为通顺的中文报告，正文目标3000-4000字。" +
                    "禁止：计算星盘、创造或修改Claim、补写证据、新增Yoga或时间窗口或建议。" +
                    "必须输出JSON：title, subtitle, executive_summary(headline,keywords,body), " +
                    "sections(12个，含display_title,body,evidence_cards), action_guide, method_note。" +
                    "写作规范：中性、非宿命、非医疗表达；" +
                    "不推断用户当前是否有孩子；C08只写\"你更容易经历的互动\"，" +
                    "禁止\"你的孩子天生…\"句式；禁止提及生育能力、子女性别数量、怀孕结果。" +
                    "允许的中性句式：\"当子女与养育议题进入或已经进入生活时…\"、" +
                    "\"无论当前是否处在实际养育阶段…\"。";
            String userPrompt = "请将以下结构化数据排版为12章中文报告：\n\n" + payloadJson;

            String llmResponse = aiService.generateLifePatternReport(systemPrompt, userPrompt);
            Map<String, Object> parsed = objectMapper.readValue(sanitizeJson(llmResponse), Map.class);
            if (isValidReportContent(parsed)) {
                return sanitizeJson(llmResponse);
            }
            log.warn("LLM 输出结构不完整（第一次），使用相同 Payload 重试一次");
            // 同一 Payload 重试一次
            String retry = aiService.generateLifePatternReport(systemPrompt, userPrompt);
            Map<String, Object> retryParsed = objectMapper.readValue(sanitizeJson(retry), Map.class);
            if (isValidReportContent(retryParsed)) {
                return sanitizeJson(retry);
            }
            log.warn("LLM 渲染两次失败，回退到确定性内容");
            return generateDefaultContent(claims, evidenceList, windows, degradationFlags);
        } catch (Exception e) {
            log.warn("LLM 渲染异常，使用确定性内容: {}", e.getMessage());
            return generateDefaultContent(claims, evidenceList, windows, degradationFlags);
        }
    }

    private Map<String, Object> buildLLMPayload(Map<String, ChildrenClaim> claims,
                                                 List<ChildrenEvidenceObject> evidenceList,
                                                 List<Map<String, Object>> windows,
                                                 List<String> degradationFlags) {
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("report_metadata", Map.of(
            "report_type", NatalChildrenConstants.REPORT_TYPE,
            "rule_version", NatalChildrenConstants.RULE_VERSION,
            "renderer_version", NatalChildrenConstants.RENDERER_VERSION));
        payload.put("data_quality", Map.of("degradation_flags", degradationFlags));

        List<Map<String, Object>> claimList = new ArrayList<>();
        for (ChildrenClaim c : claims.values()) {
            Map<String, Object> cm = new LinkedHashMap<>();
            cm.put("claim_id", c.getClaimId());
            cm.put("state", c.getState());
            cm.put("confidence", c.getConfidence());
            cm.put("primary_candidate", c.getPrimaryCandidate());
            cm.put("secondary_candidate", c.getSecondaryCandidate());
            claimList.add(cm);
        }
        payload.put("resolved_claims", claimList);
        payload.put("timing_windows", windows);

        // Evidence Cards（可见摘要，每 Claim 最多2条，共最多12条）
        List<Map<String, String>> cards = new ArrayList<>();
        for (ChildrenEvidenceObject ev : evidenceList) {
            if (!"ELIGIBLE".equals(ev.getEvidenceStatus())) continue;
            if ("CONTEXT_ONLY".equals(ev.getPolarity())) continue;
            Map<String, String> card = new LinkedHashMap<>();
            card.put("plain_summary", ev.getSubject() + "：" + ev.getRawFact());
            card.put("lane", ev.getEvidenceLane());
            cards.add(card);
            if (cards.size() >= 12) break;
        }
        payload.put("evidence_cards", cards);
        payload.put("renderer_policy", Map.of(
            "fixed_sections", SECTION_DEFS.length,
            "neutral_expression", true,
            "no_life_stage_assumption", true));
        return payload;
    }

    private String sanitizeJson(String s) {
        if (s == null) return "{}";
        String t = s.trim();
        if (t.startsWith("```json")) {
            t = t.substring(7);
        } else if (t.startsWith("```")) {
            t = t.substring(3);
        }
        if (t.endsWith("```")) {
            t = t.substring(0, t.length() - 3);
        }
        return t.trim();
    }

    @SuppressWarnings("unchecked")
    private Map<String, Object> parseRenderedContent(String content) {
        try {
            if (content != null && content.trim().startsWith("{")) {
                return objectMapper.readValue(content, Map.class);
            }
        } catch (Exception e) {
            log.warn("解析渲染内容失败: {}", e.getMessage());
        }
        Map<String, Object> fallback = new HashMap<>();
        fallback.put("raw_content", content != null ? content : "");
        return fallback;
    }

    private boolean isValidReportContent(Map<String, Object> content) {
        if (content == null || content.isEmpty()) return false;
        Object sections = content.get("sections");
        if (!(sections instanceof List) || ((List<?>) sections).size() < 12) return false;
        for (Object s : (List<?>) sections) {
            if (s instanceof Map) {
                Object body = ((Map<?, ?>) s).get("body");
                if (body instanceof String && ((String) body).trim().length() > 100) {
                    return true;
                }
            }
        }
        return false;
    }

    // ============================================================
    // 确定性内容生成（12 固定章节）
    // ============================================================

    private String generateDefaultContent(Map<String, ChildrenClaim> claims,
                                           List<ChildrenEvidenceObject> evidenceList,
                                           List<Map<String, Object>> windows,
                                           List<String> degradationFlags) {
        try {
            return objectMapper.writeValueAsString(
                    buildStructuredReportContent(claims, evidenceList, windows, degradationFlags));
        } catch (Exception e) {
            log.error("生成默认内容失败: {}", e.getMessage(), e);
            return "{\"title\":\"子女与养育深度报告\",\"sections\":[]}";
        }
    }

    private Map<String, Object> buildStructuredReportContent(Map<String, ChildrenClaim> claims,
                                                              List<ChildrenEvidenceObject> evidenceList,
                                                              List<Map<String, Object>> windows,
                                                              List<String> degradationFlags) {
        Map<String, Object> content = new LinkedHashMap<>();
        content.put("title", NatalChildrenConstants.DISPLAY_NAME);
        content.put("subtitle", "基于你的本命星盘，理解子女与家庭扩展议题的路径、支持与成长空间");

        // ===== 00 核心结论 =====
        ChildrenClaim c01 = claims.get("C01");
        ChildrenClaim c02 = claims.get("C02");
        ChildrenClaim c04 = claims.get("C04");
        ChildrenClaim c10 = claims.get("C10");

        Map<String, Object> summary = new LinkedHashMap<>();
        summary.put("headline", claimUsable(c01) ? "你的子女与养育主题已有清晰结构" : "你的子女与养育主题总览");
        List<String> keywords = new ArrayList<>();
        if (claimUsable(c02)) keywords.add(candidateCN(c02.getPrimaryCandidate()));
        if (claimUsable(c04)) keywords.add(candidateCN(c04.getPrimaryCandidate()));
        if (claimUsable(c10)) keywords.add(candidateCN(c10.getPrimaryCandidate()));
        if (keywords.isEmpty()) keywords.add("关系准备");
        summary.put("keywords", keywords);

        StringBuilder sb = new StringBuilder();
        sb.append("这份报告基于你的本命星盘（D1）与子女分盘（D7），从家庭扩展路径、父母角色、养育资源、")
          .append("亲子互动与共同养育环境等角度，理解你在子女议题上的整体结构。")
          .append("当子女与养育议题进入或已经进入生活时，这些结构会影响你的体验与选择。\n\n");
        if (claimUsable(c02)) {
            sb.append("家庭扩展路径上，你的星盘显示为「").append(candidateCN(c02.getPrimaryCandidate()))
              .append("」的结构。这一描述指路径的资源与节奏，不是生育结果的判断。\n");
        }
        if (claimUsable(c10)) {
            sb.append("子女议题为你带来的核心成长练习是「").append(candidateCN(c10.getPrimaryCandidate())).append("」。\n");
        }
        summary.put("body", sb.toString());
        content.put("executive_summary", summary);

        // ===== 01-11 固定章节 =====
        List<Map<String, Object>> sections = new ArrayList<>();
        sections.add(buildSection("CHILDREN_PATH", "子女缘与家庭扩展路径",
                claimUsable(c02) ? c02.getPrimaryCandidate() : null, c02, evidenceList,
                "这一主题既可能表现为家庭扩展的准备，也可能表现为现实养育中的调整。",
                claimText(c02, "家庭扩展路径")));
        sections.add(buildSection("PATH_SUPPORT_AND_PRESSURE", "家庭扩展中的支持、阻力与节奏",
                null, claims.get("C03"), evidenceList,
                "支持与阻力并存的路径，需要理解节奏而非强求时间表。",
                claimText(claims.get("C03"), "路径中的压力与节奏来源")));
        sections.add(buildSection("PARENT_ROLE", "你会成为怎样的父母",
                claimUsable(c04) ? c04.getPrimaryCandidate() : null, c04, evidenceList,
                "这里描述的是你更自然采用的父母角色倾向。",
                roleText(claims.get("C04"))));
        sections.add(buildSection("NURTURING_STRENGTHS", "养育优势与天然资源",
                null, claims.get("C05"), evidenceList,
                "天然资源仍需要现实练习才能稳定调用。",
                claimText(claims.get("C05"), "你的养育资源")));
        sections.add(buildSection("PARENTING_OVERUSE", "容易过度使用的养育模式",
                null, claims.get("C06"), evidenceList,
                "过度模式曾是保护方式，识别它不是为了自责。",
                claimText(claims.get("C06"), "容易过度使用的模式")));
        sections.add(buildSection("EMOTIONAL_SECURITY_AND_COMMUNICATION", "亲子之间的情绪、安全感与沟通",
                null, claims.get("C07"), evidenceList,
                "这里描述你的沟通方式倾向，不代表孩子必须接受的沟通模式。",
                claimText(claims.get("C07"), "你的沟通方式倾向")));
        sections.add(buildSection("INTERACTION_ARCHETYPE", "更容易经历怎样的亲子互动",
                null, claims.get("C08"), evidenceList,
                "你更容易形成或经历的互动原型——主语始终是你。",
                claimText(claims.get("C08"), "你更容易经历的互动原型")));
        sections.add(buildSection("COPARENTING_ENVIRONMENT", "伴侣、家庭与共同养育环境",
                null, claims.get("C09"), evidenceList,
                "共同养育环境涉及伴侣、家人与支持系统的协作结构。",
                claimText(claims.get("C09"), "共同养育环境倾向")));
        sections.add(buildSection("PARENTAL_GROWTH", "子女议题带来的人生成长",
                claimUsable(c10) ? c10.getPrimaryCandidate() : null, c10, evidenceList,
                "成长任务来自你已有的结构，不是外在要求。",
                claimText(c10, "你的成长练习")));
        sections.add(buildTimingSection(claims.get("C11"), windows));
        sections.add(buildActionGuideSection(claims));
        content.put("sections", sections);

        // 方法说明
        content.put("method_note", NatalChildrenConstants.NEUTRAL_DISCLAIMER
                + " 本报告由确定性规则引擎（" + NatalChildrenConstants.RULE_VERSION
                + "）基于 D1/D7 分盘解析生成" + (degradationFlags.isEmpty() ? "。" :
                "，以下模块降级：" + String.join("、", degradationFlags) + "。"));
        return content;
    }

    // ============================================================
    // 章节构建辅助
    // ============================================================

    private Map<String, Object> buildSection(String sectionKey, String title, String candidate,
                                              ChildrenClaim claim, List<ChildrenEvidenceObject> evidenceList,
                                              String neutralIntro, String bodyText) {
        Map<String, Object> section = new LinkedHashMap<>();
        section.put("section_key", sectionKey);
        section.put("display_title", title);

        boolean full = claim != null && ("SUPPORTED".equals(claim.getState()) || "MIXED".equals(claim.getState())
                || "CONDITIONAL".equals(claim.getState()));
        section.put("section_mode", full ? "FULL" : "SHORT");

        StringBuilder sb = new StringBuilder();
        sb.append(neutralIntro).append("\n\n");
        if (full) {
            sb.append("**本章结论：").append(candidate != null ? candidateCN(candidate) : bodyText).append("。**\n\n");
            sb.append(bodyText).append("\n");
            if (claim.getSecondaryCandidate() != null) {
                sb.append("\n同时存在次要倾向：「").append(candidateCN(claim.getSecondaryCandidate()))
                  .append("」，两种方向在不同情境下交替出现。\n");
            }
            if ("CONDITIONAL".equals(claim.getState())) {
                sb.append("\n（本章结论基于部分可用数据，条件性成立。）\n");
            }
        } else {
            sb.append("本章相关的星盘证据尚不充分，为避免武断解读，此处保持信息边界，不做展开。")
              .append("这本身就是一种诚实：结构不明显时，观察比结论更有用。\n");
        }
        section.put("body", sb.toString());

        // Evidence Cards（FULL 章 2-5 条）
        List<Map<String, String>> cards = new ArrayList<>();
        if (full && claim.getSupportRootIds() != null) {
            for (String evId : claim.getSupportRootIds()) {
                if (cards.size() >= 5) break;
                for (ChildrenEvidenceObject ev : evidenceList) {
                    if (evId.equals(ev.getEvidenceId()) && "ELIGIBLE".equals(ev.getEvidenceStatus())) {
                        Map<String, String> card = new LinkedHashMap<>();
                        card.put("plain_language", "星盘中「" + ev.getSubject() + "」的结构支持本章解读");
                        card.put("technical_summary", ev.getRawFact() + " [" + ev.getEvidenceLane() + "]");
                        cards.add(card);
                        break;
                    }
                }
            }
        }
        section.put("evidence_cards", cards);
        return section;
    }

    private Map<String, Object> buildTimingSection(ChildrenClaim c11, List<Map<String, Object>> windows) {
        Map<String, Object> section = new LinkedHashMap<>();
        section.put("section_key", NatalChildrenConstants.SECTION_FIVE_YEAR_TIMING);
        section.put("display_title", "当前与未来五年的阶段变化");

        StringBuilder sb = new StringBuilder();
        if (windows != null && !windows.isEmpty()) {
            section.put("section_mode", "FULL");
            sb.append("以下窗口只表达\"议题活跃、准备、协商、责任增加或整合\"，不承诺具体事件。\n\n");
            int i = 1;
            for (Map<String, Object> w : windows) {
                sb.append(i++).append(". **").append(w.get("start_date")).append(" ~ ").append(w.get("end_date"))
                  .append("**（").append(candidateCN(String.valueOf(w.get("window_classification")))).append("）\n")
                  .append(w.get("neutral_expression")).append("\n\n");
            }
        } else {
            section.put("section_mode", "SHORT");
            sb.append("时序数据不足，无法给出可靠的五年窗口。")
              .append("这可能是大运边界期或数据精度限制；核心章节中的结构性观察仍然适用。\n");
        }
        section.put("body", sb.toString());
        section.put("evidence_cards", new ArrayList<>());
        return section;
    }

    private Map<String, Object> buildActionGuideSection(Map<String, ChildrenClaim> claims) {
        Map<String, Object> section = new LinkedHashMap<>();
        section.put("section_key", NatalChildrenConstants.SECTION_ACTION_GUIDE);
        section.put("display_title", "养育与家庭行动建议");

        List<Map<String, String>> recommendations = buildRecommendations(claims);

        StringBuilder sb = new StringBuilder();
        if (recommendations.size() >= 3) {
            section.put("section_mode", "FULL");
        } else {
            section.put("section_mode", "SHORT");
        }
        sb.append("以下建议均来自已解析的 Claim 结构，属于可控的现实行动。\n\n");
        int i = 1;
        for (Map<String, String> rec : recommendations) {
            sb.append(i++).append(". **").append(rec.get("action")).append("**\n")
              .append("   - 依据：").append(rec.get("rationale")).append("\n")
              .append("   - 边界：").append(rec.get("boundary")).append("\n");
        }
        sb.append("\n请注意：任何建议都不能替代医疗、心理、法律或生育专业意见。");
        section.put("body", sb.toString());
        section.put("evidence_cards", new ArrayList<>());
        section.put("recommendations", recommendations);
        return section;
    }

    private List<Map<String, String>> buildRecommendations(Map<String, ChildrenClaim> claims) {
        List<Map<String, String>> recs = new ArrayList<>();

        ChildrenClaim c03 = claims.get("C03");
        ChildrenClaim c06 = claims.get("C06");
        ChildrenClaim c07 = claims.get("C07");
        ChildrenClaim c09 = claims.get("C09");
        ChildrenClaim c10 = claims.get("C10");
        ChildrenClaim c02 = claims.get("C02");

        if (c02 != null && claimUsable(c02) && "COMPLEX_OR_DELAYED".equals(c02.getPrimaryCandidate())) {
            recs.add(rec("现实准备", "C02",
                    "把家庭扩展当作需要准备的长期项目，逐步建立资源与支持系统，而不是与时间表竞赛。",
                    "路径的节奏感来自本命结构，准备能降低压力，但不能改变节奏本身。",
                    "不以此判断或承诺任何生育结果。"));
        }
        if (c03 != null && claimUsable(c03) && "TIMING_AND_PATIENCE".equals(c03.getPrimaryCandidate())) {
            recs.add(rec("节奏管理", "C03",
                    "为家庭议题设定更长的观察周期，把\"等\"重新定义为准备期的日常积累。",
                    "延迟感是结构性的节奏要求，不是缺陷。",
                    "不与他人的时间表比较。"));
        }
        if (c06 != null && claimUsable(c06)) {
            recs.add(rec("识别过度模式", "C06",
                    "在「" + candidateCN(c06.getPrimaryCandidate()) + "」启动时给自己一个暂停信号，先确认需要再行动。",
                    "过度模式曾保护过你，识别它的目的是增加选择，不是自我批评。",
                    "不强行压制，只增加觉察。"));
        }
        if (c07 != null && claimUsable(c07)) {
            recs.add(rec("沟通练习", "C07",
                    "尝试在你习惯的方式（" + candidateCN(c07.getPrimaryCandidate()) + "）之外，每周增加一次不同的回应方式。",
                    "扩展沟通弹性来自本命倾向的结构性建议。",
                    "不把方式强加为孩子必须接受的模式。"));
        }
        if (c09 != null && claimUsable(c09) && "NEGOTIATION_REQUIRED".equals(c09.getPrimaryCandidate())) {
            recs.add(rec("共同养育协商", "C09",
                    "与伴侣/家人建立定期分工回顾机制，把期待差异摆在桌面上而不是积累。",
                    "共同养育环境的协商需求来自本命结构。",
                    "不预设对方一定会按你的方式配合。"));
        }
        if (c10 != null && claimUsable(c10)) {
            recs.add(rec("成长练习", "C10",
                    "围绕「" + candidateCN(c10.getPrimaryCandidate()) + "」设定一个本季度可观察的小练习。",
                    "成长任务由已有结构派生，练习把它变成能力。",
                    "以自我观察为准，不做外部比较。"));
        }
        if (recs.size() < 3) {
            recs.add(rec("基础准备", "GENERAL",
                    "记录你在照顾、教育、责任相关情境中的自动反应模式，建立自我观察日志。",
                    "无论结构倾向如何，自我观察都是养育准备的通用资源。",
                    "记录用于自我理解，不作为评判依据。"));
        }
        return recs;
    }

    private Map<String, String> rec(String action, String sourceClaimIds, String rationale, String boundary, String extra) {
        Map<String, String> m = new LinkedHashMap<>();
        m.put("recommendation_id", "REC_" + UUID.randomUUID().toString().substring(0, 8).toUpperCase());
        m.put("source_claim_ids", sourceClaimIds);
        m.put("action", action);
        m.put("rationale", rationale);
        m.put("boundary", boundary);
        m.put("horizon", "未来五年内持续推进");
        m.put("priority", "NORMAL");
        return m;
    }

    // ============================================================
    // 文案辅助（中性表达）
    // ============================================================

    private boolean claimUsable(ChildrenClaim c) {
        return c != null && ("SUPPORTED".equals(c.getState()) || "CONDITIONAL".equals(c.getState())
                || "MIXED".equals(c.getState()));
    }

    private String claimText(ChildrenClaim claim, String topic) {
        if (claim == null || !claimUsable(claim)) {
            return topic + "相关的星盘证据尚不充分，此处保持信息边界。";
        }
        StringBuilder sb = new StringBuilder();
        sb.append(topic).append("的主要方向是「").append(candidateCN(claim.getPrimaryCandidate())).append("」。");
        switch (claim.getState()) {
            case "SUPPORTED":
                sb.append("这一方向在你的 D1 与 D7 中都有清晰的结构支持，是最稳定的主线。");
                break;
            case "CONDITIONAL":
                sb.append("这一方向有部分结构支持，属于条件性成立，需要结合实际情况观察。");
                break;
            case "MIXED":
                sb.append("这一方向存在两种并立的结构信号，在不同阶段可能交替显现。");
                break;
            default:
                break;
        }
        sb.append("这一描述指向结构与倾向，不是对结果的判断。");
        return sb.toString();
    }

    private String roleText(ChildrenClaim c04) {
        if (c04 == null || !claimUsable(c04)) {
            return "父母角色倾向的星盘证据尚不充分。";
        }
        String role = c04.getPrimaryCandidate();
        StringBuilder sb = new StringBuilder();
        sb.append("你更自然采用的父母角色倾向是「").append(candidateCN(role)).append("」。\n\n");
        switch (role != null ? role : "") {
            case "PROTECTIVE_CAREGIVER":
                sb.append("你的养育本能先于规则：你关注孩子是否感到安全、被接住。")
                  .append("这一优势在你自己曾经缺乏安全感的关系里尤其珍贵，")
                  .append("需要注意的是：保护欲过强时，孩子学习面对风险的空间会被压缩。");
                break;
            case "GUIDING_TEACHER":
                sb.append("你擅长把经验翻译成孩子能理解的意义，是天然的解释者。")
                  .append("需要注意的是：当孩子只想被倾听时，\"讲道理\"可能变成距离。");
                break;
            case "STRUCTURE_BUILDER":
                sb.append("你通过稳定的规则与节奏给孩子可预期的世界。")
                  .append("需要注意的是：结构过于刚性时，孩子的自发性需要被预留空间。");
                break;
            case "INDEPENDENCE_COACH":
                sb.append("你尊重孩子的自主空间，愿意让孩子自己尝试。")
                  .append("需要注意的是：过强的\"放养\"可能让孩子在需要依靠时不敢开口。");
                break;
            case "PRACTICAL_PROVIDER":
                sb.append("你把爱落实为现实的保障与资源的打理。")
                  .append("需要注意的是：物质供给无法完全替代情感在场。");
                break;
            case "EMOTIONAL_COMPANION":
                sb.append("你重视陪伴本身，容易与孩子建立像朋友一样的连结。")
                  .append("需要注意的是：为了避免冲突而模糊边界时，规则感需要补位。");
                break;
            case "DEVELOPMENT_PLANNER":
                sb.append("你善于为孩子的成长做规划、提供学习资源。")
                  .append("需要注意的是：计划应服务于孩子本人的兴趣，而不是你的期待。");
                break;
            default:
                sb.append("你的角色倾向是混合型的，会随情境在照顾者、引导者与陪伴者之间切换。");
        }
        return sb.toString();
    }

    private String candidateCN(String candidate) {
        if (candidate == null) return "待明确";
        switch (candidate) {
            // C01
            case "GROWTH_THROUGH_CARE_AND_CONTINUITY": return "在照顾与传承中成长";
            case "GROWTH_THROUGH_RESPONSIBILITY_AND_PATIENCE": return "在责任与耐心中成长";
            case "GROWTH_THROUGH_FLEXIBILITY_AND_NONLINEARITY": return "在灵活与非线性的路径中成长";
            case "GROWTH_THROUGH_RELATIONSHIP_COORDINATION": return "在关系协作中成长";
            case "GROWTH_THROUGH_BALANCING_CARE_AND_SELF": return "在照顾他人与自己之间取得平衡";
            case "COMPLEX_MIXED_THEME": return "多主题并立的复合成长";
            case "INSUFFICIENT_THEME": return "主题证据不足";
            // C02
            case "RELATIVELY_SUPPORTED": return "路径相对有支持";
            case "SUPPORTED_WITH_RESPONSIBILITY": return "有支持但伴随责任要求";
            case "COMPLEX_OR_DELAYED": return "路径复杂、需要更多耐心与节奏";
            case "NONLINEAR_OR_UNCONVENTIONAL": return "路径非线性、不同于常规";
            case "MIXED_PATH": return "路径信号混合";
            case "INSUFFICIENT_PATH": return "路径证据不足";
            // C03
            case "TIMING_AND_PATIENCE": return "节奏与耐心的考验";
            case "PARTNERSHIP_COORDINATION": return "需要伴侣协同";
            case "PRACTICAL_RESOURCE_LOAD": return "现实资源负荷";
            case "CONTROL_OR_WORRY_LOAD": return "控制感与担忧的负荷";
            case "RESPONSIBILITY_OVERLOAD": return "责任负荷偏重";
            case "CHANGE_OR_UNPREDICTABILITY": return "变化与不可预期";
            case "MIXED_PRESSURE": return "多种压力并立";
            case "NO_DOMINANT_PRESSURE": return "无明显主导压力";
            // C04
            case "PROTECTIVE_CAREGIVER": return "保护型照顾者";
            case "GUIDING_TEACHER": return "引导型老师";
            case "STRUCTURE_BUILDER": return "结构建立者";
            case "INDEPENDENCE_COACH": return "独立空间教练";
            case "PRACTICAL_PROVIDER": return "现实供给者";
            case "EMOTIONAL_COMPANION": return "情感陪伴者";
            case "DEVELOPMENT_PLANNER": return "成长规划者";
            case "ADAPTIVE_MIXED_ROLE": return "混合适应型角色";
            // C05
            case "EMOTIONAL_ATTUNEMENT": return "情绪敏锐与共情";
            case "PATIENCE_CONSISTENCY": return "耐心与稳定";
            case "GUIDANCE_MEANING": return "引导与意义感";
            case "PRACTICAL_SUPPORT": return "现实支持";
            case "BOUNDARY_PROTECTION": return "边界与保护";
            case "COMMUNICATION_ADAPTABILITY": return "沟通与适应力";
            case "LEARNING_ENCOURAGEMENT": return "鼓励学习与好奇";
            case "CRISIS_STEADINESS": return "危机中的稳定";
            case "ADAPTIVE_RESOURCE_MIX": return "多元资源组合";
            // C06
            case "OVERCONTROL": return "过度控制";
            case "OVERPROTECTION": return "过度保护";
            case "OVEREXPECTATION": return "过度期待";
            case "OVERRESPONSIBILITY": return "过度承担";
            case "OVERACCOMMODATION": return "过度迁就";
            case "EMOTIONAL_WITHDRAWAL": return "情感回避";
            case "INCONSISTENT_BOUNDARIES": return "边界不稳定";
            case "OVERANALYSIS_WORRY": return "过度分析与担忧";
            case "MIXED_OVERUSE_RISK": return "多种过度倾向并立";
            case "NO_DOMINANT_OVERUSE_RISK": return "无明显主导过度模式";
            // C07
            case "EMOTION_FIRST": return "先处理情绪";
            case "REASON_FIRST": return "先讲道理";
            case "ACTION_FIRST": return "先行动示范";
            case "RULE_FIRST": return "先给规则";
            case "SPACE_FIRST": return "先给空间";
            case "PLAY_EXPRESSION_FIRST": return "先玩在一起";
            case "GUIDANCE_FIRST": return "先给引导";
            case "ADAPTIVE_MIXED": return "混合适应型沟通";
            // C08
            case "CLOSE_COLLABORATIVE": return "亲密协作型";
            case "PROTECTIVE_DEPENDENT": return "保护-依赖型";
            case "INDEPENDENT_SPACIOUS": return "独立-宽松型";
            case "STRUCTURED_MENTORING": return "结构-导师型";
            case "ACTIVE_CHALLENGING": return "活跃-挑战型";
            case "SENSITIVE_REPAIR_ORIENTED": return "敏感-修复型";
            case "CHANGEABLE_LEARNING": return "变化-学习型";
            case "MIXED_INTERACTION": return "混合互动型";
            // C09
            case "SHARED_RESPONSIBILITY": return "责任共担";
            case "ROLE_SPECIALIZATION": return "角色分工互补";
            case "NEGOTIATION_REQUIRED": return "需要持续协商";
            case "EXTERNAL_FAMILY_INFLUENCE": return "受大家庭影响";
            case "RESOURCE_COORDINATION": return "资源协同";
            case "BOUNDARY_WITH_EXTENDED_FAMILY": return "需要与大家庭立边界";
            case "MIXED_COPARENTING_ENVIRONMENT": return "混合型共同养育环境";
            case "INSUFFICIENT_COPARENTING_DATA": return "共同养育证据不足";
            // C10
            case "PATIENCE_WITH_UNCERTAINTY": return "与不确定共处的耐心";
            case "FLEXIBLE_CONTROL": return "更有弹性的掌控";
            case "TRUST_AND_RELEASE": return "信任与放手";
            case "BALANCE_SELF_AND_CARE": return "平衡照顾与自己";
            case "EXPRESS_NEEDS_AND_LIMITS": return "表达需要与边界";
            case "RECEIVE_SUPPORT": return "学会接受支持";
            case "ADAPT_EXPECTATIONS": return "调整期待";
            case "SUSTAIN_CONNECTION_ACROSS_CHANGE": return "在变化中维持连结";
            case "INTEGRATED_GROWTH_TASK": return "整合型成长任务";
            case "INSUFFICIENT_GROWTH_TASK": return "成长任务证据不足";
            // C11
            case "SUPPORTIVE_WINDOW": return "支持窗口";
            case "MIXED_WINDOW": return "混合窗口";
            case "PREPARATION_WINDOW": return "准备窗口";
            case "LOW_ACTIVATION": return "低激活期";
            case "INSUFFICIENT": return "数据不足";
            default: return candidate;
        }
    }

    private String generateCoverSummary(Map<String, ChildrenClaim> claims) {
        ChildrenClaim c01 = claims.get("C01");
        if (claimUsable(c01)) {
            return "核心主题：" + candidateCN(c01.getPrimaryCandidate());
        }
        return "子女与养育主题的结构性观察";
    }
}
