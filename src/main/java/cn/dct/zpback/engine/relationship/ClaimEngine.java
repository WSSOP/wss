package cn.dct.zpback.engine.relationship;

import cn.dct.zpback.constant.*;
import cn.dct.zpback.entity.relationship.*;
import cn.dct.zpback.entity.relationship.CanonicalChartData.NatalChart;
import cn.dct.zpback.entity.relationship.CanonicalChartData.PlanetPosition;
import cn.dct.zpback.entity.relationship.CanonicalChartData.HouseInfo;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.*;
import java.util.stream.Collectors;

/**
 * Claim Engine - 声明引擎 (STEP 3A-3D)
 * 
 * 从 Evidence 解析 38 个 Canonical Claim
 */
@Component
@Slf4j
public class ClaimEngine {

    /**
     * 主入口：从 Evidence 解析所有 Claim
     */
    public Map<String, CanonicalClaim> resolveAllClaims(List<Evidence> evidenceList, CanonicalChartData chartData) {
        Map<String, CanonicalClaim> claims = new LinkedHashMap<>();

        // S1 Claims
        claims.put("LOVE_PATTERN", resolveLovePattern(evidenceList, chartData));
        claims.put("ATTRACTION_PATTERN", resolveAttractionPattern(evidenceList, chartData));
        claims.put("ROMANTIC_INITIATION_STYLE", resolveRomanticInitiationStyle(evidenceList, chartData));

        // S2 Claims
        claims.put("MARRIAGE_FOUNDATION", resolveMarriageFoundation(evidenceList, chartData));
        claims.put("LONG_TERM_RELATIONSHIP_NEED", resolveLongTermRelationshipNeed(evidenceList, chartData));
        claims.put("EMOTIONAL_SECURITY_NEED", resolveEmotionalSecurityNeed(evidenceList, chartData));
        claims.put("COMMITMENT_STYLE", resolveCommitmentStyle(evidenceList, chartData));
        claims.put("MARRIAGE_STAGE_SELF", resolveMarriageStageSelf(evidenceList, chartData));

        // S3 Claims
        claims.put("PARTNER_CORE_PERSONALITY", resolvePartnerCorePersonality(evidenceList, chartData));
        claims.put("PARTNER_RELATIONAL_ROLE", resolvePartnerRelationalRole(evidenceList, chartData));
        claims.put("PARTNER_MATURITY_STYLE", resolvePartnerMaturityStyle(evidenceList, chartData));
        claims.put("PARTNER_SOCIAL_CAREER_TENDENCY", resolvePartnerSocialCareerTendency(evidenceList, chartData));
        claims.put("PARTNER_BACKGROUND_TENDENCY", resolvePartnerBackgroundTendency(evidenceList, chartData));
        claims.put("PARTNER_APPEARANCE_PRESENCE", resolvePartnerAppearancePresence(evidenceList, chartData));
        claims.put("PARTNER_DISTANCE_CULTURE_TENDENCY", resolvePartnerDistanceCultureTendency(evidenceList, chartData));
        claims.put("PARTNER_AGE_GAP_TENDENCY", resolvePartnerAgeGapTendency(evidenceList, chartData));
        claims.put("PARTNER_NEED_ARCHETYPE_ALIGNMENT", resolvePartnerNeedArchetypeAlignment(evidenceList, chartData));

        // S4 Claims
        claims.put("CORE_MARRIAGE_STRENGTH", resolveCoreMarriageStrength(evidenceList, chartData));
        claims.put("CORE_ADAPTATION_NEED", resolveCoreAdaptationNeed(evidenceList, chartData));
        claims.put("MARRIAGE_PROTECTIVE_FACTOR", resolveMarriageProtectiveFactor(evidenceList, chartData));
        claims.put("MARRIAGE_PRESSURE_FACTOR", resolveMarriagePressureFactor(evidenceList, chartData));

        // S5 Claims
        claims.put("MARRIAGE_DYNAMIC", resolveMarriageDynamic(evidenceList, chartData));
        claims.put("LONG_TERM_INTIMACY_PATTERN", resolveLongTermIntimacyPattern(evidenceList, chartData));
        claims.put("EMOTIONAL_REGULATION_PATTERN", resolveEmotionalRegulationPattern(evidenceList, chartData));
        claims.put("CONFLICT_RESPONSE_PATTERN", resolveConflictResponsePattern(evidenceList, chartData));
        claims.put("BOUNDARY_PATTERN", resolveBoundaryPattern(evidenceList, chartData));
        claims.put("SHARED_RESPONSIBILITY_PATTERN", resolveSharedResponsibilityPattern(evidenceList, chartData));
        claims.put("REPAIR_PATTERN", resolveRepairPattern(evidenceList, chartData));
        claims.put("MARRIAGE_REALITY_PATTERN", resolveMarriageRealityPattern(evidenceList, chartData));

        // S6 Claims
        claims.put("MEETING_WINDOW", resolveMeetingWindow(evidenceList, chartData));
        claims.put("RELATIONSHIP_START_WINDOW", resolveRelationshipStartWindow(evidenceList, chartData));
        claims.put("COMMITMENT_WINDOW", resolveCommitmentWindow(evidenceList, chartData));
        claims.put("MARRIAGE_WINDOW", resolveMarriageWindow(evidenceList, chartData));
        claims.put("RELATIONSHIP_REASSESSMENT_WINDOW", resolveRelationshipReassessmentWindow(evidenceList, chartData));
        claims.put("RELATIONSHIP_PRESSURE_WINDOW", resolveRelationshipPressureWindow(evidenceList, chartData));

        // S7 Claims
        claims.put("RELATIONSHIP_GROWTH_THEME", resolveRelationshipGrowthTheme(evidenceList, chartData));
        claims.put("GROWTH_TASK", resolveGrowthTask(evidenceList, chartData));
        claims.put("RELATIONSHIP_RECOMMENDATION", resolveRelationshipRecommendation(evidenceList, chartData));

        log.info("Claim 解析完成: total={}", claims.size());
        return claims;
    }

    // ============================================================
    // S1: 你是怎样进入一段感情的
    // ============================================================

