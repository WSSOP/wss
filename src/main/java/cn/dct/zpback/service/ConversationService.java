package cn.dct.zpback.service;

import cn.dct.zpback.entity.AIConversation;
import cn.dct.zpback.entity.AIConversationMessage;
import cn.dct.zpback.mapper.AIConversationMapper;
import cn.dct.zpback.mapper.AIConversationMessageMapper;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.*;

/**
 * AI 会话管理服务
 * 负责星灵AI 6种模式的会话创建、消息存储、AI 调度
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class ConversationService {

    private final AIConversationMapper conversationMapper;
    private final AIConversationMessageMapper messageMapper;
    private final AIService aiService;
    private final ObjectMapper objectMapper;

    // ============================================================
    // 系统提示词（按模式）
    // ============================================================

    private static final Map<String, String> SYSTEM_PROMPTS = new HashMap<>();

    static {
        SYSTEM_PROMPTS.put("NATAL", """
                你是星灵AI，专业的吠陀占星师。用户正在进行本命星盘咨询。
                请基于用户的星盘数据，回答关于性格、事业、感情、成长的问题。
                回答风格：专业但不晦涩，温和但有洞察力。
                每次回答包含：1) 星盘分析 2) 具体解读 3) 实用建议
                """);

        SYSTEM_PROMPTS.put("SYNASTRY", """
                你是星灵AI，专业的吠陀占星师。用户正在进行双人合盘咨询。
                请基于两人的星盘互动，分析关系模式、契合点、挑战领域。
                回答风格：客观中立，帮助双方理解彼此，促进关系和谐。
                """);

        SYSTEM_PROMPTS.put("PRASHNA", """
                你是星灵AI，专业的吠陀占星师。用户正在针对具体问题进行问事占卜。
                请基于用户提问时的星象，给出方向性指引。
                回答风格：明确但不绝对，给出时间窗口和行动建议。
                """);

        SYSTEM_PROMPTS.put("EMOTIONAL", """
                你是星灵AI，一个温暖善解人意的倾听者。用户正在寻求情感支持。
                请先倾听和理解用户的情绪，给予温暖的陪伴。
                可以温和地结合星象给予启发，但以情感支持为主。
                回答风格：温暖、接纳、不评判。
                """);

        SYSTEM_PROMPTS.put("KNOWLEDGE", """
                你是星灵AI，吠陀占星知识导师。用户正在学习印度占星学。
                请用通俗易懂的语言解释吠陀占星的概念、原理和应用。
                可以举例说明，帮助用户理解复杂的占星知识。
                回答风格：教学式，循序渐进，鼓励提问。
                """);

        SYSTEM_PROMPTS.put("REPORT_FOLLOWUP", """
                你是星灵AI，专业的吠陀占星师。用户正在对已购报告进行追问。
                请基于报告内容回答用户的深入问题，引用报告中的相关结论。
                如果问题超出报告范围，温和地说明并建议生成新报告。
                回答风格：专业、精准、有依据。
                """);
    }

    /** 风格提示词后缀 */
    private static final Map<String, String> STYLE_SUFFIX = new HashMap<>();

    static {
        STYLE_SUFFIX.put("DIRECT", "回答风格：直接、简洁、明确。");
        STYLE_SUFFIX.put("GENTLE", "回答风格：温和、细腻、富有同理心。");
        STYLE_SUFFIX.put("PROFESSIONAL", "回答风格：专业、深入、有学术依据。");
    }

    // ============================================================
    // 会话管理
    // ============================================================

    /**
     * 创建新会话
     */
    @Transactional
    public AIConversation createConversation(String userId, String mode, String question,
                                           String responseStyle, Object context, String sourceContext) {
        LocalDateTime now = LocalDateTime.now();
        AIConversation conv = new AIConversation();
        conv.setId(generateId());
        conv.setUserId(userId);
        conv.setMode(mode);
        conv.setFirstQuestion(question);
        conv.setResponseStyle(responseStyle);
        conv.setStatus("ACTIVE");
        conv.setMessageCount(0);
        conv.setLastActiveAt(now);
        conv.setCreatedAt(now);
        conv.setUpdatedAt(now);

        // 序列化上下文
        if (context != null) {
            try {
                conv.setContextJson(objectMapper.writeValueAsString(context));
            } catch (JsonProcessingException e) {
                conv.setContextJson("{}");
            }
        } else {
            conv.setContextJson("{}");
        }
        conv.setSourceContext(sourceContext);

        conversationMapper.insert(conv);
        log.info("创建AI会话: id={}, userId={}, mode={}", conv.getId(), userId, mode);

        return conv;
    }

    /**
     * 获取会话（带权限校验）
     */
    public AIConversation getConversation(String conversationId, String userId) {
        AIConversation conv = conversationMapper.findById(conversationId);
        if (conv != null && !conv.getUserId().equals(userId)) {
            return null;
        }
        return conv;
    }

    /**
     * 获取用户会话列表
     */
    public List<AIConversation> getConversations(String userId, int page, int limit, String mode) {
        int offset = (page - 1) * limit;
        return conversationMapper.findByUserId(userId, "ACTIVE", limit, offset);
    }

    /**
     * 获取会话总数
     */
    public int getConversationCount(String userId) {
        return conversationMapper.countByUserId(userId, "ACTIVE");
    }

    /**
     * 删除会话
     */
    @Transactional
    public void deleteConversation(String conversationId, String userId) {
        AIConversation conv = getConversation(conversationId, userId);
        if (conv != null) {
            conversationMapper.updateStatus(conversationId, "DELETED", LocalDateTime.now());
        }
    }

    /**
     * 切换表达风格
     */
    public void updateStyle(String conversationId, String userId, String style) {
        AIConversation conv = getConversation(conversationId, userId);
        if (conv != null) {
            conversationMapper.updateResponseStyle(conversationId, style, LocalDateTime.now());
        }
    }

    // ============================================================
    // 消息管理
    // ============================================================

    /**
     * 获取会话消息
     */
    public List<AIConversationMessage> getMessages(String conversationId) {
        return messageMapper.findByConversationId(conversationId);
    }

    /**
     * 发送消息并获取 AI 响应
     */
    @Transactional
    public AIConversationMessage chat(String conversationId, String userContent, String responseStyle) {
        AIConversation conv = conversationMapper.findById(conversationId);
        if (conv == null) {
            return null;
        }

        LocalDateTime now = LocalDateTime.now();

        // 1. 保存用户消息
        AIConversationMessage userMsg = new AIConversationMessage();
        userMsg.setId(generateId());
        userMsg.setConversationId(conversationId);
        userMsg.setRole("user");
        userMsg.setContent(userContent);
        userMsg.setMessageType("TEXT");
        userMsg.setTokensUsed(0);
        userMsg.setSortOrder(conv.getMessageCount());
        userMsg.setCreatedAt(now);
        messageMapper.insert(userMsg);

        // 2. 构建历史消息
        List<AIService.AIMessage> history = buildHistory(conversationId);

        // 3. 调用 AI
        String systemPrompt = buildSystemPrompt(conv.getMode(), responseStyle);
        String aiContent = aiService.chatWithHistory(systemPrompt, history, userContent);

        // 4. 保存 AI 响应
        AIConversationMessage aiMsg = new AIConversationMessage();
        aiMsg.setId(generateId());
        aiMsg.setConversationId(conversationId);
        aiMsg.setRole("assistant");
        aiMsg.setContent(aiContent);
        aiMsg.setMessageType("TEXT");
        aiMsg.setTokensUsed(0);
        aiMsg.setSortOrder(conv.getMessageCount() + 1);
        aiMsg.setCreatedAt(now.plusSeconds(1));
        messageMapper.insert(aiMsg);

        // 5. 更新会话
        conversationMapper.incrementMessageCount(conversationId, now);

        return aiMsg;
    }

    // ============================================================
    // 推荐问题
    // ============================================================

    /**
     * 获取推荐问题
     */
    public List<String> getSuggestedQuestions(String mode) {
        Map<String, List<String>> questions = new HashMap<>();
        questions.put("NATAL", Arrays.asList(
                "我的星盘中最突出的特质是什么？",
                "我的事业方向适合什么类型？",
                "我的感情模式是怎样的？",
                "如何发挥我的天赋优势？"
        ));
        questions.put("SYNASTRY", Arrays.asList(
                "我们之间的契合点是什么？",
                "关系中需要注意的挑战？",
                "如何改善我们的沟通？",
                "长期发展趋势如何？"
        ));
        questions.put("PRASHNA", Arrays.asList(
                "这个决定的最佳时机是什么？",
                "我是否应该换工作？",
                "近期有重要的感情机会吗？",
                "如何提升当前运势？"
        ));
        questions.put("EMOTIONAL", Arrays.asList(
                "最近感到焦虑，星盘上有什么提示？",
                "如何应对当前的低谷期？",
                "我的情感需求是什么？",
                "如何建立内在安全感？"
        ));
        questions.put("KNOWLEDGE", Arrays.asList(
                "什么是 Shadbala 六力？",
                "如何理解九大行星的庙旺落陷？",
                "什么是大运（Dasha）？",
                "吠陀占星与西方占星有什么区别？"
        ));
        questions.put("REPORT_FOLLOWUP", Arrays.asList(
                "报告中的某个结论如何理解？",
                "某个建议具体如何操作？",
                "报告提到的趋势何时发生？"
        ));

        return questions.getOrDefault(mode, questions.get("NATAL"));
    }

    // ============================================================
    // 工具方法
    // ============================================================

    /**
     * 构建系统提示词
     */
    private String buildSystemPrompt(String mode, String responseStyle) {
        String basePrompt = SYSTEM_PROMPTS.getOrDefault(mode, SYSTEM_PROMPTS.get("NATAL"));
        String styleSuffix = STYLE_SUFFIX.getOrDefault(responseStyle, STYLE_SUFFIX.get("DIRECT"));
        return basePrompt + "\n\n" + styleSuffix;
    }

    /**
     * 构建历史消息
     */
    private List<AIService.AIMessage> buildHistory(String conversationId) {
        List<AIConversationMessage> messages = messageMapper.findByConversationId(conversationId);
        List<AIService.AIMessage> history = new ArrayList<>();
        for (AIConversationMessage msg : messages) {
            history.add(new AIService.AIMessage(msg.getRole(), msg.getContent()));
        }
        return history;
    }

    /**
     * 生成唯一ID
     */
    private String generateId() {
        return UUID.randomUUID().toString().replace("-", "");
    }
}
