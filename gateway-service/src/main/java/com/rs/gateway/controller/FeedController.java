package com.rs.gateway.controller;

import com.rs.coarse.api.CoarseRankService;
import com.rs.api.ItemDTO;
import com.rs.rank.api.RankService;
import com.rs.recall.api.RecallService;
import com.rs.rerank.api.RerankService;
import org.apache.dubbo.config.annotation.DubboReference;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/feed")
public class FeedController {

    @DubboReference
    private RecallService recallService;
    @DubboReference
    private CoarseRankService coarseRankService;
    @DubboReference
    private RankService rankService;
    @DubboReference
    private RerankService rerankService;

    /** 推荐主链路：召回(100) → 粗排(50) → 精排(20) → 重排(10) */
    @GetMapping("/recommend")
    public List<ItemDTO> recommend(@RequestParam long userId, @RequestParam(defaultValue = "10") int size) {
        List<ItemDTO> recalled = recallService.recall(userId, 100);
        List<ItemDTO> coarse = coarseRankService.coarseRank(userId, recalled, 50);
        List<ItemDTO> ranked = rankService.rank(userId, coarse, 20);
        return rerankService.rerank(userId, ranked, size);
    }
}