    private CanonicalClaim resolveLovePattern(List<Evidence> evidence, CanonicalChartData chartData) {
        CanonicalClaim claim = createClaim("LOVE_PATTERN", ClaimClass.COMPOSITE, "USER", "S1");
        claim.setSensitivity(ClaimSensitivity.STANDARD);

        // 收集相关 Evidence
        List<Evidence> related = filterEvidenceByDomain(evidence, "ROMANCE_FIELD", "ROMANCE_MECHANISM", "RELATIONSHIP_FUNCTION");
        claim.setSupportingEvidence(related);

        // 计算置信度
        int familyCount = countIndependentFamilies(related);
        claim.setConfidence(calculateConfidence(familyCount, 2));

        // 生成摘要
        String fifthSign = get5HSign(chartData);
        String venusSign = getPlanetSign(chartData, "Venus");
        String summary = "你的恋爱模式以" + fifthSign + "五宫领域为基调，金星落" + venusSign + "呈现温暖而有节奏的投入方式。你倾向于在确认对方的情感回应后才会深入投入。" +
                "在日常相处中，这种模式通常表现为：关系初期你会持续观察对方的言行是否一致，宁可慢一点也不愿意贸然交付；" +
                "一旦确认了安全感和回应的真诚度，你的投入会变得稳定而持久，且带有明显的生活化色彩——你更愿意通过日常细节而非戏剧化表达来传递爱意。" +
                "建议把\"确认回应\"与\"表达自己\"分成两件事来练习：前者可以谨慎，后者不必等到完全确认才开始。";
        claim.setSummary(summary);

        return claim;
    }

    private CanonicalClaim resolveAttractionPattern(List<Evidence> evidence, CanonicalChartData chartData) {
        CanonicalClaim claim = createClaim("ATTRACTION_PATTERN", ClaimClass.CORE, "USER", "S1");
        claim.setSensitivity(ClaimSensitivity.STANDARD);

        List<Evidence> related = filterEvidenceByDomain(evidence, "RELATIONAL_DRIVE", "RELATIONSHIP_FUNCTION");
        claim.setSupportingEvidence(related);
        claim.setConfidence(calculateConfidence(countIndependentFamilies(related), 2));

        String marsSign = getPlanetSign(chartData, "Mars");
        claim.setSummary("你的吸引模式以火星" + marsSign + "为驱动，呈现主动、直接的特征。你容易被有活力、有主见的人吸引。" +
                "需要注意：当关系长期缺乏新的挑战和目标，这个结构可能让你感到倦怠，" +
                "误以为\"不爱了\"，实际上只是吸引结构没有被持续激活。" +
                "择偶提示：不必压抑对活力的偏好，但要评估对方的活力是\"共同生活的燃料\"还是\"消耗你的火源\"——区别在于对方能否在冲突后主动修复。");

        return claim;
    }

    private CanonicalClaim resolveRomanticInitiationStyle(List<Evidence> evidence, CanonicalChartData chartData) {
        CanonicalClaim claim = createClaim("ROMANTIC_INITIATION_STYLE", ClaimClass.CORE, "USER", "S1");
        claim.setSensitivity(ClaimSensitivity.STANDARD);

        List<Evidence> related = filterEvidenceByDomain(evidence, "ROMANCE_MECHANISM", "RELATIONAL_DRIVE");
        claim.setSupportingEvidence(related);
        claim.setConfidence(calculateConfidence(countIndependentFamilies(related), 2));

        String fifthSign = get5HSign(chartData);
        claim.setSummary("你的恋爱推进风格以" + fifthSign + "五宫为基调，呈现热情、表达型的特征。你倾向于主动表达好感，通过真诚的关心来推进关系。" +
                "实际行动中，你更擅长\"创造接触机会\"，不擅长\"等待和试探\"——模糊期对你来说尤其消耗，你宁可得到一个明确的\"不\"，也不想停留在不确定里。" +
                "建议把推进节奏与对方的回应节奏对齐——对方主动回应时可以推进，对方只是礼貌回应时先退半步观察。");

        return claim;
    }

    // ============================================================
    // S2: 你真正需要怎样的长期关系
    // ============================================================

    private CanonicalClaim resolveMarriageFoundation(List<Evidence> evidence, CanonicalChartData chartData) {
        CanonicalClaim claim = createClaim("MARRIAGE_FOUNDATION", ClaimClass.COMPOSITE, "MARRIAGE", "S2");
        claim.setSensitivity(ClaimSensitivity.STANDARD);

        List<Evidence> related = filterEvidenceByDomain(evidence, "PARTNERSHIP_FIELD", "PARTNERSHIP_MECHANISM", "MARRIAGE_STAGE_SELF");
        claim.setSupportingEvidence(related);
        claim.setConfidence(calculateConfidence(countIndependentFamilies(related), 2));

        String seventhSign = get7HSign(chartData);
        String d9SeventhSign = getD97HSign(chartData);
        claim.setSummary("你的婚姻基础以" + seventhSign + "七宫为核心。D9 七宫落" + d9SeventhSign + "进一步验证了这一倾向，你的婚姻基础较为稳固。" +
                "\"基础稳固\"在现实中意味着三层含义：第一，你对伴侣关系抱有认真的预设——你不是抱着\"试试看\"的心态进入婚姻的，而是带着长期承诺的框架在择偶；" +
                "第二，即使经历重大分歧，你内心深处仍然愿意把\"我们\"放在\"我\"之前去考虑方案。" +
                "但稳固的基础不等于自动运转：七宫主题的功课在于\"如何与一个和你不同的人长期协作\"，" +
                "你需要的不是找到完美契合的人，而是在承认差异的前提下建立分工与协商的机制。");

        // D1×D9 关系
        claim.setD1d9Relation(calculateD1D9Relation(chartData, "MARRIAGE"));

        return claim;
    }

    private CanonicalClaim resolveLongTermRelationshipNeed(List<Evidence> evidence, CanonicalChartData chartData) {
        CanonicalClaim claim = createClaim("LONG_TERM_RELATIONSHIP_NEED", ClaimClass.CORE, "USER", "S2");
        claim.setSensitivity(ClaimSensitivity.STANDARD);

        List<Evidence> related = filterEvidenceByDomain(evidence, "EMOTIONAL_SECURITY", "RELATIONSHIP_FUNCTION");
        claim.setSupportingEvidence(related);
        claim.setConfidence(calculateConfidence(countIndependentFamilies(related), 2));

        String moonSign = getPlanetSign(chartData, "Moon");
        claim.setSummary("你真正需要的是一段能够让你在情感上感到安全的关系。月亮落" + moonSign + "赋予你敏锐的情绪感知能力。" +
                "\"情感上的安全\"对你而言不是一句口号，而是可以被具体定义的：你能预测对方在压力下的基本反应、" +
                "你的情绪起伏能被对方接住而不是被评判、你们之间的重要决定会互相告知而不是事后通知。" +
                "当这三条缺失时，你的消耗往往先体现在身体和睡眠上，而不是立刻表现为关系冲突——这也是你需要留意的信号。" +
                "在择偶时，比起\"条件是否匹配\"，更值得观察的是对方在你情绪低落时的第一反应：是靠近询问，还是回避沉默。前者才是你能长期依靠的安全感来源。");

        return claim;
    }

