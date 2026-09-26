package com.rs.gateway.engine.operators;

import com.rs.gateway.engine.FlowContext;
import com.rs.rank.api.RankService;
import org.apache.dubbo.config.annotation.DubboReference;
import org.springframework.stereotype.Component;

/** 精排算子：依赖粗排结果 */
@Component
public class RankOperator extends AbstractRecommendOperator {

    @DubboReference
    private RankService rankService;

    public RankOperator() {
        super("rank");
    }

    @Override
    protected void doExecute(FlowContext ctx) {
        ctx.set(FlowContext.RANKED, rankService.rank(ctx.getUserId(), ctx.itemList(FlowContext.COARSED), 20));
    }
}
