package cn.dct.zpback.engine.synbasic;

import cn.dct.zpback.constant.SynBasicConstants;
import lombok.Data;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Pair Comparator（03_PAIR_COMPARATOR_RENDERER_BINDING、04_DOMAIN_RESOLVER_AND_PAIR_COMPARATOR）
 *
 * 只比较已经形成的 A/B Resolved Profile，不重新读取星盘。
 *
 * 输出约束：
 * - primary_pattern：exactly 1；
 * - secondary_pattern：0..1；
 * - basis_a / basis_b：各至少 1 个 semantic slot id；
 * - scene_type：exactly 1，取自该行星 scene pool；
 * - light_tip：0..1；若存在，必须在 Render 阶段融合进 rendered_copy。
 *
 * 没有 A + B 双侧 basis 时禁止形成 Pair Claim。
 */
@Component
public class PairComparator {

    @Autowired
    private SynBasicAssets assets;

    @Data
    public static class PairResult {
        private String primary_pattern;
        private String secondary_pattern;
        private List<String> basis_a = new ArrayList<>();
        private List<String> basis_b = new ArrayList<>();
        private String scene_type;
        private String light_tip;
        private String basisSlotA;
        private String basisSlotB;
        private String lightTip;
    }

    /** 每个行星用于比较的 slot 对：锚定 slot + 次比较 slot */
    private static final Map<String, String[]> COMPARE_SLOTS;
    static {
        Map<String, String[]> m = new LinkedHashMap<>();
        m.put("MOON", new String[]{"reassurance_channel", "repair_preference"});
        m.put("VENUS", new String[]{"appreciation_channel", "closeness_style"});
        m.put("MARS", new String[]{"pace_preference", "execution_style"});
        m.put("MERCURY", new String[]{"information_focus", "expression_style"});
        COMPARE_SLOTS = Collections.unmodifiableMap(m);
    }

    /** primary pattern：同 sign → MATCH/ALIGNMENT；异 sign → MISMATCH/DIFFERENCE */
    private static final Map<String, String[]> PRIMARY_PATTERN;
    static {
        Map<String, String[]> m = new LinkedHashMap<>();
        m.put("MOON", new String[]{"SIMILAR_NEED", "REASSURANCE_MISMATCH"});
        m.put("VENUS", new String[]{"APPRECIATION_CHANNEL_MATCH", "APPRECIATION_CHANNEL_MISMATCH"});
        m.put("MARS", new String[]{"PACE_ALIGNMENT", "PACE_DIFFERENCE"});
        m.put("MERCURY", new String[]{"INFORMATION_FOCUS_ALIGNMENT", "INFORMATION_FOCUS_DIFFERENCE"});
        PRIMARY_PATTERN = Collections.unmodifiableMap(m);
    }

    /** secondary pattern：次比较 slot 同 → ALIGNMENT；异 → DIFFERENCE */
    private static final Map<String, String[]> SECONDARY_PATTERN;
    static {
        Map<String, String[]> m = new LinkedHashMap<>();
        m.put("MOON", new String[]{"COMPLEMENTARY_RESPONSE", "TIMING_MISMATCH"});
        m.put("VENUS", new String[]{"CLOSENESS_STYLE_ALIGNMENT", "CLOSENESS_STYLE_DIFFERENCE"});
        m.put("MARS", new String[]{"ACTION_COMPLEMENTARITY", "EXECUTION_STYLE_DIFFERENCE"});
        m.put("MERCURY", new String[]{"EXPRESSION_ORDER_DIFFERENCE", "DIRECTNESS_DIFFERENCE"});
        SECONDARY_PATTERN = Collections.unmodifiableMap(m);
    }

