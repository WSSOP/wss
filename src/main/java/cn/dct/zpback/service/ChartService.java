package cn.dct.zpback.service;

import cn.dct.zpback.entity.Chart;
import cn.dct.zpback.mapper.ChartMapper;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

/**
 * 星盘服务
 */
@Service
@Slf4j
public class ChartService {

    @Autowired
    private ChartMapper chartMapper;

    /**
     * 创建星盘
     */
    public Chart createChart(String userId, String name, String gender, String birthDate,
                             String birthTime, String birthPlace, Double latitude, Double longitude) {
        Chart chart = new Chart();
        chart.setChartId(generateChartId());
        chart.setUserId(userId);
        chart.setName(name);
        chart.setGender(gender);
        chart.setBirthDate(birthDate);
        chart.setBirthTime(birthTime);
        chart.setBirthPlace(birthPlace);
        chart.setLatitude(latitude);
        chart.setLongitude(longitude);
        chart.setTimezone("Asia/Shanghai");
        chart.setIsDefault(false);
        chart.setCreatedAt(LocalDateTime.now());
        chart.setUpdatedAt(LocalDateTime.now());

        chartMapper.insert(chart);
        log.info("星盘创建成功: chartId={}, userId={}", chart.getChartId(), userId);
        return chart;
    }

    /**
     * 根据ID查询星盘
     */
    public Chart getChartById(String chartId) {
        return chartMapper.selectById(chartId);
    }

    /**
     * 查询用户的所有星盘
     */
    public List<Chart> getUserCharts(String userId) {
        return chartMapper.selectByUserId(userId);
    }

    /**
     * 查询用户的默认星盘
     */
    public Chart getDefaultChart(String userId) {
        return chartMapper.selectDefaultByUserId(userId);
    }

    /**
     * 更新星盘
     */
    public Chart updateChart(Chart chart) {
        chart.setUpdatedAt(LocalDateTime.now());
        chartMapper.update(chart);
        return chart;
    }

    /**
     * 删除星盘
     */
    public boolean deleteChart(String chartId) {
        return chartMapper.deleteById(chartId) > 0;
    }

    /**
     * 设置默认星盘
     */
    public void setDefaultChart(String chartId, String userId) {
        chartMapper.clearDefaultByUserId(userId);
        chartMapper.setDefault(chartId, userId);
    }

    private String generateChartId() {
        return "chart_" + UUID.randomUUID().toString().replace("-", "").substring(0, 16);
    }
}
