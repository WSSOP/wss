package cn.dct.zpback.engine.synbasic;

import cn.dct.zpback.constant.SynBasicConstants;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Map;

/**
 * Module Validator + Cross-Module QA（02_RUNTIME_INPUT_OUTPUT_CONTRACT §6、QA 02）
 *
 * - 校验 module_status 与输出是否一致（READY/DEGRADED 必须有 Pair；UNAVAILABLE/VALIDATION_FAILED 必须为 null + fallback）；
 * - Claim Firewall：正文不得出现匹配度 / 谁更爱 / 婚姻结果等整体关系结论；
 * - 四模块 Primary scene 不应无理由重复。
 */
@Component
public class SynBasicValidator {

    /** 单模块校验；返回 null 表示通过，否则返回失败原因 */
    public String validateModule(String status, boolean hasPair, String fallback,
                                 String aCopy, String bCopy, String pairCopy) {
        boolean readyOrDegraded = SynBasicConstants.MODULE_READY.equals(status)
                || SynBasicConstants.MODULE_DEGRADED.equals(status);
        if (readyOrDegraded) {
            if (!hasPair || pairCopy == null) {
                return "READY/DEGRADED 必须存在 Pair Interaction";
            }
            if (fallback != null) {
                return "READY/DEGRADED 的 fallback_message 必须为 null";
            }
            if (aCopy == null || bCopy == null) {
                return "READY/DEGRADED 必须同时存在 A/B Profile";
            }
        } else {
            if (hasPair || pairCopy != null) {
                return "UNAVAILABLE/VALIDATION_FAILED 不得输出 Pair Interaction";
            }
            if (fallback == null) {
                return "UNAVAILABLE/VALIDATION_FAILED 必须提供 fallback_message";
            }
        }
        return checkFirewall(aCopy, bCopy, pairCopy);
    }

    /** Claim Firewall：禁止整体关系结论 */
    public String checkFirewall(String... texts) {
        for (String t : texts) {
            if (t == null) {
                continue;
            }
            for (String forbidden : SynBasicConstants.FORBIDDEN_OUTPUT_PATTERNS) {
                if (t.contains(forbidden)) {
                    return "正文命中禁止的整体关系结论关键词: " + forbidden;
                }
            }
        }
        return null;
    }

    /**
     * Cross-Module QA：同一 pair 四模块 Primary scene 不应无理由重复。
     *
     * @param sceneByModule moduleKey → scene_type
     * @return 重复的 scene，无重复返回 null
     */
    public String checkSceneDiversity(Map<String, String> sceneByModule) {
        List<String> seen = new java.util.ArrayList<>();
        for (Map.Entry<String, String> e : sceneByModule.entrySet()) {
            String scene = e.getValue();
            if (scene == null) {
                continue;
            }
            if (seen.contains(scene)) {
                return scene;
            }
            seen.add(scene);
        }
        return null;
    }
}
