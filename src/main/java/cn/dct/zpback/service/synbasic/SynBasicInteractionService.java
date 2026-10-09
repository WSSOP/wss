package cn.dct.zpback.service.synbasic;

import cn.dct.zpback.constant.SynBasicConstants;
import cn.dct.zpback.engine.synbasic.PairComparator;
import cn.dct.zpback.engine.synbasic.PersonDomainResolver;
import cn.dct.zpback.engine.synbasic.SynBasicAssets;
import cn.dct.zpback.engine.synbasic.SynBasicRenderer;
import cn.dct.zpback.engine.synbasic.SynBasicValidator;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 双人合盘基础互动解析（156 Canonical Library Runtime）
 *
 * 固定 pipeline（02_RESOLVER_COMPILER_BINDING §2）：
 *   Chart Placement → Normalizer → 作用域 Lookup → Person Domain Resolver
 *   → Resolved Semantic Slots → Profile Renderer → A/B Profile
 *   → Pair Comparator → Pair Renderer → Module Validator → Four-Module Cross QA
 *   → Response Schema 1.1.2
 *
 * Pair Comparator 不重新读取星盘；本层不得输出整体关系结论。
 */
@Service
@Slf4j
public class SynBasicInteractionService {

    @Autowired
    private SynBasicAssets assets;
    @Autowired
    private PersonDomainResolver resolver;
    @Autowired
    private PairComparator comparator;
    @Autowired
    private SynBasicValidator validator;

    /**
     * 生成四模块基础互动解析
     *
     * @param personA A 侧 { subject_id, display_name, moon, venus, mars, mercury }
     *                每个行星为 { sign, nakshatra }（stable ID 或 label 均可）
     * @param personB B 侧，结构同上
     * @param relationshipType 可选语境修饰器，可为 null（不得自动恋爱化）
     * @param viewerPerspective VIEWER_IS_A / VIEWER_IS_B / OBSERVER
     */
    public Map<String, Object> resolve(Map<String, Object> personA, Map<String, Object> personB,
                                       String relationshipType, String viewerPerspective) {
        String viewer = normalizeViewer(viewerPerspective);
        String nameA = displayName(personA, "A");
        String nameB = displayName(personB, "B");

        Map<String, Object> resp = new LinkedHashMap<>();
        resp.put("schema_version", SynBasicConstants.SCHEMA_VERSION);
        resp.put("content_library_version", assets.contentLibraryVersion());
        resp.put("render_profile_version", SynBasicConstants.RENDER_PROFILE_VERSION);

        Map<String, Object> pair = new LinkedHashMap<>();
        pair.put("person_a", personRef(personA, nameA));
        pair.put("person_b", personRef(personB, nameB));
        pair.put("relationship_type", normalizeRelationshipType(relationshipType));
        resp.put("pair", pair);
        resp.put("viewer_perspective", viewer);

        Map<String, Object> modules = new LinkedHashMap<>();
        Map<String, String> sceneByModule = new LinkedHashMap<>();
        for (String planet : SynBasicConstants.PLANETS) {
            String moduleKey = SynBasicConstants.MODULE_KEY_BY_PLANET.get(planet);
            Map<String, Object> module = buildModule(planet, personA, personB, viewer, nameA, nameB);
            modules.put(moduleKey, module);
            Object scene = ((Map<?, ?>) module.get("pair_interaction") == null)
                    ? null : ((Map<?, ?>) module.get("pair_interaction")).get("scene_type");
            if (scene != null) {
                sceneByModule.put(moduleKey, String.valueOf(scene));
            }
        }
        resp.put("modules", modules);

        // Four-Module Cross QA：Primary scene 不应无理由重复
        String dupScene = validator.checkSceneDiversity(sceneByModule);
        if (dupScene != null) {
            log.warn("四模块 Primary scene 重复: scene={}, scenes={}", dupScene, sceneByModule);
        }

        Map<String, Object> bridge = new LinkedHashMap<>();
        bridge.put("title", SynBasicConstants.BRIDGE_TITLE);
        bridge.put("description", SynBasicConstants.BRIDGE_DESCRIPTION);
        bridge.put("cta_label", SynBasicConstants.BRIDGE_CTA_LABEL);
        bridge.put("route_key", SynBasicConstants.BRIDGE_ROUTE_KEY);
        resp.put("deep_report_bridge", bridge);

        return resp;
    }

