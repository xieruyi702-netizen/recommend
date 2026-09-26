package com.rs.gateway.engine.operators;

import com.rs.gateway.engine.FlowContext;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;

/** 漏斗指标算子：各阶段条目量写入 Redis（与重排并行，不阻塞主链路） */
@Component
public class MetricsOperator extends AbstractRecommendOperator {

    private static final String STATS_KEY = "funnel:stats";

    private final StringRedisTemplate redis;

    public MetricsOperator(StringRedisTemplate redis) {
        super("metrics");
        this.redis = redis;
    }

    @Override
    protected void doExecute(FlowContext ctx) {
        redis.opsForHash().increment(STATS_KEY, "recall", ctx.itemList(FlowContext.CANDIDATES).size());
        redis.opsForHash().increment(STATS_KEY, "ranked", ctx.itemList(FlowContext.RANKED).size());
    }
}
