package com.rs.gateway.engine;

import com.rs.gateway.engine.operators.CoarseRankOperator;
import com.rs.gateway.engine.operators.FavoriteFilterOperator;
import com.rs.gateway.engine.operators.InterestBoostOperator;
import com.rs.gateway.engine.operators.MetricsOperator;
import com.rs.gateway.engine.operators.ProfileOperator;
import com.rs.gateway.engine.operators.RankOperator;
import com.rs.gateway.engine.operators.RecallOperator;
import com.rs.gateway.engine.operators.RerankOperator;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.util.concurrent.ExecutorService;

import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * 组装两种推荐执行架构（8 算子）：
 *
 * Pipeline：recall → profile → favFilter → coarse → rank → boost → metrics → rerank（严格串行）
 *
 * DAG：recall ─┬─→ favFilter ─→ coarse ─→ rank ─┬─→ boost ─→ rerank
 *       profile ┘                              └─→ metrics
 * （recall 与 profile 并行；boost 与 metrics 并行——DAG 的并行收益来自这里）
 */
@Configuration
public class FlowConfig {

    @Bean(destroyMethod = "shutdown")
    public ExecutorService dagExecutor() {
        return Executors.newFixedThreadPool(8);
    }

    @Bean
    public Pipeline pipeline(RecallOperator recall, ProfileOperator profile,
                             FavoriteFilterOperator favFilter, CoarseRankOperator coarse,
                             RankOperator rank, InterestBoostOperator boost,
                             MetricsOperator metrics, RerankOperator rerank) {
        return new Pipeline(List.of(recall, profile, favFilter, coarse, rank, boost, metrics, rerank));
    }

    @Bean
    public DagFlow dagFlow(RecallOperator recall, ProfileOperator profile,
                           FavoriteFilterOperator favFilter, CoarseRankOperator coarse,
                           RankOperator rank, InterestBoostOperator boost,
                           MetricsOperator metrics, RerankOperator rerank) {
        return new DagFlow()
                .node("recall", recall)
                .node("profile", profile)
                .node("favFilter", favFilter).dependsOn("favFilter", "recall")
                .node("coarseRank", coarse).dependsOn("coarseRank", "favFilter")
                .node("rank", rank).dependsOn("rank", "coarseRank")
                .node("boost", boost).dependsOn("boost", "rank", "profile")
                .node("metrics", metrics).dependsOn("metrics", "rank")
                .node("rerank", rerank).dependsOn("rerank", "boost");
    }
}
