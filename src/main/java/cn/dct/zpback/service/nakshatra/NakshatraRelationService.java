package cn.dct.zpback.service.nakshatra;

import cn.dct.zpback.constant.NakshatraRelationConstants;
import cn.dct.zpback.dto.nakshatra.NakshatraRegistries;
import cn.dct.zpback.dto.nakshatra.NakshatraRelationContent;
import cn.dct.zpback.entity.Chart;
import cn.dct.zpback.entity.User;
import cn.dct.zpback.mapper.UserMapper;
import cn.dct.zpback.service.ChartService;
import cn.dct.zpback.service.VedicAstroService;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.annotation.PostConstruct;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.core.io.ClassPathResource;
import org.springframework.stereotype.Service;

import java.io.InputStream;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.util.*;

/**
 * 星宿关系确定性引擎
 *
 * 对应 skill：首页｜星宿关系skill
 *
 * 唯一实现链路（docs/00 §2）：
 *   读取双方月亮本命星宿 → 计算 forward_offset → 读取关系与距离
 *   → 读取固定解析模板 → 组装 READY 响应
 *
 * 硬边界：
 * - 关系计算是确定性规则，不是 LLM 能力；
 * - 规则与文案一律读取随包资产，代码内不另写关系表；
 * - 缺规则或缺模板时返回稳定错误，绝不调用 AI 兜底（docs/02 §11）；
 * - 所有业胎组合距离恒为 NONE（docs/01 §4.5）。
 */
@Service
@Slf4j
@RequiredArgsConstructor
public class NakshatraRelationService {

    private final ObjectMapper objectMapper;
    private final ChartService chartService;
    private final UserMapper userMapper;
    private final VedicAstroService vedicAstroService;

    /** 27宿注册表（顺序即 index） */
    private List<NakshatraRegistries.NakshatraEntry> nakshatras = Collections.emptyList();
    /** offset → 关系规则 */
    private Map<Integer, NakshatraRegistries.OffsetRule> offsetRules = Collections.emptyMap();
    /** template_key → 固定文案 */
    private Map<String, NakshatraRegistries.CopyTemplate> copyTemplates = Collections.emptyMap();
    /** family_code → Family 解释（V2） */
    private Map<String, String> familyExplainers = Collections.emptyMap();
    /** family_code → 边界提示（V2，可为空值） */
    private Map<String, String> familyBoundaryNotes = Collections.emptyMap();
    /** 统一免责声明（V2 起由注册表顶层提供） */
    private String disclaimerShort = "";
    /** 归一化英文名 → 星宿条目（含排盘服务拼写别名） */
    private Map<String, NakshatraRegistries.NakshatraEntry> byNormalizedName = Collections.emptyMap();

    /**
     * 月亮星宿缓存：chartId|updatedAt → 星宿
     * 档案出生资料重新保存后 updatedAt 变化，key 自然失效（docs/02 §10）。
     */
    private final Map<String, NakshatraRegistries.NakshatraEntry> moonCache = new java.util.concurrent.ConcurrentHashMap<>();

    /** 英文拼写别名（排盘服务 VedicAstro ↔ 注册表，docs/01 §3.1 要求名称统一） */
    private static final Map<String, String> NAKSHATRA_ALIASES = new HashMap<>();
    /** 星座别名（含排盘服务常见拼写差异） */
    private static final Map<String, Integer> SIGN_INDEX = new HashMap<>();

    /** 坐标缺失时的中性占位值（月亮黄经与地点无关，仅用于满足排盘服务 Schema） */
    private static final double FALLBACK_LATITUDE = 1.0;
    private static final double FALLBACK_LONGITUDE = 1.0;