    private CanonicalClaim resolveEmotionalSecurityNeed(List<Evidence> evidence, CanonicalChartData chartData) {
        CanonicalClaim claim = createClaim("EMOTIONAL_SECURITY_NEED", ClaimClass.CORE, "USER", "S2");
        claim.setSensitivity(ClaimSensitivity.STANDARD);

        List<Evidence> related = filterEvidenceByDomain(evidence, "EMOTIONAL_SECURITY");
        claim.setSupportingEvidence(related);
        claim.setConfidence(calculateConfidence(countIndependentFamilies(related), 2));

        String moonSign = getPlanetSign(chartData, "Moon");
        claim.setSummary("你对情感安全感的需求较为强烈。月亮落" + moonSign + "表明你需要在关系中感受到对方的情感回应和承诺。" +
                "这种需求强烈不是缺点，它是你深度投入关系的前提——正因为你要的不是浅层陪伴，你才会对回应的质地如此敏感。" +
                "但需求强烈也意味着：当回应暂时缺席时（对方工作忙碌、情绪低谷、性格内敛），" +
                "你的不安全感会被放大，容易把\"对方此刻没能力回应\"解读为\"对方不在乎\"。" +
                "更有效的策略是建立\"回应清单\"：把对方实际做过让你感到被爱的具体行为列出来，" +
                "在不安时对照清单校准判断，而不是依赖当下的感受。同时直接告知对方你需要哪种回应方式，比等待对方领悟有效得多。");

        return claim;
    }

    private CanonicalClaim resolveCommitmentStyle(List<Evidence> evidence, CanonicalChartData chartData) {
        CanonicalClaim claim = createClaim("COMMITMENT_STYLE", ClaimClass.CORE, "USER", "S2");
        claim.setSensitivity(ClaimSensitivity.STANDARD);

        List<Evidence> related = filterEvidenceByDomain(evidence, "VALUES_MEANING_REPAIR", "PARTNERSHIP_MECHANISM");
        claim.setSupportingEvidence(related);
        claim.setConfidence(calculateConfidence(countIndependentFamilies(related), 2));

        String jupiterSign = getPlanetSign(chartData, "Jupiter");
        claim.setSummary("你的承诺风格以木星" + jupiterSign + "为指引，追求有意义、有深度的承诺。");

        return claim;
    }

    private CanonicalClaim resolveMarriageStageSelf(List<Evidence> evidence, CanonicalChartData chartData) {
        CanonicalClaim claim = createClaim("MARRIAGE_STAGE_SELF", ClaimClass.CORE, "USER", "S2");
        claim.setSensitivity(ClaimSensitivity.STANDARD);

        List<Evidence> related = filterEvidenceByDomain(evidence, "MARRIAGE_STAGE_SELF");
        claim.setSupportingEvidence(related);
        claim.setConfidence(calculateConfidence(countIndependentFamilies(related), 2));

        String d9Lagna = getD9LagnaSign(chartData);
        claim.setSummary("在婚姻中，你呈现出" + d9Lagna + " D9 上升的特质——成熟、稳重的自我定位。");

        return claim;
    }

    // ============================================================
    // S3: 未来伴侣画像
    // ============================================================

    private CanonicalClaim resolvePartnerCorePersonality(List<Evidence> evidence, CanonicalChartData chartData) {
        CanonicalClaim claim = createClaim("PARTNER_CORE_PERSONALITY", ClaimClass.CORE, "PARTNER", "S3");
        claim.setSensitivity(ClaimSensitivity.STANDARD);

        List<Evidence> related = filterEvidenceByDomain(evidence, "PARTNER_ARCHETYPE");
        claim.setSupportingEvidence(related);
        claim.setConfidence(calculateConfidence(countIndependentFamilies(related), 3));

        String dkPlanet = getDKPlanet(chartData);
        List<String> dkBase = DKPlanetBaseRegistry.getDKPlanetBase(dkPlanet);
        String dkBaseStr = dkBase.isEmpty() ? "" : String.join(" / ", dkBase);
        claim.setSummary("你的未来伴侣更可能呈现出" + dkPlanet + "的核心特质——" + dkBaseStr + "。" +
                "这里的\"核心特质\"指对方在关系中的主导气质和行为底色，会在相识后的前几个月显现：" +
                "面对分歧时的处理方式、对承诺的态度、在亲密与独立之间的默认位置。" +
                "符合这些特质的人未必以你想象的方式出现——初见可能平淡，但相处中的\"结构性舒服\"会随时间越来越明显；" +
                "短期吸引力很强但核心特质不符的对象，往往会在承诺阶段暴露出难以协调的差异。" +
                "因此观察期建议适当拉长，重点看对方在压力情境下的表现，而非最好状态时的表现。");

        return claim;
    }

    private CanonicalClaim resolvePartnerRelationalRole(List<Evidence> evidence, CanonicalChartData chartData) {
        CanonicalClaim claim = createClaim("PARTNER_RELATIONAL_ROLE", ClaimClass.CORE, "PARTNER", "S3");
        claim.setSensitivity(ClaimSensitivity.STANDARD);

        List<Evidence> related = filterEvidenceByDomain(evidence, "PARTNER_ARCHETYPE", "SELF_PARTNER_AXIS");
        claim.setSupportingEvidence(related);
        claim.setConfidence(calculateConfidence(countIndependentFamilies(related), 3));

        String dkPlanet = getDKPlanet(chartData);
        String dkRole = DKPlanetBaseRegistry.getDKPlanetRole(dkPlanet);
        claim.setSummary("在关系中，你的伴侣更可能扮演" + dkRole + "的角色，能够与你形成互补。" +
                "互补的含义是：对方擅长处理你不擅长或不愿处理的领域，让关系形成分工而非竞争。" +
                "这种互补初期表现为\"差异带来的吸引力\"，但进入长期关系后，同一种差异也可能变成摩擦点。" +
                "识别互补是否健康有一个简单标准：分歧发生时，你们最终能回到\"各自做好各自擅长的部分\"，" +
                "还是反复陷入\"凭什么要听你的\"。前者是互补在运作，后者是差异在对抗。" +
                "与互补型伴侣相处，最重要的是在早期就把\"谁负责什么\"聊清楚。");

        return claim;
    }