    /** 每个 pattern 对应的可尝试相处方式（light_tip），必须具体、不写成多步计划 */
    private static final Map<String, String> LIGHT_TIP;
    static {
        Map<String, String> m = new LinkedHashMap<>();
        m.put("SIMILAR_NEED", "可以约定一个固定方式：状态不好时先告诉对方自己需要什么，而不是等对方猜。");
        m.put("REASSURANCE_MISMATCH", "可以先把“我现在需要什么”说出来，比等对方用他自己的方式接住更快。");
        m.put("TIMING_MISMATCH", "可以先约定“我需要一点时间，但我会回来回应”，比一方追、一方退更容易接住彼此。");
        m.put("APPRECIATION_CHANNEL_MATCH", "可以直接告诉对方：哪一种示好方式你最容易收到。");
        m.put("APPRECIATION_CHANNEL_MISMATCH", "如果你很在意一种示好方式，可以直接告诉对方，不用让他一直猜。");
        m.put("CLOSENESS_STYLE_ALIGNMENT", "可以把你们都舒服的亲近节奏说出来，避免其中一方默默加码。");
        m.put("CLOSENESS_STYLE_DIFFERENCE", "可以把“多近算刚好”说清楚，而不是用疏远或黏人来表达不满。");
        m.put("PACE_ALIGNMENT", "可以在开始前先确认这一件事的节奏，避免配合默契被当成默认。");
        m.put("PACE_DIFFERENCE", "可以在动手前先说清楚“先做起来”还是“先调方案”，再开始推进。");
        m.put("EXECUTION_STYLE_DIFFERENCE", "可以把分工和改动点分开说：谁负责推进，谁负责调整方案。");
        m.put("ACTION_COMPLEMENTARITY", "可以把你们的天然分工说清楚，避免一方以为另一方在拖。");
        m.put("INFORMATION_FOCUS_ALIGNMENT", "可以在讨论前先确认这次要解决的到底是哪一件事。");
        m.put("INFORMATION_FOCUS_DIFFERENCE", "可以在说重点之前，先补一句背景，让对方知道你在回应哪一层。");
        m.put("DIRECTNESS_DIFFERENCE", "可以在给出结论时多补一句原因，减少“你只说了结果”的错位。");
        LIGHT_TIP = Collections.unmodifiableMap(m);
    }

    /** scene 选择：按 pattern 稳定映射到该行星 scene pool 中的一项，避免随机 */
    private static final Map<String, String> SCENE_BY_PATTERN;
    static {
        Map<String, String> m = new LinkedHashMap<>();
        m.put("MOON.SIMILAR_NEED", "EMOTIONAL_LOW");
        m.put("MOON.REASSURANCE_MISMATCH", "POST_TENSION_RECONNECT");
        m.put("MOON.COMPLEMENTARY_RESPONSE", "NEED_FOR_SPACE");
        m.put("MOON.TIMING_MISMATCH", "RESPONSE_DELAY");
        m.put("VENUS.APPRECIATION_CHANNEL_MATCH", "APPRECIATION");
        m.put("VENUS.APPRECIATION_CHANNEL_MISMATCH", "SMALL_GESTURE");
        m.put("VENUS.CLOSENESS_STYLE_ALIGNMENT", "INITIATING_CONTACT");
        m.put("VENUS.CLOSENESS_STYLE_DIFFERENCE", "TIME_INVESTMENT");
        m.put("MARS.PACE_ALIGNMENT", "SHARED_TASK");
        m.put("MARS.PACE_DIFFERENCE", "SHARED_TASK");
        m.put("MARS.ACTION_COMPLEMENTARITY", "TRAVEL_PLAN");
        m.put("MARS.EXECUTION_STYLE_DIFFERENCE", "SCHEDULE_CHANGE");
        m.put("MERCURY.INFORMATION_FOCUS_ALIGNMENT", "PLAN_DISCUSSION");
        m.put("MERCURY.INFORMATION_FOCUS_DIFFERENCE", "MESSAGE_AMBIGUITY");
        m.put("MERCURY.EXPRESSION_ORDER_DIFFERENCE", "EXPLAINING_INTENT");
        m.put("MERCURY.DIRECTNESS_DIFFERENCE", "CLARIFICATION");
        SCENE_BY_PATTERN = Collections.unmodifiableMap(m);
    }

