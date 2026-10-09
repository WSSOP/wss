package cn.dct.zpback.controller;

import cn.dct.zpback.dto.ApiResponse;
import cn.dct.zpback.engine.relationship.ChartDataParser;
import cn.dct.zpback.entity.Chart;
import cn.dct.zpback.entity.relationship.NatalRelationshipReportInstance;
import cn.dct.zpback.service.ChartService;
import cn.dct.zpback.service.relationship.NatalRelationshipReportService;
import cn.dct.zpback.service.VedicAstroService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.*;

import java.util.*;

/**
 * 单人星盘 Skill 控制器
 * 符合 SEALED Step2D-1 Runtime Request/Output Schema
 * 路径前缀：/api/v1/natal-skill
 */
@RestController
@RequestMapping("/v1/natal-skill")
@RequiredArgsConstructor
@Slf4j
@SuppressWarnings("unchecked")
public class NatalSkillController {

    private final NatalRelationshipReportService relationshipReportService;
    private final ChartService chartService;
    private final VedicAstroService vedicAstroService;

    /**
     * POST /v1/natal-skill/analyze
     * 分析用户问题并返回符合 SEALED Schema 的响应
     *
     * 请求格式（SEALED Step2D-1 Runtime Request Schema）:
     * {
     *   "request_id": "req_xxx",
     *   "skill": "natal",
     *   "user_query": "我的事业发展如何？",
     *   "locale": "zh-CN",
     *   "subject": {
     *     "required_chart_subject": "selected_self",
     *     "chart_id": "chart_xxx",
     *     "chart_availability": "available"
     *   },
     *   "routing": {
     *     "question_structure": "single",
     *     "topic": "career",
     *     "intent_key": "career_strengths",
     *     "analysis_scope": "natal_pattern",
     *     "time_range_status": "not_applicable",
     *     "time_range": null
     *   },
     *   "execution_control": {
     *     "precheck_status": "ready",
     *     "charge_credit": true
     *   },
     *   "context_flags": {
     *     "is_followup": false
     *   }
     * }
     */
    @PostMapping("/analyze")
    public ApiResponse<Map<String, Object>> analyze(
            @RequestHeader(value = "Authorization", required = false) String authHeader,
            @RequestBody Map<String, Object> request) {

        String userId = extractUserId(authHeader);
        String requestId = (String) request.getOrDefault("request_id", UUID.randomUUID().toString());
        String userQuery = (String) request.getOrDefault("user_query", "");
        String locale = (String) request.getOrDefault("locale", "zh-CN");

        log.info("Natal Skill 分析请求: requestId={}, userId={}, query={}", requestId, userId, userQuery);

        Map<String, Object> subject = (Map<String, Object>) request.getOrDefault("subject", Collections.emptyMap());
        Map<String, Object> routing = (Map<String, Object>) request.getOrDefault("routing", Collections.emptyMap());
        Map<String, Object> executionControl = (Map<String, Object>) request.getOrDefault("execution_control", Collections.emptyMap());

        String chartId = (String) subject.getOrDefault("chart_id", null);
        String chartAvailability = (String) subject.getOrDefault("chart_availability", "unavailable");
        String intentKey = (String) routing.getOrDefault("intent_key", "career_strengths");
        String topic = (String) routing.getOrDefault("topic", "career");
        String analysisScope = (String) routing.getOrDefault("analysis_scope", "natal_pattern");

        // 构建 SEALED 输出 Schema
        Map<String, Object> response = new HashMap<>();
        response.put("request_id", requestId);
        response.put("analysis_started", true);
        response.put("charge_credit", false); // 默认不扣费，实际应根据 entitlements 判断

        Map<String, Object> route = new HashMap<>();
        route.put("topic", topic);
        route.put("intent_key", intentKey);
        route.put("analysis_scope", analysisScope);
        response.put("route", route);

        try {
            // 检查星盘可用性
            if (!"available".equals(chartAvailability) || chartId == null) {
                response.put("status", "data_unavailable");
                response.put("answer", createClarificationAnswer("请先保存星盘信息"));
                response.put("evidence_cards", Collections.emptyList());
                response.put("limitations", Collections.singletonList("星盘数据不可用"));
                response.put("runtime_meta", createRuntimeMeta("data_unavailable"));
                return ApiResponse.success(response);
            }

            // 获取星盘数据
            Chart chart = chartService.getChartById(chartId);
            if (chart == null) {
                response.put("status", "data_unavailable");
                response.put("answer", createClarificationAnswer("星盘不存在"));
                response.put("evidence_cards", Collections.emptyList());
                response.put("limitations", Collections.singletonList("星盘不存在"));
                response.put("runtime_meta", createRuntimeMeta("data_unavailable"));
                return ApiResponse.success(response);
            }

            // 调用 VedicAstro 获取真实星盘数据
            Map<String, Object> vedicParams = buildVedicParams(chart);
            log.info("调用 VedicAstro 获取真实星盘数据: chartId={}, params={}", chartId, vedicParams);

            Map<String, Object> astroData = vedicAstroService.getJyotishyaData(vedicParams);
            log.info("VedicAstro 返回数据: {}", astroData != null ? "success" : "null");

            if (astroData == null || astroData.isEmpty()) {
                response.put("status", "data_unavailable");
                response.put("answer", createClarificationAnswer("星盘计算失败"));
                response.put("evidence_cards", Collections.emptyList());
                response.put("limitations", Collections.singletonList("星盘计算服务异常"));
                response.put("runtime_meta", createRuntimeMeta("data_unavailable"));
                return ApiResponse.success(response);
            }

            // 生成分析报告
            Map<String, Object> analysisResult = generateAnalysis(astroData, intentKey, topic, chart);

            response.put("status", "formal_answer");
            response.put("charge_credit", true);
            response.put("answer", analysisResult.get("answer"));
            response.put("evidence_cards", analysisResult.get("evidence_cards"));
            response.put("limitations", analysisResult.get("limitations"));
            response.put("runtime_meta", createRuntimeMeta("formal_answer"));

            log.info("Natal Skill 分析完成: requestId={}, status=formal_answer", requestId);

        } catch (Exception e) {
            log.error("Natal Skill 分析失败: {}", e.getMessage(), e);
            response.put("status", "data_unavailable");
            response.put("answer", createClarificationAnswer("分析服务异常"));
            response.put("evidence_cards", Collections.emptyList());
            response.put("limitations", Collections.singletonList("服务异常: " + e.getMessage()));
            response.put("runtime_meta", createRuntimeMeta("error"));
        }

        return ApiResponse.success(response);
    }

