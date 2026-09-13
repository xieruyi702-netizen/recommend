package com.rs.gateway.controller;

import com.rs.api.entity.User;
import com.rs.gateway.auth.TokenService;
import com.rs.gateway.mapper.UserMapper;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.web.bind.annotation.*;

import java.security.SecureRandom;
import java.time.Duration;
import java.util.Map;
import java.util.UUID;

/**
 * 邮箱验证码注册/登录。
 * 验证码存 Redis（5 分钟有效，验证一次即删），60s 内禁止重复发送。
 * 未配置 SMTP 时降级为模拟模式：验证码打印到服务日志，方便本地联调。
 */
@RestController
@RequestMapping("/api/auth")
public class EmailAuthController {

    private final UserMapper userMapper;
    private final TokenService tokenService;
    private final PasswordEncoder passwordEncoder;
    private final StringRedisTemplate redis;
    private final JavaMailSender mailSender;   // 无 SMTP 配置时容器内不存在该 Bean
    private final boolean smtpEnabled;

    public EmailAuthController(UserMapper userMapper, TokenService tokenService, PasswordEncoder passwordEncoder, StringRedisTemplate redis,
                               org.springframework.beans.factory.ObjectProvider<JavaMailSender> mailSenderProvider,
                               @org.springframework.beans.factory.annotation.Value("${spring.mail.host:}") String mailHost) {
        this.userMapper = userMapper;
        this.tokenService = tokenService;
        this.passwordEncoder = passwordEncoder;
        this.redis = redis;
        this.mailSender = mailSenderProvider.getIfAvailable();
        this.smtpEnabled = mailSender != null && !mailHost.isBlank();
    }

    /** 发送验证码：purpose = register | login */
    @PostMapping("/send-code")
    public Map<String, Object> sendCode(@RequestBody Map<String, String> body) {
        String email = body.getOrDefault("email", "").trim().toLowerCase();
        String purpose = body.getOrDefault("purpose", "login");
        if (!email.matches("^[^@\\s]+@[^@\\s]+\\.[^@\\s]+$")) {
            return Map.of("ok", false, "msg", "邮箱格式不正确");
        }
        if ("register".equals(purpose) && userMapper.countByEmail(email) > 0) {
            return Map.of("ok", false, "msg", "该邮箱已注册，请直接使用验证码登录");
        }
        // login 用途：邮箱未注册也允许发码，登录时自动完成注册

        String limitKey = "email:limit:" + email;
        if (Boolean.TRUE.equals(redis.hasKey(limitKey))) {
            long ttl = redis.getExpire(limitKey);
            return Map.of("ok", false, "msg", "发送太频繁，请 " + Math.max(ttl, 1) + " 秒后再试");
        }

        String code = String.format("%06d", new SecureRandom().nextInt(1000000));
        redis.opsForValue().set("email:code:" + purpose + ":" + email, code, Duration.ofMinutes(5));
        redis.opsForValue().set(limitKey, "1", Duration.ofSeconds(60));

        if (smtpEnabled) {
            SimpleMailMessage msg = new SimpleMailMessage();
            msg.setFrom(System.getenv().getOrDefault("SMTP_FROM", "echo@noreply.local"));
            msg.setTo(email);
            msg.setSubject("【_echo 音乐推荐】您的验证码");
            msg.setText("您的验证码是：" + code + "，5 分钟内有效。若非本人操作请忽略。");
            mailSender.send(msg);
            return Map.of("ok", true, "msg", "验证码已发送至邮箱，请查收（注意垃圾箱）");
        } else {
            // 模拟模式：打印到日志
            System.out.println("[MOCK-MAIL] to=" + email + " purpose=" + purpose + " code=" + code);
            return Map.of("ok", true, "msg", "SMTP 未配置，验证码已打印到服务日志（模拟发信）", "mockCode", code);
        }
    }

    /** 邮箱验证码注册：邮箱 + 验证码 + 用户名 +（可选）密码/兴趣标签 */
    @PostMapping("/register")
    public Map<String, Object> register(@RequestBody Map<String, String> body) {
        String email = body.getOrDefault("email", "").trim().toLowerCase();
        String code = body.getOrDefault("code", "").trim();
        String username = body.getOrDefault("username", "").trim();
        String password = body.getOrDefault("password", "").trim();
        String interestTags = body.getOrDefault("interestTags", "").trim();

        String err = verify("register", email, code);
        if (err != null) return Map.of("ok", false, "msg", err);
        if (username.isEmpty()) return Map.of("ok", false, "msg", "用户名不能为空");

        try {
            insertUser(username, email,
                    password.isEmpty() ? UUID.randomUUID().toString() : password, interestTags);
        } catch (org.springframework.dao.DuplicateKeyException e) {
            return Map.of("ok", false, "msg", "用户名已被占用");
        }
        return loginResult(email);
    }

    /** 邮箱验证码登录（免密码） */
    @PostMapping("/login")
    public Map<String, Object> login(@RequestBody Map<String, String> body) {
        String email = body.getOrDefault("email", "").trim().toLowerCase();
        String code = body.getOrDefault("code", "").trim();
        String err = verify("login", email, code);
        if (err != null) return Map.of("ok", false, "msg", err);
        return loginResult(email);
    }

    private String verify(String purpose, String email, String code) {
        if (code.isEmpty()) return "请输入验证码";
        String key = "email:code:" + purpose + ":" + email;
        String saved = redis.opsForValue().get(key);
        if (saved == null) return "验证码已过期，请重新获取";
        if (!saved.equals(code)) return "验证码错误";
        redis.delete(key);   // 一次一验
        return null;
    }

    private void insertUser(String username, String email, String password, String interestTags) {
        User user = new User();
        user.setUsername(username);
        user.setEmail(email);
        user.setPassword("{bcrypt}" + passwordEncoder.encode(password));
        user.setInterestTags(interestTags);
        userMapper.insert(user);
    }

    private Map<String, Object> loginResult(String email) {
        User user = userMapper.findByEmail(email);
        if (user == null) {
            // 首次验证码登录：自动注册，用户名取邮箱前缀（重名则加随机后缀），默认无兴趣标签
            String username = email.split("@")[0].replaceAll("[^a-zA-Z0-9_\\u4e00-\\u9fa5]", "");
            if (username.isBlank()) username = "user";
            try {
                insertUser(username, email, UUID.randomUUID().toString(), "");
            } catch (org.springframework.dao.DuplicateKeyException e) {
                username = username + "_" + String.format("%04d", new SecureRandom().nextInt(10000));
                insertUser(username, email, UUID.randomUUID().toString(), "");
            }
            user = userMapper.findByEmail(email);
        }
        return Map.of("ok", true,
                "userId", user.getId(),
                "username", user.getUsername(),
                "interestTags", user.getInterestTags(),
                "token", tokenService.issue(user.getId()));
    }
}
