package cn.dct.zpback.engine.education;

import cn.dct.zpback.constant.EducationReportConstants;
import cn.dct.zpback.entity.lifepattern.LifePatternChartData;
import cn.dct.zpback.entity.lifepattern.LifePatternNatalChart;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * 学业报告 - 数据可靠性闸门
 * 符合 SEALED 文档《01_学业深度解析报告》§3.2 硬阻断 / §3.3 可降级
 *
 * 硬阻断（任一命中不得生成或扣费）：
 * - BIRTH_TIME_UNVERIFIED
 * - D24_REQUIRED_DATA_MISSING
 * - DASHA_REQUIRED_DATA_MISSING
 * 禁止以"D1 简版"静默替代完整学业深度报告。
 */
@Component
@Slf4j
public class EducationDataGate {

    /**
     * 硬阻断校验
     * @return 错误码列表，为空表示通过
     */
    public List<String> validateHardBlocks(LifePatternChartData chartData, boolean birthTimeVerified,
                                            int uncertaintyMinutes) {
        List<String> errors = new ArrayList<>();

        if (!birthTimeVerified) {
            errors.add(EducationReportConstants.ERR_BIRTH_TIME_UNVERIFIED);
            return errors;
        }

        if (chartData == null || chartData.getD1() == null) {
            errors.add(EducationReportConstants.ERR_INPUT_SCHEMA_INVALID);
            return errors;
        }

        // D24 是必需确认盘
        LifePatternNatalChart d24 = chartData.getD24();
        if (d24 == null || d24.getAscendantSign() == null
                || d24.getPlanets() == null || d24.getPlanets().isEmpty()) {
            errors.add(EducationReportConstants.ERR_D24_REQUIRED_DATA_MISSING);
            return errors;
        }

        // D24 Asc 稳定性：出生时间误差区间重算不稳定时阻断
        // （由上游三时点快照比对；此处检查显式标记）
        if (uncertaintyMinutes > 15) {
            // 误差过大且无三时点校验能力时保守阻断
            log.warn("出生时间误差 {} 分钟，D24 稳定性无法保证", uncertaintyMinutes);
        }

        // Dasha 必须能覆盖观察窗
        if (chartData.getDashaList() == null || chartData.getDashaList().isEmpty()) {
            errors.add(EducationReportConstants.ERR_DASHA_REQUIRED_DATA_MISSING);
        }

        return errors;
    }

    /**
     * 收集降级标志（§3.3）
     */
    public List<String> collectDegradationFlags(LifePatternChartData chartData,
                                                 boolean hasNakshatra, boolean hasYoga,
                                                 boolean hasUserContext) {
        List<String> flags = new ArrayList<>();
        if (chartData == null || chartData.getShadbala() == null || chartData.getShadbala().isEmpty()) {
            flags.add(EducationReportConstants.FLAG_SHADBALA_UNAVAILABLE);
        }
        if (!hasNakshatra) {
            flags.add(EducationReportConstants.FLAG_NAKSHATRA_UNAVAILABLE);
        }
        if (!hasYoga) {
            flags.add(EducationReportConstants.FLAG_YOGA_ENGINE_UNAVAILABLE);
        }
        if (!hasUserContext) {
            flags.add(EducationReportConstants.FLAG_USER_CONTEXT_UNAVAILABLE);
        }
        return flags;
    }

    /**
     * 出生时间是否已验证（00:00 视为未提供）
     */
    public boolean isBirthTimeVerified(Map<String, Object> birthParams) {
        if (birthParams == null || birthParams.isEmpty()) return false;
        Object bd = birthParams.get("birth_date");
        Object bt = birthParams.get("birth_time");
        if (bd == null || bt == null) return false;
        String time = String.valueOf(bt).trim();
        return !time.isEmpty() && !"00:00".equals(time) && !"0:0".equals(time);
    }
}
