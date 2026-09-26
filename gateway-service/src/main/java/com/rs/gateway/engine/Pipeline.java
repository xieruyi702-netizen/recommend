package com.rs.gateway.engine;

import java.util.List;

/** Pipeline 架构：算子按顺序线性执行，前一个的输出即后一个的输入 */
public class Pipeline {

    private final List<Operator> operators;

    public Pipeline(List<Operator> operators) {
        this.operators = List.copyOf(operators);
    }

    public List<Operator> operators() {
        return operators;
    }

    public FlowContext execute(FlowContext ctx) {
        for (Operator op : operators) {
            op.execute(ctx);
        }
        return ctx;
    }
}
