package com.rs.gateway.engine.operators;

import com.rs.gateway.engine.FlowContext;
import com.rs.rerank.api.RerankService;
import org.apache.dubbo.config.annotation.DubboReference;
import org.springframework.stereotype.Component;

/** 重排算子：依赖精排结果，产出最终列表 */
@Component
public class RerankOperator extends AbstractRecommendOperator {

    @DubboReference
    private RerankService rerankService;

    public RerankOperator() {
        super("rerank");
    }

    @Override
    protected void doExecute(FlowContext ctx) {
        ctx.set(FlowContext.RESULT, rerankService.rerank(ctx.getUserId(), ctx.itemList(FlowContext.BOOSTED), ctx.getSize()));
    }
}