    static {
        NAKSHATRA_ALIASES.put("ashwini", "aswini");
        NAKSHATRA_ALIASES.put("punarvasu", "purnavasu");
        NAKSHATRA_ALIASES.put("jyeshta", "jyeshtha");
        NAKSHATRA_ALIASES.put("shatabisha", "shatabhisha");
        NAKSHATRA_ALIASES.put("purvaphalguni", "purvaphalguni");
        NAKSHATRA_ALIASES.put("uttaraphalguni", "uttaraphalguni");

        String[] signs = {"aries", "taurus", "gemini", "cancer", "leo", "virgo",
                "libra", "scorpio", "sagittarius", "capricorn", "aquarius", "pisces"};
        for (int i = 0; i < signs.length; i++) {
            SIGN_INDEX.put(signs[i], i);
        }
        SIGN_INDEX.put("saggitarius", 8);
        SIGN_INDEX.put("sagittari", 8);
        SIGN_INDEX.put("capricornus", 9);
    }

    // ============================================================
    // 规则资产加载
    // ============================================================

    @PostConstruct
    public void loadRegistries() {
        try {
            NakshatraRegistries.NakshatraRegistry nakReg = readJson(
                    NakshatraRelationConstants.RES_NAKSHATRA_REGISTRY,
                    NakshatraRegistries.NakshatraRegistry.class);
            NakshatraRegistries.OffsetRegistry offsetReg = readJson(
                    NakshatraRelationConstants.RES_OFFSET_REGISTRY,
                    NakshatraRegistries.OffsetRegistry.class);
            NakshatraRegistries.CopyRegistry copyReg = readJson(
                    NakshatraRelationConstants.RES_COPY_REGISTRY,
                    NakshatraRegistries.CopyRegistry.class);

            if (nakReg == null || nakReg.getEntries() == null
                    || nakReg.getEntries().size() != NakshatraRelationConstants.NAKSHATRA_COUNT) {
                throw new IllegalStateException("nakshatra_registry 条目数异常");
            }
            if (offsetReg == null || offsetReg.getEntries() == null
                    || offsetReg.getEntries().size() != NakshatraRelationConstants.NAKSHATRA_COUNT) {
                throw new IllegalStateException("relationship_offset_registry 条目数异常");
            }
            if (copyReg == null || copyReg.getEntries() == null
                    || copyReg.getEntries().size() != NakshatraRelationConstants.NAKSHATRA_COUNT) {
                throw new IllegalStateException("copy_registry 条目数异常");
            }

            nakshatras = nakReg.getEntries();

            Map<Integer, NakshatraRegistries.OffsetRule> rules = new HashMap<>();
            for (NakshatraRegistries.OffsetRule r : offsetReg.getEntries()) {
                rules.put(r.getForward_offset(), r);
            }
            offsetRules = Collections.unmodifiableMap(rules);

            Map<String, NakshatraRegistries.CopyTemplate> copies = new HashMap<>();
            for (NakshatraRegistries.CopyTemplate t : copyReg.getEntries()) {
                copies.put(t.getTemplate_key(), t);
            }
            copyTemplates = Collections.unmodifiableMap(copies);

            // V2：Family 解释 / 边界提示 / 统一免责声明必须齐备，缺失即阻断
            if (copyReg.getFamily_explainers() == null || copyReg.getFamily_explainers().isEmpty()) {
                throw new IllegalStateException("copy_registry 缺少 family_explainers");
            }
            if (copyReg.getDisclaimer_short() == null || copyReg.getDisclaimer_short().isEmpty()) {
                throw new IllegalStateException("copy_registry 缺少 disclaimer_short");
            }
            familyExplainers = Collections.unmodifiableMap(new HashMap<>(copyReg.getFamily_explainers()));
            familyBoundaryNotes = copyReg.getFamily_boundary_notes() == null
                    ? Collections.emptyMap()
                    : Collections.unmodifiableMap(new HashMap<>(copyReg.getFamily_boundary_notes()));
            disclaimerShort = copyReg.getDisclaimer_short();

            Map<String, NakshatraRegistries.NakshatraEntry> byName = new HashMap<>();
            for (NakshatraRegistries.NakshatraEntry e : nakshatras) {
                byName.put(normalize(e.getEnglish()), e);
            }
            byNormalizedName = Collections.unmodifiableMap(byName);

            log.info("星宿关系规则资产加载完成: nakshatra={}, offset={}, copy={}, familyExplainers={}, calculation={}, contract={}, content={}",
                    nakshatras.size(), offsetRules.size(), copyTemplates.size(), familyExplainers.size(),
                    offsetReg.getCalculation_version(), copyReg.getContract_version(), copyReg.getContent_version());
        } catch (Exception e) {
            // 规则资产缺失属于 P0 阻断，启动即失败，不允许带病上线
            throw new IllegalStateException("星宿关系规则资产加载失败: " + e.getMessage(), e);
        }
    }

