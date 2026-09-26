package com.rs.gateway.controller;

import com.rs.api.ItemDTO;
import com.rs.coarse.api.CoarseRankService;
import com.rs.gateway.engine.DagFlow;
import com.rs.gateway.engine.Pipeline;
import com.rs.gateway.engine.operators.CoarseRankOperator;
import com.rs.gateway.engine.operators.FavoriteFilterOperator;
import com.rs.gateway.engine.operators.InterestBoostOperator;
import com.rs.gateway.engine.operators.MetricsOperator;
import com.rs.gateway.engine.operators.ProfileOperator;
import com.rs.gateway.engine.operators.RankOperator;
import com.rs.gateway.engine.operators.RecallOperator;
import com.rs.gateway.engine.operators.RerankOperator;
import com.rs.gateway.mapper.FavoriteMapper;
import com.rs.gateway.mapper.UserMapper;
import com.rs.rank.api.RankService;
import com.rs.recall.api.RecallService;
import com.rs.rerank.api.RerankService;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.redis.core.HashOperations;
import org.springframework.data.redis.core.StringRedisTemplate;

import java.lang.reflect.Field;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Set;
import java.util.concurrent.Executors;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/** 8 算子 + 两种引擎的漏斗验证 */
@ExtendWith(MockitoExtension.class)
class FeedControllerTest {

    private final RecallService recallService = mock(RecallService.class);
    private final CoarseRankService coarseRankService = mock(CoarseRankService.class);
    private final RankService rankService = mock(RankService.class);
    private final RerankService rerankService = mock(RerankService.class);
    private final UserMapper userMapper = mock(UserMapper.class);
    private final FavoriteMapper favoriteMapper = mock(FavoriteMapper.class);
    private final StringRedisTemplate redis = mock(StringRedisTemplate.class);
    private final HashOperations<String, Object, Object> hashOps = mock(HashOperations.class);

    private ItemDTO item(long id, String tags) {
        return new ItemDTO(id, "t" + id, tags, "A", 50, 0.05, LocalDateTime.now());
    }

    private void inject(Object target, String field, Object value) throws Exception {
        Field f = target.getClass().getDeclaredField(field);
        f.setAccessible(true);
        f.set(target, value);
    }

    private FeedController controller() throws Exception {
        RecallOperator recall = new RecallOperator();
        inject(recall, "recallService", recallService);
        ProfileOperator profile = new ProfileOperator(userMapper);
        FavoriteFilterOperator favFilter = new FavoriteFilterOperator(favoriteMapper);
        CoarseRankOperator coarse = new CoarseRankOperator();
        inject(coarse, "coarseRankService", coarseRankService);
        RankOperator rank = new RankOperator();
        inject(rank, "rankService", rankService);
        InterestBoostOperator boost = new InterestBoostOperator();
        MetricsOperator metrics = new MetricsOperator(redis);
        RerankOperator rerank = new RerankOperator();
        inject(rerank, "rerankService", rerankService);

        var pool = Executors.newFixedThreadPool(4);
        Pipeline pipeline = new Pipeline(List.of(
                new com.rs.gateway.engine.ParallelGroup("recall+profile", pool, recall, profile),
                favFilter, coarse, rank,
                new com.rs.gateway.engine.ParallelGroup("boost+metrics", pool, boost, metrics),
                rerank));
        DagFlow dag = new DagFlow()
                .node("recall", recall)
                .node("profile", profile)
                .node("favFilter", favFilter).dependsOn("favFilter", "recall")
                .node("coarseRank", coarse).dependsOn("coarseRank", "favFilter")
                .node("rank", rank).dependsOn("rank", "coarseRank")
                .node("boost", boost).dependsOn("boost", "rank", "profile")
                .node("metrics", metrics).dependsOn("metrics", "rank")
                .node("rerank", rerank).dependsOn("rerank", "boost");
        return new FeedController(pipeline, dag, Executors.newFixedThreadPool(4));
    }

    private void stubFunnel() {
        when(recallService.recall(anyLong(), eq(100)))
                .thenReturn(List.of(item(1, "科技"), item(2, "体育"), item(3, "财经")));
        when(userMapper.selectInterestTags(9L)).thenReturn("科技");
        when(favoriteMapper.selectItemIdsByUserId(9L)).thenReturn(List.of(2L));
        when(coarseRankService.coarseRank(anyLong(), anyList(), eq(50)))
                .thenAnswer(inv -> inv.getArgument(1));
        when(rankService.rank(anyLong(), anyList(), eq(20)))
                .thenAnswer(inv -> inv.getArgument(1));
        when(redis.opsForHash()).thenReturn(hashOps);
        when(rerankService.rerank(anyLong(), anyList(), eq(2)))
                .thenAnswer(inv -> ((List<?>) inv.getArgument(1)).stream().limit(2)
                        .map(o -> (ItemDTO) o).toList());
    }

    @Test
    void pipelineModeShouldRunAllEightOperators() throws Exception {
        stubFunnel();
        List<ItemDTO> out = controller().recommend(9L, 2, "pipeline");

        assertEquals(2, out.size());
        verify(recallService).recall(9L, 100);
        verify(userMapper).selectInterestTags(9L);
        verify(favoriteMapper).selectItemIdsByUserId(9L);
        // 已收藏的 item=2 被过滤，粗排入参只剩 1、3
        var coarseCaptor = ArgumentCaptor.forClass((Class<List<ItemDTO>>) (Class<?>) List.class);
        verify(coarseRankService).coarseRank(eq(9L), coarseCaptor.capture(), eq(50));
        assertEquals(List.of(1L, 3L),
                coarseCaptor.getValue().stream().map(ItemDTO::getId).toList());
        verify(rerankService).rerank(eq(9L), anyList(), eq(2));
        verify(hashOps, atLeastOnce()).increment(anyString(), anyString(), anyLong());
    }

    @Test
    void dagModeShouldParallelizeAndPreserveDeps() throws Exception {
        stubFunnel();
        List<ItemDTO> out = controller().recommend(9L, 2, "dag");

        assertEquals(2, out.size());
        var inOrder = inOrder(recallService, coarseRankService, rankService, rerankService);
        inOrder.verify(recallService).recall(9L, 100);
        inOrder.verify(coarseRankService).coarseRank(eq(9L), anyList(), eq(50));
        inOrder.verify(rankService).rank(eq(9L), anyList(), eq(20));
        inOrder.verify(rerankService).rerank(eq(9L), anyList(), eq(2));
        verify(hashOps, atLeastOnce()).increment(anyString(), anyString(), anyLong());
    }
}
