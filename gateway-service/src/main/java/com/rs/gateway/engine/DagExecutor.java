package com.rs.gateway.engine;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Queue;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * DAG 执行器（CompletableFuture 事件驱动，无轮询）：
 *
 * ① 数据就绪调度：算子完成即解锁下游（入度归零立即提交）——零轮询延迟；
 * ② 算子级超时：CompletableFuture.orTimeout 按算子声明独立超时，超时走降级；
 * ③ 失败降级：非关键算子失败/超时 → 记入降级集合，下游用回退链兜底继续；
 *    critical 算子失败 → 置位 aborted，不再提交新算子（已提交的照常完成，部分结果返回）；
 * ④ 整体 deadline：超过 TOTAL_DEADLINE_MS 后，剩余算子全部跳过（降级），尽快返回。
 */
public class DagExecutor {

    /** 引擎整体预算（毫秒） */
    public static final long TOTAL_DEADLINE_MS = 2000;

    private DagExecutor() {
    }

    public static FlowContext execute(DagFlow flow, FlowContext ctx, ExecutorService pool) {
        flow.validate();

        Map<String, CompletableFuture<Void>> futures = new ConcurrentHashMap<>();  // 每节点一个完成信号
        Map<String, Integer> pending = new ConcurrentHashMap<>();                  // 剩余未完成依赖数（入度）
        Map<String, List<String>> successors = new HashMap<>();                    // 反转边：完成谁 → 解锁谁
        Queue<String> ready = new ConcurrentLinkedQueue<>();                       // 就绪队列（入度归零）
        Set<String> degraded = ConcurrentHashMap.newKeySet();                      // 降级/超时/跳过的算子
        AtomicBoolean aborted = new AtomicBoolean();                               // critical 失败后置位

        for (var e : flow.dependencies().entrySet()) {
            pending.put(e.getKey(), e.getValue().size());
            for (String dep : e.getValue()) {
                successors.computeIfAbsent(dep, k -> new ArrayList<>()).add(e.getKey());
            }
        }
        for (String n : flow.nodes().keySet()) {
            futures.put(n, new CompletableFuture<>());
            pending.putIfAbsent(n, 0);
            if (pending.get(n) == 0) ready.add(n);
        }

        // 解锁下游：入度归零的进入就绪队列
        // 提交就绪队列（synchronized 防止多线程重复提交同一节点）
        Runnable drain = new Runnable() {
            @Override
            public void run() {
                synchronized (DagExecutor.class) {
                    String name;
                    while ((name = ready.poll()) != null) {
                        if (futures.get(name).isDone()) continue;   // 已完成（降级跳过路径）
                        if (aborted.get() || ctx.elapsedMs() > TOTAL_DEADLINE_MS) {
                            ctx.recordOp(name, 0, true);
                            ctx.degradedOpsAdd(name);
                            futures.get(name).complete(null);
                            unlock(name, pending, successors, ready);
                            continue;
                        }
                        Operator op = flow.nodes().get(name);
                        CompletableFuture
                                .runAsync(() -> op.execute(ctx), pool)
                                .orTimeout(op.timeoutMs(), TimeUnit.MILLISECONDS)
                                .whenComplete((v, ex) -> {
                                    boolean failed = ex != null;
                                    ctx.recordOp(op.name(), 0, failed);
                                    if (failed) {
                                        ctx.degradedOpsAdd(op.name());
                                        if (op.critical()) aborted.set(true);
                                    }
                                    futures.get(op.name()).complete(null);
                                    unlock(op.name(), pending, successors, ready);
                                    this.run();   // 解锁可能产生新的就绪算子
                                });
                    }
                }
            }
        };

        drain.run();

        // 等待全图完成（整体 deadline 兜底；未完成节点已按降级记入）
        long deadlineNanos = System.nanoTime() + (TOTAL_DEADLINE_MS + 5_000) * 1_000_000;
        while (futures.values().stream().anyMatch(cf -> !cf.isDone())) {
            if (System.nanoTime() > deadlineNanos) break;
            try {
                CompletableFuture.anyOf(futures.values().toArray(new CompletableFuture[0]))
                        .get(50, TimeUnit.MILLISECONDS);
            } catch (TimeoutException te) {
                // 继续等
            } catch (Exception e) {
                break;
            }
        }
        return ctx;
    }

    private static void unlock(String name, Map<String, Integer> pending,
                               Map<String, List<String>> successors, Queue<String> ready) {
        for (String succ : successors.getOrDefault(name, List.of())) {
            if (pending.merge(succ, -1, Integer::sum) == 0) {
                ready.add(succ);
            }
        }
    }
}
