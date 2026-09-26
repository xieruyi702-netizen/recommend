package com.rs.gateway.engine.operators;

import com.rs.gateway.engine.FlowContext;
import com.rs.recall.api.RecallService;
import org.apache.dubbo.config.annotation.DubboReference;
import org.springframework.stereotype.Component;

/** 召回算子 */
@Component
public class RecallOperator extends AbstractRecommendOperator {

    @DubboReference
    private RecallService recallService;

    public RecallOperator() {
        super("recall");
    }

    @Override
    protected void doExecute(FlowContext ctx) {
        ctx.set(FlowContext.CANDIDATES, recallService.recall(ctx.getUserId(), 100));
    }
}
