package cn.dct.zpback.service.synastry;

import cn.dct.zpback.engine.relationship.ChartDataParser;
import cn.dct.zpback.engine.synastry.CrossChartInteractionEngine;
import cn.dct.zpback.engine.synastry.SynastryEvidenceEngine;
import cn.dct.zpback.service.VedicAstroService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.util.*;

/**
 * 双人合盘报告服务
 * 符合 SYN-STEP 3/4/5/6 SEALED 规范
 */
@Service
@Slf4j
@SuppressWarnings("unchecked")
public class SynastryReportService {

    @Autowired
    private VedicAstroService vedicAstroService;

    @Autowired
    private CrossChartInteractionEngine crossChartEngine;

    @Autowired
    private SynastryEvidenceEngine evidenceEngine;

    /**
     * 生成双人合盘报告
     */
    public Map<String, Object> generateReport(String userId,
                                               Map<String, Object> chartAData,
                                               Map<String, Object> chartBData,
                                               Map<String, Object> chartAParams,
                                               Map<String, Object> chartBParams,
                                               String relationshipType,
                                               String relationshipStatus,
                                               String focusArea) {

        log.info("开始生成双人合盘报告: userId={}, relType={}", userId, relationshipType);

        Map<String, Object> report = new HashMap<>();
        String reportId = "syn_" + UUID.randomUUID().toString().replace("-", "").substring(0, 16);

        report.put("report_id", reportId);
        report.put("report_type", "SYNASTRY_RELATIONSHIP");
        report.put("relationship_type", relationshipType);
        report.put("relationship_status", relationshipStatus);
        report.put("generated_at", new Date().toString());
        report.put("status", "COMPLETED");

        try {
            // 1. 获取两人的真实星盘数据（如果传入的是参数而非数据）
            Map<String, Object> astroDataA = resolveAstroData(chartAData, chartAParams);
            Map<String, Object> astroDataB = resolveAstroData(chartBData, chartBParams);

            if (astroDataA == null || astroDataB == null) {
                report.put("status", "FAILED");
                report.put("error", "无法获取星盘数据");
                return report;
            }

            report.put("person_a", extractPersonSummary(astroDataA, "A"));
            report.put("person_b", extractPersonSummary(astroDataB, "B"));

            // 2. 计算 D1 跨盘互动 (SYN-STEP3)
            Map<String, Object> crossChart = crossChartEngine.calculateCrossChart(astroDataA, astroDataB);
            report.put("cross_chart", crossChart);

            // 3. 选择核心证据 (SYN-STEP5)
            String topic = inferTopic(focusArea);
            String intentKey = inferIntentKey(focusArea);
            List<Map<String, Object>> evidence = evidenceEngine.selectEvidence(
                    crossChart, relationshipType, intentKey, topic);
            report.put("evidence", evidence);
            report.put("topic", topic);
            report.put("intent_key", intentKey);

            // 4. 生成渐进式回答 (SYN-STEP6)
            List<Map<String, Object>> progressiveAnswer = generateProgressiveAnswer(
                    evidence, relationshipType, topic, focusArea, astroDataA, astroDataB);
            report.put("progressive_answer", progressiveAnswer);
            report.put("answer_summary", progressiveAnswer.get(0).get("body"));

            // 5. 关系维度分析
            report.put("dimensions", generateDimensionAnalysis(crossChart, relationshipType));

            log.info("双人合盘报告生成完成: reportId={}, evidence_count={}", reportId, evidence.size());

        } catch (Exception e) {
            log.error("双人合盘报告生成失败: {}", e.getMessage(), e);
            report.put("status", "FAILED");
            report.put("error", e.getMessage());
        }

        return report;
    }

