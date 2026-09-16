package com.rs.gateway.auth;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

import java.util.Map;

/** 全局异常兜底：业务异常返回可读信息，未知异常打日志并返回通用提示（不泄露内部细节） */
@RestControllerAdvice
public class GlobalExceptionHandler {

    private static final Logger log = LoggerFactory.getLogger(GlobalExceptionHandler.class);

    @ExceptionHandler(BizException.class)
    public Map<String, Object> handleBiz(BizException e) {
        return Map.of("ok", false, "msg", e.getMessage());
    }

    @ExceptionHandler(Exception.class)
    public Map<String, Object> handleUnknown(Exception e) {
        log.error("未处理异常", e);
        return Map.of("ok", false, "msg", "服务开小差了，请稍后再试");
    }
}