    private CanonicalClaim resolvePartnerMaturityStyle(List<Evidence> evidence, CanonicalChartData chartData) {
        CanonicalClaim claim = createClaim("PARTNER_MATURITY_STYLE", ClaimClass.CORE, "PARTNER", "S3");
        claim.setSensitivity(ClaimSensitivity.STANDARD);

        List<Evidence> related = filterEvidenceByDomain(evidence, "PARTNER_ARCHETYPE_MATRURATION");
        claim.setSupportingEvidence(related);
        claim.setConfidence(calculateConfidence(countIndependentFamilies(related), 3));

        String dkPlanet = getDKPlanet(chartData);
        String dkInD9Sign = getDKInD9Sign(chartData);
        claim.setSummary("你的伴侣在关系中会表现出一种成熟的相处方式。D9 中" + dkPlanet + "落" + dkInD9Sign + "进一步修饰了这一特质。");

        return claim;
    }

    private CanonicalClaim resolvePartnerSocialCareerTendency(List<Evidence> evidence, CanonicalChartData chartData) {
        CanonicalClaim claim = createClaim("PARTNER_SOCIAL_CAREER_TENDENCY", ClaimClass.CORE, "PARTNER", "S3");
        claim.setSensitivity(ClaimSensitivity.HIGH);

        List<Evidence> related = filterEvidenceByDomain(evidence, "PARTNER_ARCHETYPE", "MARRIAGE_MANIFESTATION_LAYER");
        claim.setSupportingEvidence(related);
        claim.setConfidence(calculateConfidence(countIndependentFamilies(related), 4));

        // 高敏感 Claim，需要更多独立 Family 支持
        if (countIndependentFamilies(related) < 3) {
            claim.setConfidence(ClaimConfidence.INSUFFICIENT);
            claim.setSummary("当前证据不足以判断伴侣的具体社会/职业倾向。");
        } else {
            claim.setSummary("伴侣的社会/职业倾向需要更多星盘数据支持精确判断。");
        }

        return claim;
    }

    private CanonicalClaim resolvePartnerBackgroundTendency(List<Evidence> evidence, CanonicalChartData chartData) {
        CanonicalClaim claim = createClaim("PARTNER_BACKGROUND_TENDENCY", ClaimClass.CORE, "PARTNER", "S3");
        claim.setSensitivity(ClaimSensitivity.HIGH);

        List<Evidence> related = filterEvidenceByDomain(evidence, "PARTNER_ARCHETYPE", "MARRIAGE_MANIFESTATION_LAYER");
        claim.setSupportingEvidence(related);
        claim.setConfidence(calculateConfidence(countIndependentFamilies(related), 4));

        if (countIndependentFamilies(related) < 3) {
            claim.setConfidence(ClaimConfidence.INSUFFICIENT);
            claim.setSummary("当前证据不足以判断伴侣的具体背景倾向。");
        } else {
            claim.setSummary("伴侣的背景倾向需要更多星盘数据支持精确判断。");
        }

        return claim;
    }

    private CanonicalClaim resolvePartnerAppearancePresence(List<Evidence> evidence, CanonicalChartData chartData) {
        CanonicalClaim claim = createClaim("PARTNER_APPEARANCE_PRESENCE", ClaimClass.CORE, "PARTNER", "S3");
        claim.setSensitivity(ClaimSensitivity.HIGH);

        List<Evidence> related = filterEvidenceByDomain(evidence, "PARTNER_ARCHETYPE", "MARRIAGE_MANIFESTATION_LAYER");
        claim.setSupportingEvidence(related);
        claim.setConfidence(calculateConfidence(countIndependentFamilies(related), 4));

        if (countIndependentFamilies(related) < 3) {
            claim.setConfidence(ClaimConfidence.INSUFFICIENT);
            claim.setSummary("当前证据不足以判断伴侣的外在形象特质。");
        } else {
            claim.setSummary("伴侣的外在形象特质需要更多星盘数据支持精确判断。");
        }

        return claim;
    }

    private CanonicalClaim resolvePartnerDistanceCultureTendency(List<Evidence> evidence, CanonicalChartData chartData) {
        CanonicalClaim claim = createClaim("PARTNER_DISTANCE_CULTURE_TENDENCY", ClaimClass.CORE, "PARTNER", "S3");
        claim.setSensitivity(ClaimSensitivity.HIGH);

        List<Evidence> related = filterEvidenceByDomain(evidence, "PARTNER_ARCHETYPE", "MARRIAGE_MANIFESTATION_LAYER");
        claim.setSupportingEvidence(related);
        claim.setConfidence(calculateConfidence(countIndependentFamilies(related), 4));

        if (countIndependentFamilies(related) < 3) {
            claim.setConfidence(ClaimConfidence.INSUFFICIENT);
            claim.setSummary("当前证据不足以判断伴侣的距离/文化倾向。");
        } else {
            claim.setSummary("伴侣的距离/文化倾向需要更多星盘数据支持精确判断。");
        }

        return claim;
    }

    private CanonicalClaim resolvePartnerAgeGapTendency(List<Evidence> evidence, CanonicalChartData chartData) {
        CanonicalClaim claim = createClaim("PARTNER_AGE_GAP_TENDENCY", ClaimClass.CORE, "PARTNER", "S3");
        claim.setSensitivity(ClaimSensitivity.VERY_HIGH);

        List<Evidence> related = filterEvidenceByDomain(evidence, "PARTNER_ARCHETYPE", "MARRIAGE_MANIFESTATION_LAYER");
        claim.setSupportingEvidence(related);
        claim.setConfidence(calculateConfidence(countIndependentFamilies(related), 4));

        // 极高敏感，通常设为 INSUFFICIENT
        claim.setConfidence(ClaimConfidence.INSUFFICIENT);
        claim.setSummary("当前证据不足以判断具体的年龄差倾向。");

        return claim;
    }

    private CanonicalClaim resolvePartnerNeedArchetypeAlignment(List<Evidence> evidence, CanonicalChartData chartData) {
        CanonicalClaim claim = createClaim("PARTNER_NEED_ARCHETYPE_ALIGNMENT", ClaimClass.COMPOSITE, "RELATIONSHIP", "S3");
        claim.setSensitivity(ClaimSensitivity.STANDARD);

        List<Evidence> related = filterEvidenceByDomain(evidence, "PARTNER_ARCHETYPE", "EMOTIONAL_SECURITY", "RELATIONSHIP_FUNCTION");
        claim.setSupportingEvidence(related);
        claim.setConfidence(calculateConfidence(countIndependentFamilies(related), 2));

        claim.setSummary("真正适合你的伴侣，是那个能够与你形成深层情感联结的人。" +
                "对你来说，\"深层联结\"区别于\"表面合拍\"：表面合拍是兴趣爱好相似、社交场合聊得来，" +
                "深层联结则是双方都愿意在脆弱时刻暴露真实状态，并且相信对方会善待这份暴露。" +
                "以你的情感结构，即使和一个条件相当、社交上完全适配的人在一起，如果无法建立这种深层交流，" +
                "你在关系中依然会感到孤独。因此评估长期关系时，建议把\"我们能不能聊真正重要的事\"作为核心指标。" +
                "同时也要注意：深层联结是逐步建立的，不要要求对方在关系初期就完全敞开，给彼此暴露真实的时间。");

        return claim;
    }

