package cn.dct.zpback.engine.education;

import cn.dct.zpback.constant.EducationReportConstants;
import cn.dct.zpback.entity.education.EducationClaim;
import cn.dct.zpback.entity.education.EducationEvidenceObject;
import cn.dct.zpback.entity.lifepattern.LifePatternChartData;
import cn.dct.zpback.entity.lifepattern.LifePatternDashaPeriod;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.*;

/**
 * 学业报告 - Dasha 阶段窗口引擎
 * 符合 SEALED 文档《01_学业深度解析报告》§7.8 / contract §9
 *
 * 规则：
 * - 默认观察窗：生成时点起未来 36 个月
 * - 输出 3-5 个互不重叠的阶段窗口
 * - 被激活目标必须是 C02-C12 中已 SUPPORTED/MIXED 的 Claim
 * - Dasha lord 与该 Claim 已注册本命 Root 有直接关系（同 family）
 * - Dasha 不能创造新本命结论；不输出通过/录取结论
 */
@Component
@Slf4j
public class EducationTimingEngine {

    /** Dasha lord → 学业 Family 映射（同一 family 才能激活对应 Claim） */
    private static final Map<String, String> LORD_FAMILY = new HashMap<>();
    static {
        LORD_FAMILY.put("Mercury", "COGNITION");
        LORD_FAMILY.put("Jupiter", "MEANING_GUIDANCE");
        LORD_FAMILY.put("Moon", "MIND_REGULATION");
        LORD_FAMILY.put("Sun", "LEARNING_CONTAINER");
        LORD_FAMILY.put("Venus", "KNOWLEDGE_BASE");
        LORD_FAMILY.put("Mars", "INTELLIGENCE_APPLICATION");
        LORD_FAMILY.put("Saturn", "ADVANCED_LEARNING");
        LORD_FAMILY.put("Rahu", "ADVANCED_LEARNING");
        LORD_FAMILY.put("Ketu", "WITHDRAWAL_IMMERSION");
    }

    /**
     * 解析 36 个月阶段窗口
     */
    public List<Map<String, Object>> resolveTimingWindows(LifePatternChartData chartData,
                                                           Map<String, EducationClaim> claims,
                                                           List<EducationEvidenceObject> evidenceList) {
        List<Map<String, Object>> windows = new ArrayList<>();
        if (chartData == null || chartData.getDashaList() == null || chartData.getDashaList().isEmpty()) {
            return windows;
        }

        LocalDate now = LocalDate.now();
        LocalDate horizon = now.plusMonths(EducationReportConstants.TIMING_HORIZON_MONTHS);

        // 收集已成立 Claim 的支持 family
        Map<String, Set<String>> claimFamilies = new LinkedHashMap<>();
        for (EducationClaim claim : claims.values()) {
            if (!claim.getClaimId().matches("C0[2-9]|C1[0-2]")) continue;
            if (!EducationReportConstants.STATE_SUPPORTED.equals(claim.getState())
                    && !EducationReportConstants.STATE_MIXED.equals(claim.getState())) continue;
            Set<String> families = new LinkedHashSet<>(claim.getSupportFamilies() != null
                    ? claim.getSupportFamilies() : Collections.emptyList());
            claimFamilies.put(claim.getClaimId(), families);
        }

        // 按时序排列 Dasha 段，与观察窗取交集
        List<LifePatternDashaPeriod> periods = new ArrayList<>(chartData.getDashaList());
        periods.sort(Comparator.comparing(LifePatternDashaPeriod::getStartDate,
                Comparator.nullsLast(Comparator.naturalOrder())));

        for (LifePatternDashaPeriod period : periods) {
            LocalDate start = parseDate(period.getStartDate());
            LocalDate end = parseDate(period.getEndDate());
            if (start == null || end == null) continue;
            if (end.isBefore(now) || start.isAfter(horizon)) continue;

            LocalDate wStart = start.isBefore(now) ? now : start;
            LocalDate wEnd = end.isAfter(horizon) ? horizon : end;

            // 激活判定：Dasha lord（Maha 优先，Antar 辅助）的 family 是否命中已成立 Claim
            List<String> activatedClaims = new ArrayList<>();
            String lordFamily = LORD_FAMILY.get(period.getMahadasha());
            if (lordFamily == null) {
                lordFamily = LORD_FAMILY.get(period.getAntardasha());
            }
            if (lordFamily != null) {
                for (Map.Entry<String, Set<String>> entry : claimFamilies.entrySet()) {
                    if (entry.getValue().contains(lordFamily)) {
                        activatedClaims.add(entry.getKey());
                    }
                }
            }

            Map<String, Object> window = new LinkedHashMap<>();
            window.put("window_id", "TW_" + UUID.randomUUID().toString().substring(0, 8).toUpperCase());
            window.put("start_date", wStart.format(DateTimeFormatter.ISO_LOCAL_DATE));
            window.put("end_date", wEnd.format(DateTimeFormatter.ISO_LOCAL_DATE));
            window.put("mahadasha", period.getMahadasha());
            window.put("antardasha", period.getAntardasha());

            if (activatedClaims.isEmpty()) {
                window.put("window_type", "MAINTENANCE");
                window.put("activated_claims", Collections.emptyList());
                window.put("description", "主题平稳期：适合保持既有节奏、巩固基础方法，不必强行开辟新方向。");
                window.put("risk_hint", "可能因缺乏新刺激而松散，建议维持最低限度例行学习。");
            } else {
                window.put("window_type", activatedClaims.size() >= 3 ? "HIGH_ACTIVATION" : "THEMATIC_ACTIVATION");
                window.put("activated_claims", activatedClaims);
                window.put("description", "本阶段与「" + String.join("、", activatedClaims) + "」相关主题更容易被激活："
                        + "既有结构会以更明显的方式表现出来，适合围绕这些主题安排推进节奏。");
                window.put("risk_hint", activatedClaims.size() >= 3
                        ? "多主题同时活跃容易过载，注意排程优先级。"
                        : "主题聚焦，警惕单一方向用力过猛。");
            }
            window.put("confidence", activatedClaims.isEmpty()
                    ? EducationReportConstants.CONF_LOW : EducationReportConstants.CONF_MEDIUM);
            window.put("boundary_note", "本窗口只描述主题活跃度与推进节奏，不预测考试通过、录取或任何结果。");

            windows.add(window);
        }

        // 输出 3-5 个互不重叠窗口（§5 第 7 章）：合并/截断
        windows = normalizeWindowCount(windows);

        log.info("Education Timing 窗口解析完成: {} 个窗口", windows.size());
        return windows;
    }

