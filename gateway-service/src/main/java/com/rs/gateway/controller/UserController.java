package com.rs.gateway.controller;

import com.rs.api.entity.User;
import com.rs.gateway.mapper.UserMapper;
import org.springframework.web.bind.annotation.*;

import java.util.Map;
import java.util.UUID;

@RestController
@RequestMapping("/api/user")
public class UserController {

    private final UserMapper userMapper;

    public UserController(UserMapper userMapper) {
        this.userMapper = userMapper;
    }

    /** 登录：账号可以是用户名或邮箱 */
    @PostMapping("/login")
    public Map<String, Object> login(@RequestBody Map<String, String> body) {
        String account = body.getOrDefault("account", "");
        if (account.isEmpty()) {
            account = body.getOrDefault("username", "");
        }
        String password = body.getOrDefault("password", "");
        User user = userMapper.findByAccountAndPassword(account, password);
        if (user == null) {
            return Map.of("ok", false, "msg", "账号或密码错误");
        }
        return Map.of("ok", true,
                "userId", user.getId(),
                "username", user.getUsername(),
                "interestTags", user.getInterestTags(),
                "token", UUID.randomUUID().toString());
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
        user.setPassword(password);
        user.setInterestTags(interestTags);
        try {
            userMapper.insert(user);
        } catch (org.springframework.dao.DuplicateKeyException e) {
            return Map.of("ok", false, "msg", "用户名或邮箱已被注册");
        }
        return Map.of("ok", true, "msg", "注册成功",
                "userId", user.getId(), "username", username, "interestTags", interestTags);
    }
}
