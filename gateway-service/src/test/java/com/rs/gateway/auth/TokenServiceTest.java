package com.rs.gateway.auth;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.ValueOperations;

import java.time.Duration;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class TokenServiceTest {

    @Mock
    private StringRedisTemplate redis;
    @Mock
    private ValueOperations<String, String> valueOps;

    @Test
    void issueShouldStoreTokenBoundToUserWithTtl() {
        when(redis.opsForValue()).thenReturn(valueOps);

        String token = new TokenService(redis).issue(42L);

        assertFalse(token.isBlank());
        verify(valueOps).set(eq("auth:token:" + token), eq("42"), any(Duration.class));
    }

    @Test
    void validateShouldReturnUserIdOrResolveNull() {
        when(redis.opsForValue()).thenReturn(valueOps);
        when(valueOps.get("auth:token:good")).thenReturn("42");
        when(valueOps.get("auth:token:bad")).thenReturn(null);

        TokenService service = new TokenService(redis);
        assertEquals(42L, service.validate("good"));
        assertNull(service.validate("bad"));
        assertNull(service.validate(null));
        assertNull(service.validate("  "));
    }

    @Test
    void revokeShouldDeleteKey() {
        new TokenService(redis).revoke("abc");
        verify(redis).delete("auth:token:abc");
    }
}
