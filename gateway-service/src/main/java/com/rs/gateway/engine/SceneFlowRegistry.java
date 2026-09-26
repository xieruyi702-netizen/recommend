package com.rs.gateway.engine;

import com.rs.gateway.engine.operators.CoarseRankOperator;
import com.rs.gateway.engine.operators.FavoriteFilterOperator;
import com.rs.gateway.engine.operators.InterestBoostOperator;
import com.rs.gateway.engine.operators.MetricsOperator;
import com.rs.gateway.engine.operators.ProfileOperator;
import com.rs.gateway.engine.operators.RankOperator;
import com.rs.gateway.engine.operators.RecallOperator;
import com.rs.gateway.engine.operators.RerankOperator;
import org.springframework.stereotype.Component;

import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ExecutorService;

/**
 * 场景流注册表：同一套算子，按场景组装出不同的 Pipeline / DAG。
 * 新增场景 = 加一个组装方法；新增算子 = 算子类 + 相关场景的组装行。
 */
@Component
public class SceneFlowRegistry {

    private final Map<Scene, Pipeline> pipelines = new EnumMap<>(Scene.class);
    private final Map<Scene, DagFlow> dagFlows = new EnumMap<>(Scene.class);

    public SceneFlowRegistry(RecallOperator recall,
                             ProfileOperator profile,
                             FavoriteFilterOperator favFilter,
                             CoarseRankOperator coarse,
                             RankOperator rank,
                             InterestBoostOperator boost,
                             MetricsOperator metrics,
                             RerankOperator rerank,
                             ExecutorService dagExecutor) {
        // ── HOME：完整 8 算子漏斗 ──
        pipelines.put(Scene.HOME, new Pipeline(List.of(
                recall, profile, favFilter, coarse, rank, boost, metrics, rerank)));
        dagFlows.put(Scene.HOME, new DagFlow()
                .node("recall", recall)
                .node("profile", profile)
                .node("favFilter", favFilter).dependsOn("favFilter", "recall")
                .node("coarseRank", coarse).dependsOn("coarseRank", "favFilter")
                .node("rank", rank).dependsOn("rank", "coarseRank")
                .node("boost", boost).dependsOn("boost", "rank", "profile")
                .node("metrics", metrics).dependsOn("metrics", "rank")
                .node("rerank", rerank).dependsOn("rerank", "boost"));

        // ── RELATED：低延迟轻量链路（相关推荐，跳过画像/过滤/加权/指标）──
        pipelines.put(Scene.RELATED, new Pipeline(List.of(recall, coarse, rerank)));
        dagFlows.put(Scene.RELATED, new DagFlow()
                .node("recall", recall)
                .node("coarseRank", coarse).dependsOn("coarseRank", "recall")
                .node("rerank", rerank).dependsOn("rerank", "coarseRank"));

        // ── COLD_START：冷启动用户（无画像无收藏，跳过 profile 与 boost，保留指标）──
        pipelines.put(Scene.COLD_START, new Pipeline(List.of(
                recall, favFilter, coarse, rank, metrics, rerank)));
        dagFlows.put(Scene.COLD_START, new DagFlow()
                .node("recall", recall)
                .node("favFilter", favFilter).dependsOn("favFilter", "recall")
                .node("coarseRank", coarse).dependsOn("coarseRank", "favFilter")
                .node("rank", rank).dependsOn("rank", "coarseRank")
                .node("metrics", metrics).dependsOn("metrics", "rank")
                .node("rerank", rerank).dependsOn("rerank", "metrics"));
    }

    public Pipeline pipeline(Scene scene) {
        Pipeline p = pipelines.get(scene);
        if (p == null) throw new IllegalArgumentException("场景未注册: " + scene);
        return p;
    }

    public DagFlow dag(Scene scene) {
        DagFlow d = dagFlows.get(scene);
        if (d == null) throw new IllegalArgumentException("场景未注册: " + scene);
        return d;
    }
}
