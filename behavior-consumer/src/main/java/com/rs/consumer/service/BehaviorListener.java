package com.rs.consumer.service;

import com.rs.behavior.api.BehaviorEvent;
import com.rs.consumer.mapper.BehaviorMapper;
import com.rs.consumer.mapper.ItemMapper;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;

/**
 * 行为消费：Kafka 批量/单条消费 → MySQL 幂等落库 + Redis 热度榜实时更新。
 * 幂等：以 userId_itemId_action_timestamp 生成 event_id，DB 唯一键去重。
 */
@Component
public class BehaviorListener {

    private final BehaviorMapper behaviorMapper;
    private final ItemMapper itemMapper;
    private final StringRedisTemplate redis;

    public BehaviorListener(BehaviorMapper behaviorMapper, ItemMapper itemMapper, StringRedisTemplate redis) {
        this.behaviorMapper = behaviorMapper;
        this.itemMapper = itemMapper;
        this.redis = redis;
    }

    @KafkaListener(topics = "user-behavior")
    public void onEvent(BehaviorEvent event) {
        String eventId = event.getUserId() + "_" + event.getItemId() + "_" +
                event.getAction() + "_" + event.getTimestamp();

        // 1. 幂等落库（唯一键冲突则忽略）
        behaviorMapper.insertIgnore(event.getUserId(), event.getItemId(), event.getAction(), eventId);

        // 2. 热度榜更新：点击 +3，点赞 +5，曝光 +0.5
        double delta = switch (event.getAction()) {
            case "click" -> 3.0;
            case "like" -> 5.0;
            default -> 0.5;
        };
        redis.opsForZSet().incrementScore("hot:rank", String.valueOf(event.getItemId()), delta);

        // 3. 同步物料的 MySQL 热度（异步削峰后的最终一致）
        itemMapper.incrementHot(delta, event.getItemId());
    }
}