    // ============================================================
    // 单模块构建
    // ============================================================

    private Map<String, Object> buildModule(String planet, Map<String, Object> personA,
                                            Map<String, Object> personB,
                                            String viewer, String nameA, String nameB) {
        SynBasicConstants.ModuleCopy copy = SynBasicConstants.MODULE_COPY.get(planet);

        PersonDomainResolver.ResolvedPerson a =
                resolver.resolve(planet, signOf(personA, planet), nakOf(personA, planet));
        PersonDomainResolver.ResolvedPerson b =
                resolver.resolve(planet, signOf(personB, planet), nakOf(personB, planet));

        SynBasicRenderer.ProfileCopy aCopy = SynBasicRenderer.renderProfile(planet, a);
        SynBasicRenderer.ProfileCopy bCopy = SynBasicRenderer.renderProfile(planet, b);

        PairComparator.PairResult pairResult = comparator.compare(planet, a, b);
        SynBasicRenderer.PairCopy pairCopy = pairResult == null ? null
                : SynBasicRenderer.renderPair(planet, a, b, pairResult);

        // module_status（02_RUNTIME_INPUT_OUTPUT_CONTRACT §6）
        String status;
        if (SynBasicConstants.SOURCE_DATA_INSUFFICIENT.equals(a.getSourceMode())
                || SynBasicConstants.SOURCE_DATA_INSUFFICIENT.equals(b.getSourceMode())) {
            status = SynBasicConstants.MODULE_UNAVAILABLE;
        } else if (SynBasicConstants.SOURCE_SIGN_NAKSHATRA.equals(a.getSourceMode())
                && SynBasicConstants.SOURCE_SIGN_NAKSHATRA.equals(b.getSourceMode())) {
            status = SynBasicConstants.MODULE_READY;
        } else {
            status = SynBasicConstants.MODULE_DEGRADED;
        }

        String fallback = SynBasicConstants.MODULE_UNAVAILABLE.equals(status)
                ? SynBasicConstants.FALLBACK_MESSAGE : null;

        // Module Validator
        String failure = validator.validateModule(status,
                pairCopy != null, fallback,
                aCopy.getRenderedCopy(), bCopy.getRenderedCopy(),
                pairCopy == null ? null : pairCopy.getRenderedCopy());
        if (failure != null) {
            log.error("基础互动模块未通过 Validator: planet={}, reason={}", planet, failure);
            status = SynBasicConstants.MODULE_VALIDATION_FAILED;
            fallback = SynBasicConstants.FALLBACK_MESSAGE;
            pairCopy = null;
        }

        Map<String, Object> module = new LinkedHashMap<>();
        module.put("planet", planet);
        module.put("module_status", status);
        module.put("ui_title", copy.uiTitle);
        module.put("entry_label", copy.entryLabel);
        module.put("block_titles", blockTitles(planet, viewer, nameA, nameB));
        module.put("person_a", buildPerson(a, aCopy, status));
        module.put("person_b", buildPerson(b, bCopy, status));
        module.put("pair_interaction", buildPair(pairResult, pairCopy, status));
        module.put("fallback_message", fallback);
        return module;
    }

    private Map<String, Object> buildPerson(PersonDomainResolver.ResolvedPerson p,
                                            SynBasicRenderer.ProfileCopy copy, String status) {
        Map<String, Object> m = new LinkedHashMap<>();
        Map<String, Object> placement = new LinkedHashMap<>();
        placement.put("planet", p.getPlanetDomain());
        placement.put("sign_id", p.getSignId());
        placement.put("sign_label_zh", p.getSignLabelZh());
        placement.put("nakshatra_id", p.getNakId());
        // UI 显示原始真实星宿名：Library 缺失也不隐藏
        placement.put("nakshatra_label_zh", p.getNakLabelZh());
        placement.put("source_mode", p.getSourceMode());
        m.put("placement", placement);
        m.put("degradation_reason", p.getDegradationReason());
        // DATA_INSUFFICIENT 不得伪造 semantic_basis_ids
        m.put("semantic_basis_ids", SynBasicConstants.SOURCE_DATA_INSUFFICIENT.equals(p.getSourceMode())
                ? new ArrayList<>() : p.getSemanticBasisIds());
        m.put("rendered_copy", SynBasicConstants.MODULE_UNAVAILABLE.equals(status)
                || SynBasicConstants.MODULE_VALIDATION_FAILED.equals(status) ? null : copy.getRenderedCopy());
        return m;
    }

