package com.rs.gateway.auth;

import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.util.UUID;

/**
 * 登录令牌：签发后存 Redis（token → userId，7 天过期），
 * AuthFilter 每次请求据此校验登录态。
 */
@Service
public class TokenService {

    private static final String KEY_PREFIX = "auth:token:";
    private static final Duration TTL = Duration.ofDays(7);

    private final StringRedisTemplate redis;

    public TokenService(StringRedisTemplate redis) {
        this.redis = redis;
    }

    /** 签发 token 并绑定用户，返回 token 值 */
    public String issue(long userId) {
        String token = UUID.randomUUID().toString();
        redis.opsForValue().set(KEY_PREFIX + token, String.valueOf(userId), TTL);
        return token;
    }

    /** 校验 token，有效返回绑定的 userId，无效返回 null */
    public Long validate(String token) {
        if (token == null || token.isBlank()) return null;
        String userId = redis.opsForValue().get(KEY_PREFIX + token);
        return userId == null ? null : Long.valueOf(userId);
    }

    /** 注销（登出） */
    public void revoke(String token) {
        if (token != null && !token.isBlank()) {
            redis.delete(KEY_PREFIX + token);
        }
    }
}
