package cn.dct.zpback.service;

import cn.dct.zpback.config.AIConfig;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.*;

/**
 * AI 报告生成服务
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class ReportGenerationService {

    private final AIService aiService;
    private final AIConfig aiConfig;
    private final ObjectMapper objectMapper;

    // ============================================================
    // 报告类型定义
    // ============================================================

    private static final Map<String, String> REPORT_TITLES = new HashMap<>();

    static {
        REPORT_TITLES.put("CAREER", "事业深度报告");
        REPORT_TITLES.put("NATAL_RELATIONSHIP", "单人婚恋深度报告");
        REPORT_TITLES.put("SYNASTRY_RELATIONSHIP", "双人合盘深度报告");
        REPORT_TITLES.put("LIFEPATTERN_GROWTH", "人生课题深度报告");
        REPORT_TITLES.put("WEALTH", "财富格局深度报告");
        REPORT_TITLES.put("EDUCATION", "学业方向深度报告");
        REPORT_TITLES.put("HEALTH", "健康养生深度报告");
        REPORT_TITLES.put("ANNUAL_FORECAST", "年度运势深度报告");
    }

    // ============================================================
    // 报告生成
    // ============================================================

    /**
     * 生成报告
     * @param reportType 报告类型
     * @param natalData 本命星盘数据
     * @param extraContext 额外上下文
     * @return 报告内容
     */
    public String generateReport(String reportType, Object natalData, Map<String, Object> extraContext) {
        String title = REPORT_TITLES.getOrDefault(reportType, "深度报告");
        String systemPrompt = buildReportSystemPrompt(reportType, title);
        String userPrompt = buildReportUserPrompt(reportType, natalData, extraContext);

        return aiService.ask(systemPrompt, userPrompt);
    }

    /**
     * 构建报告系统提示词
     */
    private String buildReportSystemPrompt(String reportType, String title) {
        return String.format("""
                你是星灵AI，专业的吠陀占星师。你正在为用户生成一份【%s】。

                报告要求：
                1. 使用 Markdown 格式
                2. 包含以下结构：
                   - ## 概述（整体格局分析）
                   - ## 核心发现（3-5个关键洞察）
                   - ## 详细分析（按主题分节）
                   - ## 时间趋势（近期/长期）
                   - ## 实用建议（具体可操作）
                3. 语言风格：专业但不晦涩，温和但有洞察力
                4. 每个结论都要有占星依据
                5. 结尾给出3个具体的行动建议

                报告类型: %s
                """, title, reportType);
    }

    /**
     * 构建报告用户提示词
     */
    private String buildReportUserPrompt(String reportType, Object natalData, Map<String, Object> extraContext) {
        StringBuilder sb = new StringBuilder();
        sb.append("请基于以下星盘数据生成深度报告：\n\n");

        if (natalData != null) {
            sb.append("## 星盘数据\n");
            // 必须序列化成 JSON：直接 toString() 会得到 CanonicalChartData@1a2b3c 这类对象标识，
            // 模型拿不到任何真实数据
            sb.append(toJson(natalData)).append("\n\n");
        }

        if (extraContext != null && !extraContext.isEmpty()) {
            sb.append("## 额外信息\n");
            for (Map.Entry<String, Object> entry : extraContext.entrySet()) {
                sb.append("- ").append(entry.getKey()).append(": ")
                        .append(entry.getValue() instanceof String
                                ? entry.getValue()
                                : toJson(entry.getValue()))
                        .append("\n");
            }
        }

        return sb.toString();
    }

    private String toJson(Object value) {
        try {
            return objectMapper.writeValueAsString(value);
        } catch (Exception e) {
            log.warn("星盘数据序列化失败，退回字符串形式: {}", e.getMessage());
            return String.valueOf(value);
        }
    }

    /**
     * 获取报告标题
     */
    public String getReportTitle(String reportType) {
        return REPORT_TITLES.getOrDefault(reportType, "深度报告");
    }

    /**
     * 获取所有报告类型
     */
    public Map<String, String> getAllReportTypes() {
        return Collections.unmodifiableMap(REPORT_TITLES);
    }
}
