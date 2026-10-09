package cn.dct.zpback.engine.synbasic;

import cn.dct.zpback.constant.SynBasicConstants;
import cn.dct.zpback.dto.synbasic.SynBasicDtos;
import lombok.Data;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Person Domain Resolver（02_RESOLVER_COMPILER_BINDING §5）
 *
 * 只处理一个人的一个行星：
 *   placement → 归一化 → Sign Entry（锚定）+ Nakshatra Refinement（可选）→ Resolved Semantic Slots
 *
 * 关键约束：
 * - Sign Core 拥有锚定权；Nakshatra 只能 narrow / concretize / add cue；
 * - source_mode 依据 compiler trace 判定，不依据正文有没有出现星宿名；
 * - DATA_INSUFFICIENT 不得伪造 semantic_basis_ids。
 */
@Component
public class PersonDomainResolver {

    @Autowired
    private SynBasicAssets assets;

    /** 单侧解析结果 */
    @Data
    public static class ResolvedPerson {
        private String planetDomain;
        private String signId;
        private String signLabelZh;
        private String nakId;
        private String nakLabelZh;
        private String sourceMode;
        private String degradationReason;
        private List<String> semanticBasisIds = new ArrayList<>();
        /** slot_id → 合并后的 slot */
        private Map<String, ResolvedSlot> slots = new LinkedHashMap<>();
    }

    /** 合并后的 slot（Sign Core + Nakshatra Refinement） */
    @Data
    public static class ResolvedSlot {
        private String slotId;
        private String signCore;
        private String nakRefinement;
        private List<String> behaviorCues = new ArrayList<>();
        private List<String> basisIds = new ArrayList<>();
    }

    /**
     * 解析单侧行星 Profile
     *
     * @param planetDomain MOON / VENUS / MARS / MERCURY
     * @param rawSign      原始星座（stable ID 或 label，均可）
     * @param rawNak       原始星宿（可为 null）
     */
    public ResolvedPerson resolve(String planetDomain, String rawSign, String rawNak) {
        ResolvedPerson p = new ResolvedPerson();
        p.setPlanetDomain(planetDomain);

        // 1) 归一化：stable ID 优先，不做 fuzzy
        String signId = assets.normalizeSignId(rawSign);
        String nakId = assets.normalizeNakId(rawNak);

        if (signId == null) {
            p.setSourceMode(SynBasicConstants.SOURCE_DATA_INSUFFICIENT);
            p.setDegradationReason(rawSign == null || rawSign.trim().isEmpty()
                    ? SynBasicConstants.DEG_SIGN_DATA_MISSING
                    : SynBasicConstants.DEG_SIGN_LIBRARY_MISSING);
            return p;
        }
        p.setSignId(signId);
        p.setSignLabelZh(assets.signLabelZh(signId));
        // UI 显示真实星宿名：即使无法解析为 stable ID 或 Library 缺失，也回显原始名称
        p.setNakId(nakId);
        p.setNakLabelZh(nakId != null ? assets.nakLabelZh(nakId) : rawNak);

        // 2) Sign Entry（作用域查询必须带 planet_domain）
        SynBasicDtos.CanonicalEntry signEntry = assets.lookup(planetDomain, signId);
        if (signEntry == null || signEntry.getSemantic_slots() == null) {
            p.setSourceMode(SynBasicConstants.SOURCE_DATA_INSUFFICIENT);
            p.setDegradationReason(SynBasicConstants.DEG_SIGN_LIBRARY_MISSING);
            return p;
        }

        // 3) Sign Core 落盘（锚定）
        List<String> whiteList = assets.slotsOf(planetDomain);
        for (Map.Entry<String, SynBasicDtos.SlotCore> e : signEntry.getSemantic_slots().entrySet()) {
            if (!whiteList.contains(e.getKey())) {
                continue; // 非白名单 slot 不得进入该行星 Profile
            }
            ResolvedSlot slot = new ResolvedSlot();
            slot.setSlotId(e.getKey());
            slot.setSignCore(e.getValue().getCore());
            if (e.getValue().getBehavior_cues() != null) {
                slot.getBehaviorCues().addAll(e.getValue().getBehavior_cues());
            }
            slot.getBasisIds().add(planetDomain + "." + signId + "." + e.getKey());
            p.getSlots().put(e.getKey(), slot);
        }

        // 4) Nakshatra Refinement（只允许 refine 当前行星白名单 slot）
        if (nakId == null) {
            p.setSourceMode(SynBasicConstants.SOURCE_SIGN_ONLY);
            // 原始位置缺失 vs 位置存在但无法解析为 canonical ID，必须区分
            p.setDegradationReason(rawNak == null || rawNak.trim().isEmpty()
                    ? SynBasicConstants.DEG_NAK_DATA_MISSING
                    : SynBasicConstants.DEG_NAK_LIBRARY_MISSING);
            return p;
        }
        SynBasicDtos.CanonicalEntry nakEntry = assets.lookup(planetDomain, nakId);
        if (nakEntry == null || nakEntry.getRefines_slots() == null) {
            p.setSourceMode(SynBasicConstants.SOURCE_SIGN_ONLY);
            p.setDegradationReason(SynBasicConstants.DEG_NAK_LIBRARY_MISSING);
            return p;
        }

        int applied = 0;
        for (Map.Entry<String, SynBasicDtos.SlotRefinement> e : nakEntry.getRefines_slots().entrySet()) {
            String slotId = e.getKey();
            if (!whiteList.contains(slotId)) {
                continue; // 越界 refinement 丢弃，不生成第三种解释
            }
            ResolvedSlot slot = p.getSlots().get(slotId);
            if (slot == null) {
                continue; // Sign Core 缺失该 slot 时不得凭空创建
            }
            slot.setNakRefinement(e.getValue().getRefinement());
            if (e.getValue().getBehavior_cues() != null) {
                slot.getBehaviorCues().addAll(e.getValue().getBehavior_cues());
            }
            slot.getBasisIds().add(planetDomain + "." + nakId + "." + slotId);
            p.getSemanticBasisIds().add(planetDomain + "." + nakId + "." + slotId);
            applied++;
        }

        // 5) source_mode 依据 compiler trace：至少 1 个合法 refinement 实际进入 Resolved Slots
        if (applied >= 1) {
            p.setSourceMode(SynBasicConstants.SOURCE_SIGN_NAKSHATRA);
            p.setDegradationReason(null);
            // SIGN_NAKSHATRA 必须可追溯到至少 1 个 Sign Core basis
            for (ResolvedSlot slot : p.getSlots().values()) {
                if (!slot.getBasisIds().isEmpty()) {
                    p.getSemanticBasisIds().add(0, slot.getBasisIds().get(0));
                    break;
                }
            }
        } else {
            p.setSourceMode(SynBasicConstants.SOURCE_SIGN_ONLY);
            p.setDegradationReason(SynBasicConstants.DEG_LIBRARY_CONFLICT);
        }
        return p;
    }

    /** 该行星的锚定 slot：优先被星宿 refinement 覆盖的 slot，否则用默认锚定 slot */
    public String anchorSlotOf(ResolvedPerson p) {
        for (Map.Entry<String, ResolvedSlot> e : p.getSlots().entrySet()) {
            if (e.getValue().getNakRefinement() != null) {
                return e.getKey();
            }
        }
        return SynBasicConstants.DEFAULT_ANCHOR_SLOT_BY_PLANET.getOrDefault(p.getPlanetDomain(), "expression_style");
    }

    public List<String> slotOrder(String planetDomain) {
        List<String> list = assets.slotsOf(planetDomain);
        return list == null ? Collections.emptyList() : list;
    }
}
