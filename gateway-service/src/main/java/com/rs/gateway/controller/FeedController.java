package com.rs.gateway.controller;

import com.rs.api.ItemDTO;
import com.rs.gateway.client.RecommendClient;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/feed")
public class FeedController {

    private final RecommendClient recommendClient;

    public FeedController(RecommendClient recommendClient) {
        this.recommendClient = recommendClient;
    }

    /** 推荐主链路：召回(100) → 粗排(50) → 精排(20) → 重排(10) */
    @GetMapping("/recommend")
    public List<ItemDTO> recommend(@RequestParam long userId, @RequestParam(defaultValue = "10") int size) {
        List<ItemDTO> recalled = recommendClient.recall(userId, 100);
        List<ItemDTO> coarse = recommendClient.coarseRank(userId, recalled, 50);
        List<ItemDTO> ranked = recommendClient.rank(userId, coarse, 20);
        return recommendClient.rerank(userId, ranked, size);
    }
}
