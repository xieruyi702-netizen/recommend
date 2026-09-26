package com.rs.gateway.engine.operators;

import com.rs.api.ItemDTO;
import com.rs.gateway.engine.FlowContext;
import com.rs.recall.api.RecallService;
import org.apache.dubbo.config.annotation.DubboReference;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.function.BiFunction;

/** 标签召回通道：失败降级为空列表（通用逻辑在父类 AbstractRecallChannelOperator） */
@Component
public class RecallTagOperator extends AbstractRecallChannelOperator {

    private static final Logger log = LoggerFactory.getLogger(RecallTagOperator.class);

    @DubboReference
    private RecallService recallService;

    public RecallTagOperator() {
        super("recallTag");
    }

    @Override
    protected BiFunction<Long, Integer, List<ItemDTO>> channel() {
        return recallService::recallByTag;
    }

    @Override
    protected String exportKey() {
        return FlowContext.RECALL_TAG;
    }

    @Override
    protected Logger logger() {
        return log;
    }
}
