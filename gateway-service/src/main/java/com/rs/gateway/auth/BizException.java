package com.rs.gateway.auth;

/** 业务异常：携带用户可读信息，由 GlobalExceptionHandler 统一翻译为 HTTP 响应 */
public class BizException extends RuntimeException {

    private final int status;

    public BizException(String message) {
        this(message, 400);
    }

    public BizException(String message, int status) {
        super(message);
        this.status = status;
    }

    public int getStatus() {
        return status;
    }
}
