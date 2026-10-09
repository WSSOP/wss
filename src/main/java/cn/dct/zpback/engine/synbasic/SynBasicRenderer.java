package cn.dct.zpback.engine.synbasic;

import cn.dct.zpback.constant.SynBasicConstants;
import lombok.Data;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 基础互动渲染器（02_RESOLVER_COMPILER_BINDING §7/§8、03_PAIR_COMPARATOR §2）
 *
 * 只接收 Resolved Semantic Slots，不接收 House / Aspect / D9 / Dasha / Shadbala 等。
 * 输出严格遵守：
 * - A/B Profile 目标 90–140 中文字，至少 1 个现实行为或场景；
 * - Pair Copy 必须形成「A 倾向 + B 倾向 + 真实互动机制 + 具体场景 + 可选 light_tip」；
 * - 不输出「XX宿会让你……」，不输出整体关系结论。
 */
public final class SynBasicRenderer {

    private SynBasicRenderer() {
    }

    /** A/B 单人 Profile 渲染结果 */
    @Data
    public static class ProfileCopy {
        private String renderedCopy;
        private String anchorSlotId;
        private String lightTip;
    }

    /**
     * 渲染单侧 Profile
     *
     * 组成：行星视角前缀 + 锚定 slot（Sign Core + Nak Refinement）+ 风险侧 slot + 行为线索。
     * 每一句都可追溯到具体 slot，不补空话。
     */
    public static ProfileCopy renderProfile(String planetDomain, PersonDomainResolver.ResolvedPerson p) {
        ProfileCopy out = new ProfileCopy();
        if (p.getSourceMode() == null
                || SynBasicConstants.SOURCE_DATA_INSUFFICIENT.equals(p.getSourceMode())) {
            out.setRenderedCopy(null);
            return out;
        }

        String anchor = anchorSlot(p);
        PersonDomainResolver.ResolvedSlot anchorSlot = p.getSlots().get(anchor);
        PersonDomainResolver.ResolvedSlot riskSlot =
                p.getSlots().get(SynBasicConstants.RISK_SLOT_BY_PLANET.get(planetDomain));

        StringBuilder sb = new StringBuilder();
        sb.append(SynBasicConstants.PROFILE_OPENING_BY_PLANET.getOrDefault(planetDomain, ""));
        if (anchorSlot != null && anchorSlot.getSignCore() != null) {
            sb.append(trimPeriod(anchorSlot.getSignCore()));
            if (anchorSlot.getNakRefinement() != null) {
                // Sign + Nakshatra 融成一段自然中文，不显式提星宿名
                sb.append("；").append(trimPeriod(anchorSlot.getNakRefinement()));
            }
            sb.append("。");
        }
        // 至少 1 个现实行为或场景：用行为线索 + 风险侧 slot 组合出可观察表现
        if (anchorSlot != null && !anchorSlot.getBehaviorCues().isEmpty()) {
            sb.append("比较明显的时候，会表现为").append(joinCues(anchorSlot.getBehaviorCues())).append("。");
        }
        if (riskSlot != null && riskSlot.getSignCore() != null) {
            sb.append("如果这一点长期没被接住，").append(trimPeriod(riskSlot.getSignCore())).append("。");
        }

        out.setRenderedCopy(sb.toString());
        out.setAnchorSlotId(anchor);
        return out;
    }

    /** Pair 渲染结果 */
    @Data
    public static class PairCopy {
        private String renderedCopy;
        private String lightTip;
    }

