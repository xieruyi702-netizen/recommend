package com.rs.gateway.auth;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.ValueOperations;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;

import java.time.Duration;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class AuthFilterTest {

    @Mock
    private StringRedisTemplate redis;
    @Mock
    private ValueOperations<String, String> valueOps;

    private AuthFilter filter;

    @BeforeEach
    void setUp() {
        filter = new AuthFilter(new TokenService(redis));
    }

    private MockHttpServletRequest get(String uri, String token) {
        MockHttpServletRequest req = new MockHttpServletRequest("GET", uri);
        if (token != null) req.addHeader("Authorization", "Bearer " + token);
        return req;
    }

    @Test
    void validTokenShouldPassAndBindUserId() throws Exception {
        when(redis.opsForValue()).thenReturn(valueOps);
        when(valueOps.get("auth:token:t1")).thenReturn("42");

        MockHttpServletRequest req = get("/api/feed/recommend?userId=42", "t1");
        MockHttpServletResponse res = new MockHttpServletResponse();
        filter.doFilter(req, res, (req2, res2) -> {
            assertEquals(42L, ((Long) req2.getAttribute("userId")).longValue());
        });
        assertEquals(200, res.getStatus());
    }

    @Test
    void missingOrInvalidTokenShouldReturn401() throws Exception {
        when(redis.opsForValue()).thenReturn(valueOps);
        when(valueOps.get(anyString())).thenReturn(null);

        MockHttpServletResponse res = new MockHttpServletResponse();
        filter.doFilter(get("/api/feed/recommend?userId=42", null), res, (req2, res2) -> fail("不应放行"));
        assertEquals(401, res.getStatus());
        assertTrue(res.getContentAsString().contains("未登录"));

        MockHttpServletResponse res2 = new MockHttpServletResponse();
        filter.doFilter(get("/api/feed/recommend", "bad-token"), res2, (req3, res3) -> fail("不应放行"));
        assertEquals(401, res2.getStatus());
    }

    @Test
    void whitelistShouldPassWithoutToken() throws Exception {
        for (String uri : Set.of("/api/user/login", "/api/auth/send-code", "/api/news/list")) {
            MockHttpServletResponse res = new MockHttpServletResponse();
            filter.doFilter(get(uri, null), res, (req2, res2) -> { });
            assertEquals(200, res.getStatus(), uri + " 应在白名单内");
        }
    }
}
