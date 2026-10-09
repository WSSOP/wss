package cn.dct.zpback.engine.synastry;

import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.*;

/**
 * D1 跨盘互动引擎
 * 符合 SYN-STEP3 Cross-Chart Interaction V1 FINAL SEALED
 * 
 * 计算:
 * - Planet → Planet (P2P)
 * - Planet → House (P2H)
 * - A→B / B→A 方向
 */
@Component
@Slf4j
public class CrossChartInteractionEngine {

    // V1 Graha Drishti 规则
    private static final Map<String, List<Integer>> GRAHA_DRISHTI = new HashMap<>();
    static {
        GRAHA_DRISHTI.put("Sun", Arrays.asList(7));
        GRAHA_DRISHTI.put("Moon", Arrays.asList(7));
        GRAHA_DRISHTI.put("Mercury", Arrays.asList(7));
        GRAHA_DRISHTI.put("Venus", Arrays.asList(7));
        GRAHA_DRISHTI.put("Mars", Arrays.asList(4, 7, 8));
        GRAHA_DRISHTI.put("Jupiter", Arrays.asList(5, 7, 9));
        GRAHA_DRISHTI.put("Saturn", Arrays.asList(3, 7, 10));
        GRAHA_DRISHTI.put("Rahu", Arrays.asList(5, 7, 9));
        GRAHA_DRISHTI.put("Ketu", Arrays.asList(5, 7, 9));
    }

    private static final List<String> ALL_PLANETS = Arrays.asList(
            "Sun", "Moon", "Mars", "Mercury", "Jupiter", "Venus", "Saturn", "Rahu", "Ketu"
    );

    /**
     * 计算双人合盘的完整 D1 跨盘互动
     */
    public Map<String, Object> calculateCrossChart(Map<String, Object> chartAData, Map<String, Object> chartBData) {
        Map<String, Object> result = new HashMap<>();

        Map<String, Object> planetsA = extractPlanets(chartAData);
        Map<String, Object> planetsB = extractPlanets(chartBData);

        int ascSignA = extractAscSign(chartAData);
        int ascSignB = extractAscSign(chartBData);

        // 1. Planet → Planet interactions
        List<Map<String, Object>> p2pAB = calculateP2P(planetsA, planetsB, "A", "B");
        List<Map<String, Object>> p2pBA = calculateP2P(planetsB, planetsA, "B", "A");

        // 2. Planet → House interactions
        List<Map<String, Object>> p2hAB = calculateP2H(planetsA, ascSignB, "A", "B");
        List<Map<String, Object>> p2hBA = calculateP2H(planetsB, ascSignA, "B", "A");

        result.put("p2p_ab", p2pAB);
        result.put("p2p_ba", p2pBA);
        result.put("p2h_ab", p2hAB);
        result.put("p2h_ba", p2hBA);
        result.put("summary", buildSummary(p2pAB, p2pBA, p2hAB, p2hBA));

        return result;
    }

    /**
     * 计算 Planet → Planet 互动
     */
    private List<Map<String, Object>> calculateP2P(Map<String, Object> senderPlanets,
                                                     Map<String, Object> receiverPlanets,
                                                     String senderLabel, String receiverLabel) {
        List<Map<String, Object>> interactions = new ArrayList<>();

        for (String senderPlanet : ALL_PLANETS) {
            Map<String, Object> senderP = (Map<String, Object>) senderPlanets.get(senderPlanet);
            if (senderP == null) continue;

            int senderSign = getSignIndex((String) senderP.get("sign"));
            double senderDeg = getDegree(senderP);

            for (String receiverPlanet : ALL_PLANETS) {
                Map<String, Object> receiverP = (Map<String, Object>) receiverPlanets.get(receiverPlanet);
                if (receiverP == null) continue;

                int receiverSign = getSignIndex((String) receiverP.get("sign"));
                double receiverDeg = getDegree(receiverP);

                // 检查 conjunction (同星座)
                boolean isConjunction = (senderSign == receiverSign);

                // 检查 Graha Drishti
                boolean isDrishti = false;
                int drishtiTargetSign = (senderSign + getDrishtiHouse(senderPlanet) - 1) % 12 + 1;
                if (receiverSign == drishtiTargetSign && !isConjunction) {
                    isDrishti = true;
                }

                if (isConjunction || isDrishti) {
                    double degreeDelta = Math.abs(senderDeg - receiverDeg);
                    String tightness = getTightnessLabel(degreeDelta);
                    String aspectType = isConjunction ? "conjunction" : "drishti";

                    Map<String, Object> interaction = new HashMap<>();
                    interaction.put("sender", senderLabel + "_" + senderPlanet);
                    interaction.put("receiver", receiverLabel + "_" + receiverPlanet);
                    interaction.put("sender_planet", senderPlanet);
                    interaction.put("receiver_planet", receiverPlanet);
                    interaction.put("aspect_type", aspectType);
                    interaction.put("sender_sign", senderP.get("sign"));
                    interaction.put("receiver_sign", receiverP.get("sign"));
                    interaction.put("sender_degree", senderDeg);
                    interaction.put("receiver_degree", receiverDeg);
                    interaction.put("degree_delta", degreeDelta);
                    interaction.put("tightness", tightness);
                    interaction.put("direction", senderLabel + "→" + receiverLabel);
                    interactions.add(interaction);
                }
            }
        }

        return interactions;
    }

