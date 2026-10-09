package cn.dct.zpback.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Configuration;

/**
 * AI 服务配置
 * 支持 OpenAI 兼容格式（OpenAI / Claude / DeepSeek / 通义千问 等）
 */
@Data
@Configuration
@ConfigurationProperties(prefix = "ai")
public class AIConfig {

    /** API 基础地址 */
    private String baseUrl = "https://api.openai.com/v1";

    /** API Key */
    private String apiKey = "";

    /** 模型名称 */
    private String model = "gpt-4o-mini";

    /** 最大 token 数 */
    private int maxTokens = 4096;

    /** 温度 */
    private double temperature = 0.7;

    /** 超时时间（秒） */
    private int timeout = 120;

    /** 系统提示词 */
    private String systemPrompt = "你是星灵AI，一个专业的吠陀占星AI助手。你精通印度吠陀占星学，能够基于用户的星盘数据提供专业、温和、有洞察力的解读。";
}
