package com.rs.gateway.engine;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Future;

/**
 * DAG 执行器（大厂在线推荐服务形态）：
 *
 * ① 数据就绪调度：算子完成即解锁下游（入度归零立即提交）——快算子的下游不等同层慢算子；
 * ② 算子级超时：按 op.timeoutMs() 独立超时，超时视为失败走降级；
 * ③ 失败降级：非关键算子失败/超时 → 记入降级集合，下游用回退链兜底继续执行；
 *    critical 算子失败 → 终止整条链路（部分结果直接返回）；
 * ④ 整体 deadline：超过 TOTAL_DEADLINE_MS 后，剩余算子全部跳过（降级），尽快返回。
 */
public class DagExecutor {

    /** 引擎整体预算（毫秒） */
    public static final long TOTAL_DEADLINE_MS = 2000;

    private DagExecutor() {
    }

    private record Running(Future<?> future, long submitNanos) {
    }

    public static FlowContext execute(DagFlow flow, FlowContext ctx, ExecutorService pool) {
        flow.validate();

        Map<String, Integer> pending = new HashMap<>();          // 剩余未完成依赖数（入度）
        Map<String, List<String>> successors = new HashMap<>();  // 反转边：完成谁 → 解锁谁
        for (var e : flow.dependencies().entrySet()) {
            pending.putIfAbsent(e.getKey(), 0);
            for (String dep : e.getValue()) {
                pending.merge(e.getKey(), 1, Integer::sum);
                successors.computeIfAbsent(dep, k -> new ArrayList<>()).add(e.getKey());
            }
        }

        Map<String, Running> running = new HashMap<>();
        Set<String> done = new HashSet<>();

        while (done.size() < flow.nodes().size()) {
            // ① 提交所有就绪算子（入度归零）；整体超预算时剩余算子直接跳过（降级）
            boolean overDeadline = ctx.elapsedMs() > TOTAL_DEADLINE_MS;
            for (var e : flow.nodes().entrySet()) {
                String name = e.getKey();
                if (done.contains(name) || running.containsKey(name) || pending.get(name) > 0) continue;
                if (overDeadline) {
                    ctx.recordOp(name, 0, true);
                    done.add(name);
                    unlock(name, pending, successors);
                } else {
                    running.put(name, new Running(pool.submit(() -> e.getValue().execute(ctx)), System.nanoTime()));
                }
            }
            if (running.isEmpty() && done.size() >= flow.nodes().size()) break;

            // ② 等待任一算子完成/超时（1ms 轮询）
            String finished = null;
            boolean failed = false;
            while (finished == null) {
                for (var e : running.entrySet()) {
                    long costMs = (System.nanoTime() - e.getValue().submitNanos()) / 1_000_000;
                    long timeout = flow.nodes().get(e.getKey()).timeoutMs();
                    if (e.getValue().future().isDone()) {
                        // 完成：成败由 get() 的结果决定
                        finished = e.getKey();
                        break;
                    }
                    if (costMs >= timeout) {
                        // 超时：取消并按失败处理
                        e.getValue().future().cancel(true);
                        finished = e.getKey();
                        failed = true;
                        break;
                    }
                }
                if (finished != null) break;
                try {
                    Thread.sleep(1);
                } catch (InterruptedException ie) {
                    Thread.currentThread().interrupt();
                    return ctx;
                }
            }

            // ③ 处理结果
            Running r = running.remove(finished);
            done.add(finished);
            boolean failedNow = false;
            try {
                if (r != null) r.future().get();
            } catch (ExecutionException ee) {
                failedNow = true;
                logFailure(finished, ee.getCause());
            } catch (InterruptedException ie) {
                Thread.currentThread().interrupt();
                failedNow = true;
            } catch (java.util.concurrent.CancellationException ce) {
                failedNow = true;   // 超时取消
            }
            if (failedNow || failed) {
                ctx.recordOp(finished, 0, true);
                if (flow.nodes().get(finished).critical()) {
                    return ctx;   // 关键算子失败：终止整条链路（部分结果直接返回）
                }
                // 非关键失败：降级继续，下游用回退链兜底
            } else {
                ctx.recordOp(finished, 0, false);
            }

            // ④ 解锁下游
            unlock(finished, pending, successors);
        }
        return ctx;
    }

    private static void logFailure(String name, Throwable cause) {
        org.slf4j.LoggerFactory.getLogger(DagExecutor.class)
                .warn("算子[{}] 执行失败，走降级: {}", name, cause == null ? "" : cause.getMessage());
    }

    private static void unlock(String name, Map<String, Integer> pending,
                               Map<String, List<String>> successors) {
        for (String succ : successors.getOrDefault(name, List.of())) {
            pending.merge(succ, -1, Integer::sum);
        }
    }
}
