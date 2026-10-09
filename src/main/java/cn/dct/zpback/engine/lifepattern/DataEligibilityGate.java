package cn.dct.zpback.engine.lifepattern;

import cn.dct.zpback.entity.lifepattern.LifePatternChartData;
import cn.dct.zpback.entity.lifepattern.LifePatternNatalChart;
import cn.dct.zpback.entity.lifepattern.LifePatternPlanetPosition;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.*;

/**
 * 数据验证门
 * 符合 SEALED 文档 01 第 3.1 节规范
 *
 * 阻断级必需数据缺失时，报告不得进入收费后的正式生成
 */
@Component
@Slf4j
public class DataEligibilityGate {

    /**
     * 验证数据是否满足生成条件
     * @return 验证结果，通过返回空列表，失败返回错误码列表
     */
    public List<String> validate(LifePatternChartData chartData) {
        List<String> errors = new ArrayList<>();

        if (chartData == null) {
            errors.add("D1_REQUIRED_DATA_MISSING");
            return errors;
        }

        // D1 上升、十二宫、九颗行星、宫主关系
        LifePatternNatalChart d1 = chartData.getD1();
        if (d1 == null) {
            errors.add("D1_REQUIRED_DATA_MISSING");
            return errors;
        }

        // 检查上升
        if (d1.getAscendantSign() == null || d1.getAscendantSign().isEmpty()) {
            errors.add("D1_REQUIRED_DATA_MISSING");
        }

        // 检查行星
        if (d1.getPlanets() == null || d1.getPlanets().size() < 7) {
            errors.add("D1_REQUIRED_DATA_MISSING");
        }

        // 检查宫位
        if (d1.getHouses() == null || d1.getHouses().size() < 12) {
            errors.add("D1_REQUIRED_DATA_MISSING");
        }

        // 检查 Moon、Ascendant Lord、Atmakaraka
        if (d1.getPlanets() != null) {
            LifePatternPlanetPosition moon = d1.getPlanets().get("Moon");
            if (moon == null || moon.getSign() == null) {
                errors.add("D1_REQUIRED_DATA_MISSING");
            }
        }

        // AK 检查
        if (chartData.getAkPlanet() == null || chartData.getAkPlanet().isEmpty()) {
            errors.add("AK_ASSIGNMENT_INVALID");
        }

        // Rahu/Ketu 检查
        if (d1.getPlanets() != null) {
            LifePatternPlanetPosition rahu = d1.getPlanets().get("Rahu");
            LifePatternPlanetPosition ketu = d1.getPlanets().get("Ketu");
            if (rahu == null && ketu == null) {
                log.warn("Rahu/Ketu 数据缺失");
            }
        }

        return errors;
    }

    /**
     * 检查可降级数据
     * @return 降级说明列表
     */
    public List<String> checkDegradation(LifePatternChartData chartData) {
        List<String> degradationNotes = new ArrayList<>();

        if (chartData == null) return degradationNotes;

        // D9 缺失
        if (chartData.getD9() == null) {
            degradationNotes.add("D9 数据缺失：MATURITY_VALIDATION 不可用");
        }

        // Shadbala 缺失
        if (chartData.getShadbala() == null || chartData.getShadbala().isEmpty()) {
            degradationNotes.add("Shadbala 数据缺失：strength_modifier=UNKNOWN");
        }

        // Dasha 缺失
        if (chartData.getDashaList() == null || chartData.getDashaList().isEmpty()) {
            degradationNotes.add("Dasha 数据缺失：省略 C13 与当前激活章节");
        }

        // Nakshatra 缺失
        if (chartData.getNakshatra() == null || chartData.getNakshatra().isEmpty()) {
            degradationNotes.add("Nakshatra 数据缺失：不使用星宿细化");
        }

        return degradationNotes;
    }
}
