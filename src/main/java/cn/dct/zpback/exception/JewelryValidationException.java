package cn.dct.zpback.exception;

import java.util.Collections;
import java.util.Map;

/**
 * 行星珠宝建议｜请求校验失败
 *
 * 只表示「客户端提交的内容不满足问卷契约」，不生成 Completed 结果（§16）。
 */
public class JewelryValidationException extends RuntimeException {

    private static final long serialVersionUID = 1L;

    /** error_code -> detail（question_id / option_id 等） */
    private final Map<String, String> violations;

    public JewelryValidationException(Map<String, String> violations) {
        super("行星珠宝评估参数校验失败: " + violations);
        this.violations = violations == null ? Map.of() : Collections.unmodifiableMap(violations);
    }

    public Map<String, String> getViolations() {
        return violations;
    }
}