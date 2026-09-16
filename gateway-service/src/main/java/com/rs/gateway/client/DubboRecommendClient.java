package com.rs.gateway.client;

import com.rs.api.ItemDTO;
import com.rs.coarse.api.CoarseRankService;
import com.rs.rank.api.RankService;
import com.rs.recall.api.RecallService;
import com.rs.rerank.api.RerankService;
import org.apache.dubbo.config.annotation.DubboReference;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.util.List;

/**
 * RecommendClient 的 Dubbo 实现：所有出网调用集中在此，
 * 调试（日志/耗时打点）、替换实现（换协议/降级）只改这一个类。
 */
@Component
public class DubboRecommendClient implements RecommendClient {

    private static final Logger log = LoggerFactory.getLogger(DubboRecommendClient.class);

    @DubboReference
    private RecallService recallService;
    @DubboReference
    private CoarseRankService coarseRankService;
    @DubboReference
    private RankService rankService;
    @DubboReference
    private RerankService rerankService;

    @Override
    public List<ItemDTO> recall(long userId, int size) {
        long start = System.currentTimeMillis();
        List<ItemDTO> out = recallService.recall(userId, size);
        log.debug("recall userId={} size={} -> {} 条, {}ms", userId, size, out.size(), System.currentTimeMillis() - start);
        return out;
    }

    @Override
    public List<ItemDTO> coarseRank(long userId, List<ItemDTO> candidates, int size) {
        long start = System.currentTimeMillis();
        List<ItemDTO> out = coarseRankService.coarseRank(userId, candidates, size);
        log.debug("coarseRank userId={} {} -> {} 条, {}ms", userId, candidates.size(), out.size(), System.currentTimeMillis() - start);
        return out;
    }

    @Override
    public List<ItemDTO> rank(long userId, List<ItemDTO> candidates, int size) {
        long start = System.currentTimeMillis();
        List<ItemDTO> out = rankService.rank(userId, candidates, size);
        log.debug("rank userId={} {} -> {} 条, {}ms", userId, candidates.size(), out.size(), System.currentTimeMillis() - start);
        return out;
    }

    @Override
    public List<ItemDTO> rerank(long userId, List<ItemDTO> candidates, int topN) {
        long start = System.currentTimeMillis();
        List<ItemDTO> out = rerankService.rerank(userId, candidates, topN);
        log.debug("rerank userId={} {} -> {} 条, {}ms", userId, candidates.size(), out.size(), System.currentTimeMillis() - start);
        return out;
    }
}
