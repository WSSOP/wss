package cn.dct.zpback.engine.synastry;

import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.*;
import java.util.stream.Collectors;

/**
 * 双人合盘证据引擎
 * 符合 SYN-STEP5 Evidence Selection Rule V1 FINAL SEALED
 */
@Component
@Slf4j
public class SynastryEvidenceEngine {

    // 12 Selector Families (语义筛选器)
    private static final Map<String, List<String>> SELECTOR_FAMILIES = new HashMap<>();
    static {
        SELECTOR_FAMILIES.put("attraction", Arrays.asList("Venus", "Mars", "Moon", "5H", "7H"));
        SELECTOR_FAMILIES.put("emotional", Arrays.asList("Moon", "Venus", "4H", "7H"));
        SELECTOR_FAMILIES.put("communication", Arrays.asList("Mercury", "3H", "9H"));
        SELECTOR_FAMILIES.put("conflict", Arrays.asList("Mars", "Saturn", "6H", "8H", "12H"));
        SELECTOR_FAMILIES.put("intimacy", Arrays.asList("Venus", "Mars", "8H", "7H"));
        SELECTOR_FAMILIES.put("support", Arrays.asList("Jupiter", "Venus", "6H", "11H"));
        SELECTOR_FAMILIES.put("role", Arrays.asList("Sun", "Saturn", "10H", "7H"));
        SELECTOR_FAMILIES.put("resource", Arrays.asList("Jupiter", "Venus", "2H", "8H", "11H"));
        SELECTOR_FAMILIES.put("values", Arrays.asList("Jupiter", "Sun", "9H", "1H"));
        SELECTOR_FAMILIES.put("long_term", Arrays.asList("Saturn", "Jupiter", "7H", "9H", "10H"));
        SELECTOR_FAMILIES.put("growth", Arrays.asList("Jupiter", "Uranus", "Rahu", "9H", "11H"));
        SELECTOR_FAMILIES.put("family", Arrays.asList("Moon", "Sun", "4H", "10H"));
    }

    /**
     * 为当前 Intent 选择 3-5 条核心证据
     */
    public List<Map<String, Object>> selectEvidence(Map<String, Object> crossChartData,
                                                      String relationshipType,
                                                      String intentKey,
                                                      String topic) {
        List<Map<String, Object>> allP2P = new ArrayList<>();
        allP2P.addAll((List<Map<String, Object>>) crossChartData.getOrDefault("p2p_ab", Collections.emptyList()));
        allP2P.addAll((List<Map<String, Object>>) crossChartData.getOrDefault("p2p_ba", Collections.emptyList()));

        List<Map<String, Object>> allP2H = new ArrayList<>();
        allP2H.addAll((List<Map<String, Object>>) crossChartData.getOrDefault("p2h_ab", Collections.emptyList()));
        allP2H.addAll((List<Map<String, Object>>) crossChartData.getOrDefault("p2h_ba", Collections.emptyList()));

        // 1. 根据 topic 确定 selector family
        String selectorFamily = getSelectorFamily(topic);

        // 2. 筛选 Primary Evidence (P2P)
        List<Map<String, Object>> primaryEvidence = allP2P.stream()
                .filter(e -> isRelevantForIntent(e, selectorFamily, relationshipType))
                .sorted(this::compareByTightness)
                .limit(3)
                .collect(Collectors.toList());

        // 3. 筛选 P2H overlay (仅当有明确意义时)
        List<Map<String, Object>> p2hEvidence = allP2H.stream()
                .filter(e -> isRelevantHouseOverlay(e, selectorFamily, relationshipType))
                .limit(2)
                .collect(Collectors.toList());

        // 4. 合并证据
        List<Map<String, Object>> selectedEvidence = new ArrayList<>();
        selectedEvidence.addAll(primaryEvidence);
        selectedEvidence.addAll(p2hEvidence);

        // 5. 添加 evidence role
        for (int i = 0; i < selectedEvidence.size(); i++) {
            Map<String, Object> evidence = selectedEvidence.get(i);
            if (i < 2) {
                evidence.put("role", "PRIMARY");
            } else {
                evidence.put("role", "SECONDARY");
            }
        }

        return selectedEvidence;
    }

    private String getSelectorFamily(String topic) {
        Map<String, String> topicToFamily = new HashMap<>();
        topicToFamily.put("attraction_chemistry", "attraction");
        topicToFamily.put("emotional_interaction", "emotional");
        topicToFamily.put("communication_mental", "communication");
        topicToFamily.put("conflict_power_boundaries", "conflict");
        topicToFamily.put("intimacy_trust", "intimacy");
        topicToFamily.put("long_term_partnership", "long_term");
        topicToFamily.put("marriage", "long_term");
        topicToFamily.put("business_collaboration", "role");
        topicToFamily.put("family_kinship", "family");
        topicToFamily.put("influence_growth", "growth");
        topicToFamily.put("relationship_core", "values");
        return topicToFamily.getOrDefault(topic, "values");
    }

    private boolean isRelevantForIntent(Map<String, Object> p2p, String selectorFamily, String relationshipType) {
        String senderPlanet = (String) p2p.get("sender_planet");
        String receiverPlanet = (String) p2p.get("receiver_planet");

        List<String> relevantPlanets = SELECTOR_FAMILIES.getOrDefault(selectorFamily, Collections.emptyList());

        boolean senderRelevant = relevantPlanets.contains(senderPlanet);
        boolean receiverRelevant = relevantPlanets.contains(receiverPlanet);

        // 至少一侧相关
        return senderRelevant || receiverRelevant;
    }

    private boolean isRelevantHouseOverlay(Map<String, Object> p2h, String selectorFamily, String relationshipType) {
        int overlayHouse = (int) p2h.getOrDefault("overlay_house", 0);
        List<String> relevantItems = SELECTOR_FAMILIES.getOrDefault(selectorFamily, Collections.emptyList());

        // 检查 overlay house 是否相关
        String houseKey = overlayHouse + "H";
        return relevantItems.contains(houseKey);
    }

    private int compareByTightness(Map<String, Object> a, Map<String, Object> b) {
        Map<String, Integer> tightnessOrder = new HashMap<>();
        tightnessOrder.put("exact_critical", 0);
        tightnessOrder.put("very_tight", 1);
        tightnessOrder.put("tight", 2);
        tightnessOrder.put("moderate", 3);
        tightnessOrder.put("loose", 4);
        tightnessOrder.put("broad", 5);
        tightnessOrder.put("very_broad", 6);

        String tA = (String) a.getOrDefault("tightness", "very_broad");
        String tB = (String) b.getOrDefault("tightness", "very_broad");
        return Integer.compare(tightnessOrder.getOrDefault(tA, 6), tightnessOrder.getOrDefault(tB, 6));
    }
}
