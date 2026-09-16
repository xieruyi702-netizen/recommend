package com.rs.recall.application;

import com.rs.api.ItemDTO;
import com.rs.recall.api.RecallService;
import com.rs.api.entity.Item;
import com.rs.recall.infrastructure.persistence.RecallMapper;
import org.apache.dubbo.config.annotation.DubboService;
import org.springframework.data.redis.core.StringRedisTemplate;

import java.util.*;
import java.util.concurrent.CompletableFuture;
import java.util.stream.Collectors;

/**
 * 多路召回：热度召回(Redis ZSet) + 标签召回(用户兴趣 × 物料标签) + 简化 ItemCF(行为共现)
 * 三路并行执行（CompletableFuture），合并去重后返回。
 */
@DubboService
public class RecallApplicationService implements RecallService {

    private final RecallMapper recallMapper;
    private final StringRedisTemplate redis;

    public RecallApplicationService(RecallMapper recallMapper, StringRedisTemplate redis) {
        this.recallMapper = recallMapper;
        this.redis = redis;
    }

    @Override
    public List<ItemDTO> recall(long userId, int size) {
        var byHot = CompletableFuture.supplyAsync(() -> recallByHot(size));
        var byTag = CompletableFuture.supplyAsync(() -> recallByTag(userId, size));
        var byItemCF = CompletableFuture.supplyAsync(() -> recallByItemCF(userId, size));

        CompletableFuture.allOf(byHot, byTag, byItemCF).join();

        Map<Long, ItemDTO> merged = new LinkedHashMap<>();
        for (var future : List.of(byHot, byTag, byItemCF)) {
            for (ItemDTO dto : future.join()) {
                merged.putIfAbsent(dto.getId(), dto);
            }
        }
        return new ArrayList<>(merged.values());
    }

    /** 热度召回：优先取 Redis ZSet 实时热度榜，未预热时回源 MySQL 并回填 */
    private List<ItemDTO> recallByHot(int size) {
        Set<String> ids = redis.opsForZSet().reverseRange("hot:rank", 0, size - 1);
        if (ids != null && !ids.isEmpty()) {
            return loadItems(ids.stream().map(Long::parseLong).toList(), "hot");
        }
        List<ItemDTO> items = toDTOs(recallMapper.selectByHot(size), "hot");
        // 回填热度榜
        for (ItemDTO i : items) {
            redis.opsForZSet().add("hot:rank", String.valueOf(i.getId()), i.getHotScore());
        }
        return items;
    }

    /** 标签召回：用户兴趣标签 LIKE 匹配物料 */
    private List<ItemDTO> recallByTag(long userId, int size) {
        String tags = recallMapper.selectInterestTags(userId);
        if (tags == null || tags.isBlank()) return List.of();
        List<String> tagList = splitTags(tags);
        if (tagList.isEmpty()) return List.of();
        return toDTOs(recallMapper.selectByTags(tagList, size), "tag");
    }

    /** 简化 ItemCF：取用户最近点击的物料，召回与它们标签相同的其他物料 */
    private List<ItemDTO> recallByItemCF(long userId, int size) {
        List<String> clickedTags = recallMapper.selectClickedTags(userId, 5);
        if (clickedTags.isEmpty()) return List.of();
        // 每条记录本身是逗号分隔的多标签，拍平成单标签列表（OR LIKE 语义不变）
        List<String> tagList = clickedTags.stream()
                .flatMap(t -> splitTags(t).stream())
                .distinct()
                .collect(Collectors.toList());
        if (tagList.isEmpty()) return List.of();
        return toDTOs(recallMapper.selectByTags(tagList, size), "itemcf");
    }

    private List<ItemDTO> loadItems(List<Long> ids, String source) {
        if (ids.isEmpty()) return List.of();
        return toDTOs(recallMapper.selectByIds(ids), source);
    }

    private List<String> splitTags(String tags) {
        return Arrays.stream(tags.split(","))
                .map(String::trim)
                .filter(t -> !t.isEmpty())
                .collect(Collectors.toList());
    }

    private List<ItemDTO> toDTOs(List<Item> items, String source) {
        return items.stream().map(Item::toDTO).peek(d -> d.setRecallSource(source)).collect(Collectors.toList());
    }
}