    /**
     * 获取真实星盘数据
     */
    private Map<String, Object> resolveAstroData(Map<String, Object> chartData, Map<String, Object> chartParams) {
        // 如果已有解析后的数据，直接使用
        if (chartData != null && !chartData.isEmpty() && chartData.containsKey("vargas")) {
            return chartData;
        }

        // 如果有出生参数，调用 VedicAstro
        if (chartParams != null && !chartParams.isEmpty()) {
            try {
                Map<String, Object> vedicParams = buildVedicParams(chartParams);
                return vedicAstroService.getJyotishyaData(vedicParams);
            } catch (Exception e) {
                log.error("调用 VedicAstro 失败: {}", e.getMessage());
            }
        }

        return null;
    }

    private Map<String, Object> buildVedicParams(Map<String, Object> params) {
        Map<String, Object> vedicParams = new HashMap<>();
        vedicParams.put("name", params.getOrDefault("name", "User"));
        vedicParams.put("gender", params.getOrDefault("gender", "male"));

        if (params.containsKey("birthDate")) {
            String[] dateParts = ((String) params.get("birthDate")).split("-");
            if (dateParts.length == 3) {
                vedicParams.put("year", Integer.parseInt(dateParts[0]));
                vedicParams.put("month", Integer.parseInt(dateParts[1]));
                vedicParams.put("day", Integer.parseInt(dateParts[2]));
            }
        }

        if (params.containsKey("birthTime")) {
            String[] timeParts = ((String) params.get("birthTime")).split(":");
            if (timeParts.length >= 2) {
                vedicParams.put("hour", Integer.parseInt(timeParts[0]));
                vedicParams.put("minute", Integer.parseInt(timeParts[1]));
                vedicParams.put("second", timeParts.length > 2 ? Integer.parseInt(timeParts[2]) : 0);
            }
        } else {
            vedicParams.put("hour", 12);
            vedicParams.put("minute", 0);
            vedicParams.put("second", 0);
        }

        vedicParams.put("place", params.getOrDefault("birthPlace", "Unknown"));
        // 出生地坐标必填：缺失时抛错，绝不套用默认坐标导致上升与宫位错位
        vedicParams.put("latitude", ChartDataParser.requireCoordinate(params.get("latitude"), "纬度", -90, 90));
        vedicParams.put("longitude", ChartDataParser.requireCoordinate(params.get("longitude"), "经度", -180, 180));
        vedicParams.put("timezone", "+8.0");
        vedicParams.put("ayanamsa", "Lahiri");
        vedicParams.put("houseSystem", "Whole Sign");
        vedicParams.put("charts", null);
        return vedicParams;
    }

    private Map<String, Object> extractPersonSummary(Map<String, Object> astroData, String label) {
        Map<String, Object> summary = new HashMap<>();
        summary.put("label", label);

        Map<String, Object> vargas = (Map<String, Object>) astroData.getOrDefault("vargas", Collections.emptyMap());
        Map<String, Object> d1 = (Map<String, Object>) vargas.getOrDefault("D1", Collections.emptyMap());

        Map<String, Object> asc = (Map<String, Object>) d1.getOrDefault("ascendant", Collections.emptyMap());
        summary.put("asc_sign", asc.getOrDefault("sign", "Unknown"));

        Map<String, Object> planets = (Map<String, Object>) d1.getOrDefault("planets", Collections.emptyMap());
        Map<String, Object> sunP = (Map<String, Object>) planets.get("Sun");
        Map<String, Object> moonP = (Map<String, Object>) planets.get("Moon");
        Map<String, Object> venusP = (Map<String, Object>) planets.get("Venus");
        Map<String, Object> marsP = (Map<String, Object>) planets.get("Mars");

        if (sunP != null) summary.put("sun_sign", sunP.getOrDefault("sign", "Unknown"));
        if (moonP != null) summary.put("moon_sign", moonP.getOrDefault("sign", "Unknown"));
        if (venusP != null) summary.put("venus_sign", venusP.getOrDefault("sign", "Unknown"));
        if (marsP != null) summary.put("mars_sign", marsP.getOrDefault("sign", "Unknown"));

        return summary;
    }

