package cn.dct.zpback.constant;

import java.util.Arrays;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 双人合盘基础互动常量
 *
 * 对应 skill：STEP SYN-CHART-LIB4｜156 Canonical Library Integration V1
 *
 * - Response Schema：1.1.2
 * - Content Library：syn-chart-basic-interaction-156-1.0.0
 * - Resolver：SYN_CHART_BASIC_INTERACTION_RESOLVER_V1.0.0
 * - Render Profile：SYN_CHART_BASIC_INTERACTION_RENDER_V1.1
 *
 * 计费边界：本层为基础互动解析，不得输出整体关系结论（合/不合、匹配度、谁更爱等）。
 */
public final class SynBasicConstants {

    private SynBasicConstants() {
    }

    // ===== 版本（必须参与 Response trace 与 cache key） =====
    public static final String SCHEMA_VERSION = "1.1.2";
    public static final String CONTENT_LIBRARY_VERSION = "syn-chart-basic-interaction-156-1.0.0";
    public static final String RESOLVER_VERSION = "SYN_CHART_BASIC_INTERACTION_RESOLVER_V1.0.0";
    public static final String RENDER_PROFILE_VERSION = "SYN_CHART_BASIC_INTERACTION_RENDER_V1.1";
    public static final String NAMING_DICTIONARY_VERSION = "1.0.0";

    // ===== 固定四行星（禁止运行时扩展 Sun/Jupiter/Saturn/ASC/Rahu/Ketu） =====
    public static final List<String> PLANETS = Collections.unmodifiableList(
            Arrays.asList("MOON", "VENUS", "MARS", "MERCURY"));

    /** 模块 key（响应 modules 字段） */
    public static final Map<String, String> MODULE_KEY_BY_PLANET;
    static {
        Map<String, String> m = new LinkedHashMap<>();
        m.put("MOON", "moon");
        m.put("VENUS", "venus");
        m.put("MARS", "mars");
        m.put("MERCURY", "mercury");
        MODULE_KEY_BY_PLANET = Collections.unmodifiableMap(m);
    }

    // ===== source_mode =====
    public static final String SOURCE_SIGN_NAKSHATRA = "SIGN_NAKSHATRA";
    public static final String SOURCE_SIGN_ONLY = "SIGN_ONLY";
    public static final String SOURCE_DATA_INSUFFICIENT = "DATA_INSUFFICIENT";

    // ===== degradation_reason =====
    public static final String DEG_NAK_DATA_MISSING = "NAKSHATRA_DATA_MISSING";
    public static final String DEG_NAK_LIBRARY_MISSING = "NAKSHATRA_LIBRARY_MISSING";
    public static final String DEG_LIBRARY_CONFLICT = "LIBRARY_CONFLICT";
    public static final String DEG_SIGN_DATA_MISSING = "SIGN_DATA_MISSING";
    public static final String DEG_SIGN_LIBRARY_MISSING = "SIGN_LIBRARY_MISSING";

    // ===== module_status =====
    public static final String MODULE_READY = "READY";
    public static final String MODULE_DEGRADED = "DEGRADED";
    public static final String MODULE_UNAVAILABLE = "UNAVAILABLE";
    public static final String MODULE_VALIDATION_FAILED = "VALIDATION_FAILED";

    // ===== viewer_perspective =====
    public static final String VIEWER_IS_A = "VIEWER_IS_A";
    public static final String VIEWER_IS_B = "VIEWER_IS_B";
    public static final String VIEWER_OBSERVER = "OBSERVER";

    // ===== 产品边界：禁止输出的整体关系结论关键词（Claim Firewall） =====
    public static final List<String> FORBIDDEN_OUTPUT_PATTERNS = Collections.unmodifiableList(Arrays.asList(
            "匹配度", "合不合", "不适合结婚", "谁更爱", "谁更主动", "灵魂伴侣", "命定", "业力",
            "复合", "分手", "出轨", "背叛", "长期稳定", "百分之", "%"));

    // ===== 模块 UI 文案（07_UI_BINDING §2，Canonical UI Copy） =====
    public static final Map<String, ModuleCopy> MODULE_COPY;
    static {
        Map<String, ModuleCopy> m = new LinkedHashMap<>();
        m.put("MOON", new ModuleCopy("月亮｜情绪需求与回应方式", "查看你们的情绪互动",
                "在情绪上更需要什么", "你们的情绪回应容易在哪里错位"));
        m.put("VENUS", new ModuleCopy("金星｜喜欢、示好与被打动的方式", "查看你们的喜欢方式",
                "怎样表达喜欢", "你们的示好方式容易怎样互动"));
        m.put("MARS", new ModuleCopy("火星｜行动节奏与冲突反应", "查看你们的行动与冲突模式",
                "通常怎样推进事情", "你们行动不同步时容易发生什么"));
        m.put("MERCURY", new ModuleCopy("水星｜沟通、理解与误会方式", "查看你们的沟通方式",
                "通常怎样表达和理解事情", "你们沟通时容易在哪里错位"));
        MODULE_COPY = Collections.unmodifiableMap(m);
    }

    /** 模块固定文案载体 */
    public static class ModuleCopy {
        public final String uiTitle;
        public final String entryLabel;
        public final String abSemanticTitle;
        public final String pairTitle;

        ModuleCopy(String uiTitle, String entryLabel, String abSemanticTitle, String pairTitle) {
            this.uiTitle = uiTitle;
            this.entryLabel = entryLabel;
            this.abSemanticTitle = abSemanticTitle;
            this.pairTitle = pairTitle;
        }
    }

