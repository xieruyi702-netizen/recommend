package com.rs.gateway.engine;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Future;

/**
 * 并行组（复合算子）：组内算子并行执行、组间按 Pipeline 顺序串行。
 * 本身实现 Operator，因此对 Pipeline 完全透明——它就是"一个算子"。
 * 任一算子失败立即终止整组并抛出首个异常。
 */
public class ParallelGroup implements Operator {

    private final String name;
    private final List<Operator> operators;
    private final ExecutorService pool;

    public ParallelGroup(String name, ExecutorService pool, Operator... operators) {
        if (operators.length == 0) throw new IllegalArgumentException("并行组不能为空: " + name);
        this.name = name;
        this.operators = List.of(operators);
        this.pool = pool;
    }

    @Override
    public String name() {
        return name;
    }

    public List<Operator> operators() {
        return operators;
    }

    @Override
    public void execute(FlowContext ctx) {
        List<Future<?>> futures = new ArrayList<>();
        for (Operator op : operators) {
            futures.add(pool.submit(() -> op.execute(ctx)));
        }
        RuntimeException firstFailure = null;
        for (int i = 0; i < futures.size(); i++) {
            try {
                futures.get(i).get();
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                throw new IllegalStateException("并行组[" + name + "]执行被中断", e);
            } catch (java.util.concurrent.ExecutionException e) {
                if (firstFailure == null) firstFailure = asRuntime(e.getCause());
            }
        }
        if (firstFailure != null) throw firstFailure;
    }

    private RuntimeException asRuntime(Throwable t) {
        return t instanceof RuntimeException rt ? rt : new IllegalStateException(t);
    }
}