    private <T> T readJson(String path, Class<T> type) throws Exception {
        try (InputStream in = new ClassPathResource(path).getInputStream()) {
            return objectMapper.readValue(in, type);
        }
    }

    // ============================================================
    // 解析入口
    // ============================================================

    /**
     * 解析双人星宿关系
     *
     * @return resolve_response.schema.json 规定的 READY 响应
     */
    public Map<String, Object> resolve(String userId, String profileAId, String profileBId,
                                       String locale, String contractVersion) {

        // 1. 版本合同
        if (contractVersion != null && !contractVersion.isBlank()
                && !NakshatraRelationConstants.CONTRACT_VERSION.equals(contractVersion)) {
            throw new NakshatraRelationException(NakshatraRelationConstants.ERR_VERSION_NOT_SUPPORTED, 409,
                    "当前版本不受支持，请升级后重试。", false);
        }
        if (locale != null && !locale.isBlank()
                && !NakshatraRelationConstants.LOCALE_ZH_CN.equals(locale)) {
            throw new NakshatraRelationException(NakshatraRelationConstants.ERR_VERSION_NOT_SUPPORTED, 409,
                    "当前版本不受支持，请升级后重试。", false);
        }

        // 2. 入参
        if (isBlank(profileAId) || isBlank(profileBId)) {
            throw new NakshatraRelationException(NakshatraRelationConstants.ERR_PROFILE_NOT_FOUND, 404,
                    "未找到该星盘档案，请重新选择。", false);
        }
        if (profileAId.equals(profileBId)) {
            throw new NakshatraRelationException(NakshatraRelationConstants.ERR_SAME_PROFILE, 400,
                    "两位不能选择同一份星盘档案。", false);
        }

        // 3. 读取两份档案（含归属校验，防 IDOR）
        Chart chartA = loadChart(userId, profileAId);
        Chart chartB = loadChart(userId, profileBId);

        // 4. 双方月亮本命星宿
        NakshatraRegistries.NakshatraEntry nakA = resolveMoonNakshatra(chartA);
        NakshatraRegistries.NakshatraEntry nakB = resolveMoonNakshatra(chartB);
        if (nakA == null || nakB == null) {
            throw new NakshatraRelationException(NakshatraRelationConstants.ERR_NAKSHATRA_UNAVAILABLE, 422,
                    "暂时无法读取其中一份档案的本命星宿，请检查出生资料。", false);
        }

        // 5. forward_offset = (target_index - base_index + 27) % 27
        int offset = Math.floorMod(nakB.getIndex() - nakA.getIndex(), NakshatraRelationConstants.NAKSHATRA_COUNT);
        NakshatraRegistries.OffsetRule rule = offsetRules.get(offset);
        if (rule == null) {
            log.error("星宿关系规则缺失: offset={}", offset);
            throw new NakshatraRelationException(NakshatraRelationConstants.ERR_RELATION_RULE_NOT_FOUND, 500,
                    "关系规则缺失，请稍后重试。", false);
        }

        // 6. 业胎覆盖：distance_code 强制 NONE（P0 硬规则）
        String distanceCode = rule.getDistance_code();
        String distanceLabel = rule.getDistance_label();
        if (NakshatraRelationConstants.FAMILY_KARMA_WOMB.equals(rule.getFamily_code())) {
            distanceCode = NakshatraRelationConstants.DISTANCE_NONE;
            distanceLabel = "无距离";
        }

        // 7. 固定文案
        NakshatraRegistries.CopyTemplate template = copyTemplates.get(rule.getTemplate_key());
        if (template == null || template.getContent() == null) {
            log.error("星宿关系固定文案缺失: template_key={}", rule.getTemplate_key());
            throw new NakshatraRelationException(NakshatraRelationConstants.ERR_CONTENT_NOT_FOUND, 500,
                    "解析内容暂时不可用，请重试。", true);
        }

        // 8. 组装响应
        return buildResponse(userId, chartA, chartB, nakA, nakB, offset, rule, distanceCode, distanceLabel, template);
    }

