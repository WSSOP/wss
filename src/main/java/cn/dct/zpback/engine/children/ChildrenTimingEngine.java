package cn.dct.zpback.engine.children;

import cn.dct.zpback.constant.NatalChildrenConstants;
import cn.dct.zpback.entity.children.ChildrenClaim;
import cn.dct.zpback.entity.children.ChildrenEvidenceObject;
import cn.dct.zpback.entity.lifepattern.LifePatternChartData;
import cn.dct.zpback.entity.lifepattern.LifePatternDashaPeriod;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.*;

/**
 * 子女报告 - 五年 Dasha 时序引擎
 * 符合 SEALED 文档《01_子女与养育深度报告_最终开发总说明》第 11 节
 *
 * - 范围：generated_at 起未来五年
 * - 激活桥：Dasha Lord 必须通过已注册根连接至已成立的 C01-C10
 * - 窗口只能表达"议题活跃、准备、协商、责任增加或整合"
 */
@Component
@Slf4j
public class ChildrenTimingEngine {

    /** Dasha Lord 连接养有序列：D1 5L、D7 5L、D7 Asc Lord、Jupiter、Moon、PK 行星 */
    private static final Set<String> TIMING_BRIDGE_PLANETS = new HashSet<>(Arrays.asList(
        "Jupiter", "Moon", "Sun", "Mercury", "Venus", "Mars", "Saturn", "Rahu", "Ketu"));

    /**
     * 生成未来五年时序窗口
     */
    public List<Map<String, Object>> resolveFiveYearWindows(LifePatternChartData chartData,
                                                             Map<String, ChildrenClaim> claims,
                                                             List<ChildrenEvidenceObject> evidenceList) {
        List<Map<String, Object>> windows = new ArrayList<>();

        if (chartData == null || chartData.getDashaList() == null || chartData.getDashaList().isEmpty()) {
            return windows;
        }

        // 已成立的 natal claims（C02-C10）
        List<ChildrenClaim> established = new ArrayList<>();
        for (ChildrenClaim c : claims.values()) {
            if ("C11".equals(c.getClaimId())) continue;
            if ("SUPPORTED".equals(c.getState()) || "CONDITIONAL".equals(c.getState()) || "MIXED".equals(c.getState())) {
                established.add(c);
            }
        }
        if (established.isEmpty()) {
            log.info("无已成立 natal claims，Dasha 不能激活任何窗口");
            return windows;
        }

        // 支持桥接的行星集合（来自 Evidence 的 subject）
        Set<String> bridgedPlanets = new HashSet<>();
        for (ChildrenEvidenceObject ev : evidenceList) {
            if ("ELIGIBLE".equals(ev.getEvidenceStatus()) && "SUPPORT".equals(ev.getPolarity())) {
                if (TIMING_BRIDGE_PLANETS.contains(ev.getSubject())) {
                    bridgedPlanets.add(ev.getSubject());
                }
            }
        }

        // 计算未来五年窗口
        LocalDate now = LocalDate.now();
        LocalDate fiveYearsLater = now.plusYears(5);

        Map<String, WindowAgg> merged = new LinkedHashMap<>();
        for (LifePatternDashaPeriod period : chartData.getDashaList()) {
            LocalDate start = parseDate(period.getStartDate());
            LocalDate end = parseDate(period.getEndDate());
            if (start == null || end == null) continue;
            // 与五年窗口相交
            if (end.isBefore(now) || start.isAfter(fiveYearsLater)) continue;

            String lord = period.getMahadasha();
            if (lord == null) continue;

            // 激活桥判定
            WindowAgg agg = merged.computeIfAbsent(lord + "|" + period.getAntardasha(), k -> new WindowAgg());
            agg.lord = lord;
            agg.antardasha = period.getAntardasha();
            agg.start = start.isBefore(now) ? now : start;
            agg.end = end.isAfter(fiveYearsLater) ? fiveYearsLater : end;
            agg.bridged = bridgedPlanets.contains(lord);
        }

        for (WindowAgg agg : merged.values()) {
            Map<String, Object> window = new LinkedHashMap<>();
            String classification = agg.bridged ? classifyWindow(agg) : NatalChildrenConstants.WINDOW_LOW_ACTIVATION;
            window.put("mahadasha", agg.lord);
            window.put("antardasha", agg.antardasha);
            window.put("start_date", agg.start.toString());
            window.put("end_date", agg.end.toString());
            window.put("window_classification", classification);
            window.put("activation_bridge", agg.bridged ? agg.lord : null);
            window.put("neutral_expression", windowText(classification, agg.lord));
            windows.add(window);
        }

        // 相邻同类窗口合并已在 key 层面处理；按开始日期排序
        windows.sort(Comparator.comparing(w -> String.valueOf(w.get("start_date"))));

        // 正文保留 3-5 个最重要窗口
        List<Map<String, Object>> important = new ArrayList<>();
        for (Map<String, Object> w : windows) {
            if (!NatalChildrenConstants.WINDOW_LOW_ACTIVATION.equals(w.get("window_classification"))) {
                important.add(w);
            }
            if (important.size() >= 5) break;
        }
        return important;
    }