    /**
     * 构建 VedicAstro 请求参数
     */
    private Map<String, Object> buildVedicParams(Chart chart) {
        Map<String, Object> params = new HashMap<>();
        params.put("name", chart.getName());
        params.put("gender", chart.getGender());

        // 解析出生日期
        if (chart.getBirthDate() != null) {
            String[] dateParts = chart.getBirthDate().split("-");
            if (dateParts.length == 3) {
                params.put("year", Integer.parseInt(dateParts[0]));
                params.put("month", Integer.parseInt(dateParts[1]));
                params.put("day", Integer.parseInt(dateParts[2]));
            }
        }

        // 解析出生时间
        if (chart.getBirthTime() != null && !chart.getBirthTime().isEmpty()) {
            String[] timeParts = chart.getBirthTime().split(":");
            if (timeParts.length >= 2) {
                params.put("hour", Integer.parseInt(timeParts[0]));
                params.put("minute", Integer.parseInt(timeParts[1]));
                params.put("second", timeParts.length > 2 ? Integer.parseInt(timeParts[2]) : 0);
            }
        } else {
            params.put("hour", 12);
            params.put("minute", 0);
            params.put("second", 0);
        }

        params.put("place", chart.getBirthPlace() != null ? chart.getBirthPlace() : "Unknown");
        // 出生地坐标必填：缺失时抛错，绝不套用默认坐标导致上升与宫位错位
        params.put("latitude", ChartDataParser.requireCoordinate(chart.getLatitude(), "纬度", -90, 90));
        params.put("longitude", ChartDataParser.requireCoordinate(chart.getLongitude(), "经度", -180, 180));
        params.put("timezone", chart.getTimezone() != null ? chart.getTimezone() : "+8.0");
        params.put("ayanamsa", "Lahiri");
        params.put("houseSystem", "Whole Sign");
        params.put("charts", null); // 返回全部分盘

        return params;
    }