    private Map<String, Object> buildResponse(String userId, Chart chartA, Chart chartB,
                                              NakshatraRegistries.NakshatraEntry nakA,
                                              NakshatraRegistries.NakshatraEntry nakB,
                                              int offset,
                                              NakshatraRegistries.OffsetRule rule,
                                              String distanceCode, String distanceLabel,
                                              NakshatraRegistries.CopyTemplate template) {

        NakshatraRegistries.CopyContent c = template.getContent();
        String nameA = chartA.getName() == null ? "" : chartA.getName();
        String nameB = chartB.getName() == null ? "" : chartB.getName();
        boolean mutual = NakshatraRelationConstants.INFLUENCE_MUTUAL.equals(template.getInfluence_mode());
        String roleA = rule.getRole_a_label();
        String roleB = rule.getRole_b_label();

        // Family 解释（V2）：缺失即阻断，不允许运行时用 LLM 兜底
        String familyExplainer = familyExplainers.get(rule.getFamily_code());
        if (familyExplainer == null || familyExplainer.isEmpty()) {
            log.error("星宿关系 Family 解释缺失: family_code={}", rule.getFamily_code());
            throw new NakshatraRelationException(NakshatraRelationConstants.ERR_CONTENT_NOT_FOUND, 500,
                    "解析内容暂时不可用，请重试。", true);
        }

        Map<String, Object> resp = new LinkedHashMap<>();
        resp.put("request_id", "nr_" + UUID.randomUUID().toString().replace("-", ""));
        resp.put("status", NakshatraRelationConstants.STATUS_READY);
        resp.put("contract_version", NakshatraRelationConstants.CONTRACT_VERSION);
        resp.put("calculation_version", NakshatraRelationConstants.CALCULATION_VERSION);
        resp.put("content_version", NakshatraRelationConstants.CONTENT_VERSION);
        resp.put("person_a", buildPerson(chartA, nakA, rule.getRole_a_code(), roleA));
        resp.put("person_b", buildPerson(chartB, nakB, rule.getRole_b_code(), roleB));

        Map<String, Object> relationship = new LinkedHashMap<>();
        relationship.put("forward_offset", offset);
        relationship.put("family_code", rule.getFamily_code());
        relationship.put("family_label", rule.getFamily_label());
        relationship.put("distance_code", distanceCode);
        relationship.put("distance_label", distanceLabel);
        relationship.put("role_a_code", rule.getRole_a_code());
        relationship.put("role_a_label", roleA);
        relationship.put("role_b_code", rule.getRole_b_code());
        relationship.put("role_b_label", roleB);
        relationship.put("relation_display_name", rule.getRelation_display_name());
        relationship.put("template_key", rule.getTemplate_key());
        resp.put("relationship", relationship);

        // ===== 内容装配（V2）：所有正文占位符在此完成绑定，前端只做纯文本渲染 =====
        NakshatraRelationContent content = new NakshatraRelationContent();
        content.setContentMode(NakshatraRelationConstants.CONTENT_MODE);
        content.setInfluenceMode(template.getInfluence_mode());
        // 角色映射：命之星同角色不重复标注
        content.setRoleMappingText(mutual
                ? String.format(NakshatraRelationConstants.ROLE_MAPPING_MUTUAL_FORMAT, nameA, nameB)
                : String.format(NakshatraRelationConstants.ROLE_MAPPING_DIRECTIONAL_FORMAT, nameA, roleA, nameB, roleB));
        content.setFamilyExplainer(bindNames(familyExplainer, nameA, nameB));
        content.setSummary(bindNames(c.getSummary(), nameA, nameB));
        content.setRelationshipTone(bindNames(c.getRelationship_tone(), nameA, nameB));
        content.setRelationshipKey(bindNames(c.getRelationship_key(), nameA, nameB));
        content.setFamilyBoundaryNote(bindNames(
                familyBoundaryNotes.get(rule.getFamily_code()), nameA, nameB));
        content.setDisclaimerShort(disclaimerShort);

        if (mutual) {
            // 命之星：不输出 A→B / B→A，只输出「默契」与「一起卡住」两个模块
            content.setAToBTitle(null);
            content.setAToB(null);
            content.setBToATitle(null);
            content.setBToA(null);
            content.setMutualResonanceTitle(NakshatraRelationConstants.TITLE_MUTUAL_RESONANCE);
            content.setMutualResonance(bindNames(c.getMutual_resonance(), nameA, nameB));
            content.setMutualBlindspotTitle(NakshatraRelationConstants.TITLE_MUTUAL_BLINDSPOT);
            content.setMutualBlindspot(bindNames(c.getMutual_blindspot(), nameA, nameB));
        } else {
            // 方向关系：标题必须直接显示姓名 + 角色，不用箭头替代
            content.setAToBTitle(String.format(NakshatraRelationConstants.TITLE_DIRECTIONAL_FORMAT,
                    nameA, roleA, nameB, roleB));
            content.setAToB(bindNames(c.getA_to_b(), nameA, nameB));
            content.setBToATitle(String.format(NakshatraRelationConstants.TITLE_DIRECTIONAL_FORMAT,
                    nameB, roleB, nameA, roleA));
            content.setBToA(bindNames(c.getB_to_a(), nameA, nameB));
            content.setMutualResonanceTitle(null);
            content.setMutualResonance(null);
            content.setMutualBlindspotTitle(null);
            content.setMutualBlindspot(null);
        }
        resp.put("content", content);

        Map<String, Object> handoff = new LinkedHashMap<>();
        handoff.put("target_mode", NakshatraRelationConstants.HANDOFF_TARGET_MODE);
        handoff.put("source", NakshatraRelationConstants.HANDOFF_SOURCE);
        handoff.put("profile_a_id", chartA.getChartId());
        handoff.put("profile_b_id", chartB.getChartId());
        Map<String, Object> ctx = new LinkedHashMap<>();
        ctx.put("family_code", rule.getFamily_code());
        ctx.put("distance_code", distanceCode);
        ctx.put("role_a_code", rule.getRole_a_code());
        ctx.put("role_b_code", rule.getRole_b_code());
        handoff.put("nakshatra_relation_context", ctx);
        handoff.put("remaining_count", readRemainingCount(userId));
        handoff.put("suggested_questions", Arrays.asList(NakshatraRelationConstants.SUGGESTED_QUESTIONS));
        resp.put("ai_handoff", handoff);

        resp.put("generated_at", OffsetDateTime.now(ZoneOffset.UTC)
                .format(DateTimeFormatter.ofPattern("yyyy-MM-dd'T'HH:mm:ss.SSSSSSxxx")));
        return resp;
    }

