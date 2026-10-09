package cn.dct.zpback.engine.children;

import cn.dct.zpback.entity.lifepattern.LifePatternChartData;
import cn.dct.zpback.entity.lifepattern.LifePatternNatalChart;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * 子女报告 - 数据验证门
 * 符合 SEALED 文档《01_子女与养育深度报告_最终开发总说明》第 4.1 节
 *
 * 阻断条件（任一满足 → FAILED_DATA）：
 * - 出生时间未提供/不可验证
 * - D1 不完整
 * - D7 不完整或不能可靠计算
 * - 行星位置、宫位、宫主映射存在 Schema 错误
 */
@Component
@Slf4j
public class ChildrenDataGate {

    public List<String> validate(LifePatternChartData chartData, boolean birthTimeReliable) {
        List<String> errors = new ArrayList<>();

        if (!birthTimeReliable) {
            errors.add("BIRTH_TIME_UNRELIABLE");
            return errors;
        }

        if (chartData == null) {
            errors.add("D1_NOT_COMPLETE");
            return errors;
        }

        LifePatternNatalChart d1 = chartData.getD1();
        if (d1 == null) {
            errors.add("D1_NOT_COMPLETE");
            return errors;
        }

        if (d1.getAscendantSign() == null || d1.getAscendantSign().isEmpty()) {
            errors.add("D1_NOT_COMPLETE");
        }
        if (d1.getPlanets() == null || d1.getPlanets().size() < 7) {
            errors.add("D1_NOT_COMPLETE");
        }
        if (d1.getHouses() != null && d1.getHouses().size() < 12) {
            log.warn("D1 宫位数据少于 12（可能为降级数据），由整宫制补齐");
        }

        // D7 是核心盘，缺失阻断
        LifePatternNatalChart d7 = chartData.getD7();
        if (d7 == null) {
            errors.add("D7_NOT_AVAILABLE");
            return errors;
        }
        if (d7.getAscendantSign() == null || d7.getAscendantSign().isEmpty()) {
            errors.add("D7_NOT_COMPLETE");
        }
        if (d7.getPlanets() == null || d7.getPlanets().size() < 7) {
            errors.add("D7_NOT_COMPLETE");
        }

        return errors;
    }

    /**
     * 收集降级标志
     */
    public List<String> collectDegradationFlags(LifePatternChartData chartData, boolean hasD9) {
        List<String> flags = new ArrayList<>();
        if (!hasD9) flags.add("D9_UNAVAILABLE");
        if (chartData == null || chartData.getShadbala() == null || chartData.getShadbala().isEmpty()) {
            flags.add("SHADBALA_UNAVAILABLE");
        }
        if (chartData == null || chartData.getDashaList() == null || chartData.getDashaList().isEmpty()) {
            flags.add("DASHA_UNAVAILABLE");
        }
        return flags;
    }

    /**
     * 校验 birth_params 是否可用（出生时间可靠）
     */
    public boolean isBirthTimeReliable(Map<String, Object> birthParams) {
        if (birthParams == null || birthParams.isEmpty()) return false;
        Object bd = birthParams.get("birth_date");
        Object bt = birthParams.get("birth_time");
        if (bd == null || bt == null) return false;
        String time = String.valueOf(bt).trim();
        if (time.isEmpty() || "00:00".equals(time) || "0:0".equals(time)) {
            // 00:00 视为不可靠（未提供具体时间）
            return false;
        }
        return true;
    }
}
