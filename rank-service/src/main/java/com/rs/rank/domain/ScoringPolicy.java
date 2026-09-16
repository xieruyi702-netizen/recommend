package com.rs.rank.domain;

import com.rs.api.ItemDTO;

import java.time.Duration;
import java.time.LocalDateTime;
import java.util.Set;

/**
 * 打分策略（领域服务）：score = w1*ctr + w2*兴趣匹配度 + w3*热度归一 + w4*新鲜度。
 * 抽成接口是为了将来 A/B 实验：新旧打分策略并存，按流量选择实现。
 */
public interface ScoringPolicy {

    double score(ItemDTO item, Set<String> interests, RankWeights weights, LocalDateTime now);

    /** 加权线性打分（当前生产策略） */
    @org.springframework.stereotype.Component
    class WeightedLinear implements ScoringPolicy {

        @Override
        public double score(ItemDTO item, Set<String> interests, RankWeights w, LocalDateTime now) {
            double match = interestMatch(item, interests);
            double hot = Math.min(item.getHotScore() / 100.0, 1.0);
            double fresh = 1.0;
            if (item.getPublishTime() != null) {
                long hours = Math.max(Duration.between(item.getPublishTime(), now).toHours(), 0);
                fresh = 1.0 / (1.0 + hours / 48.0);
            }
            return w.ctr() * item.getCtr() + w.interest() * match + w.hot() * hot + w.fresh() * fresh;
        }

        /** 兴趣命中比例 */
        private double interestMatch(ItemDTO item, Set<String> interests) {
            if (interests.isEmpty()) return 0;
            long hit = item.tagSet().stream().filter(interests::contains).count();
            return (double) hit / interests.size();
        }
    }
}
