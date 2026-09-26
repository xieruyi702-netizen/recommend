package com.rs.gateway.controller;

import com.rs.api.ItemDTO;
import com.rs.coarse.api.CoarseRankService;
import com.rs.gateway.engine.Scene;
import com.rs.gateway.engine.SceneFlowRegistry;
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
import org.mockito.ArgumentCaptor;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import org.springframework.data.redis.core.HashOperations;
import org.springframework.data.redis.core.StringRedisTemplate;

import java.lang.reflect.Field;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.concurrent.Executors;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/** 场景路由 + 双引擎 + 降级 + trace 验证 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class FeedControllerTest {

    private final RecallService recallService = mock(RecallService.class);
    private final CoarseRankService coarseRankService = mock(CoarseRankService.class);
    private final RankService rankService = mock(RankService.class);
    private final RerankService rerankService = mock(RerankService.class);
    private final UserMapper userMapper = mock(UserMapper.class);
    private final FavoriteMapper favoriteMapper = mock(FavoriteMapper.class);
    private final StringRedisTemplate redis = mock(StringRedisTemplate.class);
    @SuppressWarnings("unchecked")
    private final HashOperations<String, Object, Object> hashOps = mock(HashOperations.class);

    private ItemDTO item(long id, String tags) {
        return new ItemDTO(id, "t" + id, tags, "A", 50, 0.05, LocalDateTime.now());
    }

    private void inject(Object target, String field, Object value) throws Exception {
        for (Class<?> c = target.getClass(); c != null; c = c.getSuperclass()) {
            try {
                Field f = c.getDeclaredField(field);
                f.setAccessible(true);
                f.set(target, value);
                return;
            } catch (NoSuchFieldException ignored) {
                // 向父类继续找
            }
        }
        throw new NoSuchFieldException(field);
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
        var recallHot = new com.rs.gateway.engine.operators.RecallHotOperator();
        inject(recallHot, "recallService", recallService);
        var recallTag = new com.rs.gateway.engine.operators.RecallTagOperator();
        inject(recallTag, "recallService", recallService);
        var recallCf = new com.rs.gateway.engine.operators.RecallCfOperator();
        inject(recallCf, "recallService", recallService);
        var recallMerge = new com.rs.gateway.engine.operators.RecallMergeOperator();

        var registry = new SceneFlowRegistry(
                List.of(recall, recallHot, recallTag, recallCf, recallMerge,
                        profile, favFilter, coarse, rank, boost, metrics, rerank),
                Executors.newFixedThreadPool(4),
                new org.springframework.core.io.DefaultResourceLoader(),
                "classpath:flows/flows.json");
        return new FeedController(registry, Executors.newFixedThreadPool(4));
    }

    private void stubBase() {
        when(recallService.recall(anyLong(), eq(100)))
                .thenReturn(List.of(item(1, "科技"), item(2, "体育"), item(3, "财经")));
        when(recallService.recallHot(anyLong(), eq(100)))
                .thenReturn(List.of(item(1, "科技"), item(2, "体育")));
        when(recallService.recallByTag(anyLong(), eq(100)))
                .thenReturn(List.of(item(3, "财经")));
        when(recallService.recallItemCf(anyLong(), eq(100))).thenReturn(List.of());
        when(userMapper.selectInterestTags(9L)).thenReturn("科技");
        when(favoriteMapper.selectItemIdsByUserId(9L)).thenReturn(List.of(2L));
        when(coarseRankService.coarseRank(anyLong(), anyList(), eq(50)))
                .thenAnswer(inv -> inv.getArgument(1));
        when(rankService.rank(anyLong(), anyList(), eq(20)))
                .thenAnswer(inv -> inv.getArgument(1));
        when(redis.opsForHash()).thenReturn(hashOps);
        when(rerankService.rerank(anyLong(), anyList(), eq(3)))
                .thenAnswer(inv -> ((List<?>) inv.getArgument(1)).stream().limit(3)
                        .map(o -> (ItemDTO) o).toList());
    }

    @Test
    void homeSceneShouldRunFullFunnel() throws Exception {
        stubBase();
        var out = (List<ItemDTO>) controller().recommend(9L, 3, Scene.HOME.code(), "pipeline", false);

        // 召回 3 条，已收藏 item=2 被过滤 → 最终 2 条
        assertEquals(2, out.size());
        verify(recallService, never()).recall(anyLong(), anyInt());   // home 走三路并行召回
        verify(recallService).recallHot(9L, 100);
        verify(recallService).recallByTag(9L, 100);
        verify(recallService).recallItemCf(9L, 100);
        var captor = ArgumentCaptor.forClass((Class<List<ItemDTO>>) (Class<?>) List.class);
        verify(coarseRankService).coarseRank(eq(9L), captor.capture(), eq(50));
        assertEquals(List.of(1L, 3L), captor.getValue().stream().map(ItemDTO::getId).toList());
        verify(hashOps, atLeastOnce()).increment(anyString(), anyString(), anyLong());
    }

    @Test
    void relatedSceneShouldRunLightweightChainOnly() throws Exception {
        stubBase();
        var out = (List<ItemDTO>) controller().recommend(9L, 3, Scene.RELATED.code(), "pipeline", false);

        assertEquals(3, out.size());
        verify(recallService).recall(9L, 100);
        verify(coarseRankService).coarseRank(eq(9L), anyList(), eq(50));
        verify(rerankService).rerank(eq(9L), anyList(), eq(3));
        verify(userMapper, never()).selectInterestTags(anyLong());
        verify(favoriteMapper, never()).selectItemIdsByUserId(anyLong());
        verifyNoInteractions(hashOps);
    }

    @Test
    void coldStartSceneShouldSkipProfileAndBoost() throws Exception {
        stubBase();
        var out = (List<ItemDTO>) controller().recommend(9L, 3, Scene.COLD_START.code(), "pipeline", false);

        assertEquals(2, out.size());
        verify(favoriteMapper).selectItemIdsByUserId(9L);
        verify(userMapper, never()).selectInterestTags(anyLong());
        verify(hashOps, atLeastOnce()).increment(anyString(), anyString(), anyLong());
    }

    @Test
    void dagModeShouldMatchPipelineAcrossScenes() throws Exception {
        var expectedSizes = Map.of(Scene.HOME, 2, Scene.RELATED, 3, Scene.COLD_START, 2);
        for (var e : expectedSizes.entrySet()) {
            stubBase();
            var out = (List<ItemDTO>) controller().recommend(9L, 3, e.getKey().code(), "dag", false);
            assertEquals(e.getValue(), out.size(), e.getKey() + " dag 应正常出结果");
        }
    }

    @Test
    void rankFailureShouldDegradeNotFail() throws Exception {
        stubBase();
        when(rankService.rank(anyLong(), anyList(), eq(20)))
                .thenThrow(new RuntimeException("rank down"));

        // 精排挂掉：DAG 降级继续，重排回退到过滤后的候选，仍出结果
        var out = (List<ItemDTO>) controller().recommend(9L, 3, Scene.HOME.code(), "dag", false);

        assertEquals(2, out.size());
        assertTrue(out.stream().map(ItemDTO::getId).toList().containsAll(List.of(1L, 3L)));
    }

    @Test
    void debugShouldExposeTrace() throws Exception {
        stubBase();
        var out = controller().recommend(9L, 3, Scene.HOME.code(), "dag", true);

        assertTrue(out instanceof Map<?, ?> m && m.containsKey("traceMs"));
        var trace = (Map<?, ?>) ((Map<?, ?>) out).get("traceMs");
        assertTrue(trace.keySet().containsAll(
                List.of("recallHot", "recallTag", "recallCf", "recallMerge",
                        "profile", "favFilter", "coarseRank", "rank", "boost", "metrics", "rerank")),
                "trace 实际内容: " + trace.keySet());
    }

    @Test
    void unknownSceneShouldThrow() throws Exception {
        stubBase();
        var c = controller();
        assertThrows(IllegalArgumentException.class, () -> c.recommend(9L, 3, "whatever", "pipeline", false));
    }
}