    /**
     * 姓名占位符绑定（docs/00 §4）
     *
     * 只做白名单字符串替换，不使用 eval / MessageFormat / HTML 解析；
     * 昵称中若含 &lt; &gt; {{ }} 等字符按纯文本原样保留，前端负责普通文本渲染。
     * 校验发生在替换前：模板里除 {{name_a}} / {{name_b}} 外不允许出现其它占位符，
     * 否则视为文案缺陷直接阻断，避免脏占位符上线。
     */
    private String bindNames(String text, String nameA, String nameB) {
        if (text == null) {
            return null;
        }
        String stripped = text
                .replace(NakshatraRelationConstants.PLACEHOLDER_NAME_A, "")
                .replace(NakshatraRelationConstants.PLACEHOLDER_NAME_B, "");
        if (stripped.contains("{{") || stripped.contains("}}")) {
            log.error("星宿关系文案存在未知占位符: {}", text.substring(0, Math.min(60, text.length())));
            throw new NakshatraRelationException(NakshatraRelationConstants.ERR_CONTENT_NOT_FOUND, 500,
                    "解析内容暂时不可用，请重试。", true);
        }
        return text
                .replace(NakshatraRelationConstants.PLACEHOLDER_NAME_A, nameA)
                .replace(NakshatraRelationConstants.PLACEHOLDER_NAME_B, nameB);
    }

