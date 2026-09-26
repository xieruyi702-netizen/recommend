package com.rs.gateway.engine;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Future;

/** DAG 执行器：按拓扑分层执行，同层算子并行、层间屏障同步；任一算子失败立即终止并抛出原因 */
public class DagExecutor {

    private DagExecutor() {
    }

    public static FlowContext execute(DagFlow flow, FlowContext ctx, ExecutorService pool) {
        flow.validate();                          // 三色标记法检环
        List<List<String>> levels = flow.kahnLevels();   // Kahn 分层拓扑
        Map<String, Throwable> failures = new HashMap<>();
        for (List<String> level : levels) {
            List<Future<?>> futures = new ArrayList<>();
            Map<String, Operator> byName = flow.nodes();
            for (String name : level) {
                futures.add(pool.submit(() -> byName.get(name).execute(ctx)));
            }
            for (int i = 0; i < futures.size(); i++) {
                try {
                    futures.get(i).get();
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                    throw new IllegalStateException("DAG 执行被中断", e);
                } catch (ExecutionException e) {
                    failures.put(level.get(i), e.getCause());
                }
            }
            if (!failures.isEmpty()) {
                var first = failures.entrySet().iterator().next();
                throw new IllegalStateException("算子[" + first.getKey() + "] 执行失败", first.getValue());
            }
        }
        return ctx;
    }
}
