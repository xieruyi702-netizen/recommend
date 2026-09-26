package com.rs.gateway.engine.operators;

import com.rs.api.ItemDTO;
import com.rs.gateway.engine.FlowContext;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Set;

/**
 * 兴趣加权算子（本地轻量重排）：命中用户兴趣的条目前移（稳定排序，不打乱精排相对顺序）。
 * 依赖精排结果与用户画像（两个依赖在 DAG 中来自不同分支）。
 */
@Component
public class InterestBoostOperator extends AbstractRecommendOperator {

    public InterestBoostOperator() {
        super("boost");
    }

    @Override
    protected void doExecute(FlowContext ctx) {
        List<ItemDTO> ranked = ctx.itemList(FlowContext.RANKED);
        Set<String> interests = ctx.get(FlowContext.PROFILE);
        if (interests == null || interests.isEmpty() || ranked.size() < 2) {
            ctx.set(FlowContext.BOOSTED, ranked);
            return;
        }
        List<ItemDTO> boosted = new ArrayList<>(ranked);
        boosted.sort(Comparator.comparingInt(
                i -> i.tagSet().stream().anyMatch(interests::contains) ? 0 : 1));  // 稳定排序：命中者前移
        ctx.set(FlowContext.BOOSTED, boosted);
    }
}
