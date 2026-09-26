package com.rs.gateway.engine.operators;

import com.rs.gateway.engine.FlowContext;
import com.rs.recall.api.RecallService;
import org.apache.dubbo.config.annotation.DubboReference;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.util.List;

/** ItemCF 召回通道：失败降级为空列表——单路故障不影响整体出结果（故障隔离） */
@Component
public class RecallCfOperator extends AbstractRecommendOperator {

    private static final Logger log = LoggerFactory.getLogger(RecallCfOperator.class);

    @DubboReference
    private RecallService recallService;

    public RecallCfOperator() {
        super("recallCf");
    }

    @Override
    protected void doExecute(FlowContext ctx) {
        try {
            ctx.set(FlowContext.RECALL_CF, recallService.recallItemCf(ctx.getUserId(), 100));
        } catch (Exception e) {
            log.warn("召回通道[{}]失败，降级为空: {}", name, e.getMessage());
            ctx.set(FlowContext.RECALL_CF, List.of());
        }
    }

    @Override
    public java.util.Set<String> imports() {
        return java.util.Set.of();
    }

    @Override
    public java.util.Set<String> exports() {
        return java.util.Set.of(FlowContext.RECALL_CF);
    }
}
