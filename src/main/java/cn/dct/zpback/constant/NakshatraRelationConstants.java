package cn.dct.zpback.constant;

/**
 * 星宿关系常量
 *
 * 对应 skill：首页｜星宿关系skill（docs/05 API字段枚举错误码与版本合同）
 *
 * - Contract Version：2.0.0（字段或条件结构改变时提升）
 * - Calculation Version：1.0.0（星宿/offset/角色/距离规则改变时提升）
 * - Content Version：zh-CN_2.0.0（固定解析文案改变时提升）
 *
 * 计费边界（docs/05 §13）：本接口免费，不创建 AI Request，不扣任何星灵AI额度。
 */
public final class NakshatraRelationConstants {

    private NakshatraRelationConstants() {}

    // ===== 版本 =====
    public static final String CONTRACT_VERSION = "2.0.0";
    public static final String CALCULATION_VERSION = "1.0.0";
    public static final String CONTENT_VERSION = "zh-CN_2.0.0";
    public static final String LOCALE_ZH_CN = "zh-CN";

    // ===== 响应状态 =====
    public static final String STATUS_READY = "READY";
    public static final String STATUS_ERROR = "ERROR";

    /** 内容运行策略：固定文案注册表，运行时不调用 LLM */
    public static final String CONTENT_MODE = "PREGENERATED_COPY_REGISTRY";

    // ===== 错误码（docs/05 §10） =====
    public static final String ERR_PROFILE_NOT_FOUND = "NR_PROFILE_NOT_FOUND";
    public static final String ERR_SAME_PROFILE = "NR_SAME_PROFILE";
    public static final String ERR_NAKSHATRA_UNAVAILABLE = "NR_NAKSHATRA_UNAVAILABLE";
    public static final String ERR_RELATION_RULE_NOT_FOUND = "NR_RELATION_RULE_NOT_FOUND";
    public static final String ERR_CONTENT_NOT_FOUND = "NR_CONTENT_NOT_FOUND";
    public static final String ERR_VERSION_NOT_SUPPORTED = "NR_VERSION_NOT_SUPPORTED";
    public static final String ERR_INTERNAL_ERROR = "NR_INTERNAL_ERROR";

    // ===== 关系族 =====
    public static final String FAMILY_SAME = "SAME";
    public static final String FAMILY_KARMA_WOMB = "KARMA_WOMB";

    // ===== 距离 =====
    public static final String DISTANCE_NONE = "NONE";

    // ===== 影响模式 =====
    public static final String INFLUENCE_DIRECTIONAL = "DIRECTIONAL";
    public static final String INFLUENCE_MUTUAL = "MUTUAL";

    // ===== V2 标题固定文案（docs/01 页面槽位、renderer-policy） =====
    /** 命之星：默契模块标题 */
    public static final String TITLE_MUTUAL_RESONANCE = "你们容易产生默契的地方";
    /** 命之星：一起卡住模块标题 */
    public static final String TITLE_MUTUAL_BLINDSPOT = "你们容易一起卡住的地方";
    /** 方向标题模板：{A}（{roleA}）对 {B}（{roleB}）的影响 */
    public static final String TITLE_DIRECTIONAL_FORMAT = "%s（%s）对 %s（%s）的影响";
    /** 方向角色映射模板：{A}（{roleA}） × {B}（{roleB}） */
    public static final String ROLE_MAPPING_DIRECTIONAL_FORMAT = "%s（%s） × %s（%s）";
    /** 命之星角色映射模板（同角色不重复标注）：{A} × {B} */
    public static final String ROLE_MAPPING_MUTUAL_FORMAT = "%s × %s";

    // ===== 姓名占位符（docs/00 §4：Runtime 仅替换这两个） =====
    public static final String PLACEHOLDER_NAME_A = "{{name_a}}";
    public static final String PLACEHOLDER_NAME_B = "{{name_b}}";

    // ===== 星灵AI承接 =====
    public static final String HANDOFF_TARGET_MODE = "SYNASTRY_AI";
    public static final String HANDOFF_SOURCE = "NAKSHATRA_RELATIONSHIP";

    // ===== 规则资产（随包资源，运行时唯一事实源） =====
    public static final String RES_NAKSHATRA_REGISTRY = "nakshatra-relationship/nakshatra_registry.json";
    public static final String RES_OFFSET_REGISTRY = "nakshatra-relationship/relationship_offset_registry.json";
    public static final String RES_COPY_REGISTRY = "nakshatra-relationship/copy_registry_zh-CN.json";

    // ===== 星宿计算 =====
    /** 27宿，不含 Abhijit */
    public static final int NAKSHATRA_COUNT = 27;
    /** 每宿 13°20′ = 800 角分 */
    public static final int ARCMINUTES_PER_NAKSHATRA = 800;
    /** 全黄道角分总数 27 × 800 */
    public static final int TOTAL_ARCMINUTES = 21600;

    /** 推荐问题（docs/04 §5） */
    public static final String[] SUGGESTED_QUESTIONS = {
            "我们彼此最容易被什么吸引？",
            "我们发生矛盾时应该怎么沟通？",
            "这段关系长期发展最需要注意什么？"
    };
}