    private Map<String, Object> buildPerson(Chart chart, NakshatraRegistries.NakshatraEntry nak,
                                            String roleCode, String roleLabel) {
        Map<String, Object> p = new LinkedHashMap<>();
        p.put("profile_id", chart.getChartId());
        p.put("display_name", chart.getName());
        p.put("avatar_url", null);
        p.put("nakshatra_id", nak.getNakshatra_id());
        p.put("nakshatra_english", nak.getEnglish());
        p.put("nakshatra_chinese", nak.getChinese());
        p.put("pada", null);
        p.put("role_code", roleCode);
        p.put("role_label", roleLabel);
        return p;
    }

    /**
     * 读取统一额度（docs/05 §9：星宿关系服务不得独立计算会员次数）
     * 用户不存在或未登录时按 0 处理，前端会展示"今日免费次数已用完"。
     */
    private int readRemainingCount(String userId) {
        try {
            User user = userMapper.selectById(userId);
            if (user == null) {
                return 0;
            }
            int daily = user.getDailyFreeRemaining() == null ? 0 : user.getDailyFreeRemaining();
            int extra = user.getExtraPackRemaining() == null ? 0 : user.getExtraPackRemaining();
            return daily + extra;
        } catch (Exception e) {
            log.warn("读取额度失败，按 0 返回: userId={}, err={}", userId, e.getMessage());
            return 0;
        }
    }

    // ============================================================
    // 档案与月亮星宿
    // ============================================================

    private Chart loadChart(String userId, String chartId) {
        Chart chart = chartService.getChartById(chartId);
        if (chart == null) {
            throw new NakshatraRelationException(NakshatraRelationConstants.ERR_PROFILE_NOT_FOUND, 404,
                    "未找到该星盘档案，请重新选择。", false);
        }
        // 归属校验：无权档案不得泄露昵称、头像和星宿
        if (!Objects.equals(chart.getUserId(), userId)) {
            throw new NakshatraRelationException(NakshatraRelationConstants.ERR_PROFILE_NOT_FOUND, 404,
                    "未找到该星盘档案，请重新选择。", false);
        }
        return chart;
    }