    /**
     * 生成分析结果
     */
    private Map<String, Object> generateAnalysis(Map<String, Object> astroData, String intentKey, String topic, Chart chart) {
        Map<String, Object> result = new HashMap<>();

        // 提取关键占星数据
        Map<String, Object> vargas = (Map<String, Object>) astroData.getOrDefault("vargas", Collections.emptyMap());
        Map<String, Object> balas = (Map<String, Object>) astroData.getOrDefault("Balas", Collections.emptyMap());
        Map<String, Object> userDetails = (Map<String, Object>) astroData.getOrDefault("user_details", Collections.emptyMap());

        // 构建 answer
        Map<String, Object> answer = new HashMap<>();
        answer.put("summary", generateSummary(intentKey, topic, chart, userDetails));

        List<Map<String, Object>> blocks = new ArrayList<>();
        blocks.add(createBlock("summary", "核心解读", generateCoreInterpretation(intentKey, topic, vargas, userDetails)));
        blocks.add(createBlock("pattern", "模式分析", generatePatternAnalysis(intentKey, topic, vargas)));
        blocks.add(createBlock("advice", "建议", generateAdvice(intentKey, topic, balas)));
        answer.put("blocks", blocks);

        result.put("answer", answer);

        // 构建 evidence_cards
        List<Map<String, Object>> evidenceCards = new ArrayList<>();
        evidenceCards.add(createEvidenceCard("D1", "本命盘", "基于本命盘分析", vargas));
        if (vargas.containsKey("D9")) {
            evidenceCards.add(createEvidenceCard("D9", "九分盘", "婚姻与关系", vargas));
        }
        if (vargas.containsKey("D10")) {
            evidenceCards.add(createEvidenceCard("D10", "十分盘", "事业与成就", vargas));
        }
        result.put("evidence_cards", evidenceCards);

        // 构建 limitations
        List<String> limitations = new ArrayList<>();
        limitations.add("本报告基于吠陀占星学，仅供参考");
        limitations.add("占星解读不构成专业建议");
        result.put("limitations", limitations);

        return result;
    }

    private String generateSummary(String intentKey, String topic, Chart chart, Map<String, Object> userDetails) {
        String name = chart.getName();
        String rashi = (String) userDetails.getOrDefault("rashi", "未知");
        String nakshatra = (String) userDetails.getOrDefault("nakshatra", "未知");

        return String.format("亲爱的 %s，根据您的本命星盘（星座：%s，星宿：%s），以下是关于%s的分析：",
                name, rashi, nakshatra, getTopicName(topic));
    }

    private String generateCoreInterpretation(String intentKey, String topic, Map<String, Object> vargas, Map<String, Object> userDetails) {
        StringBuilder sb = new StringBuilder();
        Map<String, Object> d1 = (Map<String, Object>) vargas.getOrDefault("D1", Collections.emptyMap());

        if (d1.containsKey("ascendant")) {
            Map<String, Object> asc = (Map<String, Object>) d1.get("ascendant");
            sb.append(String.format("您的上升星座是 %s，", asc.getOrDefault("sign", "未知")));
        }

        if (d1.containsKey("planets")) {
            Map<String, Object> planets = (Map<String, Object>) d1.get("planets");
            int planetCount = planets.size();
            sb.append(String.format("本命盘共有 %d 个行星位置被分析。", planetCount));
        }

        return sb.toString();
    }