    /**
     * C13 汇总
     */
    public EducationClaim summarizeTiming(List<Map<String, Object>> windows) {
        EducationClaim claim = new EducationClaim();
        claim.setClaimId("C13");
        claim.setClaimKey("CURRENT_STUDY_ACTIVATION");
        claim.setClaimType("Timing");
        claim.setRenderPermission("DIRECT");
        claim.setSectionOwner("TIMING_WINDOWS");

        if (windows == null || windows.isEmpty()) {
            claim.setState(EducationReportConstants.STATE_INSUFFICIENT);
            claim.setConfidence(EducationReportConstants.CONF_LOW);
            claim.setInsufficiencyReason(EducationReportConstants.ERR_DASHA_REQUIRED_DATA_MISSING);
            return claim;
        }

        long active = windows.stream().filter(w ->
                !"MAINTENANCE".equals(String.valueOf(w.get("window_type")))).count();
        claim.setPrimaryCandidate(active > 0 ? "STAGED_ACTIVATION" : "STEADY_MAINTENANCE");
        claim.setState(active > 0
                ? EducationReportConstants.STATE_SUPPORTED
                : EducationReportConstants.STATE_MIXED);
        claim.setConfidence(EducationReportConstants.CONF_MEDIUM);
        claim.getNotes().add("windows=" + windows.size() + ", active=" + active);
        return claim;
    }

    private List<Map<String, Object>> normalizeWindowCount(List<Map<String, Object>> windows) {
        if (windows.size() <= EducationReportConstants.MAX_TIMING_WINDOWS) {
            if (windows.size() < EducationReportConstants.MIN_TIMING_WINDOWS && !windows.isEmpty()) {
                // 不足 3 个时保留现有窗口（数据如实），Renderer 按实际数量输出
                return windows;
            }
            return windows;
        }
        // 超过 5 个：保留前 5 个（最早开始的优先，保证互不重叠）
        return new ArrayList<>(windows.subList(0, EducationReportConstants.MAX_TIMING_WINDOWS));
    }

    private LocalDate parseDate(String date) {
        if (date == null || date.isEmpty()) return null;
        try {
            if (date.length() > 10) date = date.substring(0, 10);
            return LocalDate.parse(date, DateTimeFormatter.ISO_LOCAL_DATE);
        } catch (Exception e) {
            try {
                return LocalDate.parse(date, DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm"));
            } catch (Exception e2) {
                log.warn("无法解析 Dasha 日期: {}", date);
                return null;
            }
        }
    }
}
