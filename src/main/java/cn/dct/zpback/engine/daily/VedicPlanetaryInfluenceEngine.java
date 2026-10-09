package cn.dct.zpback.engine.daily;

import lombok.Data;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * 公共印占行星影响引擎（VedicPlanetaryInfluenceEngine）
 *
 * Source of Truth：`问星渡_公共印占行星相位引擎_代码与双人合盘复用说明_V1`
 * （该文档明确说明此前只有机器配置 / Schema / 公式，没有 Java 实现，需程序员实现一次公共 Engine。）
 *
 * 复用范围：Prashna / Natal / Synastry / Birth Time Rectification 共用本 Engine，
 * 不允许为今日观星或双人合盘各写一套相位体系。
 *
 * 锁定口径：
 * - Sidereal + Whole Sign，宫位从 source house 起算（inclusive）；
 * - 九大行星 influence positions；
 * - aspect_position = 1 → placement_influence；> 1 → graha_drishti；
 * - 同宫只生成 conjunction，不再重复生成 1st aspect；
 * - degree exactness 只决定“已成立关系有多紧”，不决定关系是否成立；
 * - mutual_graha_drishti 只做聚合标记，不产生额外证据；
 * - 禁止西占 aspect / orb / Tajika 动态相位 / general partial aspects。
 */
public final class VedicPlanetaryInfluenceEngine {

    /** 九大行星影响位置（相对 position，1 = 本宫 placement） */
    public static final Map<String, Set<Integer>> INFLUENCE_POSITIONS;
    static {
        Map<String, Set<Integer>> m = new LinkedHashMap<>();
        m.put("Sun", Set.of(1, 7));
        m.put("Moon", Set.of(1, 7));
        m.put("Mercury", Set.of(1, 7));
        m.put("Venus", Set.of(1, 7));
        m.put("Mars", Set.of(1, 4, 7, 8));
        m.put("Jupiter", Set.of(1, 5, 7, 9));
        m.put("Saturn", Set.of(1, 3, 7, 10));
        m.put("Rahu", Set.of(1, 5, 7, 9));
        m.put("Ketu", Set.of(1, 5, 7, 9));
        INFLUENCE_POSITIONS = Collections.unmodifiableMap(m);
    }

    /** aspect_position → 精确角度（度） */
    public static final Map<Integer, Integer> ASPECT_POSITION_TO_EXACT_ANGLE;
    static {
        Map<Integer, Integer> m = new LinkedHashMap<>();
        m.put(1, 0);
        m.put(3, 60);
        m.put(4, 90);
        m.put(5, 120);
        m.put(7, 180);
        m.put(8, 210);
        m.put(9, 240);
        m.put(10, 270);
        ASPECT_POSITION_TO_EXACT_ANGLE = Collections.unmodifiableMap(m);
    }

    public static final String TYPE_PLACEMENT = "placement_influence";
    public static final String TYPE_DRISHTI = "graha_drishti";
    public static final String TYPE_CONJUNCTION = "conjunction";

    private VedicPlanetaryInfluenceEngine() {
    }

    /** 行星位置（星座序号 0=Aries + 星座内度数） */
    @Data
    public static class PlanetPos {
        private String planet;
        private int signIndex;
        private double degreeInSign;
        /** 星宿英文名（星历服务原始值，仅用于 Moon 时间轴展示，不参与相位计算） */
        private String nakshatra;

        public PlanetPos(String planet, int signIndex, double degreeInSign) {
            this.planet = planet;
            this.signIndex = signIndex;
            this.degreeInSign = degreeInSign;
        }
    }

    /** Planet → Planet 关系 */
    @Data
    public static class PlanetRelation {
        private String relationType;
        private String sourcePlanet;
        private String targetPlanet;
        private int sourceHouse;
        private int targetHouse;
        private int aspectPosition;
        private int exactAngleDeg;
        private double degreeDeltaDeg;
        private double exactnessScore;
        private String exactnessClass;
        private String strength;
        private boolean valid = true;
        private boolean bidirectional;
    }