    private Map<String, Object> buildPair(PairComparator.PairResult r,
                                          SynBasicRenderer.PairCopy copy, String status) {
        if (r == null || copy == null
                || SynBasicConstants.MODULE_UNAVAILABLE.equals(status)
                || SynBasicConstants.MODULE_VALIDATION_FAILED.equals(status)) {
            return null;
        }
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("primary_pattern", r.getPrimary_pattern());
        m.put("secondary_pattern", r.getSecondary_pattern());
        m.put("basis_a", r.getBasis_a());
        m.put("basis_b", r.getBasis_b());
        m.put("scene_type", r.getScene_type());
        m.put("rendered_copy", copy.getRenderedCopy());
        // light_tip 已融合进 rendered_copy，此处仅供 trace
        m.put("light_tip", copy.getLightTip());
        return m;
    }

    // ============================================================
    // Viewer Perspective → Block Title（07_UI_BINDING §3）
    // ============================================================

    private Map<String, Object> blockTitles(String planet, String viewer, String nameA, String nameB) {
        String semantic = SynBasicConstants.MODULE_COPY.get(planet).abSemanticTitle;
        String aTitle;
        String bTitle;
        switch (viewer) {
            case SynBasicConstants.VIEWER_IS_B:
                aTitle = "TA " + semantic;
                bTitle = "你" + semantic;
                break;
            case SynBasicConstants.VIEWER_OBSERVER:
                aTitle = nameA + semantic;
                bTitle = nameB + semantic;
                break;
            case SynBasicConstants.VIEWER_IS_A:
            default:
                aTitle = "你" + semantic;
                bTitle = "TA " + semantic;
                break;
        }
        Map<String, Object> titles = new LinkedHashMap<>();
        titles.put("a", aTitle);
        titles.put("b", bTitle);
        titles.put("pair", SynBasicConstants.MODULE_COPY.get(planet).pairTitle);
        return titles;
    }

    // ============================================================
    // 输入辅助
    // ============================================================

    @SuppressWarnings("unchecked")
    private String signOf(Map<String, Object> person, String planet) {
        Object p = person == null ? null : person.get(planet.toLowerCase());
        if (!(p instanceof Map)) {
            return null;
        }
        Object sign = ((Map<String, Object>) p).get("sign");
        return sign == null ? null : String.valueOf(sign);
    }

    @SuppressWarnings("unchecked")
    private String nakOf(Map<String, Object> person, String planet) {
        Object p = person == null ? null : person.get(planet.toLowerCase());
        if (!(p instanceof Map)) {
            return null;
        }
        Object nak = ((Map<String, Object>) p).get("nakshatra");
        return nak == null ? null : String.valueOf(nak);
    }

    private Map<String, Object> personRef(Map<String, Object> person, String fallbackName) {
        Map<String, Object> m = new LinkedHashMap<>();
        Object id = person == null ? null : person.get("subject_id");
        m.put("subject_id", id == null ? fallbackName : String.valueOf(id));
        m.put("display_name", fallbackName);
        return m;
    }

    private String displayName(Map<String, Object> person, String fallback) {
        Object n = person == null ? null : person.get("display_name");
        String s = n == null ? null : String.valueOf(n);
        return (s == null || s.trim().isEmpty()) ? fallback : s;
    }

    private String normalizeViewer(String viewer) {
        if (SynBasicConstants.VIEWER_IS_B.equals(viewer) || SynBasicConstants.VIEWER_OBSERVER.equals(viewer)) {
            return viewer;
        }
        return SynBasicConstants.VIEWER_IS_A;
    }

    /** relationship_type 为可选语境修饰器：缺失时保持 null，不得自动恋爱化 */
    private String normalizeRelationshipType(String type) {
        if (type == null || type.trim().isEmpty()) {
            return null;
        }
        List<String> allowed = List.of("romantic", "marriage", "parent_child", "sibling",
                "friendship", "business_partner", "colleague", "other");
        return allowed.contains(type) ? type : null;
    }
}
