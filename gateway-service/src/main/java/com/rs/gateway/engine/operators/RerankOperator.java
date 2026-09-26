package com.rs.gateway.engine.operators;

import com.rs.api.ItemDTO;
import com.rs.gateway.engine.FlowContext;
import com.rs.rerank.api.RerankService;

import java.util.List;
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
        // 输入沿特征链回退：boost 过的 → 精排的 → 过滤的 → 原始候选（适配不同场景的算子组合）
        List<ItemDTO> input = ctx.firstNonEmptyList(
                FlowContext.BOOSTED, FlowContext.RANKED, FlowContext.FILTERED, FlowContext.CANDIDATES);
        ctx.set(FlowContext.RESULT, rerankService.rerank(ctx.getUserId(), input, ctx.getSize()));
    }
}