    private String classifyWindow(WindowAgg agg) {
        // 确定性分类：桥接行星性质决定窗口性质
        switch (agg.lord) {
            case "Jupiter": case "Moon": case "Venus": case "Mercury":
                return NatalChildrenConstants.WINDOW_SUPPORTIVE;
            case "Saturn":
                return NatalChildrenConstants.WINDOW_PREPARATION;
            case "Mars": case "Sun":
                return NatalChildrenConstants.WINDOW_MIXED;
            case "Rahu": case "Ketu":
                return NatalChildrenConstants.WINDOW_MIXED;
            default:
                return NatalChildrenConstants.WINDOW_LOW_ACTIVATION;
        }
    }

    private String windowText(String classification, String lord) {
        switch (classification) {
            case NatalChildrenConstants.WINDOW_SUPPORTIVE:
                return "该阶段子女与家庭议题更容易获得资源与支持，适合推进准备与协商。";
            case NatalChildrenConstants.WINDOW_MIXED:
                return "该阶段相关主题活跃，支持与调整并存，节奏需要灵活安排。";
            case NatalChildrenConstants.WINDOW_PREPARATION:
                return "该阶段更适合做现实准备、建立结构与分工，责任要求增加。";
            case NatalChildrenConstants.WINDOW_LOW_ACTIVATION:
            default:
                return "该阶段相关主题激活较弱，可把注意力放在已有的生活重心上。";
        }
    }

    private LocalDate parseDate(String dateStr) {
        if (dateStr == null || dateStr.isEmpty()) return null;
        try {
            if (dateStr.length() > 10) {
                return LocalDate.parse(dateStr.substring(0, 10), DateTimeFormatter.ISO_LOCAL_DATE);
            }
            return LocalDate.parse(dateStr, DateTimeFormatter.ISO_LOCAL_DATE);
        } catch (Exception e) {
            return null;
        }
    }

    /**
     * 汇总 C11
     */
    public ChildrenClaim summarizeTiming(List<Map<String, Object>> windows) {
        ChildrenClaim claim = new ChildrenClaim();
        claim.setClaimId("C11");
        claim.setClaimKey("CHILDREN_TIMING_ACTIVATION");
        claim.setSectionOwner("FIVE_YEAR_TIMING");
        claim.setRenderPermission("TIMING_ONLY");

        if (windows == null || windows.isEmpty()) {
            claim.setState("INSUFFICIENT");
            claim.setConfidence("NOT_APPLICABLE");
            claim.setPrimaryCandidate("INSUFFICIENT");
            return claim;
        }

        boolean hasSupportive = false, hasMixed = false, hasPreparation = false;
        for (Map<String, Object> w : windows) {
            String cls = String.valueOf(w.get("window_classification"));
            if (NatalChildrenConstants.WINDOW_SUPPORTIVE.equals(cls)) hasSupportive = true;
            if (NatalChildrenConstants.WINDOW_MIXED.equals(cls)) hasMixed = true;
            if (NatalChildrenConstants.WINDOW_PREPARATION.equals(cls)) hasPreparation = true;
        }

        if (hasSupportive && !hasMixed) {
            claim.setPrimaryCandidate(NatalChildrenConstants.WINDOW_SUPPORTIVE);
            claim.setState("SUPPORTED");
        } else if (hasSupportive && hasMixed) {
            claim.setPrimaryCandidate(NatalChildrenConstants.WINDOW_MIXED);
            claim.setState("MIXED");
        } else if (hasPreparation || hasMixed) {
            claim.setPrimaryCandidate(NatalChildrenConstants.WINDOW_PREPARATION);
            claim.setState("CONDITIONAL");
        } else {
            claim.setPrimaryCandidate(NatalChildrenConstants.WINDOW_LOW_ACTIVATION);
            claim.setState("CONDITIONAL");
        }
        claim.setConfidence("MEDIUM");
        return claim;
    }

    private static class WindowAgg {
        String lord;
        String antardasha;
        LocalDate start;
        LocalDate end;
        boolean bridged;
    }
}
