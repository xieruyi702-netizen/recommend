package com.rs.gateway.engine;

import com.rs.gateway.engine.operators.CoarseRankOperator;
import com.rs.gateway.engine.operators.RankOperator;
import com.rs.gateway.engine.operators.RecallOperator;
import com.rs.gateway.engine.operators.RerankOperator;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/** 组装两种推荐执行架构 */
@Configuration
public class FlowConfig {

    /** DAG 并行执行线程池 */
    @Bean(destroyMethod = "shutdown")
    public ExecutorService dagExecutor() {
        return Executors.newFixedThreadPool(8);
    }

    /** Pipeline：召回 → 粗排 → 精排 → 重排，严格顺序 */
    @Bean
    public Pipeline pipeline(RecallOperator recall, CoarseRankOperator coarse,
                             RankOperator rank, RerankOperator rerank) {
        return new Pipeline(List.of(recall, coarse, rank, rerank));
    }

    /** DAG：同样的漏斗节点 + 依赖边（当前链路是线性的；未来出现可并行算子时只需加节点和边） */
    @Bean
    public DagFlow dagFlow(RecallOperator recall, CoarseRankOperator coarse,
                           RankOperator rank, RerankOperator rerank) {
        return new DagFlow()
                .node("recall", recall)
                .node("coarseRank", coarse).dependsOn("coarseRank", "recall")
                .node("rank", rank).dependsOn("rank", "coarseRank")
                .node("rerank", rerank).dependsOn("rerank", "rank");
    }
}
