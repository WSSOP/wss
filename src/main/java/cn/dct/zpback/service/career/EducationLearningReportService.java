package cn.dct.zpback.service.career;

import cn.dct.zpback.constant.EducationReportConstants;
import cn.dct.zpback.entity.career.CareerReportInstance;
import cn.dct.zpback.entity.education.EducationClaim;
import cn.dct.zpback.entity.education.EducationEvidenceObject;
import cn.dct.zpback.engine.education.EducationClaimEngine;
import cn.dct.zpback.engine.education.EducationDataGate;
import cn.dct.zpback.engine.education.EducationEvidenceEngine;
import cn.dct.zpback.engine.education.EducationTimingEngine;
import cn.dct.zpback.engine.relationship.ChartDataParser;
import cn.dct.zpback.entity.lifepattern.LifePatternChartData;
import cn.dct.zpback.entity.lifepattern.LifePatternDashaPeriod;
import cn.dct.zpback.entity.lifepattern.LifePatternNatalChart;
import cn.dct.zpback.entity.lifepattern.LifePatternPlanetPosition;
import cn.dct.zpback.entity.lifepattern.LifePatternShadbalaData;
import cn.dct.zpback.service.AIService;
import cn.dct.zpback.service.VedicAstroService;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.*;

/**
 * 学业深度解析报告生成服务
 * 符合 SEALED 文档《01_学业深度解析报告_最终开发总说明_程序员唯一执行版.md》V1.0.0
 *
 * 架构（claim contract §1 运行时不变量）：
 * canonical facts → eligible Evidence → deduplicated Candidates
 * → D1 sufficiency → D1×D24 resolution → derived Claims → Dasha activation → Recommendations
 *
 * 关键约束：
 * - D24 为必需确认盘，缺失硬阻断（不得以 D1 简版静默替代）
 * - LLM 只排版措辞，不做任何判断；首次失败相同 Payload 重试一次
 * - 低证据 SHORTEN_ON_LOW_EVIDENCE：缩短或省略，不用常识填充
 * - 禁止考试结果、录取概率、学历上限、智力判断等禁断结论
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class EducationLearningReportService {

    private final AIService aiService;
    private final ObjectMapper objectMapper;
    private final EducationDataGate dataGate;
    private final EducationEvidenceEngine evidenceEngine;
    private final EducationClaimEngine claimEngine;
    private final EducationTimingEngine timingEngine;
    private final VedicAstroService vedicAstroService;
    private final ChartDataParser chartDataParser;

    /**
     * 生成学业深度解析报告
     */
    public CareerReportInstance generateReport(String userId, Map<String, Object> chartDataRaw,
                                                String chartProfileId, Map<String, Object> birthParams) {
        log.info("开始生成学业深度解析报告: userId={}, chartProfileId={}, hasBirthParams={}",
                userId, chartProfileId, birthParams != null && !birthParams.isEmpty());

        CareerReportInstance report = new CareerReportInstance();
        report.setReportInstanceId("ELR_" + UUID.randomUUID().toString().replace("-", "").substring(0, 16).toUpperCase());
        report.setUserId(userId);
        report.setReportType(EducationReportConstants.REPORT_TYPE);
        report.setReportStatus("DATA_VALIDATING");
        report.setGeneratedAt(LocalDateTime.now());
        report.setCreatedAt(LocalDateTime.now());
        report.setUpdatedAt(LocalDateTime.now());

        try {
            // 1. 获取原始星盘数据（含 D24）
            Map<String, Object> rawData = resolveRawChartData(chartDataRaw, birthParams);
            LifePatternChartData chartData = parseChartData(rawData);

            // 2. 硬阻断闸门
            boolean birthTimeVerified = dataGate.isBirthTimeVerified(birthParams);
            List<String> blockErrors = dataGate.validateHardBlocks(chartData, birthTimeVerified, 0);
            if (!blockErrors.isEmpty()) {
                log.error("学业报告数据门槛阻断: {}", blockErrors);
                report.setReportStatus("FAILED_DATA");
                Map<String, Object> err = new LinkedHashMap<>();
                err.put("status", "FAILED_DATA");
                err.put("reasons", blockErrors);
                err.put("message", blockErrors.contains(EducationReportConstants.ERR_D24_REQUIRED_DATA_MISSING)
                        ? "学业报告需要 D24 学业分盘与完整出生时间，请校正出生信息后重试"
                        : "需要完整出生时间与分盘数据，请校正后重试");
                report.setReportContent(err);
                return report;
            }

            List<String> degradationFlags = dataGate.collectDegradationFlags(
                    chartData, false, false, birthParams != null && !birthParams.isEmpty());

            // 3. Evidence 构建与去重
            report.setReportStatus("EVIDENCE_RESOLVING");
            List<EducationEvidenceObject> evidenceList = evidenceEngine.buildEvidence(chartData);

            // 4. Claim 解析（C02-C12 → C01）
            report.setReportStatus("CLAIM_RESOLVING");
            Map<String, EducationClaim> claims = claimEngine.resolveAllClaims(evidenceList);

            // 5. Dasha 阶段窗口（C13）
            List<Map<String, Object>> windows = timingEngine.resolveTimingWindows(chartData, claims, evidenceList);
            claims.put("C13", timingEngine.summarizeTiming(windows));

            // 6. LLM Renderer（仅排版，失败同 Payload 重试一次）
            report.setReportStatus("RENDERING");
            String rendered = renderWithLLM(claims, evidenceList, windows, degradationFlags);
            Map<String, Object> content = parseRenderedContent(rendered);
            if (!isValidReportContent(content)) {
                content = buildStructuredReportContent(claims, evidenceList, windows, degradationFlags);
            }

            report.setReportStatus("COMPLETED");
            report.setTitle(EducationReportConstants.DISPLAY_NAME);
            report.setSubtitle("理解你如何学习、优势与阻力在哪、适合怎样的方法与方向");
            report.setReportContent(content);
            report.setCoverSummary(generateCoverSummary(claims));

            log.info("学业深度解析报告生成完成: reportId={}, claims={}, evidence={}",
                    report.getReportInstanceId(), claims.size(), evidenceList.size());

        } catch (Exception e) {
            log.error("学业深度解析报告生成失败: {}", e.getMessage(), e);
            report.setReportStatus("FAILED_RULE_ENGINE");
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
                log.info("调用 VedicAstro 获取星盘数据（含 D24）");
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
        Object d24Raw = vargas.get("D24");
        if (d24Raw instanceof Map) {
            chartData.setD24(parseNatalChart((Map<String, Object>) d24Raw));
        }

        chartData.setShadbala(parseShadbala(rawData));
        chartData.setDashaList(parseDasha(rawData));
        return chartData;
    }

    @SuppressWarnings("unchecked")
    private Map<String, LifePatternShadbalaData> parseShadbala(Map<String, Object> rawData) {
        Map<String, LifePatternShadbalaData> shadbala = new LinkedHashMap<>();
        Object shadbalaObj = rawData.get("shadbala");
        if (shadbalaObj instanceof Map) {
            for (Map.Entry<String, Object> entry : ((Map<String, Object>) shadbalaObj).entrySet()) {
                if (!(entry.getValue() instanceof Map)) continue;
                Map<String, Object> sdMap = (Map<String, Object>) entry.getValue();
                LifePatternShadbalaData sd = new LifePatternShadbalaData();
                Object ratio = sdMap.get("ratio_to_required");
                if (ratio instanceof Number) {
                    sd.setScore(((Number) ratio).doubleValue());
                } else {
                    Object score = sdMap.get("score");
                    if (score instanceof Number) {
                        sd.setScore(((Number) score).doubleValue());
                    }
                }
                sd.setExecutionCapacity((String) sdMap.get("execution_capacity"));
                shadbala.put(entry.getKey(), sd);
            }
        }
        return shadbala;
    }

    @SuppressWarnings("unchecked")
    private List<LifePatternDashaPeriod> parseDasha(Map<String, Object> rawData) {
        List<LifePatternDashaPeriod> dashaList = new ArrayList<>();
        Object dashaObj = rawData.get("dasha");
        if (dashaObj instanceof List) {
            for (Object item : (List<?>) dashaObj) {
                if (!(item instanceof Map)) continue;
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
        return dashaList;
    }

    @SuppressWarnings("unchecked")
    private LifePatternNatalChart parseNatalChart(Map<String, Object> raw) {
        LifePatternNatalChart chart = new LifePatternNatalChart();

        Object ascRaw = raw.get("ascendant");
        if (ascRaw instanceof String) {
            chart.setAscendantSign((String) ascRaw);
        } else if (ascRaw instanceof Map) {
            chart.setAscendantSign((String) ((Map<String, Object>) ascRaw).get("sign"));
        }

        Object planetsObj = raw.get("planets");
        Map<String, LifePatternPlanetPosition> planets = new LinkedHashMap<>();
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
    private LifePatternPlanetPosition parsePlanet(Map<String, Object> raw) {
        LifePatternPlanetPosition pos = new LifePatternPlanetPosition();
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

    // ============================================================
    // LLM Renderer（renderer_contract）
    // ============================================================

    private String renderWithLLM(Map<String, EducationClaim> claims, List<EducationEvidenceObject> evidenceList,
                                  List<Map<String, Object>> windows, List<String> degradationFlags) {
        try {
            Map<String, Object> payload = buildLLMPayload(claims, evidenceList, windows, degradationFlags);
            String payloadJson = objectMapper.writeValueAsString(payload);

            String systemPrompt = "你是问星渡《学业深度解析报告》的AI排版引擎，renderer_version="
                    + EducationReportConstants.RENDERER_VERSION + "。" +
                    "你的唯一职责是将已裁决的结构化报告数据排版为通顺的中文报告，正文目标"
                    + EducationReportConstants.BODY_TARGET_MIN + "-" + EducationReportConstants.BODY_TARGET_MAX + "个中文字符。" +
                    "禁止：重新算盘、改判Claim、补写缺失结论、新增证据或建议、使用禁断表达" +
                    "（不判断考试通过/录取/学历上限/智力，不使用'一定会考上'等确定性表述）。" +
                    "必须输出JSON：title, subtitle, summary(headline,keywords,strongest_resource,biggest_friction,stage_label,body), " +
                    "sections(9个，含display_title,body,evidence_cards), timing_windows, action_plan, method_note。" +
                    "INSUFFICIENT 的 Claim 所在章节使用 SHORTEN_ON_LOW_EVIDENCE：缩短或省略，不得用常识填充。" +
                    "阻力必须写成'触发→自动反应→短期作用→长期代价'，不得贴人格标签。" +
                    "写作风格：具体、可操作、温和、清醒。";
            String userPrompt = "请将以下结构化数据排版为学业报告（开篇摘要+8章）：\n\n" + payloadJson;

            String llmResponse = aiService.generateLifePatternReport(systemPrompt, userPrompt);
            Map<String, Object> parsed = parseRenderedContent(sanitizeJson(llmResponse));
            if (isValidReportContent(parsed)) {
                return sanitizeJson(llmResponse);
            }
            log.warn("LLM 输出结构不完整（第一次），使用相同 Payload 重试一次");
            String retry = aiService.generateLifePatternReport(systemPrompt, userPrompt);
            Map<String, Object> retryParsed = parseRenderedContent(sanitizeJson(retry));
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

    private Map<String, Object> buildLLMPayload(Map<String, EducationClaim> claims,
                                                 List<EducationEvidenceObject> evidenceList,
                                                 List<Map<String, Object>> windows,
                                                 List<String> degradationFlags) {
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("report_metadata", Map.of(
                "report_type", EducationReportConstants.REPORT_TYPE,
                "rule_version", EducationReportConstants.RULE_VERSION,
                "renderer_version", EducationReportConstants.RENDERER_VERSION,
                "timing_horizon_months", EducationReportConstants.TIMING_HORIZON_MONTHS));
        payload.put("data_quality", Map.of("degradation_flags", degradationFlags));

        List<Map<String, Object>> claimList = new ArrayList<>();
        for (EducationClaim c : claims.values()) {
            Map<String, Object> cm = new LinkedHashMap<>();
            cm.put("claim_id", c.getClaimId());
            cm.put("state", c.getState());
            cm.put("confidence", c.getConfidence());
            cm.put("primary_candidate", c.getPrimaryCandidate());
            cm.put("qualifier_candidates", c.getQualifierCandidates());
            cm.put("d24_action", c.getD24Action());
            if (c.getInsufficiencyReason() != null) {
                cm.put("insufficiency_reason", c.getInsufficiencyReason());
            }
            claimList.add(cm);
        }
        payload.put("resolved_claims", claimList);
        payload.put("timing_windows", windows);

        List<Map<String, String>> cards = new ArrayList<>();
        for (EducationEvidenceObject ev : evidenceList) {
            if (!"ELIGIBLE".equals(ev.getEvidenceStatus())) continue;
            if (!"VOTE_BEARING".equals(ev.getNatalVoteEligibility())) continue;
            Map<String, String> card = new LinkedHashMap<>();
            card.put("plain_summary", ev.getSubject() + "：" + ev.getRawFact());
            card.put("family", ev.getFamily());
            cards.add(card);
            if (cards.size() >= 12) break;
        }
        payload.put("evidence_cards", cards);
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
        if (!(sections instanceof List) || ((List<?>) sections).size() < 8) return false;
        for (Object s : (List<?>) sections) {
            if (s instanceof Map) {
                Object body = ((Map<?, ?>) s).get("body");
                if (body instanceof String && ((String) body).trim().length() > 80) {
                    return true;
                }
            }
        }
        return false;
    }

    // ============================================================
    // 确定性内容生成（摘要 + 8 章）
    // ============================================================

    private String generateDefaultContent(Map<String, EducationClaim> claims,
                                           List<EducationEvidenceObject> evidenceList,
                                           List<Map<String, Object>> windows,
                                           List<String> degradationFlags) {
        try {
            return objectMapper.writeValueAsString(
                    buildStructuredReportContent(claims, evidenceList, windows, degradationFlags));
        } catch (Exception e) {
            log.error("生成默认内容失败: {}", e.getMessage(), e);
            return "{\"title\":\"学业深度解析报告\",\"sections\":[]}";
        }
    }

    private Map<String, Object> buildStructuredReportContent(Map<String, EducationClaim> claims,
                                                              List<EducationEvidenceObject> evidenceList,
                                                              List<Map<String, Object>> windows,
                                                              List<String> degradationFlags) {
        Map<String, Object> content = new LinkedHashMap<>();
        content.put("title", EducationReportConstants.DISPLAY_NAME);
        content.put("subtitle", "理解你如何学习、优势与阻力在哪、适合怎样的方法与方向");

        // ===== 开篇摘要 =====
        Map<String, Object> summary = new LinkedHashMap<>();
        EducationClaim c01 = claims.get("C01");
        EducationClaim c02 = claims.get("C02");
        EducationClaim c08 = claims.get("C08");
        EducationClaim c09 = claims.get("C09");
        EducationClaim c13 = claims.get("C13");

        summary.put("headline", claimRenderable(c01) ? "你的学习机制结构已经清晰" : "你的学习结构总览");
        List<String> keywords = new ArrayList<>();
        if (claimRenderable(c02)) keywords.add(candidateCN("C02", c02.getPrimaryCandidate()));
        if (claimRenderable(c08)) keywords.add(candidateCN("C08", c08.getPrimaryCandidate()));
        if (claimRenderable(c09)) keywords.add(candidateCN("C09", c09.getPrimaryCandidate()));
        if (keywords.isEmpty()) keywords.add("学习方式");
        summary.put("keywords", keywords);
        summary.put("strongest_resource", claimRenderable(c08) ? candidateCN("C08", c08.getPrimaryCandidate()) : null);
        summary.put("biggest_friction", claimRenderable(c09) ? candidateCN("C09", c09.getPrimaryCandidate()) : null);
        summary.put("stage_label", c13 != null && c13.getPrimaryCandidate() != null
                ? candidateCN("C13", c13.getPrimaryCandidate()) : "当前阶段待明确");

        StringBuilder sb = new StringBuilder();
        sb.append("这份报告基于你的本命盘（D1）与学业分盘（D24），回答：你如何学习、优势在哪里、")
          .append("何处容易卡住、适合怎样的方法与环境、哪些方向值得深入、何时适合推进。\n\n");
        if (claimRenderable(c02)) {
            sb.append("你吸收知识的核心方式是「").append(candidateCN("C02", c02.getPrimaryCandidate())).append("」。\n");
        }
        if (claimRenderable(c08)) {
            sb.append("你最容易建立优势的地方在「").append(candidateCN("C08", c08.getPrimaryCandidate())).append("」。\n");
        }
        if (claimRenderable(c09)) {
            sb.append("你最需要留意的阻力循环是「").append(candidateCN("C09", c09.getPrimaryCandidate())).append("」。\n");
        }
        summary.put("body", sb.toString());
        content.put("summary", summary);

        // ===== 第 1-8 章 =====
        List<Map<String, Object>> sections = new ArrayList<>();
        sections.add(buildSection("LEARNING_MECHANISM", "你是怎样把知识真正学进去的", claims.get("C02"),
                evidenceList, "本章覆盖认知加工、吸收顺序、记忆专注与知识表达。",
                claimText(claims.get("C02"), "C02", "你吸收与理解信息的核心方式")));
        sections.add(buildSection("STRENGTHS", "你最容易建立优势的地方", claims.get("C08"),
                evidenceList, "每项优势都有其成立条件；优势需要现实练习才能稳定调用。",
                strengthsText(claims.get("C08"))));
        sections.add(buildSection("FRICTION_LOOPS", "为什么有时很能学，有时却容易卡住", claims.get("C09"),
                evidenceList, "阻力不是人格缺陷，而是可观察的循环模式。",
                frictionText(claims.get("C09"), claims.get("C05"), claims.get("C06"))));
        sections.add(buildSection("METHOD_ENVIRONMENT", "什么样的方法和环境更适合你", claims.get("C07"),
                evidenceList, "方法建议来自你的认知与动机结构，不是通用清单。",
                methodText(claims.get("C07"))));
        sections.add(buildSection("SUBJECT_DIRECTIONS", "你更适合深入哪些知识方向", claims.get("C10"),
                evidenceList, "方向簇区分兴趣、能力与持续完成度，不构成专业录取建议。",
                directionsText(claims.get("C10"))));
        sections.add(buildSection("LEARNING_ROUTES", "考试、研究、项目还是证书：哪条路更适合你", claims.get("C11"),
                evidenceList, "只讨论路线适配与深造条件，不承诺结果。",
                routesText(claims.get("C11"), claims.get("C12"))));
        sections.add(buildTimingSection(claims.get("C13"), windows));
        sections.add(buildActionPlanSection(claims));
        content.put("sections", sections);
        content.put("timing_windows", windows);

        content.put("method_note", EducationReportConstants.NEUTRAL_DISCLAIMER
                + " 规则版本：" + EducationReportConstants.RULE_VERSION
                + (degradationFlags.isEmpty() ? "。" : "，降级模块：" + String.join("、", degradationFlags) + "。"));
        return content;
    }

    // ============================================================
    // 章节构建
    // ============================================================

    private Map<String, Object> buildSection(String sectionKey, String title, EducationClaim claim,
                                              List<EducationEvidenceObject> evidenceList,
                                              String intro, String bodyText) {
        Map<String, Object> section = new LinkedHashMap<>();
        section.put("section_key", sectionKey);
        section.put("display_title", title);

        boolean full = claimRenderable(claim);
        section.put("section_mode", full ? EducationReportConstants.SECTION_MODE_FULL
                : EducationReportConstants.SECTION_MODE_SHORTEN_ON_LOW_EVIDENCE);

        StringBuilder sb = new StringBuilder();
        sb.append(intro).append("\n\n");
        if (full) {
            sb.append(bodyText).append("\n");
            if ("MIXED".equals(claim.getState())) {
                sb.append("\n注意：本章结论存在条件限定或双面信号，在不同情境下表现不同。\n");
            }
            if (claim.getD24Action() != null) {
                switch (claim.getD24Action()) {
                    case "QUALIFIES":
                        sb.append("D24 学业分盘为此结论附加了现实条件，实际学业情境中会体现为节奏或环境的额外要求。\n");
                        break;
                    case "CONTRADICTS":
                        sb.append("D24 学业分盘与本命结构存在张力：本命机制保持不变，但学业场景中可能以受限的方式表现。\n");
                        break;
                    case "CONFIRMS":
                        sb.append("D24 学业分盘与 D1 同向确认了这一结论。\n");
                        break;
                    default:
                        break;
                }
            }
        } else {
            sb.append("本章相关证据尚不充分，按低证据规则缩短处理，不为凑篇幅而展开。")
              .append("结构不明显时，观察比结论更有用。\n");
        }
        section.put("body", sb.toString());

        List<Map<String, String>> cards = new ArrayList<>();
        if (full && claim.getSupportRootIds() != null) {
            for (String evId : claim.getSupportRootIds()) {
                if (cards.size() >= 4) break;
                for (EducationEvidenceObject ev : evidenceList) {
                    if (evId.equals(ev.getEvidenceId()) && "ELIGIBLE".equals(ev.getEvidenceStatus())) {
                        Map<String, String> card = new LinkedHashMap<>();
                        card.put("plain_language", "星盘中「" + ev.getSubject() + "」的结构支持本章解读");
                        card.put("technical_summary", ev.getRawFact() + " [" + ev.getFamily() + "]");
                        cards.add(card);
                        break;
                    }
                }
            }
        }
        section.put("evidence_cards", cards);
        return section;
    }

    private Map<String, Object> buildTimingSection(EducationClaim c13, List<Map<String, Object>> windows) {
        Map<String, Object> section = new LinkedHashMap<>();
        section.put("section_key", EducationReportConstants.SECTION_CH7_TIMING_WINDOWS);
        section.put("display_title", "当前及未来36个月的学习阶段");

        StringBuilder sb = new StringBuilder();
        if (windows != null && !windows.isEmpty()) {
            section.put("section_mode", EducationReportConstants.SECTION_MODE_FULL);
            sb.append("以下窗口只描述主题活跃度与推进节奏，不预测考试通过或录取结果。\n\n");
            int i = 1;
            for (Map<String, Object> w : windows) {
                sb.append(i++).append(". **").append(w.get("start_date")).append(" ~ ").append(w.get("end_date"))
                  .append("**（").append(String.valueOf(w.get("window_type"))).append("，")
                  .append(w.get("mahadasha")).append("/").append(w.get("antardasha")).append("）\n")
                  .append(w.get("description")).append("\n")
                  .append("风险提示：").append(w.get("risk_hint")).append("\n\n");
            }
        } else {
            section.put("section_mode", EducationReportConstants.SECTION_MODE_SHORTEN_ON_LOW_EVIDENCE);
            sb.append("时序数据不足，无法给出可靠的阶段窗口。结构性章节中的观察仍然适用。\n");
        }
        section.put("body", sb.toString());
        section.put("evidence_cards", new ArrayList<>());
        return section;
    }

    private Map<String, Object> buildActionPlanSection(Map<String, EducationClaim> claims) {
        Map<String, Object> section = new LinkedHashMap<>();
        section.put("section_key", EducationReportConstants.SECTION_CH8_ACTION_PLAN);
        section.put("display_title", "给你的学习行动方案");

        List<Map<String, String>> recommendations = buildRecommendations(claims);
        section.put("section_mode", recommendations.size() >= EducationReportConstants.MIN_RECOMMENDATIONS
                ? EducationReportConstants.SECTION_MODE_FULL
                : EducationReportConstants.SECTION_MODE_SHORTEN_ON_LOW_EVIDENCE);

        StringBuilder sb = new StringBuilder();
        sb.append("以下建议均来自已裁决的 Claim 结构。\n\n");
        int i = 1;
        for (Map<String, String> rec : recommendations) {
            sb.append(i++).append(". **").append(rec.get("action")).append("**\n")
              .append("   - 适用场景：").append(rec.get("context")).append("\n")
              .append("   - 识别信号：").append(rec.get("signal")).append("\n")
              .append("   - 节奏：").append(rec.get("cadence")).append("\n")
              .append("   - 检查点：").append(rec.get("checkpoint")).append("\n")
              .append("   - 来源：").append(rec.get("source_claims")).append("\n");
        }
        section.put("body", sb.toString());
        section.put("evidence_cards", new ArrayList<>());
        section.put("recommendations", recommendations);
        return section;
    }

    private List<Map<String, String>> buildRecommendations(Map<String, EducationClaim> claims) {
        List<Map<String, String>> recs = new ArrayList<>();

        EducationClaim c02 = claims.get("C02");
        EducationClaim c06 = claims.get("C06");
        EducationClaim c07 = claims.get("C07");
        EducationClaim c08 = claims.get("C08");
        EducationClaim c09 = claims.get("C09");
        EducationClaim c10 = claims.get("C10");

        if (claimRenderable(c07)) {
            recs.add(rec("按适配方法安排学习", "C07",
                    "把「" + candidateCN("C07", c07.getPrimaryCandidate()) + "」作为默认学习环境的设置依据。",
                    "开始新学习任务或更换学习环境时",
                    "发现旧方法连续低效超过一周",
                    "每周执行至少 4 次并记录效果",
                    "连续两周后对比学习完成度"));
        }
        if (claimRenderable(c08)) {
            recs.add(rec("优先在优势区建立成果", "C08",
                    "先在「" + candidateCN("C08", c08.getPrimaryCandidate()) + "」上取得可观察的小成果，再迁移到弱项。",
                    "规划学习优先级时",
                    "同时推进多个方向导致精力分散",
                    "每周固定 2-3 次专项练习",
                    "一个月内产出 1 个可展示的学习成果"));
        }
        if (claimRenderable(c09)) {
            recs.add(rec("为阻力循环设计暂停点", "C09",
                    "在「" + candidateCN("C09", c09.getPrimaryCandidate()) + "」触发前设置具体的应对动作（如25分钟计时、任务拆半、写下一小步）。",
                    "面对大任务、高压力或无聊例行内容时",
                    "出现熟悉的拖延、跳转或卡住信号",
                    "每次触发即执行，每日至少复盘一次",
                    "一周内记录 3 次成功中断循环的经历"));
        }
        if (claimRenderable(c06)) {
            recs.add(rec("让节奏匹配你的节律类型", "C06",
                    "按「" + candidateCN("C06", c06.getPrimaryCandidate()) + "」安排学期/项目节奏，而不是套用别人的时间表。",
                    "制定学期计划或备考计划时",
                    "计划反复失效或总在赶工",
                    "每两周校准一次计划",
                    "计划完成率提升且赶工次数减少"));
        }
        if (claimRenderable(c10)) {
            recs.add(rec("围绕方向簇做探索", "C10",
                    "围绕「" + candidateCN("C10", c10.getPrimaryCandidate()) + "」安排低成本的探索（公开课、短项目、访谈）。",
                    "选择课程、专业方向或自学主题时",
                    "对方向感到模糊或犹豫",
                    "每月完成 1 次方向探索并写小结",
                    "对主要方向形成自己的判断依据"));
        }
        if (recs.size() < EducationReportConstants.MIN_RECOMMENDATIONS) {
            recs.add(rec("建立学习记录习惯", "GENERAL",
                    "记录每次学习的时间、内容、状态与卡点，形成自己的观察数据。",
                    "日常学习",
                    "感觉效率低但说不清原因",
                    "每次学习后花 2 分钟记录",
                    "两周后能识别出自己的效率模式"));
        }
        if (recs.size() > EducationReportConstants.MAX_RECOMMENDATIONS) {
            recs = new ArrayList<>(recs.subList(0, EducationReportConstants.MAX_RECOMMENDATIONS));
        }
        return recs;
    }

    private Map<String, String> rec(String action, String sourceClaims, String rationale, String context,
                                     String signal, String cadence, String checkpoint) {
        Map<String, String> m = new LinkedHashMap<>();
        m.put("recommendation_id", "REC_" + UUID.randomUUID().toString().substring(0, 8).toUpperCase());
        m.put("action", action);
        m.put("source_claims", sourceClaims);
        m.put("rationale", rationale);
        m.put("context", context);
        m.put("signal", signal);
        m.put("cadence", cadence);
        m.put("checkpoint", checkpoint);
        m.put("priority", "NORMAL");
        return m;
    }

    // ============================================================
    // 文案
    // ============================================================

    private boolean claimRenderable(EducationClaim c) {
        return c != null && (EducationReportConstants.STATE_SUPPORTED.equals(c.getState())
                || EducationReportConstants.STATE_MIXED.equals(c.getState()));
    }

    private String claimText(EducationClaim claim, String claimId, String topic) {
        if (!claimRenderable(claim)) {
            return topic + "相关的星盘证据尚不充分，按低证据规则缩短处理。";
        }
        StringBuilder sb = new StringBuilder();
        sb.append(topic).append("的主要方向是「").append(candidateCN(claimId, claim.getPrimaryCandidate())).append("」。\n\n");
        switch (claim.getState()) {
            case "SUPPORTED":
                sb.append("这一方向在 D1 中满足最小充分条件");
                sb.append("CONFIRMS".equals(claim.getD24Action()) ? "，并得到 D24 学业分盘的同向确认。" : "。");
                break;
            case "MIXED":
                sb.append("这一方向存在双面信号或条件限定，在不同情境下表现不同。");
                break;
            default:
                break;
        }
        if (!claim.getQualifierCandidates().isEmpty()) {
            sb.append("同时存在次要倾向：");
            for (int i = 0; i < claim.getQualifierCandidates().size(); i++) {
                if (i > 0) sb.append("、");
                sb.append(candidateCN(claimId, claim.getQualifierCandidates().get(i)));
            }
            sb.append("。\n");
        }
        return sb.toString();
    }

    private String strengthsText(EducationClaim c08) {
        if (!claimRenderable(c08)) {
            return "优势资源的星盘证据尚不充分，按低证据规则缩短处理。";
        }
        StringBuilder sb = new StringBuilder();
        sb.append("你最核心的优势资源是「").append(candidateCN("C08", c08.getPrimaryCandidate())).append("」。\n\n");
        for (String q : c08.getQualifierCandidates()) {
            sb.append("- **").append(candidateCN("C08", q)).append("**：同样有结构支持，适合作为第二发展点。\n");
        }
        sb.append("\n优势的成立条件：需要持续的现实练习与反馈才能稳定调用；Shadbala 显示的可调用程度只影响稳定性，不改变方向本身。");
        return sb.toString();
    }

    private String frictionText(EducationClaim c09, EducationClaim c05, EducationClaim c06) {
        if (!claimRenderable(c09)) {
            return "阻力循环的星盘证据尚不充分，按低证据规则缩短处理。";
        }
        StringBuilder sb = new StringBuilder();
        String friction = candidateCN("C09", c09.getPrimaryCandidate());
        sb.append("你最需要留意的阻力循环是「").append(friction).append("」。\n\n");
        sb.append("**触发 → 自动反应 → 短期作用 → 长期代价**\n\n");
        switch (c09.getPrimaryCandidate() != null ? c09.getPrimaryCandidate() : "") {
            case "OVERLOAD_DISTRACTION":
                sb.append("当任务量大或内容无聊时（触发），你会切换到更轻松的刺激（自动反应），")
                  .append("这带来即时的舒适与减压（短期作用），但导致进度碎片化、深任务难以推进（长期代价）。\n");
                break;
            case "PERFECTIONISM_DELAY":
                sb.append("当标准高、怕出错时（触发），你会反复打磨开头而不推进（自动反应），")
                  .append("这维持了\"在做\"的感觉（短期作用），但完成度低、期限压力累积（长期代价）。\n");
                break;
            case "INTEREST_DEPENDENCE":
                sb.append("当内容失去新鲜感时（触发），你的投入迅速下降（自动反应），")
                  .append("这让你避免枯燥（短期作用），但系统性知识难以积累完整（长期代价）。\n");
                break;
            case "INCONSISTENT_ROUTINE":
                sb.append("当情绪或环境波动时（触发），学习节奏被打断（自动反应），")
                  .append("这顺从了当下状态（短期作用），但稳定习惯难以建立（长期代价）。\n");
                break;
            case "PRESSURE_BLOCK":
                sb.append("当期限临近或负荷过高时（触发），你会陷入停摆（自动反应），")
                  .append("这暂时回避了压力（短期作用），但积压加剧下一轮卡顿（长期代价）。\n");
                break;
            case "OVEREXPANSION":
                sb.append("当新机会出现时（触发），你倾向全部纳入（自动反应），")
                  .append("这带来掌控感与可能性（短期作用），但摊薄投入、多条线都不深入（长期代价）。\n");
                break;
            case "PREMATURE_SWITCHING":
                sb.append("当遇到平台期或转折时（触发），你倾向换方向重来（自动反应），")
                  .append("这恢复了新鲜感（短期作用），但先前的积累不断清零（长期代价）。\n");
                break;
            case "MISSING_FEEDBACK":
                sb.append("当独自学习缺少对照时（触发），问题被延迟发现（自动反应），")
                  .append("这避免了即时的暴露感（短期作用），但偏差持续累积难以纠正（长期代价）。\n");
                break;
            case "THEORY_PRACTICE_GAP":
                sb.append("当理论脱离应用场景时（触发），理解停留在抽象层（自动反应），")
                  .append("这看起来已完成学习（短期作用），但实际运用时卡壳（长期代价）。\n");
                break;
            default:
                sb.append("这一循环的具体形态需要结合实际情境观察。\n");
        }
        sb.append("\n这是可观察的模式，不是人格标签；每个环节都可以被干预。");
        return sb.toString();
    }

    private String methodText(EducationClaim c07) {
        if (!claimRenderable(c07)) {
            return "方法环境的星盘证据尚不充分，按低证据规则缩短处理。";
        }
        String method = candidateCN("C07", c07.getPrimaryCandidate());
        StringBuilder sb = new StringBuilder();
        sb.append("更适合你的方法环境是「").append(method).append("」。\n\n");
        switch (c07.getPrimaryCandidate() != null ? c07.getPrimaryCandidate() : "") {
            case "QUIET_INDEPENDENT":
                sb.append("你需要安静、少打扰的独学环境，按自己的节奏消化内容；讨论对你是辅助而非主通道。");
                break;
            case "GUIDED_MENTOR":
                sb.append("有导师或榜样带领时你的学习效率明显更高；及时的纠偏与解释比自学材料更有效。");
                break;
            case "COLLABORATIVE_DIALOGUE":
                sb.append("讨论、互相讲解与协作项目是你理解知识的主要通道；独处过久反而低效。");
                break;
            case "STRUCTURED_CURRICULUM":
                sb.append("清晰的体系、进度表与阶段性目标让你安定；碎片化自由探索会让你失去方向感。");
                break;
            case "PROJECT_BASED":
                sb.append("以真实任务或项目为载体时你学得最快；纯理论输入需要尽快接到应用出口。");
                break;
            case "FLEXIBLE_MODULAR":
                sb.append("模块化、可自由组合的学习路径最适合你；刚性课表会压抑你的适应优势。");
                break;
            case "IMMERSIVE_INTENSIVE":
                sb.append("深度沉浸的专题式学习比浅尝辄止更有效；你适合长时间聚焦单一主题。");
                break;
            default:
                sb.append("结合你的认知与动机结构安排方法环境。");
        }
        sb.append("\n\n反馈与复盘方式：建议按周设置轻量复盘节点，用\"做了什么-卡在哪-下一步\"三问收尾。");
        return sb.toString();
    }

    private String directionsText(EducationClaim c10) {
        if (!claimRenderable(c10)) {
            return "知识方向的星盘证据尚不充分，按低证据规则缩短处理。";
        }
        StringBuilder sb = new StringBuilder();
        sb.append("结合 D1 与 D24，以下知识方向簇与你的结构更匹配：\n\n");
        sb.append("1. **").append(candidateCN("C10", c10.getPrimaryCandidate())).append("**（主方向）\n");
        for (int i = 0; i < c10.getQualifierCandidates().size(); i++) {
            sb.append(i + 2).append(". **").append(candidateCN("C10", c10.getQualifierCandidates().get(i))).append("**\n");
        }
        sb.append("\n每项方向建议按\"为何适合 → 如何切入 → 常见挑战\"三步自行验证：")
          .append("先用低成本方式（公开课、短项目）试水，再决定是否深入。")
          .append("此处区分的是结构适配度，不构成专业录取或职业收入建议。");
        return sb.toString();
    }

    private String routesText(EducationClaim c11, EducationClaim c12) {
        if (!claimRenderable(c11)) {
            return "学习路线的星盘证据尚不充分，按低证据规则缩短处理。";
        }
        StringBuilder sb = new StringBuilder();
        sb.append("**主路线：").append(candidateCN("C11", c11.getPrimaryCandidate())).append("**\n\n");
        if (claimRenderable(c12)) {
            sb.append("对应的深造方式是「").append(candidateCN("C12", c12.getPrimaryCandidate())).append("」。\n");
            sb.append("成立条件：需要稳定的完成能力（C06）与方向确认（C10/C11）同时在线。\n");
            sb.append("反条件：当阻力循环（C09）持续活跃时，宜先修补节奏再推进深造。\n\n");
        }
        if (!c11.getQualifierCandidates().isEmpty()) {
            sb.append("**次路线：**\n");
            for (String q : c11.getQualifierCandidates()) {
                sb.append("- ").append(candidateCN("C11", q)).append("\n");
            }
            sb.append("\n");
        }
        sb.append("深造条件讨论的是路径适配，不推断你能达到的最高学历，也不承诺任何录取结果。");
        return sb.toString();
    }

    private String candidateCN(String claimId, String candidate) {
        if (candidate == null) return "待明确";
        switch (claimId) {
            case "C02":
                switch (candidate) {
                    case "STRUCTURE_FIRST": return "结构优先型";
                    case "EXAMPLE_FIRST": return "示例先行型";
                    case "PATTERN_SYNTHESIS": return "模式综合型";
                    case "DIALOGUE_ITERATIVE": return "对话迭代型";
                    case "EXPERIMENTAL_APPLIED": return "实验应用型";
                    case "DEEP_DIVE_SEQUENTIAL": return "纵深递进型";
                    default: return candidate;
                }
            case "C03":
                switch (candidate) {
                    case "STABLE_REPETITION": return "稳定重复型记忆";
                    case "CONTEXT_ASSOCIATION": return "情境联想型记忆";
                    case "VERBAL_RECALL": return "语言复述型记忆";
                    case "VISUAL_STRUCTURE": return "视觉结构型记忆";
                    case "BURST_FOCUS": return "爆发式专注";
                    case "STATE_SENSITIVE": return "状态敏感型";
                    case "EXTERNAL_RHYTHM_SUPPORT": return "需要外部节奏支持";
                    default: return candidate;
                }
            case "C04":
                switch (candidate) {
                    case "WRITTEN_SYNTHESIS": return "书面综合表达";
                    case "ORAL_EXPLANATION": return "口头讲解表达";
                    case "ANALYTICAL_PROBLEM_SOLVING": return "分析解题表达";
                    case "PRACTICAL_DEMONSTRATION": return "实操演示表达";
                    case "CREATIVE_TRANSLATION": return "创意转化表达";
                    case "TEACHING_OUTPUT": return "教学输出表达";
                    default: return candidate;
                }
            case "C05":
                switch (candidate) {
                    case "CURIOSITY_MASTERY": return "好奇与掌握感驱动";
                    case "PRACTICAL_UTILITY": return "实用价值驱动";
                    case "PURPOSE_MEANING": return "意义与目标驱动";
                    case "ACHIEVEMENT_RECOGNITION": return "成就与认可驱动";
                    case "AUTONOMY_EXPLORATION": return "自主探索驱动";
                    case "RESPONSIBILITY_SECURITY": return "责任与安全感驱动";
                    case "COMMUNITY_COLEARNING": return "同伴共学驱动";
                    default: return candidate;
                }
            case "C06":
                switch (candidate) {
                    case "ROUTINE_INCREMENTAL": return "例行渐进型节律";
                    case "SPRINT_BASED": return "冲刺型节律";
                    case "DEADLINE_ACTIVATED": return "期限激活型节律";
                    case "EXTERNAL_ACCOUNTABILITY": return "外部问责型节律";
                    case "SELF_MILESTONE": return "自我里程碑型节律";
                    case "CYCLICAL_VARIABILITY": return "周期波动型节律";
                    default: return candidate;
                }
            case "C07":
                switch (candidate) {
                    case "QUIET_INDEPENDENT": return "安静独学";
                    case "GUIDED_MENTOR": return "导师带教";
                    case "COLLABORATIVE_DIALOGUE": return "协作对话";
                    case "STRUCTURED_CURRICULUM": return "结构化课程";
                    case "PROJECT_BASED": return "项目制学习";
                    case "FLEXIBLE_MODULAR": return "灵活模块化";
                    case "IMMERSIVE_INTENSIVE": return "沉浸式强化";
                    default: return candidate;
                }
            case "C08":
                switch (candidate) {
                    case "CONCEPTUAL_UNDERSTANDING": return "概念理解力";
                    case "INFORMATION_SYNTHESIS": return "信息综合力";
                    case "ANALYTICAL_REASONING": return "分析推理力";
                    case "LANGUAGE_EXPRESSION": return "语言表达力";
                    case "MEMORY_RETENTION": return "记忆保持力";
                    case "RESEARCH_DEPTH": return "研究深度";
                    case "PRACTICAL_APPLICATION": return "实际应用力";
                    case "CREATIVE_ASSOCIATION": return "创意联想力";
                    case "PERSISTENCE": return "坚持力";
                    case "TEACHING_TRANSLATION": return "教学转化力";
                    default: return candidate;
                }
            case "C09":
                switch (candidate) {
                    case "OVERLOAD_DISTRACTION": return "过载分心循环";
                    case "PERFECTIONISM_DELAY": return "完美主义拖延循环";
                    case "INTEREST_DEPENDENCE": return "兴趣依赖循环";
                    case "INCONSISTENT_ROUTINE": return "节律不稳循环";
                    case "PRESSURE_BLOCK": return "压力卡顿循环";
                    case "OVEREXPANSION": return "过度扩张循环";
                    case "PREMATURE_SWITCHING": return "过早切换循环";
                    case "MISSING_FEEDBACK": return "反馈缺失循环";
                    case "THEORY_PRACTICE_GAP": return "理论实践脱节循环";
                    default: return candidate;
                }
            case "C10":
                switch (candidate) {
                    case "LANGUAGE_COMMUNICATION": return "语言与传播";
                    case "HUMANITIES_CULTURE": return "人文与文化";
                    case "PSYCHOLOGY_EDUCATION_HELPING": return "心理·教育·助人";
                    case "BUSINESS_MANAGEMENT": return "商业与管理";
                    case "FINANCE_COMMERCE_ANALYSIS": return "金融与商业分析";
                    case "LAW_POLICY_PUBLIC_AFFAIRS": return "法律与公共政策";
                    case "MATH_LOGIC": return "数学与逻辑";
                    case "ENGINEERING_TECHNOLOGY": return "工程与技术";
                    case "DATA_RESEARCH": return "数据与研究";
                    case "MEDICINE_LIFE_SCIENCE": return "医学与生命科学";
                    case "ART_DESIGN_MEDIA": return "艺术·设计·媒体";
                    case "SPIRITUAL_PHILOSOPHICAL_ESOTERIC": return "哲学与精神传统";
                    default: return candidate;
                }
            case "C11":
                switch (candidate) {
                    case "STANDARDIZED_EXAM": return "标准化考试路线";
                    case "RESEARCH_ACADEMIC": return "学术研究路线";
                    case "PROJECT_PORTFOLIO": return "项目作品集路线";
                    case "PROFESSIONAL_CERTIFICATION": return "专业认证路线";
                    case "APPRENTICESHIP_PRACTICE": return "实践学徒路线";
                    case "TEACHING_KNOWLEDGE_OUTPUT": return "教学输出路线";
                    case "INTERDISCIPLINARY_EXPLORATION": return "跨学科探索路线";
                    default: return candidate;
                }
            case "C12":
                switch (candidate) {
                    case "FORMAL_DEGREE": return "正式学历深造";
                    case "PROFESSIONAL_CREDENTIAL": return "职业资格深造";
                    case "RESEARCH_SPECIALIZATION": return "研究专精深造";
                    case "APPLIED_MASTERY": return "应用精进深造";
                    case "MODULAR_LIFELONG_LEARNING": return "模块化终身学习";
                    case "STAGED_FOUNDATION_BEFORE_ADVANCEMENT": return "先基础后进阶的分段深造";
                    default: return candidate;
                }
            case "C13":
                switch (candidate) {
                    case "STAGED_ACTIVATION": return "分阶段激活期";
                    case "STEADY_MAINTENANCE": return "平稳维持期";
                    default: return candidate;
                }
            default:
                return candidate;
        }
    }

    private String generateCoverSummary(Map<String, EducationClaim> claims) {
        EducationClaim c02 = claims.get("C02");
        if (claimRenderable(c02)) {
            return "学习机制：" + candidateCN("C02", c02.getPrimaryCandidate());
        }
        return "学业结构与学习方法的结构性观察";
    }
}
