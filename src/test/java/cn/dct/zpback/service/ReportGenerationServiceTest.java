package cn.dct.zpback.service;

import cn.dct.zpback.config.AIConfig;
import cn.dct.zpback.exception.AIUnavailableException;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;

import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

/**
 * ReportGenerationService 提示词构造测试
 *
 * 回归背景：旧实现用 natalData.toString() 把星盘塞进提示词，
 * 对 POJO 而言输出的是 CanonicalChartData@1a2b3c 这类对象标识，
 * 模型拿不到任何真实数据。
 */
class ReportGenerationServiceTest {

    /** 记录 prompt 的假 AIService，避免真实网络调用 */
    private static class RecordingAI extends AIService {
        String lastPrompt;

        RecordingAI() {
            super(new AIConfig(), null, new ObjectMapper());
        }

        @Override
        public String ask(String systemPrompt, String userPrompt) {
            this.lastPrompt = userPrompt;
            return "ok";
        }
    }

    private static ReportGenerationService service(RecordingAI ai) {
        return new ReportGenerationService(ai, new AIConfig(), new ObjectMapper());
    }

    @Test
    void natalData_isSerializedAsJson_notObjectIdentity() {
        RecordingAI ai = new RecordingAI();
        Map<String, Object> natal = new LinkedHashMap<>();
        natal.put("ascendant", "Leo");
        natal.put("nakshatra", "Shatabhisha");

        service(ai).generateReport("CAREER", natal, null);

        assertNotNull(ai.lastPrompt);
        assertTrue(ai.lastPrompt.contains("\"ascendant\""), "必须输出 JSON 键: " + ai.lastPrompt);
        assertTrue(ai.lastPrompt.contains("Shatabhisha"), "必须带出真实数值");
        assertFalse(ai.lastPrompt.contains("@"), "不得出现 Java 对象标识 ClassName@hash");
    }

    @Test
    void nestedNatalData_isSerializedRecursively() {
        RecordingAI ai = new RecordingAI();
        Map<String, Object> planet = new LinkedHashMap<>();
        planet.put("sign", "Scorpio");
        planet.put("house", 7);
        Map<String, Object> natal = new LinkedHashMap<>();
        natal.put("planets", new HashMap<>(Map.of("Mars", planet)));

        service(ai).generateReport("CAREER", natal, null);

        assertTrue(ai.lastPrompt.contains("Scorpio"), "嵌套结构必须序列化: " + ai.lastPrompt);
        assertFalse(ai.lastPrompt.contains("@"), "不得出现 Java 对象标识");
    }

    @Test
    void extraContext_structuredValue_isSerializedAsJson() {
        RecordingAI ai = new RecordingAI();
        Map<String, Object> extra = new LinkedHashMap<>();
        extra.put("focus", Map.of("career", "change"));

        service(ai).generateReport("CAREER", null, extra);

        assertTrue(ai.lastPrompt.contains("\"career\""), "结构化 extra 也应序列化: " + ai.lastPrompt);
        assertFalse(ai.lastPrompt.contains("@"), "不得出现 Java 对象标识");
    }

    @Test
    void aiFailure_propagatesInsteadOfBecomingReportContent() {
        AIService failing = new AIService(new AIConfig(), null, new ObjectMapper()) {
            @Override
            public String ask(String systemPrompt, String userPrompt) {
                throw new AIUnavailableException("AI 调用失败");
            }
        };
        ReportGenerationService svc =
                new ReportGenerationService(failing, new AIConfig(), new ObjectMapper());

        assertThrows(AIUnavailableException.class,
                () -> svc.generateReport("CAREER", Map.of("a", "b"), null),
                "AI 失败必须抛出，不能把失败文案当报告正文返回");
    }
}