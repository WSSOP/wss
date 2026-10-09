package cn.dct.zpback.controller;

import cn.dct.zpback.dto.ApiResponse;
import cn.dct.zpback.service.VedicAstroService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

/**
 * VedicAstro 占星计算代理控制器
 * 将 Python 占星服务（端口 8088）代理到后端 API
 * 路径前缀：/api/v1/astro
 */
@RestController
@RequestMapping("/v1/astro")
@RequiredArgsConstructor
@Slf4j
public class VedicAstroController {

    private final VedicAstroService vedicAstroService;

    /**
     * POST /api/v1/astro/jyotishya
     * 获取完整占星数据（分盘 + 六力 + 八值图 + 大运）
     */
    @PostMapping("/jyotishya")
    public ApiResponse<Map<String, Object>> getJyotishyaData(@RequestBody Map<String, Object> params) {
        try {
            Map<String, Object> result = vedicAstroService.getJyotishyaData(params);
            return ApiResponse.success(result);
        } catch (Exception e) {
            log.error("获取完整占星数据失败", e);
            return ApiResponse.error(500, e.getMessage());
        }
    }

    /**
     * POST /api/v1/astro/horoscope
     * 获取本命盘全量数据（flatlib）
     */
    @PostMapping("/horoscope")
    public ApiResponse<Map<String, Object>> getHoroscopeData(@RequestBody Map<String, Object> params) {
        try {
            Map<String, Object> result = vedicAstroService.getAllHoroscopeData(params);
            return ApiResponse.success(result);
        } catch (Exception e) {
            log.error("获取本命盘数据失败", e);
            return ApiResponse.error(500, e.getMessage());
        }
    }

    /**
     * POST /api/v1/astro/horary
     * 获取 KP 问事盘数据
     */
    @PostMapping("/horary")
    public ApiResponse<Map<String, Object>> getHoraryData(@RequestBody Map<String, Object> params) {
        try {
            Map<String, Object> result = vedicAstroService.getAllHoraryData(params);
            return ApiResponse.success(result);
        } catch (Exception e) {
            log.error("获取问事盘数据失败", e);
            return ApiResponse.error(500, e.getMessage());
        }
    }

    /**
     * POST /api/v1/astro/vargas
     * 仅获取分盘数据（轻量）
     */
    @PostMapping("/vargas")
    public ApiResponse<Map<String, Object>> getVargaCharts(@RequestBody Map<String, Object> params) {
        try {
            Map<String, Object> result = vedicAstroService.getVargaCharts(params);
            return ApiResponse.success(result);
        } catch (Exception e) {
            log.error("获取分盘数据失败", e);
            return ApiResponse.error(500, e.getMessage());
        }
    }
}
