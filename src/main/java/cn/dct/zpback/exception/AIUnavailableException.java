package cn.dct.zpback.exception;

/**
 * AI 服务不可用
 *
 * AIService 不再把「调用失败」伪装成一段模型输出文本返回。
 *
 * 原因：失败文案一旦被当成模型内容返回，上层就无法区分
 * 「模型真的返回了这段话」和「这次调用炸了」，
 * 失败文案会被直接当作报告正文写进数据库，用户看到一份内容是
 * 「抱歉，AI 服务暂时不可用」的报告。
 *
 * 调用方必须显式处理：要么降级到确定性内容，要么向上抛出本异常。
 */
public class AIUnavailableException extends RuntimeException {

    private static final long serialVersionUID = 1L;

    /** 是否值得重试（超时 / 5xx / 网络抖动为 true，参数或鉴权错误为 false） */
    private final boolean retryable;

    public AIUnavailableException(String message) {
        this(message, true, null);
    }

    public AIUnavailableException(String message, Throwable cause) {
        this(message, true, cause);
    }

    public AIUnavailableException(String message, boolean retryable, Throwable cause) {
        super(message, cause);
        this.retryable = retryable;
    }

    public boolean isRetryable() {
        return retryable;
    }
}