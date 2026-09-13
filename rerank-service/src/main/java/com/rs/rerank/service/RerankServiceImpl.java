package com.rs.rerank.service;

import com.rs.api.ItemDTO;
import com.rs.rerank.api.RerankService;
import org.apache.dubbo.config.annotation.DubboService;
import org.springframework.data.redis.core.StringRedisTemplate;

import java.util.*;

/**
 * 重排：已曝光过滤(Redis Set) + 同作者/同标签打散 + 保底回填。
 * 每次返回的结果同时写入该用户的已曝光集合（7 天过期）。
 */
@DubboService
public class RerankServiceImpl implements RerankService {

    private static final String EXPOSE_KEY = "exposed:%d";
    private static final long EXPOSE_TTL_SECONDS = 7 * 24 * 3600;

    private final StringRedisTemplate redis;

    public RerankServiceImpl(StringRedisTemplate redis) {
        this.redis = redis;
    }

    @Override
    public List<ItemDTO> rerank(long userId, List<ItemDTO> candidates, int topN) {
        if (candidates == null || candidates.isEmpty()) return List.of();

        String exposeKey = String.format(EXPOSE_KEY, userId);
        Set<String> exposed = members(exposeKey);

        // 1. 过滤已曝光；若过滤后不足 topN，用被过滤的候选保底回填（避免刷空）
        List<ItemDTO> fresh = new ArrayList<>(
                candidates.stream().filter(i -> !exposed.contains(String.valueOf(i.getId()))).toList());
        if (fresh.size() < topN) {
            for (ItemDTO c : candidates) {
                if (fresh.size() >= candidates.size()) break;
                if (!fresh.contains(c)) fresh.add(c);
            }
        }
        Deque<ItemDTO> pool = new ArrayDeque<>(fresh);

        // 2. 贪心打散：同作者连续出现不超过 1 次，同标签连续不超过 2 次
        List<ItemDTO> result = new ArrayList<>();
        String lastAuthor = null;
        Map<String, Integer> recentTagCount = new HashMap<>();

        while (result.size() < topN && !pool.isEmpty()) {
            ItemDTO picked = pickOne(pool, lastAuthor, recentTagCount);
            result.add(picked);
            pool.remove(picked);
            lastAuthor = picked.getAuthor();
            for (String tag : picked.tagSet()) {
                recentTagCount.merge(tag, 1, Integer::sum);
            }
        }

        // 3. 曝光去重集合写入（用于下次请求过滤）
        for (ItemDTO i : result) {
            redis.opsForSet().add(exposeKey, String.valueOf(i.getId()));
        }
        redis.expire(exposeKey, java.time.Duration.ofSeconds(EXPOSE_TTL_SECONDS));

        return result;
    }

    /** 从候选池挑第一个满足打散规则的，若都不满足则取第一个（保底） */
    private ItemDTO pickOne(Deque<ItemDTO> pool, String lastAuthor, Map<String, Integer> recentTagCount) {
        for (ItemDTO i : pool) {
            if (i.getAuthor() != null && i.getAuthor().equals(lastAuthor)) continue;
            boolean tagOk = i.tagSet().stream().allMatch(t -> recentTagCount.getOrDefault(t, 0) < 2);
            if (tagOk) return i;
        }
        return pool.getFirst();
    }

    private Set<String> members(String key) {
        try {
            Set<String> s = redis.opsForSet().members(key);
            return s == null ? Set.of() : s;
        } catch (Exception e) {
            return Set.of();
        }
    }
}
