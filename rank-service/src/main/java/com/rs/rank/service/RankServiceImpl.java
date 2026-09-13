package com.rs.rank.service;

import com.rs.api.ItemDTO;
import com.rs.rank.api.RankService;
import com.rs.api.entity.RankConfig;
import com.rs.rank.mapper.RankConfigMapper;
import com.rs.rank.mapper.UserMapper;
import org.apache.dubbo.config.annotation.DubboService;

import java.time.Duration;
import java.time.LocalDateTime;
import java.util.*;
import java.util.concurrent.ThreadLocalRandom;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

/**
 * 精排：加权打分模型
 *   score = w1*ctr + w2*兴趣匹配度 + w3*热度归一 + w4*新鲜度
 * 权重存于 MySQL rank_config 表，本地缓存 30s 定时刷新，支持在线热修改。
 */
@DubboService
public class RankServiceImpl implements RankService {

    private final RankConfigMapper rankConfigMapper;
    private final UserMapper userMapper;
    private volatile double[] weights = {0.4, 0.3, 0.2, 0.1};

    private final ScheduledExecutorService refresher = Executors.newSingleThreadScheduledExecutor();

    public RankServiceImpl(RankConfigMapper rankConfigMapper, UserMapper userMapper) {
        this.rankConfigMapper = rankConfigMapper;
        this.userMapper = userMapper;
        refreshWeights();
        refresher.scheduleAtFixedRate(this::refreshWeights, 30, 30, TimeUnit.SECONDS);
    }

    @Override
    public List<ItemDTO> rank(long userId, List<ItemDTO> candidates, int size) {
        if (candidates == null || candidates.isEmpty()) return List.of();

        Set<String> interests = loadUserInterests(userId);
        LocalDateTime now = LocalDateTime.now();
        double[] w = weights;

        for (ItemDTO i : candidates) {
            double match = interestMatch(i, interests);
            double hot = Math.min(i.getHotScore() / 100.0, 1.0);
            double fresh = 1.0;
            if (i.getPublishTime() != null) {
                long hours = Math.max(Duration.between(i.getPublishTime(), now).toHours(), 0);
                fresh = 1.0 / (1.0 + hours / 48.0);
            }
            i.setRankScore(w[0] * i.getCtr() + w[1] * match + w[2] * hot + w[3] * fresh);
        }

        List<ItemDTO> sorted = candidates.stream()
                .sorted(Comparator.comparingDouble(ItemDTO::getRankScore).reversed())
                .toList();

        // 探索保量：给非主流来源的内容保留约 30% 名额，避免单一平台垄断信息流
        Map<String, Long> byAuthor = candidates.stream()
                .collect(java.util.stream.Collectors.groupingBy(ItemDTO::getAuthor, java.util.stream.Collectors.counting()));
        String majority = byAuthor.entrySet().stream()
                .max(Map.Entry.comparingByValue()).map(Map.Entry::getKey).orElse("");
        int exploreSlots = Math.max(1, size * 3 / 10);

        List<ItemDTO> result = new ArrayList<>(sorted.subList(0, Math.min(size, sorted.size())));
        List<ItemDTO> explore = sorted.stream()
                .filter(i -> !majority.equals(i.getAuthor()))
                .limit(exploreSlots)
                .toList();
        for (ItemDTO e : explore) {
            if (!result.contains(e)) {
                result.set(Math.min(result.size() - 1, ThreadLocalRandom.current().nextInt(result.size())), e);
            }
        }
        return result.size() > size ? new ArrayList<>(result.subList(0, size)) : result;
    }

    /** 修改 rank_config 表后最多 30s 生效，无需重启 */
    private void refreshWeights() {
        try {
            RankConfig config = rankConfigMapper.selectById(1L);
            if (config != null) {
                weights = new double[]{
                        config.getWCtr(),
                        config.getWInterest(),
                        config.getWHot(),
                        config.getWFresh()};
            }
        } catch (Exception ignored) {
            // DB 尚未就绪时保留默认权重
        }
    }

    private Set<String> loadUserInterests(long userId) {
        try {
            String tags = userMapper.selectInterestTags(userId);
            if (tags == null || tags.isBlank()) return Set.of();
            return new HashSet<>(Arrays.asList(tags.split(",")));
        } catch (Exception e) {
            return Set.of();
        }
    }

    private double interestMatch(ItemDTO item, Set<String> interests) {
        if (interests.isEmpty()) return 0;
        long hit = item.tagSet().stream().filter(interests::contains).count();
        return (double) hit / interests.size();
    }
}
