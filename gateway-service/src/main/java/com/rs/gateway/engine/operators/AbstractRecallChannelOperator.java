package com.rs.gateway.engine.operators;

import com.rs.api.ItemDTO;
import com.rs.gateway.engine.FlowContext;
import com.rs.recall.api.RecallService;
import org.apache.dubbo.config.annotation.DubboReference;

import java.util.List;
import java.util.function.BiFunction;

/**
 * 召回通道算子的中间父类（两级继承）：
 * 封装"调用通道 RPC → 失败降级为空列表"的通用逻辑，
 * 子类只需提供通道名与通道方法引用（如 RecallService::recallHot）。
 */
public abstract class AbstractRecallChannelOperator extends AbstractRecommendOperator {

    @DubboReference
    protected RecallService recallService;   // 三路通道共用同一个召回服务

    protected AbstractRecallChannelOperator(String name) {
        super(name);
    }

    @Override
    protected void doExecute(FlowContext ctx) {
        try {
            List<ItemDTO> items = channel().apply(ctx.getUserId(), 100);
            ctx.set(exportKey(), items);
        } catch (Exception e) {
            logger().warn("召回通道[{}]失败，降级为空: {}", name, e.getMessage());
            ctx.set(exportKey(), List.of());
        }
    }

    /** 通道方法引用，如 RecallService::recallHot */
    protected abstract BiFunction<Long, Integer, List<ItemDTO>> channel();

    /** 写入上下文的特征 key，如 FlowContext.RECALL_HOT */
    protected abstract String exportKey();

    protected org.slf4j.Logger logger() {
        return org.slf4j.LoggerFactory.getLogger(getClass());
    }
}