    private String generatePatternAnalysis(String intentKey, String topic, Map<String, Object> vargas) {
        StringBuilder sb = new StringBuilder();
        for (String vargaKey : new String[]{"D1", "D9", "D10", "D12"}) {
            if (vargas.containsKey(vargaKey)) {
                Map<String, Object> varga = (Map<String, Object>) vargas.get(vargaKey);
                String name = (String) varga.getOrDefault("name", vargaKey);
                sb.append(String.format("%s（%s）已分析；", vargaKey, name));
            }
        }
        return sb.toString();
    }

    private String generateAdvice(String intentKey, String topic, Map<String, Object> balas) {
        StringBuilder sb = new StringBuilder();
        if (balas.containsKey("Shadbala")) {
            Map<String, Object> shadbala = (Map<String, Object>) balas.get("Shadbala");
            Map<String, Object> total = (Map<String, Object>) shadbala.getOrDefault("Total", Collections.emptyMap());
            if (!total.isEmpty()) {
                Map.Entry<String, Object> maxEntry = null;
                for (Map.Entry<String, Object> entry : total.entrySet()) {
                    if (maxEntry == null || ((Number) entry.getValue()).doubleValue() > ((Number) maxEntry.getValue()).doubleValue()) {
                        maxEntry = entry;
                    }
                }
                if (maxEntry != null) {
                    sb.append(String.format("您的最强行星是 %s（六力值：%s），建议发挥此行星特质。",
                            maxEntry.getKey(), maxEntry.getValue()));
                }
            }
        }
        return sb.toString();
    }

    private Map<String, Object> createBlock(String blockType, String title, String body) {
        Map<String, Object> block = new HashMap<>();
        block.put("block_type", blockType);
        block.put("title", title);
        block.put("body", body);
        return block;
    }

    private Map<String, Object> createEvidenceCard(String layerType, String title, String description, Map<String, Object> vargas) {
        Map<String, Object> card = new HashMap<>();
        card.put("layer_type", layerType);
        card.put("title", title);
        card.put("description", description);
        card.put("source_refs", Collections.singletonList(layerType));
        return card;
    }

    private Map<String, Object> createClarificationAnswer(String message) {
        Map<String, Object> answer = new HashMap<>();
        answer.put("summary", message);
        answer.put("blocks", Collections.singletonList(createBlock("redirect", "需要补充信息", message)));
        return answer;
    }

    private Map<String, Object> createRuntimeMeta(String status) {
        Map<String, Object> meta = new HashMap<>();
        meta.put("status", status);
        meta.put("timestamp", System.currentTimeMillis());
        meta.put("version", "1.0.0");
        return meta;
    }

    private String getTopicName(String topic) {
        Map<String, String> topicNames = new HashMap<>();
        topicNames.put("career", "事业");
        topicNames.put("money", "财富");
        topicNames.put("relationship", "关系");
        topicNames.put("marriage", "婚姻");
        topicNames.put("life_theme", "人生主题");
        topicNames.put("learning", "学习");
        topicNames.put("children", "子女");
        topicNames.put("family", "家庭");
        topicNames.put("personality", "性格");
        topicNames.put("wellbeing", "健康");
        topicNames.put("relocation", "迁移");
        return topicNames.getOrDefault(topic, topic);
    }

    /**
     * 从 Authorization header 中提取用户ID
     */
    private String extractUserId(String authHeader) {
        if (authHeader != null && authHeader.startsWith("Bearer ")) {
            String token = authHeader.substring(7);
            if (token.startsWith("token_")) {
                return "user_" + token.substring(6);
            }
            if (token.startsWith("mock_token_")) {
                return "user_" + Math.abs(token.hashCode());
            }
        }
        return "anonymous";
    }
}
