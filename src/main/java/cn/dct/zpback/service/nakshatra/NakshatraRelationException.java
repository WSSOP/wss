package cn.dct.zpback.service.nakshatra;

/**
 * 星宿关系稳定错误
 *
 * 对应 skill/docs/05 §10：错误码必须稳定，前端按 error_code 决定处理策略。
 * 规则或固定文案缺失时返回稳定错误，绝不调用 AI 兜底。
 */
public class NakshatraRelationException extends RuntimeException {

    private static final long serialVersionUID = 1L;

    private final String errorCode;
    private final int httpStatus;
    private final boolean retryable;

    public NakshatraRelationException(String errorCode, int httpStatus, String message, boolean retryable) {
        super(message);
        this.errorCode = errorCode;
        this.httpStatus = httpStatus;
        this.retryable = retryable;
    }

    public String getErrorCode() {
        return errorCode;
    }

    public int getHttpStatus() {
        return httpStatus;
    }

    public boolean isRetryable() {
        return retryable;
    }
}