    private String inferTopic(String focusArea) {
        if (focusArea == null || focusArea.isEmpty()) return "relationship_core";
        String lower = focusArea.toLowerCase();
        if (lower.contains("吸引") || lower.contains("喜欢") || lower.contains("爱")) return "attraction_chemistry";
        if (lower.contains("情感") || lower.contains("情绪") || lower.contains("感受")) return "emotional_interaction";
        if (lower.contains("沟通") || lower.contains("交流") || lower.contains("说话")) return "communication_mental";
        if (lower.contains("冲突") || lower.contains("吵架") || lower.contains("矛盾")) return "conflict_power_boundaries";
        if (lower.contains("信任") || lower.contains("亲密") || lower.contains("背叛")) return "intimacy_trust";
        if (lower.contains("未来") || lower.contains("长期") || lower.contains("结婚")) return "long_term_partnership";
        if (lower.contains("事业") || lower.contains("合作") || lower.contains("工作")) return "business_collaboration";
        if (lower.contains("家庭") || lower.contains("父母") || lower.contains("兄弟")) return "family_kinship";
        if (lower.contains("成长") || lower.contains("影响") || lower.contains("改变")) return "influence_growth";
        return "relationship_core";
    }

    private String inferIntentKey(String focusArea) {
        if (focusArea == null || focusArea.isEmpty()) return "overall_relationship_pattern";
        String lower = focusArea.toLowerCase();
        if (lower.contains("模式") || lower.contains("整体")) return "overall_relationship_pattern";
        if (lower.contains("吸引")) return "attraction_pattern_pair";
        if (lower.contains("情感")) return "emotional_resonance_pair";
        if (lower.contains("沟通")) return "communication_style_fit_pair";
        if (lower.contains("冲突")) return "conflict_pattern_pair";
        if (lower.contains("信任")) return "trust_building_pattern";
        if (lower.contains("未来")) return "long_term_compatibility_pair";
        return "overall_relationship_pattern";
    }

    /**
     * 生成渐进式回答 (SYN-STEP6)
     * 字数统一规范：5 轮渐进，每轮 600-800 字，总量 3000-4000 中文字
     * 证据少时缩为 4 轮，但每轮仍保持深度，不为凑轮数重复
     * 每轮推进新的关系机制
     */
    private List<Map<String, Object>> generateProgressiveAnswer(List<Map<String, Object>> evidence,
                                                                  String relationshipType,
                                                                  String topic,
                                                                  String focusArea,
                                                                  Map<String, Object> astroDataA,
                                                                  Map<String, Object> astroDataB) {
        List<Map<String, Object>> turns = new ArrayList<>();
        String relName = getRelationshipName(relationshipType);
        int evidenceCount = evidence.size();
        // 根据证据数量决定轮数：证据少时缩为 4 轮，不硬凑
        int totalTurns = evidenceCount <= 1 ? 4 : 5;

        // Turn 1: 核心结论（直接回答问题）
        Map<String, Object> turn1 = new HashMap<>();
        turn1.put("turn", 1);
        turn1.put("type", "summary");
        turn1.put("title", "核心解读");
        turn1.put("body", generateTurn1Body(evidence, relName, focusArea));
        turns.add(turn1);

        // Turn 2: 第一核心机制
        Map<String, Object> turn2 = new HashMap<>();
        turn2.put("turn", 2);
        turn2.put("type", "mechanism");
        turn2.put("title", "关键互动机制");
        turn2.put("body", generateTurn2Body(evidence, astroDataA, astroDataB));
        turns.add(turn2);

        // Turn 3: 第二机制/方向性（仅证据充足时）
        if (totalTurns >= 5 && evidenceCount >= 2) {
            Map<String, Object> turn3 = new HashMap<>();
            turn3.put("turn", 3);
            turn3.put("type", "direction");
            turn3.put("title", "关系动态");
            turn3.put("body", generateTurn3Body(evidence));
            turns.add(turn3);
        }

        // Turn 4(或3): 优势与挑战
        Map<String, Object> turn4 = new HashMap<>();
        turn4.put("turn", totalTurns >= 5 ? 4 : 3);
        turn4.put("type", "mixed");
        turn4.put("title", "关系的优势与挑战");
        turn4.put("body", generateTurn4Body(evidence, relName));
        turns.add(turn4);

        // Turn 5(或4): 建议
        Map<String, Object> turn5 = new HashMap<>();
        turn5.put("turn", totalTurns >= 5 ? 5 : 4);
        turn5.put("type", "advice");
        turn5.put("title", "关系建议");
        turn5.put("body", generateTurn5Body(evidence, relName, topic));
        turns.add(turn5);

        return turns;
    }