    // ============================================================
    // S4: 婚姻里的优势与容易卡住的地方
    // ============================================================

    private CanonicalClaim resolveCoreMarriageStrength(List<Evidence> evidence, CanonicalChartData chartData) {
        CanonicalClaim claim = createClaim("CORE_MARRIAGE_STRENGTH", ClaimClass.COMPOSITE, "MARRIAGE", "S4");
        claim.setSensitivity(ClaimSensitivity.STANDARD);

        List<Evidence> related = filterEvidenceByDomain(evidence, "PARTNERSHIP_FIELD", "MARRIAGE_STAGE_SELF", "VALUES_MEANING_REPAIR");
        claim.setSupportingEvidence(related);
        claim.setConfidence(calculateConfidence(countIndependentFamilies(related), 2));

        String seventhSign = get7HSign(chartData);
        String d9SeventhSign = getD97HSign(chartData);
        claim.setSummary("你的婚姻结构具有较为稳固的基础。D1 " + seventhSign + " 七宫与 D9 " + d9SeventhSign + " 七宫的配置表明你在婚姻中能够保持平衡。" +
                "这种\"平衡\"体现在三个方面：情绪上，你不容易被日常摩擦长期带偏，吵过之后能回到中线；" +
                "角色上，你能在\"伴侣\"\"家人\"\"自己\"三种身份之间切换；决策上，你在重大事项上倾向于寻求双方共识。" +
                "你的优势区在于\"长期经营\"：时间越久，你越能体现出可靠和沉淀的价值，婚姻对你的回报也是后置的。" +
                "需要注意的只有一点：稳固结构的人容易低估风险而减少经营，建议保持固定的深度沟通习惯，" +
                "让稳固成为主动维护的结果，而不是默认状态。");

        return claim;
    }

    private CanonicalClaim resolveCoreAdaptationNeed(List<Evidence> evidence, CanonicalChartData chartData) {
        CanonicalClaim claim = createClaim("CORE_ADAPTATION_NEED", ClaimClass.COMPOSITE, "MARRIAGE", "S4");
        claim.setSensitivity(ClaimSensitivity.STANDARD);

        List<Evidence> related = filterEvidenceByDomain(evidence, "PARTNERSHIP_MECHANISM", "MARRIAGE_FUNCTION_MATURATION");
        claim.setSupportingEvidence(related);
        claim.setConfidence(calculateConfidence(countIndependentFamilies(related), 2));

        claim.setSummary("在婚姻中，你可能需要适应伴侣与你不同的情感表达方式。" +
                "对你而言，你表达爱的方式和你想被爱的方式不完全相同——你给出的，未必是对方需要的；对方给出的，未必是你期待的。" +
                "常见的错位包括：你用\"把事情做好\"来表达在意，而对方需要的是语言确认；" +
                "或者你期待情绪上的深度交流，而对方的表达偏向务实和行动。" +
                "更有效的方法是建立\"翻译机制\"：把对方的表达方式翻译成TA的在意，而不是用自己的接收标准判定\"TA不够爱我\"。" +
                "同时明确告诉对方你的接收频道是什么，大多数表达错位，一次说清楚就能解决一大半。");

        return claim;
    }

    private CanonicalClaim resolveMarriageProtectiveFactor(List<Evidence> evidence, CanonicalChartData chartData) {
        CanonicalClaim claim = createClaim("MARRIAGE_PROTECTIVE_FACTOR", ClaimClass.FACTOR, "MARRIAGE", "S4");
        claim.setSensitivity(ClaimSensitivity.STANDARD);

        List<Evidence> related = filterEvidenceByDomain(evidence, "VALUES_MEANING_REPAIR", "MARRIAGE_STAGE_SELF");
        claim.setSupportingEvidence(related);
        claim.setConfidence(calculateConfidence(countIndependentFamilies(related), 2));

        String jupiterSign = getPlanetSign(chartData, "Jupiter");
        claim.setSummary("木星" + jupiterSign + "在你的婚姻中扮演着保护者的角色。");

        return claim;
    }

    private CanonicalClaim resolveMarriagePressureFactor(List<Evidence> evidence, CanonicalChartData chartData) {
        CanonicalClaim claim = createClaim("MARRIAGE_PRESSURE_FACTOR", ClaimClass.FACTOR, "MARRIAGE", "S4");
        claim.setSensitivity(ClaimSensitivity.STANDARD);

        List<Evidence> related = filterEvidenceByDomain(evidence, "RELATIONSHIP_PRESSURE", "STRUCTURE_MODIFIER");
        claim.setSupportingEvidence(related);
        claim.setConfidence(calculateConfidence(countIndependentFamilies(related), 2));

        String saturnSign = getPlanetSign(chartData, "Saturn");
        claim.setSummary("土星" + saturnSign + "在你的婚姻中带来了一定的压力和挑战。");

        return claim;
    }

    // ============================================================
    // S5: 长期相处与婚后关系模式
    // ============================================================

    private CanonicalClaim resolveMarriageDynamic(List<Evidence> evidence, CanonicalChartData chartData) {
        CanonicalClaim claim = createClaim("MARRIAGE_DYNAMIC", ClaimClass.COMPOSITE, "RELATIONSHIP", "S5");
        claim.setSensitivity(ClaimSensitivity.STANDARD);

        List<Evidence> related = filterEvidenceByDomain(evidence, "MARRIAGE_FUNCTION_MATURATION", "PARTNERSHIP_FIELD");
        claim.setSupportingEvidence(related);
        claim.setConfidence(calculateConfidence(countIndependentFamilies(related), 2));

        claim.setSummary("你的婚姻动态呈现一种相互滋养、共同成长的特征。" +
                "\"相互滋养\"的关系不是没有矛盾，而是矛盾之后的走向：冲突过后，你们对彼此的了解加深而不是怨气累积。" +
                "对你而言，婚姻最理想的功能是\"共同成长的容器\"。要实现这一点，有几个条件需要主动创造：" +
                "一是保留各自的成长空间，允许对方有你不参与的朋友、爱好和目标；" +
                "二是建立\"成长同步\"的习惯，定期交换彼此的变化和想法；" +
                "三是在对方成长遇挫时提供支撑而不是说教。" +
                "只要这三条在线，你的婚姻质量会随时间上升，而不是像很多关系那样随时间平淡化。");

        return claim;
    }

