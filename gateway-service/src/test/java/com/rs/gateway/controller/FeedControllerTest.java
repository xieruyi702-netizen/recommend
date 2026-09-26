package com.rs.gateway.controller;

import com.rs.api.ItemDTO;
import com.rs.coarse.api.CoarseRankService;
import com.rs.gateway.engine.DagFlow;
import com.rs.gateway.engine.Pipeline;
import com.rs.gateway.engine.operators.CoarseRankOperator;
import com.rs.gateway.engine.operators.RankOperator;
import com.rs.gateway.engine.operators.RecallOperator;
import com.rs.gateway.engine.operators.RerankOperator;
import com.rs.rank.api.RankService;
import com.rs.recall.api.RecallService;
import com.rs.rerank.api.RerankService;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.lang.reflect.Field;
import java.time.LocalDateTime;
import java.util.List;
import java.util.concurrent.Executors;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/** 算子通过反射注入 mock 的 Dubbo 服务，验证两种引擎的漏斗执行 */
class FeedControllerTest {

    private final RecallService recallService = mock(RecallService.class);
    private final CoarseRankService coarseRankService = mock(CoarseRankService.class);
    private final RankService rankService = mock(RankService.class);
    private final RerankService rerankService = mock(RerankService.class);

    private ItemDTO item(long id) {
        return new ItemDTO(id, "t" + id, "科技", "A", 50, 0.05, LocalDateTime.now());
    }

    private void inject(Object target, String field, Object value) throws Exception {
        Field f = target.getClass().getDeclaredField(field);
        f.setAccessible(true);
        f.set(target, value);
    }

    private FeedController controller() throws Exception {
        RecallOperator recall = new RecallOperator();
        inject(recall, "recallService", recallService);
        CoarseRankOperator coarse = new CoarseRankOperator();
        inject(coarse, "coarseRankService", coarseRankService);
        RankOperator rank = new RankOperator();
        inject(rank, "rankService", rankService);
        RerankOperator rerank = new RerankOperator();
        inject(rerank, "rerankService", rerankService);

        Pipeline pipeline = new Pipeline(List.of(recall, coarse, rank, rerank));
        DagFlow dag = new DagFlow()
                .node("recall", recall)
                .node("coarseRank", coarse).dependsOn("coarseRank", "recall")
                .node("rank", rank).dependsOn("rank", "coarseRank")
                .node("rerank", rerank).dependsOn("rerank", "rank");
        return new FeedController(pipeline, dag, Executors.newFixedThreadPool(4));
    }

    private void stubFunnel() {
        when(recallService.recall(anyLong(), eq(100))).thenReturn(List.of(item(1), item(2)));
        when(coarseRankService.coarseRank(anyLong(), anyList(), eq(50))).thenReturn(List.of(item(1)));
        when(rankService.rank(anyLong(), anyList(), eq(20))).thenReturn(List.of(item(1)));
        when(rerankService.rerank(anyLong(), anyList(), eq(2))).thenReturn(List.of(item(1), item(2)));
    }

    @Test
    void pipelineModeShouldRunFunnel() throws Exception {
        stubFunnel();
        List<ItemDTO> out = controller().recommend(9L, 2, "pipeline");
        assertEquals(2, out.size());
        verify(recallService).recall(9L, 100);
        var captor = ArgumentCaptor.forClass((Class<List<ItemDTO>>) (Class<?>) List.class);
        verify(rerankService).rerank(eq(9L), captor.capture(), eq(2));
        assertEquals(List.of(1L), captor.getValue().stream().map(ItemDTO::getId).toList());
    }

    @Test
    void dagModeShouldRunFunnelInTopologicalOrder() throws Exception {
        stubFunnel();
        List<ItemDTO> out = controller().recommend(9L, 2, "dag");
        assertEquals(2, out.size());
        var inOrder = inOrder(recallService, coarseRankService, rankService, rerankService);
        inOrder.verify(recallService).recall(9L, 100);
        inOrder.verify(coarseRankService).coarseRank(eq(9L), anyList(), eq(50));
        inOrder.verify(rankService).rank(eq(9L), anyList(), eq(20));
        inOrder.verify(rerankService).rerank(eq(9L), anyList(), eq(2));
    }

    @Test
    void unknownModeShouldFallbackToPipeline() throws Exception {
        stubFunnel();
        assertEquals(2, controller().recommend(9L, 2, "whatever").size());
    }
}