    /** Planet → House 影响 */
    @Data
    public static class HouseInfluence {
        private String sourcePlanet;
        private int sourceHouse;
        private int targetHouse;
        private int aspectPosition;
        private String influenceType;
        private int exactAngleDeg;
    }

    // ============================================================
    // 基础公式
    // ============================================================

    /** 相对位置：target 相对 source 的 1-based position */
    public static int relativePosition(int sourceSign, int targetSign) {
        return Math.floorMod(targetSign - sourceSign, 12) + 1;
    }

    /** Planet → House：((source_house + aspect_position - 2) mod 12) + 1 */
    public static int targetHouse(int sourceHouse, int aspectPosition) {
        return ((sourceHouse + aspectPosition - 2) % 12) + 1;
    }

    public static double degreeDelta(PlanetPos source, PlanetPos target) {
        return Math.abs(source.getDegreeInSign() - target.getDegreeInSign());
    }

    /** exactness_score = max(0, 1 - delta/30)，不是概率 */
    public static double exactnessScore(double degreeDeltaDeg) {
        return Math.max(0, 1 - degreeDeltaDeg / 30.0);
    }

    /** 度数紧密度档位 + coarse_strength */
    public static String[] exactnessBand(double delta) {
        if (delta <= 1) return new String[]{"exact_critical", "strong"};
        if (delta <= 3) return new String[]{"very_tight", "strong"};
        if (delta <= 6) return new String[]{"tight", "strong"};
        if (delta <= 10) return new String[]{"moderate", "medium"};
        if (delta <= 15) return new String[]{"loose", "medium"};
        if (delta <= 20) return new String[]{"broad", "weak"};
        return new String[]{"very_broad", "weak"};
    }

    // ============================================================
    // Planet → Planet（同一张盘内：transit 盘或本命盘内部）
    // ============================================================

    /**
     * 单盘内 Planet → Planet：同宫 conjunction + influence position graha_drishti。
     * 同宫只生成 conjunction（按无序 pair 一条），不再重复生成 1st aspect。
     */
    public static List<PlanetRelation> planetToPlanet(List<PlanetPos> positions) {
        List<PlanetRelation> out = new ArrayList<>();
        for (int i = 0; i < positions.size(); i++) {
            for (int j = 0; j < positions.size(); j++) {
                if (i == j) {
                    continue;
                }
                PlanetPos source = positions.get(i);
                PlanetPos target = positions.get(j);
                if (source.getSignIndex() == target.getSignIndex()) {
                    // 同宫：无序 pair 只保留一条 conjunction
                    if (i < j) {
                        out.add(relation(source, target));
                    }
                    continue;
                }
                PlanetRelation rel = relation(source, target);
                if (rel != null) {
                    out.add(rel);
                }
            }
        }
        return out;
    }

    /**
     * 跨盘 Planet → Planet（Synastry / 今日观星 P2P）
     *
     * 不能比较 A 的宫位号与 B 的宫位号；双方共用同一 sidereal zodiac，
     * 用 relative_position 判断 influence position。
     * A→B、B→A 分别独立计算（本方法一次给出两个方向）。
     *
     * 同宫 conjunction 按无序 pair 只生成一条（bidirectional = true），
     * 不再重复生成 1st aspect，也不重复生成反向 conjunction。
     */
    public static List<PlanetRelation> crossChartPlanetToPlanet(List<PlanetPos> senders,
                                                               List<PlanetPos> receivers,
                                                               boolean markMutual) {
        List<PlanetRelation> out = new ArrayList<>();
        // A → B（跳过同宫：同宫由 unordered 去重阶段统一处理）
        for (PlanetPos source : senders) {
            for (PlanetPos target : receivers) {
                if (source.getSignIndex() == target.getSignIndex()) {
                    continue;
                }
                PlanetRelation rel = relation(source, target);
                if (rel != null) {
                    out.add(rel);
                }
            }
        }
        // B → A
        for (PlanetPos source : receivers) {
            for (PlanetPos target : senders) {
                if (source.getSignIndex() == target.getSignIndex()) {
                    continue;
                }
                PlanetRelation rel = relation(source, target);
                if (rel != null) {
                    out.add(rel);
                }
            }
        }
        // 同宫 conjunction：按无序 pair 只保留一条
        for (PlanetPos source : senders) {
            for (PlanetPos target : receivers) {
                if (source.getSignIndex() != target.getSignIndex()) {
                    continue;
                }
                out.add(relation(source, target));
            }
        }
        if (markMutual) {
            markMutualPairs(out);
        }
        return out;
    }