    private String generateTurn1Body(List<Map<String, Object>> evidence, String relName, String focusArea) {
        StringBuilder sb = new StringBuilder();
        sb.append(String.format("从你们的双人合盘来看，这段%s的核心结构是混合型——既有相互吸引和支持的机制，也有需要磨合的地方。", relName));

        if (!evidence.isEmpty()) {
            Map<String, Object> topEvidence = evidence.get(0);
            String sender = (String) topEvidence.get("sender_planet");
            String receiver = (String) topEvidence.get("receiver_planet");
            String direction = (String) topEvidence.get("direction");
            String tightness = (String) topEvidence.get("tightness");
            sb.append(String.format("其中最显著的结构是%s对%s的影响（%s，紧密程度：%s），这为关系奠定了基调。",
                    sender, receiver, direction, tightness));
        }

        sb.append(String.format("关于%s，你们的关系模式不是单一的'好'或'不好'，而是同时包含支持性与挑战性的复合结构。",
                focusArea.isEmpty() ? "你们的关系" : focusArea));

        sb.append("\n\n先说支持性的一面。合盘中紧密而良性的行星互动，会在关系中自然表现为：\n");
        sb.append("1. 相处时有\"对得上频道\"的感觉——某些话题、节奏和情绪反应不需要解释就能彼此理解；\n");
        sb.append("2. 一方的状态会稳定地滋养另一方：例如一方的行动力恰好推动另一方的犹豫，或一方的稳定感恰好接住另一方的波动；\n");
        sb.append("3. 共同做事比各自单干更顺利，这是结构层面的协同，不是偶然。\n\n");

        sb.append("再说挑战性的一面。同样的紧密结构，在压力情境下也可能呈现另一面：\n");
        sb.append("1. 曾经吸引彼此的特质，在熟悉之后可能变成摩擦点——比如\"当初喜欢TA的直接\"变成\"现在嫌TA说话冲\"；\n");
        sb.append("2. 结构带来的互动模式有惯性，双方都可能感觉\"总是以同样的方式卡住\"；\n");
        sb.append("3. 若只看到冲突的表象而不理解背后的结构，容易把结构性差异误读为\"对方不爱了\"或\"性格不合\"。\n\n");

        sb.append("因此，这份报告的用法是：先理解你们的互动结构是什么、各自从哪里来、在什么情境下以哪种面向出现，");
        sb.append("然后再谈如何相处。结构是中性的，理解结构之后的经营才是有效的经营。");
        return sb.toString();
    }

