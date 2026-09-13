package com.rs.gateway.controller;

import com.rs.api.entity.User;
import com.rs.gateway.auth.TokenService;
import com.rs.gateway.mapper.UserMapper;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

@RestController
@RequestMapping("/api/user")
public class UserController {

    private final UserMapper userMapper;
    private final TokenService tokenService;
    private final PasswordEncoder passwordEncoder;

    public UserController(UserMapper userMapper, TokenService tokenService, PasswordEncoder passwordEncoder) {
        this.userMapper = userMapper;
        this.tokenService = tokenService;
        this.passwordEncoder = passwordEncoder;
    }

    private String encode(String raw) {
        return "{bcrypt}" + passwordEncoder.encode(raw);
    }

    /** 登录：账号可以是用户名或邮箱 */
    @PostMapping("/login")
    public Map<String, Object> login(@RequestBody Map<String, String> body) {
        String account = body.getOrDefault("account", "");
        if (account.isEmpty()) {
            account = body.getOrDefault("username", "");
        }
        String password = body.getOrDefault("password", "");
        User user = userMapper.findByAccount(account);
        if (user == null) {
            return Map.of("ok", false, "msg", "账号或密码错误");
        }
        // 密码校验：{bcrypt} 哈希比对；{plain}/无前缀为存量明文，登录成功即惰性升级为哈希
        String stored = user.getPassword();
        boolean matched;
        if (stored != null && stored.startsWith("{bcrypt}")) {
            matched = passwordEncoder.matches(password, stored.substring(8));
        } else if (stored != null && stored.startsWith("{plain}")) {
            matched = stored.substring(7).equals(password);
        } else {
            matched = stored != null && stored.equals(password);
        }
        if (!matched) {
            return Map.of("ok", false, "msg", "账号或密码错误");
        }
        if (stored == null || !stored.startsWith("{bcrypt}")) {
            userMapper.updatePassword(user.getId(), encode(password));   // 惰性迁移
        }
        return Map.of("ok", true,
                "userId", user.getId(),
                "username", user.getUsername(),
                "interestTags", user.getInterestTags(),
                "token", tokenService.issue(user.getId()));
    }

    /** 注册：用户名 + 邮箱 + 密码 + 兴趣标签（用于个性化召回） */
    @PostMapping("/register")
    public Map<String, Object> register(@RequestBody Map<String, String> body) {
        String username = body.getOrDefault("username", "").trim();
        String email = body.getOrDefault("email", "").trim();
        String password = body.getOrDefault("password", "").trim();
        String interestTags = body.getOrDefault("interestTags", "").trim();
        if (username.isEmpty() || password.isEmpty()) {
            return Map.of("ok", false, "msg", "用户名和密码不能为空");
        }
        if (!email.matches("^[^@\\s]+@[^@\\s]+\\.[^@\\s]+$")) {
            return Map.of("ok", false, "msg", "邮箱格式不正确");
        }
        User user = new User();
        user.setUsername(username);
        user.setEmail(email);
        user.setPassword(encode(password));
        user.setInterestTags(interestTags);
        try {
            userMapper.insert(user);
        } catch (org.springframework.dao.DuplicateKeyException e) {
            return Map.of("ok", false, "msg", "用户名或邮箱已被注册");
        }
        return Map.of("ok", true, "msg", "注册成功",
                "userId", user.getId(), "username", username, "interestTags", interestTags,
                "token", tokenService.issue(user.getId()));
    }
}