    private CanonicalClaim resolveLongTermIntimacyPattern(List<Evidence> evidence, CanonicalChartData chartData) {
        CanonicalClaim claim = createClaim("LONG_TERM_INTIMACY_PATTERN", ClaimClass.CORE, "RELATIONSHIP", "S5");
        claim.setSensitivity(ClaimSensitivity.STANDARD);

        List<Evidence> related = filterEvidenceByDomain(evidence, "EMOTIONAL_SECURITY", "RELATIONSHIP_FUNCTION");
        claim.setSupportingEvidence(related);
        claim.setConfidence(calculateConfidence(countIndependentFamilies(related), 2));

        claim.setSummary("你在长期关系中的亲密模式偏向于深度情感联结。");

        return claim;
    }

    private CanonicalClaim resolveEmotionalRegulationPattern(List<Evidence> evidence, CanonicalChartData chartData) {
        CanonicalClaim claim = createClaim("EMOTIONAL_REGULATION_PATTERN", ClaimClass.CORE, "USER", "S5");
        claim.setSensitivity(ClaimSensitivity.STANDARD);

        List<Evidence> related = filterEvidenceByDomain(evidence, "EMOTIONAL_SECURITY");
        claim.setSupportingEvidence(related);
        claim.setConfidence(calculateConfidence(countIndependentFamilies(related), 2));

        String moonSign = getPlanetSign(chartData, "Moon");
        claim.setSummary("你在关系中的情绪调节方式以月亮" + moonSign + "为基调。");

        return claim;
    }

    private CanonicalClaim resolveConflictResponsePattern(List<Evidence> evidence, CanonicalChartData chartData) {
        CanonicalClaim claim = createClaim("CONFLICT_RESPONSE_PATTERN", ClaimClass.CORE, "USER", "S5");
        claim.setSensitivity(ClaimSensitivity.STANDARD);

        List<Evidence> related = filterEvidenceByDomain(evidence, "RELATIONAL_DRIVE", "RELATIONSHIP_PRESSURE");
        claim.setSupportingEvidence(related);
        claim.setConfidence(calculateConfidence(countIndependentFamilies(related), 2));

        String marsSign = getPlanetSign(chartData, "Mars");
        claim.setSummary("面对冲突时，你的第一反应更倾向于火星" + marsSign + "式的直接面对。" +
                "直接面对是优势也是风险：优势在于问题不会被攒着，有不满通常会在近期表达出来，让问题有被及时处理的机会；" +
                "风险在于\"直接\"可能快于\"思考\"——情绪最强烈的时刻说出的话，往往比问题本身更伤人。" +
                "给你的冲突建议是\"延迟但不回避\"：觉察到情绪上头时，先约定时间整理再谈，然后在承诺的时间内回来处理。" +
                "另外注意区分\"对事的直接\"和\"对人的直接\"：前者是健康表达，后者（指责人格和动机）是关系腐蚀剂。");

        return claim;
    }

    private CanonicalClaim resolveBoundaryPattern(List<Evidence> evidence, CanonicalChartData chartData) {
        CanonicalClaim claim = createClaim("BOUNDARY_PATTERN", ClaimClass.CORE, "USER", "S5");
        claim.setSensitivity(ClaimSensitivity.STANDARD);

        List<Evidence> related = filterEvidenceByDomain(evidence, "EMOTIONAL_SECURITY", "RELATIONSHIP_FUNCTION");
        claim.setSupportingEvidence(related);
        claim.setConfidence(calculateConfidence(countIndependentFamilies(related), 2));

        claim.setSummary("你在关系中的边界模式偏向于柔性边界。");

        return claim;
    }

    private CanonicalClaim resolveSharedResponsibilityPattern(List<Evidence> evidence, CanonicalChartData chartData) {
        CanonicalClaim claim = createClaim("SHARED_RESPONSIBILITY_PATTERN", ClaimClass.CORE, "RELATIONSHIP", "S5");
        claim.setSensitivity(ClaimSensitivity.STANDARD);

        List<Evidence> related = filterEvidenceByDomain(evidence, "PARTNERSHIP_MECHANISM", "MARRIAGE_STAGE_SELF");
        claim.setSupportingEvidence(related);
        claim.setConfidence(calculateConfidence(countIndependentFamilies(related), 2));

        claim.setSummary("在共同责任方面，你倾向于承担更多的家庭责任。");

        return claim;
    }

    private CanonicalClaim resolveRepairPattern(List<Evidence> evidence, CanonicalChartData chartData) {
        CanonicalClaim claim = createClaim("REPAIR_PATTERN", ClaimClass.CORE, "RELATIONSHIP", "S5");
        claim.setSensitivity(ClaimSensitivity.STANDARD);

        List<Evidence> related = filterEvidenceByDomain(evidence, "VALUES_MEANING_REPAIR");
        claim.setSupportingEvidence(related);
        claim.setConfidence(calculateConfidence(countIndependentFamilies(related), 2));

        String jupiterSign = getPlanetSign(chartData, "Jupiter");
        claim.setSummary("你的关系修复能力以木星" + jupiterSign + "为指引。");

        return claim;
    }

    private CanonicalClaim resolveMarriageRealityPattern(List<Evidence> evidence, CanonicalChartData chartData) {
        CanonicalClaim claim = createClaim("MARRIAGE_REALITY_PATTERN", ClaimClass.CORE, "MARRIAGE", "S5");
        claim.setSensitivity(ClaimSensitivity.STANDARD);

        List<Evidence> related = filterEvidenceByDomain(evidence, "MARRIAGE_MANIFESTATION_LAYER", "STRUCTURE_MODIFIER");
        claim.setSupportingEvidence(related);
        claim.setConfidence(calculateConfidence(countIndependentFamilies(related), 2));

        claim.setSummary("婚姻的现实层面需要你投入时间和精力去经营。");

        return claim;
    }

    // ============================================================
    // S6: 未来3-5年的婚恋时间窗口
    // ============================================================

    private CanonicalClaim resolveMeetingWindow(List<Evidence> evidence, CanonicalChartData chartData) {
        CanonicalClaim claim = createClaim("MEETING_WINDOW", ClaimClass.TIMING, "FUTURE_EVENT", "S6");
        claim.setSensitivity(ClaimSensitivity.STANDARD);

        List<Evidence> related = filterEvidenceByDomain(evidence, "TIMING_ACTIVATION", "ROMANCE_FIELD");
        claim.setSupportingEvidence(related);
        claim.setConfidence(calculateConfidence(countIndependentFamilies(related), 2));

        claim.setSummary("未来一段时间内有可能出现认识新人的机会。" +
                "这类机会通常伴随生活节奏的改变出现：进入新环境、社交圈层更替、或你自身状态放松之后吸引力的变化。" +
                "窗口期提高的是接触概率，关系的质量仍取决于你的识别与选择，" +
                "因此更该做的是扩大优质社交面、保持开放姿态，而不是遇到第一个有好感的人就仓促投入。");

        return claim;
    }

