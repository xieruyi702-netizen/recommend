package com.rs.rerank.domain;

import com.rs.api.ItemDTO;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * 打散器（纯领域规则）：同作者连续出现不超过 1 次，同标签连续不超过 2 次；
 * 若池内无满足规则的候选则保底取第一个，保证结果不为空。
 * 无任何外部依赖，可独立单测。
 */
public final class Diversifier {

    private Diversifier() {
    }

    public static List<ItemDTO> diversify(List<ItemDTO> pool, int topN) {
        List<ItemDTO> result = new ArrayList<>();
        Deque<ItemDTO> remaining = new ArrayDeque<>(pool);
        String lastAuthor = null;
        Map<String, Integer> recentTagCount = new HashMap<>();

        while (result.size() < topN && !remaining.isEmpty()) {
            ItemDTO picked = pickOne(remaining, lastAuthor, recentTagCount);
            result.add(picked);
            remaining.remove(picked);
            lastAuthor = picked.getAuthor();
            for (String tag : picked.tagSet()) {
                recentTagCount.merge(tag, 1, Integer::sum);
            }
        }
        return result;
    }

    /** 从候选池挑第一个满足打散规则的，若都不满足则取第一个（保底） */
    private static ItemDTO pickOne(Deque<ItemDTO> pool, String lastAuthor, Map<String, Integer> recentTagCount) {
        for (ItemDTO candidate : pool) {
            if (candidate.getAuthor() != null && candidate.getAuthor().equals(lastAuthor)) continue;
            boolean tagOk = candidate.tagSet().stream()
                    .allMatch(t -> recentTagCount.getOrDefault(t, 0) < 2);
            if (tagOk) return candidate;
        }
        return pool.getFirst();
    }
}
