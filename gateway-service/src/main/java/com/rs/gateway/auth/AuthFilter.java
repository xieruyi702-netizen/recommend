package com.rs.gateway.auth;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.Set;

/**
 * 登录态统一校验：除白名单外的 /api/** 请求必须携带有效 token
 * （Authorization: Bearer <token> 或 X-Token: <token>），
 * 校验通过后把 userId 写入 request attribute 供 Controller 使用。
 */
@Component
public class AuthFilter extends OncePerRequestFilter {

    private static final Set<String> WHITELIST = Set.of(
            "/api/user/login",
            "/api/user/register",
            "/api/auth/send-code",
            "/api/auth/register",
            "/api/auth/login",
            "/api/news/list"      // 频道页对未登录用户开放
    );

    private final TokenService tokenService;

    public AuthFilter(TokenService tokenService) {
        this.tokenService = tokenService;
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response,
                                    FilterChain chain) throws ServletException, IOException {
        String path = request.getRequestURI();
        if (!path.startsWith("/api/") || WHITELIST.contains(path)) {
            chain.doFilter(request, response);
            return;
        }

        Long userId = tokenService.validate(extractToken(request));
        if (userId == null) {
            response.setStatus(HttpServletResponse.SC_UNAUTHORIZED);
            response.setContentType("application/json;charset=UTF-8");
            response.getWriter().write("{\"ok\":false,\"msg\":\"未登录或登录已过期\"}");
            return;
        }
        request.setAttribute("userId", userId);
        chain.doFilter(request, response);
    }

    private String extractToken(HttpServletRequest request) {
        String auth = request.getHeader("Authorization");
        if (auth != null && auth.startsWith("Bearer ")) {
            return auth.substring(7);
        }
        return request.getHeader("X-Token");
    }
}
