package cn.dct.zpback.exception;

import java.util.Collections;
import java.util.Map;

/**
 * 问星渡｜课程与活动 请求校验失败
 *
 * 与 JewelryValidationException 分开：这个系统是付费交易，
 * 错误分类要更细（权限不足 / 名额已满 / 已拥有权限 / 状态非法），
 * 混在一个异常里会让前端无法区分「换个操作」还是「必须联系客服」。
 */
public class LearningValidationException extends RuntimeException {

    private static final long serialVersionUID = 1L;

    /** 稳定的 error_code，前端据此决定提示与后续动作 */
    private final String code;

    /** 补充信息，如当前名额 / 已报名人数 */
    private final Map<String, Object> details;

    public LearningValidationException(String code, String message) {
        this(code, message, Map.of());
    }

    public LearningValidationException(String code, String message, Map<String, Object> details) {
        super(message);
        this.code = code;
        this.details = details == null ? Map.of() : Collections.unmodifiableMap(details);
    }

    public String getCode() {
        return code;
    }

    public Map<String, Object> getDetails() {
        return details;
    }
}