    // ===== 行星中文名（Person Card 用） =====
    public static final Map<String, String> PLANET_LABEL_ZH;
    static {
        Map<String, String> m = new LinkedHashMap<>();
        m.put("MOON", "月亮");
        m.put("VENUS", "金星");
        m.put("MARS", "火星");
        m.put("MERCURY", "水星");
        PLANET_LABEL_ZH = Collections.unmodifiableMap(m);
    }

    /** 每个行星的「风险侧」slot：用于 Profile 末段说明失衡表现 */
    public static final Map<String, String> RISK_SLOT_BY_PLANET;
    static {
        Map<String, String> m = new LinkedHashMap<>();
        m.put("MOON", "stress_response");
        m.put("VENUS", "cooling_trigger");
        m.put("MARS", "friction_response");
        m.put("MERCURY", "misunderstanding_trigger");
        RISK_SLOT_BY_PLANET = Collections.unmodifiableMap(m);
    }

    /** 行星默认锚定 slot（当星宿 refinement 未覆盖任何 slot 时使用） */
    public static final Map<String, String> DEFAULT_ANCHOR_SLOT_BY_PLANET;
    static {
        Map<String, String> m = new LinkedHashMap<>();
        m.put("MOON", "reassurance_channel");
        m.put("VENUS", "appreciation_channel");
        m.put("MARS", "pace_preference");
        m.put("MERCURY", "information_focus");
        DEFAULT_ANCHOR_SLOT_BY_PLANET = Collections.unmodifiableMap(m);
    }

    /** Profile 开场视角前缀（把 slot 语义落到「你」） */
    public static final Map<String, String> PROFILE_OPENING_BY_PLANET;
    static {
        Map<String, String> m = new LinkedHashMap<>();
        m.put("MOON", "情绪上，");
        m.put("VENUS", "在喜欢与示好上，");
        m.put("MARS", "在行动与推进上，");
        m.put("MERCURY", "在沟通与理解上，");
        PROFILE_OPENING_BY_PLANET = Collections.unmodifiableMap(m);
    }

    // ===== 场景 → 现实情境句（Cross-Module Scene Rule 要求场景具体化） =====
    public static final Map<String, String> SCENE_SENTENCE;
    static {
        Map<String, String> m = new LinkedHashMap<>();
        m.put("EMOTIONAL_LOW", "比如一方情绪低落、状态明显不好的时候");
        m.put("POST_TENSION_RECONNECT", "比如不愉快之后，两个人准备重新说话的时候");
        m.put("RESPONSE_DELAY", "比如一方回复慢了、反应比平时淡的时候");
        m.put("NEED_FOR_SPACE", "比如一方需要先安静一会儿、暂时不想多聊的时候");
        m.put("INITIATING_CONTACT", "比如谁先开口联系、主动起话头的时候");
        m.put("INVITATION", "比如约见面、约时间的时候");
        m.put("APPRECIATION", "比如一方做了事、希望被注意到的时候");
        m.put("SMALL_GESTURE", "比如记住偏好、顺手做一件小事的时候");
        m.put("TIME_INVESTMENT", "比如安排固定时间、把精力稳定投进来的时候");
        m.put("TRAVEL_PLAN", "比如一起出行、安排行程的时候");
        m.put("SHARED_TASK", "比如一起做一件事、需要分工的时候");
        m.put("SCHEDULE_CHANGE", "比如计划临时变动的时候");
        m.put("URGENT_EXECUTION", "比如事情很急、需要马上决定的时候");
        m.put("OBSTACLE_HANDLING", "比如推进到一半卡住、遇到阻力的时候");
        m.put("PLAN_DISCUSSION", "比如一起讨论一件要做的事的时候");
        m.put("MESSAGE_AMBIGUITY", "比如一条消息写得比较简短的时候");
        m.put("EXPLAINING_INTENT", "比如一方需要解释自己本意的时候");
        m.put("RULE_OR_SCHEDULE", "比如涉及规则、约定和时间安排的时候");
        m.put("CLARIFICATION", "比如需要把一件事重新说清楚的时候");
        SCENE_SENTENCE = Collections.unmodifiableMap(m);
    }

    /** 兜底文案（UNAVAILABLE / VALIDATION_FAILED） */
    public static final String FALLBACK_MESSAGE =
            "这一部分暂时无法形成完整解读。可能缺少当前行星的星座或星宿数据，补充出生信息后可以重新生成。";

    /** Deep Report Bridge（07_UI_BINDING §7） */
    public static final String BRIDGE_TITLE = "想进一步了解你们的关系？";
    public static final String BRIDGE_DESCRIPTION =
            "月亮、金星、火星和水星，可以帮助你快速理解彼此在情绪、喜欢、行动与沟通上的基础互动。"
                    + "完整关系模式还需要结合双方星盘之间的双向作用、宫位触发及其他重要关系证据综合判断。";
    public static final String BRIDGE_CTA_LABEL = "查看双人关系深度报告";
    public static final String BRIDGE_ROUTE_KEY = "/pages/synastry/generate";

    /** Scope Note（07_UI_BINDING §6） */
    public static final String SCOPE_NOTE =
            "这是你们关系中的一个基础互动切面，并不代表完整关系结论。更全面的跨盘作用、宫位触发、"
                    + "关键关系模式与综合建议进入「双人关系 AI 深度报告」。";

    /** 规则资产（随包资源，运行时唯一事实源） */
    public static final String RES_CANONICAL_LIBRARY = "syn-basic-interaction/syn_chart_basic_interaction_156_canonical_v1.json";
    public static final String RES_NAMING_DICTIONARY = "syn-basic-interaction/canonical_naming_dictionary_v1.json";
    public static final String RES_LOOKUP_INDEX = "syn-basic-interaction/canonical_lookup_index_v1.json";
}
