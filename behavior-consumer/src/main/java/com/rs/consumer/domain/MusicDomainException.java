package com.rs.consumer.domain;

/** 音乐领域异常：Controller 层统一翻译为用户可读的错误响应 */
public class MusicDomainException extends RuntimeException {

    public MusicDomainException(String message) {
        super(message);
    }
}