    private CanonicalClaim resolveRelationshipStartWindow(List<Evidence> evidence, CanonicalChartData chartData) {
        CanonicalClaim claim = createClaim("RELATIONSHIP_START_WINDOW", ClaimClass.TIMING, "FUTURE_EVENT", "S6");
        claim.setSensitivity(ClaimSensitivity.STANDARD);

        List<Evidence> related = filterEvidenceByDomain(evidence, "TIMING_ACTIVATION", "ROMANCE_MECHANISM");
        claim.setSupportingEvidence(related);
        claim.setConfidence(calculateConfidence(countIndependentFamilies(related), 2));

        claim.setSummary("未来一段时间内有可能出现开始新关系的机会。" +
                "它指从有好感走向明确关系的机会，通常出现在你情感状态舒展、生活有内容的时期。" +
                "窗口期的建议：若有正在暧昧的对象，适合推进确认关系的对话；若还没有具体对象，把结识渠道维持起来即可。" +
                "机会期不等于\"必须在此期间脱单\"，仓促进入错误关系的代价远大于多等一段时间。");

        return claim;
    }

    private CanonicalClaim resolveCommitmentWindow(List<Evidence> evidence, CanonicalChartData chartData) {
        CanonicalClaim claim = createClaim("COMMITMENT_WINDOW", ClaimClass.TIMING, "FUTURE_EVENT", "S6");
        claim.setSensitivity(ClaimSensitivity.STANDARD);

        List<Evidence> related = filterEvidenceByDomain(evidence, "TIMING_ACTIVATION", "PARTNERSHIP_MECHANISM");
        claim.setSupportingEvidence(related);
        claim.setConfidence(calculateConfidence(countIndependentFamilies(related), 2));

        claim.setSummary("未来一段时间内有可能出现承诺升级的机会，指从暧昧到确定关系、从恋爱到订婚结婚等关键节点。" +
                "这类窗口通常出现在关系经历过一次有效磨合之后，此时的承诺基于了解，稳固度高于热恋期的冲动决定。" +
                "行动建议：适合主动开启关于未来的对话，探测双方方向是否一致；" +
                "对方积极则顺势推进，对方回避则回到关系本身检视问题，而不是反复施压。");

        return claim;
    }

    private CanonicalClaim resolveMarriageWindow(List<Evidence> evidence, CanonicalChartData chartData) {
        CanonicalClaim claim = createClaim("MARRIAGE_WINDOW", ClaimClass.TIMING, "FUTURE_EVENT", "S6");
        claim.setSensitivity(ClaimSensitivity.STANDARD);

        List<Evidence> related = filterEvidenceByDomain(evidence, "TIMING_ACTIVATION", "MARRIAGE_STAGE_SELF");
        claim.setSupportingEvidence(related);
        // 婚姻窗口通常需要更多证据
        claim.setConfidence(calculateConfidence(countIndependentFamilies(related), 4));

        if (countIndependentFamilies(related) < 3) {
            claim.setConfidence(ClaimConfidence.INSUFFICIENT);
            claim.setSummary("当前证据不足以确定具体的婚姻时间窗口。");
        } else {
            claim.setSummary("未来一段时间内有可能出现婚姻机会。");
        }

        return claim;
    }

    private CanonicalClaim resolveRelationshipReassessmentWindow(List<Evidence> evidence, CanonicalChartData chartData) {
        CanonicalClaim claim = createClaim("RELATIONSHIP_REASSESSMENT_WINDOW", ClaimClass.TIMING, "FUTURE_EVENT", "S6");
        claim.setSensitivity(ClaimSensitivity.STANDARD);

        List<Evidence> related = filterEvidenceByDomain(evidence, "TIMING_ACTIVATION", "STRUCTURE_MODIFIER");
        claim.setSupportingEvidence(related);
        claim.setConfidence(calculateConfidence(countIndependentFamilies(related), 2));

        claim.setSummary("未来一段时间内，你可能需要对当前关系进行重新评估。" +
                "评估期不等于关系要出问题，它更像一次定期检修：此前被忽略的差异浮出水面，" +
                "或双方在居住、家庭、职业方向等议题上第一次正面相遇。" +
                "正确的方式是把它当机会而非危机：增加结构性对话，把问题摊开逐条讨论，并减少此期间的重大决定。");

        return claim;
    }

    private CanonicalClaim resolveRelationshipPressureWindow(List<Evidence> evidence, CanonicalChartData chartData) {
        CanonicalClaim claim = createClaim("RELATIONSHIP_PRESSURE_WINDOW", ClaimClass.TIMING, "FUTURE_EVENT", "S6");
        claim.setSensitivity(ClaimSensitivity.STANDARD);

        List<Evidence> related = filterEvidenceByDomain(evidence, "TIMING_ACTIVATION", "RELATIONSHIP_PRESSURE");
        claim.setSupportingEvidence(related);
        claim.setConfidence(calculateConfidence(countIndependentFamilies(related), 2));

        claim.setSummary("未来一段时间内，你的关系可能面临一些外部压力：工作负荷挤占相处时间、家庭意见介入、经济压力或聚少离多等。" +
                "这类压力的特点是测试关系的承载力——问题不在你们之间，但会放大原有的薄弱环节。" +
                "需要警惕两个失败模式：\"各自硬扛\"导致情感连接在沉默中流失；\"压力转嫁\"让本是同盟的两人互相消耗。" +
                "正确做法是结盟对外：明确压力是共同的敌人，并每天保留一段不受压力话题侵占的相处时间。");

        return claim;
    }

    // ============================================================
    // S7: 最值得调整的三个关系课题
    // ============================================================

    private CanonicalClaim resolveRelationshipGrowthTheme(List<Evidence> evidence, CanonicalChartData chartData) {
        CanonicalClaim claim = createClaim("RELATIONSHIP_GROWTH_THEME", ClaimClass.DERIVED, "RELATIONSHIP", "S7");
        claim.setSensitivity(ClaimSensitivity.STANDARD);

        List<Evidence> related = filterEvidenceByDomain(evidence, "EMOTIONAL_SECURITY", "RELATIONSHIP_FUNCTION", "RELATIONAL_DRIVE");
        claim.setSupportingEvidence(related);
        claim.setConfidence(calculateConfidence(countIndependentFamilies(related), 2));

        claim.setSummary("你的关系成长主题集中在建立健康的边界和提升情感表达能力。");

        return claim;
    }

