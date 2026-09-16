package com.rs.rerank.infrastructure;

import com.rs.rerank.domain.ExposureStore;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.util.List;
import java.util.Set;

/** Redis 实现：exposed:{userId} Set，7 天过期 */
@Component
public class RedisExposureStore implements ExposureStore {

    private static final String KEY_PREFIX = "exposed:";
    private static final Duration EXPOSE_TTL = Duration.ofDays(7);

    private final StringRedisTemplate redis;

    public RedisExposureStore(StringRedisTemplate redis) {
        this.redis = redis;
    }

    @Override
    public Set<String> exposedIds(long userId) {
        Set<String> members = redis.opsForSet().members(KEY_PREFIX + userId);
        return members == null ? Set.of() : members;
    }

    @Override
    public void record(long userId, List<Long> itemIds) {
        if (itemIds.isEmpty()) return;
        String key = KEY_PREFIX + userId;
        itemIds.forEach(id -> redis.opsForSet().add(key, String.valueOf(id)));
        redis.expire(key, EXPOSE_TTL);
    }
}