    /** 判断并标记 mutual（只做聚合标记，不产生额外证据票） */
    private static void markMutualPairs(List<PlanetRelation> relations) {
        for (PlanetRelation a : relations) {
            if (!TYPE_DRISHTI.equals(a.getRelationType())) {
                continue;
            }
            for (PlanetRelation b : relations) {
                if (!TYPE_DRISHTI.equals(b.getRelationType())) {
                    continue;
                }
                if (a.getSourcePlanet().equals(b.getTargetPlanet())
                        && a.getTargetPlanet().equals(b.getSourcePlanet())) {
                    a.setBidirectional(true);
                    b.setBidirectional(true);
                }
            }
        }
    }

    private static PlanetRelation relation(PlanetPos source, PlanetPos target) {
        if (source.getSignIndex() == target.getSignIndex()) {
            // 同宫：conjunction（bidirectional），degree 只决定紧密度
            double delta = degreeDelta(source, target);
            PlanetRelation rel = base(source, target, 1, TYPE_CONJUNCTION, delta);
            rel.setBidirectional(true);
            return rel;
        }
        int pos = relativePosition(source.getSignIndex(), target.getSignIndex());
        Set<Integer> allowed = INFLUENCE_POSITIONS.get(source.getPlanet());
        if (allowed == null || pos == 1 || !allowed.contains(pos)) {
            return null;
        }
        double delta = degreeDelta(source, target);
        return base(source, target, pos, TYPE_DRISHTI, delta);
    }

    private static PlanetRelation base(PlanetPos source, PlanetPos target, int pos,
                                       String type, double delta) {
        PlanetRelation rel = new PlanetRelation();
        rel.setRelationType(type);
        rel.setSourcePlanet(source.getPlanet());
        rel.setTargetPlanet(target.getPlanet());
        rel.setAspectPosition(pos);
        rel.setExactAngleDeg(ASPECT_POSITION_TO_EXACT_ANGLE.getOrDefault(pos, 0));
        rel.setDegreeDeltaDeg(delta);
        rel.setExactnessScore(exactnessScore(delta));
        String[] band = exactnessBand(delta);
        rel.setExactnessClass(band[0]);
        rel.setStrength(band[1]);
        rel.setValid(true);
        return rel;
    }

    // ============================================================
    // Planet → House
    // ============================================================

    /**
     * Planet → House：source 所在宫 + 其 influence positions 覆盖的全部目标宫。
     * position 1 为 placement_influence，其余为 graha_drishti。
     * P2H 没有目标行星，不计算 degree delta。
     */
    public static List<HouseInfluence> planetToHouse(String planet, int sourceHouse) {
        List<HouseInfluence> out = new ArrayList<>();
        Set<Integer> positions = INFLUENCE_POSITIONS.get(planet);
        if (positions == null) {
            return out;
        }
        List<Integer> sorted = new ArrayList<>(positions);
        Collections.sort(sorted);
        for (int pos : sorted) {
            HouseInfluence inf = new HouseInfluence();
            inf.setSourcePlanet(planet);
            inf.setSourceHouse(sourceHouse);
            inf.setTargetHouse(targetHouse(sourceHouse, pos));
            inf.setAspectPosition(pos);
            inf.setInfluenceType(pos == 1 ? TYPE_PLACEMENT : TYPE_DRISHTI);
            inf.setExactAngleDeg(ASPECT_POSITION_TO_EXACT_ANGLE.getOrDefault(pos, 0));
            out.add(inf);
        }
        return out;
    }

    /** 跨盘 Planet → House Overlay：sender planet sign vs receiver Asc sign（Whole Sign） */
    public static int overlayHouse(int senderSignIndex, int receiverAscSignIndex) {
        return Math.floorMod(senderSignIndex - receiverAscSignIndex, 12) + 1;
    }
}