    /**
     * 计算 Planet → House overlay
     * Whole Sign: receiver_house = ((sender_sign_index - receiver_asc_sign_index) mod 12) + 1
     */
    private List<Map<String, Object>> calculateP2H(Map<String, Object> senderPlanets,
                                                    int receiverAscSign,
                                                    String senderLabel, String receiverLabel) {
        List<Map<String, Object>> overlays = new ArrayList<>();

        for (String planetName : ALL_PLANETS) {
            Map<String, Object> planet = (Map<String, Object>) senderPlanets.get(planetName);
            if (planet == null) continue;

            int senderSign = getSignIndex((String) planet.get("sign"));
            int overlayHouse = ((senderSign - receiverAscSign + 12) % 12) + 1;

            Map<String, Object> overlay = new HashMap<>();
            overlay.put("sender", senderLabel + "_" + planetName);
            overlay.put("planet", planetName);
            overlay.put("sign", planet.get("sign"));
            overlay.put("overlay_house", overlayHouse);
            overlay.put("receiver", receiverLabel);
            overlay.put("direction", senderLabel + "→" + receiverLabel);
            overlays.add(overlay);
        }

        return overlays;
    }

    private Map<String, Object> buildSummary(List<Map<String, Object>> p2pAB,
                                               List<Map<String, Object>> p2pBA,
                                               List<Map<String, Object>> p2hAB,
                                               List<Map<String, Object>> p2hBA) {
        Map<String, Object> summary = new HashMap<>();
        summary.put("total_p2p_interactions", p2pAB.size() + p2pBA.size());
        summary.put("total_p2h_overlays", p2hAB.size() + p2hBA.size());
        summary.put("a_to_b_aspects", p2pAB.size());
        summary.put("b_to_a_aspects", p2pBA.size());
        return summary;
    }

    /**
     * 从星盘数据中提取行星位置
     */
    private Map<String, Object> extractPlanets(Map<String, Object> chartData) {
        Map<String, Object> planets = new HashMap<>();

        // 从 vargas.D1.planets 提取
        Map<String, Object> vargas = (Map<String, Object>) chartData.getOrDefault("vargas", Collections.emptyMap());
        Map<String, Object> d1 = (Map<String, Object>) vargas.getOrDefault("D1", Collections.emptyMap());
        Map<String, Object> d1Planets = (Map<String, Object>) d1.getOrDefault("planets", Collections.emptyMap());

        for (Map.Entry<String, Object> entry : d1Planets.entrySet()) {
            String planetName = entry.getKey();
            Map<String, Object> planetData = (Map<String, Object>) entry.getValue();

            Map<String, Object> normalized = new HashMap<>();
            normalized.put("sign", planetData.getOrDefault("sign", ""));
            normalized.put("house", planetData.getOrDefault("house-num", 0));

            // 提取度数
            Map<String, Object> pos = (Map<String, Object>) planetData.getOrDefault("pos", Collections.emptyMap());
            double decDeg = 0;
            if (pos.containsKey("dec_deg")) {
                Object degObj = pos.get("dec_deg");
                if (degObj instanceof Number) {
                    decDeg = ((Number) degObj).doubleValue();
                }
            } else if (pos.containsKey("deg")) {
                Object degObj = pos.get("deg");
                if (degObj instanceof Number) {
                    decDeg = ((Number) degObj).doubleValue();
                }
            }
            normalized.put("degree", decDeg);

            planets.put(planetName, normalized);
        }

        return planets;
    }

    private int extractAscSign(Map<String, Object> chartData) {
        Map<String, Object> vargas = (Map<String, Object>) chartData.getOrDefault("vargas", Collections.emptyMap());
        Map<String, Object> d1 = (Map<String, Object>) vargas.getOrDefault("D1", Collections.emptyMap());
        Map<String, Object> ascendant = (Map<String, Object>) d1.getOrDefault("ascendant", Collections.emptyMap());
        String sign = (String) ascendant.getOrDefault("sign", "Aries");
        return getSignIndex(sign);
    }

    private int getDrishtiHouse(String planet) {
        List<Integer> houses = GRAHA_DRISHTI.getOrDefault(planet, Arrays.asList(7));
        return houses.get(0); // 返回第一个 drishti house
    }

    private String getTightnessLabel(double degreeDelta) {
        if (degreeDelta <= 1) return "exact_critical";
        if (degreeDelta <= 3) return "very_tight";
        if (degreeDelta <= 6) return "tight";
        if (degreeDelta <= 10) return "moderate";
        if (degreeDelta <= 15) return "loose";
        if (degreeDelta <= 20) return "broad";
        return "very_broad";
    }

    private int getSignIndex(String sign) {
        Map<String, Integer> signMap = new HashMap<>();
        signMap.put("Aries", 1); signMap.put("Taurus", 2); signMap.put("Gemini", 3);
        signMap.put("Cancer", 4); signMap.put("Leo", 5); signMap.put("Virgo", 6);
        signMap.put("Libra", 7); signMap.put("Scorpio", 8); signMap.put("Sagittarius", 9);
        signMap.put("Capricorn", 10); signMap.put("Aquarius", 11); signMap.put("Pisces", 12);
        return signMap.getOrDefault(sign, 1);
    }

    private double getDegree(Map<String, Object> planet) {
        Object deg = planet.get("degree");
        if (deg instanceof Number) return ((Number) deg).doubleValue();
        return 0;
    }
}
