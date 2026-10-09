package cn.dct.zpback.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Service;
import org.springframework.web.reactive.function.client.WebClient;
import org.springframework.web.reactive.function.client.WebClientResponseException;
import reactor.core.publisher.Mono;

import java.time.Duration;
import java.util.Map;

/**
 * VedicAstro Python 占星服务代理
 * 通过 WebFlux 调用本地 Python 服务（端口 8088）
 * 接口文档：http://127.0.0.1:8088/docs
 */
@Service
@Slf4j
@RequiredArgsConstructor
public class VedicAstroService {

    @Value("${vedic-astro.base-url:http://127.0.0.1:8088}")
    private String baseUrl;

    @Value("${vedic-astro.timeout:30000}")
    private long timeout;

    private final WebClient.Builder webClientBuilder;

    /** 懒加载 WebClient */
    private WebClient getClient() {
        return webClientBuilder
                .baseUrl(baseUrl)
                .build();
    }

    // ============================================================
    // 接口 1：本命盘全量数据 (flatlib)
    // POST /get_all_horoscope_data
    // ============================================================

    /**
     * 获取本命盘全量数据
     * @param params 出生信息参数
     * @return 行星、宫位、相位、大运等数据
     */
    public Map<String, Object> getAllHoroscopeData(Map<String, Object> params) {
        log.info("调用 VedicAstro 本命盘接口, params={}", params);
        try {
            return getClient()
                    .post()
                    .uri("/get_all_horoscope_data")
                    .contentType(MediaType.APPLICATION_JSON)
                    .bodyValue(params)
                    .retrieve()
                    .bodyToMono(Map.class)
                    .timeout(Duration.ofMillis(timeout))
                    .block();
        } catch (WebClientResponseException e) {
            log.error("VedicAstro 本命盘接口调用失败: status={}, body={}", e.getStatusCode(), e.getResponseBodyAsString());
            throw new RuntimeException("占星计算服务异常: " + e.getMessage());
        } catch (Exception e) {
            log.error("VedicAstro 本命盘接口调用异常", e);
            throw new RuntimeException("占星计算服务不可用");
        }
    }

    // ============================================================
    // 接口 2：KP 问事盘
    // POST /get_all_horary_data
    // ============================================================

    /**
     * 获取 KP 问事盘数据
     * @param params 问事参数（含 horary_number 1-249）
     * @return 匹配的精确时间和占星数据
     */
    public Map<String, Object> getAllHoraryData(Map<String, Object> params) {
        log.info("调用 VedicAstro KP问事盘接口, params={}", params);
        try {
            return getClient()
                    .post()
                    .uri("/get_all_horary_data")
                    .contentType(MediaType.APPLICATION_JSON)
                    .bodyValue(params)
                    .retrieve()
                    .bodyToMono(Map.class)
                    .timeout(Duration.ofMillis(timeout))
                    .block();
        } catch (WebClientResponseException e) {
            log.error("VedicAstro KP问事盘接口调用失败: status={}", e.getStatusCode());
            throw new RuntimeException("问事盘计算异常: " + e.getMessage());
        } catch (Exception e) {
            log.error("VedicAstro KP问事盘接口调用异常", e);
            throw new RuntimeException("问事盘服务不可用");
        }
    }

    // ============================================================
    // 接口 3：完整占星数据（推荐，含分盘、Shadbala、AshtakaVarga）
    // POST /get_jyotishya_data
    // ============================================================

    /**
     * 获取完整占星数据（分盘 + 六力 + 八值图 + 大运）
     * @param params 出生信息
     * @return 完整占星数据 Map
     */
    public Map<String, Object> getJyotishyaData(Map<String, Object> params) {
        log.info("调用 VedicAstro 完整数据接口, name={}, year={}",
                params.get("name"), params.get("year"));
        try {
            return getClient()
                    .post()
                    .uri("/get_jyotishya_data")
                    .contentType(MediaType.APPLICATION_JSON)
                    .bodyValue(params)
                    .retrieve()
                    .bodyToMono(Map.class)
                    .timeout(Duration.ofMillis(timeout))
                    .block();
        } catch (WebClientResponseException e) {
            log.error("VedicAstro 完整数据接口调用失败: status={}, body={}", e.getStatusCode(), e.getResponseBodyAsString());
            throw new RuntimeException("占星数据计算异常: " + e.getMessage());
        } catch (Exception e) {
            log.error("VedicAstro 完整数据接口调用异常", e);
            throw new RuntimeException("占星数据服务不可用");
        }
    }

    // ============================================================
    // 接口 4：仅分盘数据（轻量）
    // POST /get_varga_charts
    // ============================================================

    /**
     * 仅获取分盘数据（响应更小更快）
     * @param params 出生信息
     * @return 分盘数据 Map
     */
    public Map<String, Object> getVargaCharts(Map<String, Object> params) {
        log.info("调用 VedicAstro 分盘接口, name={}", params.get("name"));
        try {
            return getClient()
                    .post()
                    .uri("/get_varga_charts")
                    .contentType(MediaType.APPLICATION_JSON)
                    .bodyValue(params)
                    .retrieve()
                    .bodyToMono(Map.class)
                    .timeout(Duration.ofMillis(timeout))
                    .block();
        } catch (WebClientResponseException e) {
            log.error("VedicAstro 分盘接口调用失败: status={}, body={}", e.getStatusCode(), e.getResponseBodyAsString());
            throw new RuntimeException("分盘计算异常: " + e.getMessage());
        } catch (Exception e) {
            log.error("VedicAstro 分盘接口调用异常", e);
            throw new RuntimeException("分盘服务不可用");
        }
    }
}
