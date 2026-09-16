package com.rs.rank.service;

import com.rs.api.ItemDTO;
import com.rs.rank.api.RankService;
import com.rs.api.entity.RankConfig;
import com.rs.rank.domain.RankWeights;
import com.rs.rank.domain.ScoringPolicy;
import com.rs.rank.mapper.RankConfigMapper;
import com.rs.rank.mapper.UserMapper;
import org.apache.dubbo.config.annotation.DubboService;

import java.time.LocalDateTime;
import java.util.*;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ThreadLocalRandom;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;

/**
 * 精排应用编排：加载权重与兴趣 → 委托 ScoringPolicy 打分 → 排序 → 探索保量。
 * 打分公式细节在 domain 层的 WeightedLinear 策略中，可被新策略替换做 A/B。
 */
@DubboService
public class RankServiceImpl implements RankService {

    private final RankConfigMapper rankConfigMapper;
    private final UserMapper userMapper;
    private final ScoringPolicy scoringPolicy;
    private volatile RankWeights weights = RankWeights.of(0.4, 0.3, 0.2, 0.1);

    private final ScheduledExecutorService refresher = Executors.newSingleThreadScheduledExecutor();

    public RankServiceImpl(RankConfigMapper rankConfigMapper, UserMapper userMapper, ScoringPolicy scoringPolicy) {
        this.rankConfigMapper = rankConfigMapper;
        this.userMapper = userMapper;
        this.scoringPolicy = scoringPolicy;
        refreshWeights();
        refresher.scheduleAtFixedRate(this::refreshWeights, 30, 30, TimeUnit.SECONDS);
    }

    @Override
    public List<ItemDTO> rank(long userId, List<ItemDTO> candidates, int size) {
        if (candidates == null || candidates.isEmpty()) return List.of();

        Set<String> interests = loadUserInterests(userId);
        LocalDateTime now = LocalDateTime.now();
        RankWeights w = weights;

        for (ItemDTO i : candidates) {
            i.setRankScore(scoringPolicy.score(i, interests, w, now));
        }

        List<ItemDTO> sorted = candidates.stream()
                .sorted(Comparator.comparingDouble(ItemDTO::getRankScore).reversed())
                .collect(Collectors.toList());

        // 探索保量：给非主流来源的内容保留约 30% 名额，避免单一平台垄断信息流
        Map<String, Long> byAuthor = candidates.stream()
                .collect(Collectors.groupingBy(ItemDTO::getAuthor, Collectors.counting()));
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
                weights = RankWeights.of(config.getWCtr(), config.getWInterest(), config.getWHot(), config.getWFresh());
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
}
