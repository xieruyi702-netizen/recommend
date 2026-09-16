package com.rs.gateway.client;

import com.rs.api.ItemDTO;

import java.util.List;

/** 推荐链路出网调用的统一抽象：业务代码只依赖此接口，Dubbo 细节隔离在实现类中（防腐层） */
public interface RecommendClient {

    List<ItemDTO> recall(long userId, int size);

    List<ItemDTO> coarseRank(long userId, List<ItemDTO> candidates, int size);

    List<ItemDTO> rank(long userId, List<ItemDTO> candidates, int size);

    List<ItemDTO> rerank(long userId, List<ItemDTO> candidates, int topN);
}
