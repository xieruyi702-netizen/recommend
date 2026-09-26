package com.rs.gateway.engine.operators;

import com.rs.coarse.api.CoarseRankService;
import com.rs.gateway.engine.FlowContext;
import org.apache.dubbo.config.annotation.DubboReference;
import org.springframework.stereotype.Component;

/** 粗排算子：依赖召回结果 */
@Component
public class CoarseRankOperator extends AbstractRecommendOperator {

    @DubboReference
    private CoarseRankService coarseRankService;

    public CoarseRankOperator() {
        super("coarseRank");
    }

    @Override
    protected void doExecute(FlowContext ctx) {
                // 轻量场景无 favFilter 时回退原始候选
        ctx.set(FlowContext.COARSED, coarseRankService.coarseRank(ctx.getUserId(),
                ctx.firstNonEmptyList(FlowContext.FILTERED, FlowContext.CANDIDATES), 50));
    }

    @Override
    public java.util.Set<String> imports() {
        return java.util.Set.of(FlowContext.FILTERED);
    }

    @Override
    public java.util.Set<String> exports() {
        return java.util.Set.of(FlowContext.COARSED);
    }
}
