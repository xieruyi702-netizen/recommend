package com.rs.gateway.engine;

import java.util.Set;

/** 算子：推荐流的最小执行单元，输入输出均通过 FlowContext 传递 */
public interface Operator {

    /** 算子名，同时作为 DAG 节点 id */
    String name();

    void execute(FlowContext ctx);

    /** 本算子消费的特征（数据契约，DAG 加载时校验：每个特征必须在依赖闭包内有生产者） */
    default Set<String> imports() {
        return Set.of();
    }

    /** 本算子产出的特征（数据契约，DAG 加载时校验：同一特征不允许被重复产出） */
    default Set<String> exports() {
        return Set.of();
    }
}