    /**
     * 渲染 Pair Interaction
     *
     * 结构固定为：A 倾向 + B 倾向 + 互动机制 + 具体场景 + （可选）light_tip。
     * light_tip 若存在必须融合进 rendered_copy，UI 不再单独渲染。
     */
    public static PairCopy renderPair(String planetDomain,
                                      PersonDomainResolver.ResolvedPerson a,
                                      PersonDomainResolver.ResolvedPerson b,
                                      PairComparator.PairResult result) {
        PairCopy out = new PairCopy();
        PersonDomainResolver.ResolvedSlot aSlot = a.getSlots().get(result.getBasisSlotA());
        PersonDomainResolver.ResolvedSlot bSlot = b.getSlots().get(result.getBasisSlotB());

        StringBuilder sb = new StringBuilder();
        // A 倾向
        if (aSlot != null && aSlot.getSignCore() != null) {
            sb.append("一方").append(trimPeriod(aSlot.getSignCore()));
            if (aSlot.getNakRefinement() != null) {
                sb.append("，").append(trimPeriod(aSlot.getNakRefinement()));
            }
            sb.append("；");
        }
        // B 倾向
        if (bSlot != null && bSlot.getSignCore() != null) {
            sb.append("另一方").append(trimPeriod(bSlot.getSignCore()));
            if (bSlot.getNakRefinement() != null) {
                sb.append("，").append(trimPeriod(bSlot.getNakRefinement()));
            }
            sb.append("。");
        }
        // 真实互动机制（由 pattern 决定，不是「A 这样 B 那样所以不一样」）
        sb.append(patternMechanism(result.getPrimary_pattern()));
        // 具体场景
        String scene = SynBasicConstants.SCENE_SENTENCE.get(result.getScene_type());
        if (scene != null) {
            sb.append(scene).append("，这组差异最容易显出来。");
        }
        // light_tip：融合进末段，作为可尝试的相处方式
        if (result.getLightTip() != null) {
            sb.append(result.getLightTip());
        }
        out.setRenderedCopy(sb.toString());
        out.setLightTip(result.getLightTip());
        return out;
    }

    /** 互动机制句：按 primary pattern 给出可观察的互动描述，避免只做并列对比 */
    private static String patternMechanism(String pattern) {
        if (pattern == null) {
            return "你们的差异会在日常互动里被反复触发。";
        }
        if (pattern.endsWith("_MATCH") || pattern.endsWith("_ALIGNMENT") || "SIMILAR_NEED".equals(pattern)
                || "COMPLEMENTARY_RESPONSE".equals(pattern) || "ACTION_COMPLEMENTARITY".equals(pattern)) {
            return "你们这一维度比较同频，容易自然接上对方的方式，但也可能一起忽略同一种盲点。";
        }
        if (pattern.contains("MISMATCH") || pattern.contains("DIFFERENCE")) {
            return "你们并不是谁对谁错，而是各自默认的回应方式不一样，容易在同一个点上互相错判。";
        }
        if ("ACTION_PUSH_PULL".equals(pattern)) {
            return "你们容易形成一方推进、一方回退的循环，越用力越难同步。";
        }
        return "你们的互动方式会在同一件事上走出不同的处理顺序。";
    }

    private static String joinCues(List<String> cues) {
        int n = Math.min(2, cues.size());
        return String.join("、", cues.subList(0, n));
    }

    private static String trimPeriod(String s) {
        if (s == null) {
            return "";
        }
        String t = s.trim();
        while (t.endsWith("。") || t.endsWith("；")) {
            t = t.substring(0, t.length() - 1);
        }
        return t;
    }

    /** 锚定 slot 选择与 Resolver 保持一致 */
    private static String anchorSlot(PersonDomainResolver.ResolvedPerson p) {
        for (Map.Entry<String, PersonDomainResolver.ResolvedSlot> e : p.getSlots().entrySet()) {
            if (e.getValue().getNakRefinement() != null) {
                return e.getKey();
            }
        }
        return SynBasicConstants.DEFAULT_ANCHOR_SLOT_BY_PLANET.getOrDefault(p.getPlanetDomain(), "expression_style");
    }

    /** 用于 Cross-Module QA：提取某模块实际使用的 scene 列表（此处仅单值，保留结构） */
    public static List<String> scenesOf(Map<String, String> sceneByModule) {
        return new ArrayList<>(sceneByModule.values());
    }

    public static Map<String, String> emptySceneMap() {
        return new LinkedHashMap<>();
    }
}
