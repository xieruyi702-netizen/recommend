package com.rs.gateway.engine.operators;

import com.rs.gateway.engine.FlowContext;
import com.rs.gateway.engine.Operator;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * 公共算子基类（模板方法）：统一封装计时与异常日志，
 * 子类只需实现 doExecute 并自行注入 @DubboReference，实现"算子继承复用"。
 */
public abstract class AbstractRecommendOperator implements Operator {

    private final Logger log = LoggerFactory.getLogger(getClass());
    private final String name;

    protected AbstractRecommendOperator(String name) {
        this.name = name;
    }

    @Override
    public final String name() {
        return name;
    }

    @Override
    public final void execute(FlowContext ctx) {
        long start = System.currentTimeMillis();
        doExecute(ctx);
        log.info("算子[{}] 执行完成, 耗时 {}ms", name, System.currentTimeMillis() - start);
    }

    protected abstract void doExecute(FlowContext ctx);
}