    /**
     * 取档案的 D1 月亮本命星宿
     *
     * 优先使用排盘服务已返回并校验的 nakshatra（来自档案 chartData）；
     * 缺失时调用排盘服务的轻量分盘接口（get_varga_charts，仅 D1，约 9KB），
     * 而不是 200KB+ 的全量接口。
     * 结果按 chartId|updatedAt 缓存，避免每次解析都重新计算。
     */
    private NakshatraRegistries.NakshatraEntry resolveMoonNakshatra(Chart chart) {
        String cacheKey = chart.getChartId() + "|" + chart.getUpdatedAt();
        NakshatraRegistries.NakshatraEntry cached = moonCache.get(cacheKey);
        if (cached != null) {
            return cached;
        }

        Map<String, Object> data = readChartData(chart);
        if (data == null) {
            // 月亮本命星宿只取决于出生时刻（地心黄经），与出生地点无关；
            // 但排盘服务 Schema 强制要求经纬度，因此坐标缺失时用一个中性占位值，
            // 保证星宿结果仍然正确（星座/宫位不受影响，本功能不使用）。
            if (isBlank(chart.getBirthDate()) || isBlank(chart.getBirthTime())) {
                log.warn("档案缺少出生日期或时间，无法计算本命星宿: chartId={}, name={}",
                        chart.getChartId(), chart.getName());
                return null;
            }
            if (chart.getLatitude() == null || chart.getLongitude() == null) {
                log.warn("档案缺少出生地点坐标，使用中性占位坐标计算月亮星宿（月亮黄经与地点无关）: chartId={}, name={}",
                        chart.getChartId(), chart.getName());
            }
            try {
                data = vedicAstroService.getVargaCharts(buildBirthParams(chart));
            } catch (Exception e) {
                log.warn("排盘服务取月亮星宿失败: chartId={}, err={}", chart.getChartId(), e.getMessage());
                return null;
            }
        }

        NakshatraRegistries.NakshatraEntry nak = extractMoon(data);
        if (nak != null) {
            moonCache.put(cacheKey, nak);
        }
        return nak;
    }

    @SuppressWarnings("unchecked")
    private Map<String, Object> readChartData(Chart chart) {
        String json = chart.getChartData();
        if (json == null || json.isBlank()) {
            return null;
        }
        try {
            return objectMapper.readValue(json, Map.class);
        } catch (Exception e) {
            log.warn("档案 chartData 解析失败，将回退到排盘服务: chartId={}", chart.getChartId());
            return null;
        }
    }

    private Map<String, Object> buildBirthParams(Chart chart) {
        Map<String, Object> params = new LinkedHashMap<>();
        params.put("name", chart.getName() == null ? "User" : chart.getName());
        params.put("gender", chart.getGender() == null ? "male" : chart.getGender());

        String[] d = splitDate(chart.getBirthDate());
        String[] t = splitTime(chart.getBirthTime());
        // 排盘服务 Schema：year/month/day/hour/minute 为 integer，必须传数字而非字符串
        params.put("year", toInt(d[0], 1990));
        params.put("month", toInt(d[1], 1));
        params.put("day", toInt(d[2], 1));
        params.put("hour", toInt(t[0], 12));
        params.put("minute", toInt(t[1], 0));
        params.put("second", 0);
        params.put("place", chart.getBirthPlace() == null ? "Unknown" : chart.getBirthPlace());
        // 月亮黄经与出生地点无关，坐标缺失时用中性占位值（排盘服务 Schema 要求非空）
        params.put("latitude", chart.getLatitude() == null ? FALLBACK_LATITUDE : chart.getLatitude());
        params.put("longitude", chart.getLongitude() == null ? FALLBACK_LONGITUDE : chart.getLongitude());
        params.put("timezone", "+8.0");
        params.put("ayanamsa", "Lahiri");
        params.put("house_system", "Whole Sign");
        // 只需 D1 即可取月亮本命星宿，避免拉取全部分盘
        params.put("charts", Collections.singletonList("D1"));
        return params;
    }

    @SuppressWarnings("unchecked")
    private NakshatraRegistries.NakshatraEntry extractMoon(Map<String, Object> data) {
        if (data == null) {
            return null;
        }
        Map<String, Object> d1 = extractD1(data);
        Object planetsObj = d1.get("planets");
        if (!(planetsObj instanceof Map)) {
            return null;
        }
        Object moonObj = ((Map<String, Object>) planetsObj).get("Moon");
        if (!(moonObj instanceof Map)) {
            return null;
        }
        Map<String, Object> moon = (Map<String, Object>) moonObj;

        // 优先：排盘服务返回的 nakshatra 名称
        Object nakName = moon.get("nakshatra");
        if (nakName instanceof String && !((String) nakName).isBlank()) {
            NakshatraRegistries.NakshatraEntry hit = byNormalizedName.get(normalize((String) nakName));
            if (hit != null) {
                return hit;
            }
            log.warn("未知星宿名称，回退到黄经计算: {}", nakName);
        }

        // 回退：sign + 度数 → 黄经 → 宿
        Object signObj = moon.get("sign");
        Double degInSign = decDeg(moon);
        if (signObj instanceof String && degInSign != null) {
            Integer signIdx = SIGN_INDEX.get(normalize((String) signObj));
            if (signIdx != null) {
                double abs = signIdx * 30.0 + degInSign;
                int arcmin = (int) Math.floor(abs * 60.0);
                int index = Math.min(NakshatraRelationConstants.NAKSHATRA_COUNT,
                        arcmin / NakshatraRelationConstants.ARCMINUTES_PER_NAKSHATRA + 1);
                return nakshatras.get(index - 1);
            }
        }
        return null;
    }

