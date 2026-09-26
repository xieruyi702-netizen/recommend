package com.rs.gateway.engine.operators;

import com.rs.api.ItemDTO;
import com.rs.gateway.engine.FlowContext;
import org.springframework.stereotype.Component;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** 合并算子：三路召回按 id 去重合并（热度路优先），截断到 100 条 */
@Component
public class RecallMergeOperator extends AbstractRecommendOperator {

    public RecallMergeOperator() {
        super("recallMerge");
    }

    @Override
    protected void doExecute(FlowContext ctx) {
        Map<Long, ItemDTO> merged = new LinkedHashMap<>();
        for (String key : new String[]{FlowContext.RECALL_HOT, FlowContext.RECALL_TAG, FlowContext.RECALL_CF}) {
            for (ItemDTO dto : ctx.itemList(key)) {
                merged.putIfAbsent(dto.getId(), dto);
            }
        }
        ctx.set(FlowContext.CANDIDATES, List.copyOf(merged.values()));
    }

    @Override
    public java.util.Set<String> imports() {
        return java.util.Set.of(FlowContext.RECALL_HOT, FlowContext.RECALL_TAG, FlowContext.RECALL_CF);
    }

    @Override
    public java.util.Set<String> exports() {
        return java.util.Set.of(FlowContext.CANDIDATES);
    }
}