    private String generateTurn2Body(List<Map<String, Object>> evidence,
                                       Map<String, Object> astroDataA,
                                       Map<String, Object> astroDataB) {
        StringBuilder sb = new StringBuilder();
        sb.append("在这个关系中，最核心的互动机制由以下结构支撑：\n\n");

        int count = 0;
        for (Map<String, Object> e : evidence) {
            if (count >= 3) break; // 最多展示3条核心证据
            String sender = (String) e.get("sender_planet");
            String receiver = (String) e.get("receiver_planet");
            String aspect = (String) e.get("aspect_type");
            String tightness = (String) e.get("tightness");
            String direction = (String) e.get("direction");
            String role = (String) e.getOrDefault("role", "SECONDARY");

            sb.append(String.format("· %s对%s的%s关系（%s，紧密程度：%s）\n", sender, receiver, aspect, direction, tightness));
            sb.append(String.format("  这表示%s侧的%s功能作用于%s侧的%s接收结构，是%s证据。\n\n",
                    direction.charAt(0), sender, direction.charAt(2), receiver,
                    "PRIMARY".equals(role) ? "主要" : "次要"));
            count++;
        }

        sb.append("逐条展开来看：\n\n");
        sb.append("1. 关于触发结构：当一方的行星频繁触发另一方的行星时，关系中会形成\"按键—反应\"的固定回路。");
        sb.append("熟悉的伴侣都会发现，某些话题一提起、某些语气一出现，对方的反应几乎是可预测的——这不是巧合，而是结构在起作用。\n\n");
        sb.append("2. 关于支持结构：良性相位（如三分相、六分相）形成的回路，让双方在特定领域天然默契。");
        sb.append("这类领域不需要刻意经营就能运转良好，是关系的\"节能模式\"，值得识别出来并有意识地依赖。\n\n");
        sb.append("3. 关于张力结构：紧张相位（如四分相、对分相）形成的回路，初期带来张力和吸引力，长期则成为磨合的重点。");
        sb.append("张力结构不是\"坏结构\"——很多长期关系的深度恰恰来自这些必须共同面对的议题。\n\n");

        sb.append("需要注意的是，这些结构本身没有绝对的'好'或'坏'，关键在于你们如何理解和运用这些能量。");
        sb.append("同一组结构，在互相指责的语境下是摩擦源，在互相理解的语境下是成长路径。");
        return sb.toString();
    }

    private String generateTurn3Body(List<Map<String, Object>> evidence) {
        StringBuilder sb = new StringBuilder();
        sb.append("这段关系中的能量流动不是单向的。");

        long aToB = evidence.stream().filter(e -> ((String) e.get("direction")).equals("A→B")).count();
        long bToA = evidence.stream().filter(e -> ((String) e.get("direction")).equals("B→A")).count();

        sb.append(String.format("A对B的结构影响有%d条，B对A的结构影响有%d条。", aToB, bToA));
        sb.append("这种双向流动意味着关系的动力是相互的，不存在简单的'主动-被动'之分。\n\n");

        if (aToB > bToA) {
            sb.append("从数量上看，A侧的行星更多地对B侧的结构发起作用。这通常表现为：\n");
            sb.append("1. A的行为方式、情绪表达和决策节奏，更直接地搅动B的内在状态；\n");
            sb.append("2. 在日常相处中，B更容易感受到\"被影响\"——情绪被牵动、计划被调整、状态被改变；\n");
            sb.append("3. 因此B需要对\"我为什么会有这种反应\"有更多自我觉察，分清哪些是A带来的触发，哪些是自己本来的课题。\n\n");
        } else if (bToA > aToB) {
            sb.append("从数量上看，B侧的行星更多地对A侧的结构发起作用。这通常表现为：\n");
            sb.append("1. B的行为方式、情绪表达和决策节奏，更直接地搅动A的内在状态；\n");
            sb.append("2. 在日常相处中，A更容易感受到\"被影响\"——情绪被牵动、计划被调整、状态被改变；\n");
            sb.append("3. 因此A需要对\"我为什么会有这种反应\"有更多自我觉察，分清哪些是B带来的触发，哪些是自己本来的课题。\n\n");
        } else {
            sb.append("两个方向的影响力基本对等，这通常表现为：\n");
            sb.append("1. 双方都会明显地感受到对方对自己的影响，关系中的\"谁迁就谁\"不是固定答案；\n");
            sb.append("2. 冲突和甜蜜都容易升级——因为触发是相互的，一方的反应会立刻反作用于另一方；\n");
            sb.append("3. 这类关系特别需要\"暂停机制\"：在互相激化的时刻，先有一方停下来，才能打断回路。\n\n");
        }

        sb.append("更重要的观察是\"影响的领域\"：触发彼此情绪的行星结构，多出现在情感与安全感的领域；");
        sb.append("触发彼此行动的行星结构，多出现在目标与执行方式的领域。把影响\"按领域归类\"之后，");
        sb.append("你们会发现摩擦往往集中在两三个固定场景，而不是\"处处都是问题\"——这本身就是一种安心。\n\n");

        sb.append("理解方向性的价值在于：当关系出现张力时，你们可以问\"这次是哪条回路被触发了、谁在这一端、谁在那端\"，");
        sb.append("而不是笼统地指责对方。方向感清晰的结构地图，是有效沟通的前提。");
        return sb.toString();
    }

