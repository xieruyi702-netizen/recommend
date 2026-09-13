package com.rs.gateway.auth;

import org.junit.jupiter.api.Test;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;

import static org.junit.jupiter.api.Assertions.*;

/** 验证真实 BCrypt 编码/校验行为（非 mock），覆盖惰性迁移的格式约定 */
class PasswordMigrationTest {

    private final BCryptPasswordEncoder encoder = new BCryptPasswordEncoder();

    @Test
    void bcryptHashShouldMatchRawPasswordAndEmbedSalt() {
        String raw = "s3cret-密码";
        String hash = encoder.encode(raw);
        assertTrue(hash.startsWith("$2a$") || hash.startsWith("$2b$"));
        assertNotEquals(hash, encoder.encode(raw), "随机盐：同一明文两次编码结果应不同");
        assertTrue(encoder.matches(raw, hash));
        assertFalse(encoder.matches("wrong", hash));
    }

    @Test
    void plainPrefixLazyMigrationFlow() {
        String stored = "{plain}123456";
        String raw = "123456";

        boolean matched = stored.substring(7).equals(raw);
        assertTrue(matched);
        if (matched) {
            String upgraded = "{bcrypt}" + encoder.encode(raw);
            assertTrue(upgraded.startsWith("{bcrypt}"));
            assertTrue(encoder.matches(raw, upgraded.substring(8)));
        }
    }
}
