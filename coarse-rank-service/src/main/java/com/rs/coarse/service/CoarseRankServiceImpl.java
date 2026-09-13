package com.rs.coarse.service;

import com.rs.coarse.api.CoarseRankService;
import com.rs.api.ItemDTO;
import org.apache.dubbo.config.annotation.DubboService;

import java.time.Duration;
import java.time.LocalDateTime;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;

/**
 * 粗排：多路召回结果去重合并，按"热度 + 新鲜度"轻量打分截断。
 * 目标是把召回集快速压缩，为精排减负。
 */
@DubboService
public class CoarseRankServiceImpl implements CoarseRankService {

    @Override
    public List<ItemDTO> coarseRank(long userId, List<ItemDTO> candidates, int size) {
        if (candidates == null || candidates.isEmpty()) return List.of();

        // 按 id 去重（保留先到的，保留其召回来源）
        var dedup = new LinkedHashMap<Long, ItemDTO>();
        for (ItemDTO dto : candidates) {
            dedup.putIfAbsent(dto.getId(), dto);
        }

        LocalDateTime now = LocalDateTime.now();
        return dedup.values().stream()
                .sorted(Comparator.comparingDouble((ItemDTO i) ->
                        coarseScore(i, now)).reversed())
                .limit(size)
                .toList();
    }

    /** 粗排分 = 0.7 * 热度(归一) + 0.3 * 新鲜度(72h 衰减) */
    private double coarseScore(ItemDTO i, LocalDateTime now) {
        double hot = Math.min(i.getHotScore() / 100.0, 1.0);
        double fresh = 1.0;
        if (i.getPublishTime() != null) {
            long hours = Duration.between(i.getPublishTime(), now).toHours();
            fresh = 1.0 / (1.0 + hours / 72.0);
        }
        return 0.7 * hot + 0.3 * fresh;
    }
}