    private String generateTurn4Body(List<Map<String, Object>> evidence, String relName) {
        StringBuilder sb = new StringBuilder();
        sb.append(String.format("这段%s的优势在于：\n\n", relName));
        sb.append("1. 互动结构是真实存在的——你们之间的吸引和共鸣不是想象，而是有星盘配置支撑的；\n");
        sb.append("2. 能量的双向流动意味着关系的动力是相互的，不存在简单的'主动-被动'之分；\n");
        sb.append("3. 支持性结构在关系顺利时会自然运作，为你们提供情感和行动上的支撑；\n");
        sb.append("4. 结构的可预测性反而是礼物：因为模式固定，所以一旦被理解，就几乎总能被提前识别和干预。\n\n");

        sb.append("同时，挑战也不可忽视：\n\n");
        sb.append("1. 带来强烈吸引力的结构，也可能在冲突时被触发——激情与摩擦常常来自同一条回路；\n");
        sb.append("2. 相同的结构在不同情境下可能呈现不同面向：顺境中是默契，压力下是火药味；\n");
        sb.append("3. 如果双方没有意识到这些差异，就可能产生误解和摩擦——把结构性的\"自动反应\"当成对方的故意行为；\n");
        sb.append("4. 长期关系中最消耗的，往往不是大冲突，而是同一条小回路被反复触发、反复以同样方式失败。\n\n");

        sb.append("把优势与挑战放在一起看，会发现它们其实是同一组结构的两面：\n\n");
        sb.append("· 让你们\"来电\"的能量，和让你们\"吵架\"的能量，物理上是同一条结构；\n");
        sb.append("· 让你们\"省心\"的默契，如果被滥用（以为不用沟通也能懂），也可能变成\"想当然\"的隔阂；\n");
        sb.append("· 让关系\"有深度\"的张力议题，回避它则反复出现，面对它则转化为信任。\n\n");

        sb.append("关键认识：关系的挑战不是'问题'，而是结构的双面性。接纳这些结构的双面性，而不是试图消除它们，");
        sb.append("是这段关系成长的必经之路。判断一段关系前景的，从来不是有没有挑战结构，而是双方如何对待这些结构。");
        return sb.toString();
    }

