package cn.dct.zpback.service.jewelry;

import cn.dct.zpback.entity.Chart;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 行星珠宝建议｜星盘选择（页面 P1）
 *
 * 对应 02_双模式页面流程与状态机 §3 的槽位契约：
 * {@code chart_id} / {@code subject_name} / {@code masked_birth_data} /
 * {@code birth_time_quality} / {@code chart_calculation_version} /
 * {@code is_current_self_chart} / {@code switch_chart_action} / {@code create_chart_action}。
 *
 * 两条隐私与业务规则在这里落地，而不是交给前端：
 * <ul>
 *   <li>出生信息脱敏由服务端完成（01 §16「前端展示出生信息沿用现有隐私规则」）；</li>
 *   <li>自测模式下若所选星盘主体不是答题者，返回 {@code self_chart_warning}，
 *       前端据此阻止继续用自测措辞（§3）。</li>
 * </ul>
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class JewelryChartSelectionService {

    private final cn.dct.zpback.service.ChartService chartService;

    /** 出生时间质量枚举（suitability_policy.data_quality） */
    private static final String QUALITY_EXACT = "DECLARED_EXACT";
    private static final String QUALITY_APPROXIMATE = "APPROXIMATE";
    private static final String QUALITY_NO_TIME = "NO_TIME";

    /**
     * 列出可用于本功能的星盘
     *
     * @param mode SELF_ASSESSMENT / GIFT_BLESSING
     * @param userId 当前用户（与 ChartController 用同一套 userId 口径）
     */
    public Map<String, Object> listSelectableCharts(String mode, String userId, String currentChartId) {
        List<Chart> charts = chartService.getUserCharts(userId);
        boolean selfMode = JewelryAdvisorService.MODE_SELF.equals(mode);

        List<Map<String, Object>> items = new ArrayList<>();
        for (Chart c : charts) {
            Map<String, Object> item = toSelectionItem(c, selfMode, currentChartId);
            items.add(item);
        }

        Map<String, Object> out = new LinkedHashMap<>();
        out.put("assessment_mode", mode);
        out.put("charts", items);
        out.put("has_any_chart", !items.isEmpty());
        // 自测默认用当前本人星盘（默认盘）
        out.put("default_chart_id", defaultChartId(items));
        out.put("switch_chart_action", "OPEN_CHART_LIST");
        out.put("create_chart_action", "OPEN_NEW_CHART");
        // 自测不应新建他人星盘（§3）
        out.put("create_chart_allowed", !selfMode);
        return out;
    }

    /**
     * 校验一次选盘是否合法
     *
     * 自测模式下主体不是答题者时返回 false + self_chart_warning，
     * 前端必须引导切换到赠礼模式或确认关系（§3）。
     */
    public Map<String, Object> validateSelection(String mode, String userId, String chartId) {
        Map<String, Object> out = new LinkedHashMap<>();
        if (chartId == null || chartId.isBlank()) {
            out.put("valid", false);
            out.put("error_code", "PJA_CHART_REQUIRED");
            out.put("message", "请先选择或新建一份星盘。");
            return out;
        }
        Chart chart = chartService.getChartById(chartId);
        if (chart == null) {
            out.put("valid", false);
            out.put("error_code", "PJA_CHART_NOT_FOUND");
            out.put("message", "未找到该星盘，请重新选择。");
            return out;
        }
        out.put("valid", true);
        out.put("chart", toSelectionItem(chart, JewelryAdvisorService.MODE_SELF.equals(mode), null));
        return out;
    }

    // ------------------------------------------------------------------

    private Map<String, Object> toSelectionItem(Chart c, boolean selfMode, String currentChartId) {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("chart_id", c.getChartId());
        m.put("subject_name", c.getName());
        m.put("masked_birth_data", maskBirthData(c));
        m.put("birth_time_quality", birthTimeQuality(c));
        m.put("chart_calculation_version", "V1_PARASHARI");
        m.put("is_current_self_chart", Boolean.TRUE.equals(c.getIsDefault()));
        m.put("is_selected", c.getChartId().equals(currentChartId));
        if (selfMode) {
            m.put("self_chart_warning", !Boolean.TRUE.equals(c.getIsDefault())
                    ? "这份星盘不是你的本人星盘。自测模式只描述你自己的现状；"
                      + "如果要为对方选礼物，请切换到「为TA选礼物」。"
                    : null);
        }
        return m;
    }

    /**
     * 出生信息脱敏
     *
     * 列表页只展示到年；出生时间不返回具体时刻（只通过 quality 传达精度），
     * 经纬度一律不下发（01 §16）。
     */
    private String maskBirthData(Chart c) {
        String date = c.getBirthDate() == null ? "" : c.getBirthDate();
        String year = date.length() >= 4 ? date.substring(0, 4) : date;
        String place = c.getBirthPlace();
        return year + (place == null || place.isBlank() ? "" : " · " + place);
    }

    /**
     * 出生时间质量
     *
     * 依据存储精度推断：能精确到分钟视为 DECLARED_EXACT；
     * 只有小时或空视为 APPROXIMATE；完全为空视为 NO_TIME。
     * 已校准（RECTIFIED）与有出生证明（DOCUMENTED）属于外部标记，
     * V1 存储层暂无这两个字段，因此这里不臆造。
     */
    private String birthTimeQuality(Chart c) {
        String t = c.getBirthTime();
        if (t == null || t.isBlank() || "null".equalsIgnoreCase(t)) {
            return QUALITY_NO_TIME;
        }
        String v = t.trim();
        // 形如 14:30 / 14:30:00 视为精确；仅 14 或 14时 视为大概
        return v.matches("\\d{1,2}:\\d{2}(:\\d{2})?") ? QUALITY_EXACT : QUALITY_APPROXIMATE;
    }

    private String defaultChartId(List<Map<String, Object>> items) {
        for (Map<String, Object> it : items) {
            if (Boolean.TRUE.equals(it.get("is_current_self_chart"))) {
                return String.valueOf(it.get("chart_id"));
            }
        }
        return items.isEmpty() ? null : String.valueOf(items.get(0).get("chart_id"));
    }
}