    /**
     * 从排盘服务响应中取出 D1 层，兼容三种结构：
     * - get_varga_charts:    { "vargas": { "D1": {...} } }
     * - get_jyotishya_data:  { "D1": {...} }
     * - 已平铺的 D1 本体:     { "planets": {...} }
     */
    @SuppressWarnings("unchecked")
    private Map<String, Object> extractD1(Map<String, Object> data) {
        Object vargasObj = data.get("vargas");
        if (vargasObj instanceof Map) {
            Map<String, Object> vargas = (Map<String, Object>) vargasObj;
            Object vd1 = vargas.containsKey("D1") ? vargas.get("D1") : vargas.get("d1");
            if (vd1 instanceof Map) {
                return (Map<String, Object>) vd1;
            }
        }
        Object d1Obj = data.containsKey("D1") ? data.get("D1") : data.get("d1");
        if (d1Obj instanceof Map) {
            return (Map<String, Object>) d1Obj;
        }
        return data;
    }

    @SuppressWarnings("unchecked")
    private Double decDeg(Map<String, Object> moon) {        Object posObj = moon.get("pos");
        if (posObj instanceof Map) {
            Map<String, Object> pos = (Map<String, Object>) posObj;
            Object dec = pos.get("dec_deg");
            if (dec instanceof Number) {
                return ((Number) dec).doubleValue();
            }
            double deg = num(pos.get("deg"));
            double min = num(pos.get("min"));
            double sec = num(pos.get("sec"));
            if (deg != 0 || min != 0 || sec != 0) {
                return deg + min / 60.0 + sec / 3600.0;
            }
        }
        Object dec = moon.get("dec_deg");
        return dec instanceof Number ? ((Number) dec).doubleValue() : null;
    }

    // ============================================================
    // 工具
    // ============================================================

    private static boolean isBlank(String s) {
        return s == null || s.isBlank();
    }

    /** 归一化：小写 + 去除空格/连字符/下划线/点 */
    private static String normalize(String s) {
        if (s == null) {
            return "";
        }
        String n = s.toLowerCase(Locale.ROOT).replaceAll("[\\s\\-_.'\u2019]", "");
        String alias = NAKSHATRA_ALIASES.get(n);
        return alias != null ? alias : n;
    }

    private static double num(Object o) {
        return o instanceof Number ? ((Number) o).doubleValue() : 0d;
    }

    private static int toInt(String s, int fallback) {
        try {
            return Integer.parseInt(s.trim());
        } catch (Exception e) {
            return fallback;
        }
    }

    private static String[] splitDate(String birthDate) {
        if (birthDate != null && birthDate.contains("-")) {
            String[] parts = birthDate.trim().split("-");
            if (parts.length >= 3) {
                return new String[]{parts[0], parts[1], parts[2]};
            }
        }
        return new String[]{"1990", "1", "1"};
    }

    private static String[] splitTime(String birthTime) {
        if (birthTime != null && birthTime.contains(":")) {
            String[] parts = birthTime.trim().split(":");
            if (parts.length >= 2) {
                return new String[]{parts[0], parts[1]};
            }
        }
        return new String[]{"12", "0"};
    }
}