    private String generateTurn5Body(List<Map<String, Object>> evidence, String relName, String topic) {
        StringBuilder sb = new StringBuilder();
        sb.append(String.format("基于以上结构，对这段%s的实际建议如下：\n\n", relName));
        sb.append("1. 识别结构模式：当冲突发生时，尝试识别是哪个结构被触发，而不是归咎于对方。你们之间的很多摩擦其实是结构性差异，不是某一方的问题；\n\n");
        sb.append("2. 发挥支持作用：利用已经存在的支持性结构，在关系顺利时主动强化这些能量。不要只在遇到问题时才关注星盘——支持性结构是你们的\"储蓄\"，平时多存，难时才能取；\n\n");
        sb.append("3. 接纳双面性：认识到每个结构都有其优势面和挑战面。金星带来和谐，但也可能回避冲突；火星带来行动力，但也可能引发冲动；月亮带来亲密，但也可能放大情绪波动。了解双方行星的\"双面说明书\"，比要求对方改变更现实；\n\n");
        sb.append("4. 尊重差异节奏：你们在情感表达、冲突修复、需求回应上可能存在节奏差异。快节奏的一方学会等待，慢节奏的一方学会告知进度——\"我需要一点时间，但我没有关上门\"这类表达，能消解大部分节奏摩擦；\n\n");
        sb.append("5. 建立回路中断机制：针对你们最容易重复的那个卡点，约定一个具体的中断信号或暂停动作。结构回路很强，但\"识别—暂停—换场景\"三步能可靠地打断它。事后复盘比当场解决更重要；\n\n");
        sb.append("6. 定期回到结构地图：每隔一段时间，一起回顾最近 triggered 的是哪条结构、是否以新的方式被处理了。把报告从\"一次性解读\"变成\"长期使用的地图\"，它的价值才会真正显现。\n\n");

        sb.append("温馨提示：以上解读基于吠陀占星学的双人合盘分析，旨在提供关系结构的理解框架，不构成具体的行为建议。");
        sb.append("关系的最终走向取决于你们共同的选择和努力；结构解释模式，但选择权始终在你们手中。");
        return sb.toString();
    }

    /**
     * 关系维度分析
     */
    private Map<String, Object> generateDimensionAnalysis(Map<String, Object> crossChart, String relationshipType) {
        Map<String, Object> dimensions = new HashMap<>();

        List<Map<String, Object>> p2pAB = (List<Map<String, Object>>) crossChart.getOrDefault("p2p_ab", Collections.emptyList());
        List<Map<String, Object>> p2pBA = (List<Map<String, Object>>) crossChart.getOrDefault("p2p_ba", Collections.emptyList());

        // 吸引力维度
        long attractionCount = p2pAB.stream().filter(e ->
                Arrays.asList("Venus", "Mars", "Moon").contains(e.get("sender_planet"))).count()
                + p2pBA.stream().filter(e ->
                Arrays.asList("Venus", "Mars", "Moon").contains(e.get("sender_planet"))).count();

        dimensions.put("attraction_level", attractionCount > 3 ? "strong" : attractionCount > 1 ? "moderate" : "mild");

        // 沟通维度
        long commCount = p2pAB.stream().filter(e ->
                Arrays.asList("Mercury").contains(e.get("sender_planet"))).count()
                + p2pBA.stream().filter(e ->
                Arrays.asList("Mercury").contains(e.get("sender_planet"))).count();

        dimensions.put("communication_level", commCount > 2 ? "strong" : commCount > 0 ? "moderate" : "mild");

        // 冲突维度
        long conflictCount = p2pAB.stream().filter(e ->
                Arrays.asList("Mars", "Saturn").contains(e.get("sender_planet"))).count()
                + p2pBA.stream().filter(e ->
                Arrays.asList("Mars", "Saturn").contains(e.get("sender_planet"))).count();

        dimensions.put("conflict_risk", conflictCount > 3 ? "high" : conflictCount > 1 ? "moderate" : "low");

        return dimensions;
    }

    private String getRelationshipName(String type) {
        Map<String, String> names = new HashMap<>();
        names.put("ROMANTIC", "恋爱关系");
        names.put("MARRIED", "婚姻关系");
        names.put("FRIENDSHIP", "友谊关系");
        names.put("FAMILY", "家庭关系");
        names.put("COLLEAGUE", "同事关系");
        names.put("BUSINESS_PARTNER", "合作关系");
        names.put("PARENT_CHILD", "亲子关系");
        names.put("OTHER", "关系");
        return names.getOrDefault(type, "关系");
    }
}
