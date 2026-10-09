package cn.dct.zpback.engine.lifepattern;

import cn.dct.zpback.entity.lifepattern.*;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.*;

/**
 * Dasha 激活器
 * 符合 SEALED 文档 01 第 9.6 节规范
 *
 * 规则：
 * 1. Dasha 只激活已存在的本命 Claim
 * 2. Dasha 不创造人格、需要、创伤或成长课题
 * 3. Dasha 不提高 natal confidence
 */
@Component
@Slf4j
public class DashaActivationResolver {

    /**
     * 解析 C13 当前阶段激活主题
     */
    public LifePatternClaim resolveC13(Map<String, LifePatternClaim> claims, List<LifePatternDashaPeriod> dashaList) {
        LifePatternClaim c13 = new LifePatternClaim();
        c13.setClaimId("C13");
        c13.setClaimName("CURRENT_GROWTH_ACTIVATION");
        c13.setSectionOwner("S8");
        c13.setSupportEvidenceIds(new ArrayList<>());
        c13.setContradictEvidenceIds(new ArrayList<>());

        // 生成条件 1: 当前 Dasha MD/AD 时间窗口有效
        if (dashaList == null || dashaList.isEmpty()) {
            c13.setState("INSUFFICIENT");
            c13.setConfidence("NONE");
            c13.setOmissionReason("No dasha data available");
            c13.setRenderPermission("DERIVED_ONLY");
            return c13;
        }

        LifePatternDashaPeriod currentDasha = dashaList.get(0);
        String dashaLord = currentDasha.getMahadasha();

        // 生成条件 2: Dasha Lord 可通过 activation bridge 指向已成立的 C02–C12
        String activatedClaimId = findActivatedClaim(dashaLord, claims);

        if (activatedClaimId != null) {
            LifePatternClaim activatedClaim = claims.get(activatedClaimId);

            // 生成条件 3: 被激活 Claim 的状态必须为 SUPPORTED/CONDITIONAL/MIXED
            if ("SUPPORTED".equals(activatedClaim.getState()) ||
                "CONDITIONAL".equals(activatedClaim.getState()) ||
                "MIXED".equals(activatedClaim.getState())) {

                c13.setState("SUPPORTED");
                c13.setConfidence("MEDIUM");
                c13.setCandidateDirection(activatedClaim.getCandidateDirection());
                c13.setDerivedFrom(activatedClaimId);
                c13.setRenderPermission("DERIVED_ONLY");
                c13.setSupportEvidenceIds(Arrays.asList("DASHA_" + dashaLord));
            } else {
                c13.setState("INSUFFICIENT");
                c13.setConfidence("NONE");
                c13.setOmissionReason("Activated claim state: " + activatedClaim.getState());
                c13.setRenderPermission("DERIVED_ONLY");
            }
        } else {
            c13.setState("INSUFFICIENT");
            c13.setConfidence("NONE");
            c13.setOmissionReason("No claim activated by dasha lord: " + dashaLord);
            c13.setRenderPermission("DERIVED_ONLY");
        }

        return c13;
    }

    /**
     * 根据 Dasha Lord 查找可激活的 Claim
     */
    private String findActivatedClaim(String dashaLord, Map<String, LifePatternClaim> claims) {
        // Activation Bridge: Dasha Lord → Claim 方向映射
        Map<String, String[]> activationBridge = new HashMap<>();
        activationBridge.put("Sun", new String[]{"C04", "C01"});
        activationBridge.put("Moon", new String[]{"C02", "C03"});
        activationBridge.put("Mars", new String[]{"C05", "C07"});
        activationBridge.put("Mercury", new String[]{"C08", "C12"});
        activationBridge.put("Jupiter", new String[]{"C12", "C11"});
        activationBridge.put("Venus", new String[]{"C02", "C08"});
        activationBridge.put("Saturn", new String[]{"C10", "C11"});
        activationBridge.put("Rahu", new String[]{"C06", "C07"});
        activationBridge.put("Ketu", new String[]{"C06", "C11"});

        String[] targetClaims = activationBridge.get(dashaLord);
        if (targetClaims == null) return null;

        for (String claimId : targetClaims) {
            LifePatternClaim claim = claims.get(claimId);
            if (claim != null && "SUPPORTED".equals(claim.getState())) {
                return claimId;
            }
        }

        // 如果没有 SUPPORTED 的，尝试 CONDITIONAL
        for (String claimId : targetClaims) {
            LifePatternClaim claim = claims.get(claimId);
            if (claim != null && "CONDITIONAL".equals(claim.getState())) {
                return claimId;
            }
        }

        return null;
    }

    /**
     * 生成时间窗口
     */
    public List<LifePatternTimingPayload> generateTimingWindows(List<LifePatternDashaPeriod> dashaList) {
        List<LifePatternTimingPayload> timings = new ArrayList<>();

        if (dashaList == null || dashaList.isEmpty()) return timings;

        for (LifePatternDashaPeriod dasha : dashaList) {
            LifePatternTimingPayload timing = new LifePatternTimingPayload();
            timing.setTimingType("RELATIONSHIP_START_WINDOW");
            timing.setDashaLord(dasha.getMahadasha());
            timing.setStartDate(dasha.getStartDate());
            timing.setEndDate(dasha.getEndDate());
            timing.setDescription("当前 " + dasha.getMahadasha() + " 大运时期");
            timings.add(timing);
        }

        return timings;
    }
}
