package com.rs.gateway.engine;

/** 算子：推荐流的最小执行单元，输入输出均通过 FlowContext 传递 */
public interface Operator {

    /** 算子名，同时作为 DAG 节点 id */
    String name();

    void execute(FlowContext ctx);
}
