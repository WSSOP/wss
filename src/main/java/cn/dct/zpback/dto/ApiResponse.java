package cn.dct.zpback.dto;

import lombok.Data;

import java.io.Serializable;

/**
 * 统一 API 响应包装
 */
@Data
public class ApiResponse<T> implements Serializable {

    private static final long serialVersionUID = 1L;

    /** 状态码：0=成功，其他=失败 */
    private int code;

    /** 消息 */
    private String message;

    /** 数据 */
    private T data;

    public static <T> ApiResponse<T> success(T data) {
        ApiResponse<T> resp = new ApiResponse<>();
        resp.code = 0;
        resp.message = "success";
        resp.data = data;
        return resp;
    }

    public static <T> ApiResponse<T> success() {
        return success(null);
    }

    public static <T> ApiResponse<T> error(int code, String message) {
        ApiResponse<T> resp = new ApiResponse<>();
        resp.code = code;
        resp.message = message;
        return resp;
    }

    /**
     * 带 data 的错误响应
     * 用于需要把稳定错误体（error_code / retryable）透传给前端的场景，
     * 例如星宿关系接口的 error_response.schema.json。
     */
    public static <T> ApiResponse<T> error(int code, String message, T data) {
        ApiResponse<T> resp = new ApiResponse<>();
        resp.code = code;
        resp.message = message;
        resp.data = data;
        return resp;
    }
}
