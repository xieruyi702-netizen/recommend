package com.rs.gateway.engine.operators;

import com.rs.gateway.engine.FlowContext;
import com.rs.gateway.mapper.FavoriteMapper;
import org.springframework.stereotype.Component;

import java.util.HashSet;
import java.util.List;

/** 收藏过滤算子：已收藏的内容不再出现在推荐里，依赖召回结果 */
@Component
public class FavoriteFilterOperator extends AbstractRecommendOperator {

    private final FavoriteMapper favoriteMapper;

    public FavoriteFilterOperator(FavoriteMapper favoriteMapper) {
        super("favFilter");
        this.favoriteMapper = favoriteMapper;
    }

    @Override
    protected void doExecute(FlowContext ctx) {
        var favIds = new HashSet<Long>(favoriteMapper.selectItemIdsByUserId(ctx.getUserId()));
        List<com.rs.api.ItemDTO> filtered = ctx.itemList(FlowContext.CANDIDATES).stream()
                .filter(i -> !favIds.contains(i.getId()))
                .toList();
        ctx.set(FlowContext.FILTERED, filtered);
    }

    @Override
    public java.util.Set<String> imports() {
        return java.util.Set.of(FlowContext.CANDIDATES);
    }

    @Override
    public java.util.Set<String> exports() {
        return java.util.Set.of(FlowContext.FILTERED);
    }
}