    /**
     * 比较 A/B Profile，形成 Pair Claim
     *
     * @return null 表示任一侧 DATA_INSUFFICIENT（不得形成 Pair Claim）
     */
    public PairResult compare(String planetDomain,
                              PersonDomainResolver.ResolvedPerson a,
                              PersonDomainResolver.ResolvedPerson b) {
        if (a == null || b == null
                || SynBasicConstants.SOURCE_DATA_INSUFFICIENT.equals(a.getSourceMode())
                || SynBasicConstants.SOURCE_DATA_INSUFFICIENT.equals(b.getSourceMode())) {
            return null;
        }

        String[] compare = COMPARE_SLOTS.get(planetDomain);
        String primarySlot = compare[0];
        String secondarySlot = compare[1];

        String aSignPrimary = signOf(a, primarySlot);
        String bSignPrimary = signOf(b, primarySlot);
        String aSignSecondary = signOf(a, secondarySlot);
        String bSignSecondary = signOf(b, secondarySlot);

        if (aSignPrimary == null || bSignPrimary == null) {
            return null; // 缺 basis 不得形成 Pair Claim
        }

        PairResult r = new PairResult();
        boolean samePrimary = aSignPrimary.equals(bSignPrimary);
        r.setPrimary_pattern(PRIMARY_PATTERN.get(planetDomain)[samePrimary ? 0 : 1]);

        if (aSignSecondary != null && bSignSecondary != null) {
            boolean sameSecondary = aSignSecondary.equals(bSignSecondary);
            r.setSecondary_pattern(SECONDARY_PATTERN.get(planetDomain)[sameSecondary ? 0 : 1]);
        }

        // basis 必须来自实际参与的 semantic slot id
        r.setBasis_a(singleBasis(planetDomain, a, primarySlot));
        r.setBasis_b(singleBasis(planetDomain, b, primarySlot));
        r.setBasisSlotA(primarySlot);
        r.setBasisSlotB(primarySlot);

        // scene 必须落在该行星 scene pool 内
        List<String> pool = assets.scenePoolOf(planetDomain);
        String scene = SCENE_BY_PATTERN.get(planetDomain + "." + r.getPrimary_pattern());
        if (scene == null || !pool.contains(scene)) {
            scene = pool.isEmpty() ? null : pool.get(0);
        }
        r.setScene_type(scene);

        r.setLight_tip(LIGHT_TIP.get(r.getPrimary_pattern()));
        r.setLightTip(r.getLight_tip());

        // 自检：pattern 必须在该行星 allowed list 内
        List<String> allowed = assets.allowedPatternsOf(planetDomain);
        if (!allowed.contains(r.getPrimary_pattern())) {
            throw new IllegalStateException("primary_pattern 不在允许集合内: " + r.getPrimary_pattern());
        }
        if (r.getSecondary_pattern() != null && !allowed.contains(r.getSecondary_pattern())) {
            throw new IllegalStateException("secondary_pattern 不在允许集合内: " + r.getSecondary_pattern());
        }
        return r;
    }

    private String signOf(PersonDomainResolver.ResolvedPerson p, String slotId) {
        PersonDomainResolver.ResolvedSlot slot = p.getSlots().get(slotId);
        if (slot == null || slot.getBasisIds().isEmpty()) {
            return null;
        }
        // basis id 形如 MOON.SIGN_LEO.reassurance_channel → 取星座 ID
        String basis = slot.getBasisIds().get(0);
        String[] parts = basis.split("\\.");
        return parts.length >= 2 ? parts[1] : null;
    }

    private List<String> singleBasis(String planetDomain, PersonDomainResolver.ResolvedPerson p, String slotId) {
        PersonDomainResolver.ResolvedSlot slot = p.getSlots().get(slotId);
        if (slot == null || slot.getBasisIds().isEmpty()) {
            return Collections.emptyList();
        }
        return new ArrayList<>(slot.getBasisIds());
    }
}