    private CanonicalClaim resolveGrowthTask(List<Evidence> evidence, CanonicalChartData chartData) {
        CanonicalClaim claim = createClaim("GROWTH_TASK", ClaimClass.DERIVED, "USER", "S7");
        claim.setSensitivity(ClaimSensitivity.STANDARD);

        List<Evidence> related = filterEvidenceByDomain(evidence, "EMOTIONAL_SECURITY", "RELATIONAL_DRIVE", "VALUES_MEANING_REPAIR");
        claim.setSupportingEvidence(related);
        claim.setConfidence(calculateConfidence(countIndependentFamilies(related), 2));

        claim.setSummary("你最重要的三个关系成长任务：表达需求、建立边界、冷静处理冲突。" +
                "第一，表达需求：你的结构倾向于\"先观察对方需要什么，再决定自己要不要说\"，" +
                "这让你长期处于\"需求不被看见\"的位置。练习把\"我希望……\"说出口，需求被听见是需求被满足的前提。" +
                "第二，建立边界：你对亲密的向往容易让你在关系早期过度让渡个人空间，等到不适累积才爆发式收回。" +
                "更健康的方式是从小事开始温和地设立边界，让边界成为关系的常识而非冲突的爆点。" +
                "第三，冷静处理冲突：你在情绪激烈时的表达杀伤力较大。给自己设定\"暂停词\"，在情绪顶点按下暂停再回来。" +
                "这三个任务不需要同时完成，建议以季度为单位专注其中一个，反复练习。");

        return claim;
    }

    private CanonicalClaim resolveRelationshipRecommendation(List<Evidence> evidence, CanonicalChartData chartData) {
        CanonicalClaim claim = createClaim("RELATIONSHIP_RECOMMENDATION", ClaimClass.DERIVED, "USER", "S7");
        claim.setSensitivity(ClaimSensitivity.STANDARD);

        List<Evidence> related = filterEvidenceByDomain(evidence, "EMOTIONAL_SECURITY", "RELATIONSHIP_FUNCTION", "RELATIONAL_DRIVE");
        claim.setSupportingEvidence(related);
        claim.setConfidence(calculateConfidence(countIndependentFamilies(related), 2));

        claim.setSummary("建议你：练习表达需求、每周深度对话、冲突时先冷静再沟通。");

        return claim;
    }

    // ============================================================
    // 辅助方法
    // ============================================================

    private CanonicalClaim createClaim(String name, ClaimClass claimClass, String subject, String owner) {
        CanonicalClaim claim = new CanonicalClaim();
        claim.setClaimId(name);
        claim.setCanonicalName(name);
        claim.setClaimClass(claimClass);
        claim.setSubject(subject);
        claim.setPrimaryChapterOwner(owner);
        claim.setRelationshipStage("LONG_TERM_RELATIONSHIP");
        return claim;
    }

    private List<Evidence> filterEvidenceByDomain(List<Evidence> evidence, String... domains) {
        Set<String> domainSet = new HashSet<>(Arrays.asList(domains));
        return evidence.stream()
                .filter(e -> domainSet.contains(e.getTargetDomain()))
                .collect(Collectors.toList());
    }

    private int countIndependentFamilies(List<Evidence> evidence) {
        Set<String> families = new HashSet<>();
        for (Evidence ev : evidence) {
            families.add(ev.getType().name() + "_" + ev.getTargetDomain());
        }
        return families.size();
    }

    private ClaimConfidence calculateConfidence(int familyCount, int requiredCount) {
        if (familyCount >= requiredCount + 1) return ClaimConfidence.HIGH;
        if (familyCount >= requiredCount) return ClaimConfidence.MEDIUM;
        if (familyCount >= requiredCount - 1) return ClaimConfidence.LOW;
        return ClaimConfidence.INSUFFICIENT;
    }

    private D1D9Relation calculateD1D9Relation(CanonicalChartData chartData, String domain) {
        // 简化实现：根据 D1/D9 数据计算关系
        NatalChart d1 = chartData.getD1();
        NatalChart d9 = chartData.getD9();
        if (d1 == null || d9 == null) return D1D9Relation.INSUFFICIENT;

        // 这里应该实现完整的 D1×D9 关系计算
        // 简化返回 REINFORCED
        return D1D9Relation.REINFORCED;
    }

    // 获取行星星座
    private String getPlanetSign(CanonicalChartData chartData, String planet) {
        NatalChart d1 = chartData.getD1();
        if (d1 == null || d1.getPlanets() == null) return "";
        PlanetPosition pos = d1.getPlanets().get(planet);
        return pos != null && pos.getSign() != null ? pos.getSign() : "";
    }

    private String get5HSign(CanonicalChartData chartData) {
        NatalChart d1 = chartData.getD1();
        if (d1 == null || d1.getHouses() == null) return "";
        HouseInfo hi = d1.getHouses().get("5");
        return hi != null && hi.getSign() != null ? hi.getSign() : "";
    }

    private String get7HSign(CanonicalChartData chartData) {
        NatalChart d1 = chartData.getD1();
        if (d1 == null || d1.getHouses() == null) return "";
        HouseInfo hi = d1.getHouses().get("7");
        return hi != null && hi.getSign() != null ? hi.getSign() : "";
    }

    private String getD97HSign(CanonicalChartData chartData) {
        NatalChart d9 = chartData.getD9();
        if (d9 == null || d9.getHouses() == null) return "";
        HouseInfo hi = d9.getHouses().get("7");
        return hi != null && hi.getSign() != null ? hi.getSign() : "";
    }

    private String getD9LagnaSign(CanonicalChartData chartData) {
        NatalChart d9 = chartData.getD9();
        return d9 != null && d9.getAscendantSign() != null ? d9.getAscendantSign() : "";
    }

    private String getDKPlanet(CanonicalChartData chartData) {
        if (chartData.getDkPlanet() != null) return chartData.getDkPlanet();
        NatalChart d1 = chartData.getD1();
        if (d1 == null || d1.getPlanets() == null) return "Venus";
        // 找度数最低
        String dk = null;
        double minDeg = 360;
        for (Map.Entry<String, PlanetPosition> e : d1.getPlanets().entrySet()) {
            if (e.getValue().getDegree() != null && e.getValue().getDegree() < minDeg) {
                minDeg = e.getValue().getDegree();
                dk = e.getKey();
            }
        }
        return dk != null ? dk : "Venus";
    }

    private String getDKInD9Sign(CanonicalChartData chartData) {
        String dk = getDKPlanet(chartData);
        NatalChart d9 = chartData.getD9();
        if (d9 == null || d9.getPlanets() == null) return "";
        PlanetPosition pos = d9.getPlanets().get(dk);
        return pos != null && pos.getSign() != null ? pos.getSign() : "";
    }
}
