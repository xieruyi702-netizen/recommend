package com.rs.gateway.controller;

import com.rs.api.ItemDTO;
import com.rs.gateway.engine.DagExecutor;
import com.rs.gateway.engine.DagFlow;
import com.rs.gateway.engine.FlowContext;
import com.rs.gateway.engine.Pipeline;
import com.rs.gateway.engine.Scene;
import com.rs.gateway.engine.SceneFlowRegistry;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.web.bind.annotation.*;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/feed")
public class FeedController {

    private final SceneFlowRegistry registry;
    private final java.util.concurrent.ExecutorService dagExecutor;

    public FeedController(SceneFlowRegistry registry,
                          @Qualifier("dagExecutor") java.util.concurrent.ExecutorService dagExecutor) {
        this.registry = registry;
        this.dagExecutor = dagExecutor;
    }

    /**
     * 推荐主链路。
     * scene: home（完整漏斗）| related（轻量低延迟）| cold_start（冷启动）
     * mode:  pipeline（组序列流水线，默认）| dag（依赖图拓扑并行）
     */
    @GetMapping("/recommend")
    public Object recommend(@RequestParam long userId,
                            @RequestParam(defaultValue = "10") int size,
                            @RequestParam(defaultValue = "home") String scene,
                            @RequestParam(defaultValue = "pipeline") String mode,
                            @RequestParam(defaultValue = "false") boolean debug) {
        FlowContext ctx = new FlowContext(userId, size);
        execute(Scene.fromCode(scene), mode, ctx);
        List<ItemDTO> items = ctx.itemList(FlowContext.RESULT);
        if (!debug) return items;
        return java.util.Map.of(
                "items", items,
                "traceMs", ctx.opTrace(),                  // 各算子耗时
                "degraded", ctx.degradedOps(),             // 降级/超时/跳过的算子
                "elapsedMs", ctx.elapsedMs());             // 整链耗时
    }

    /** 重载场景流配置（改编排不发版） */
    @PostMapping("/flows/reload")
    public Map<String, Object> reloadFlows() {
        return registry.reload();
    }

    /** 当前已注册的场景 */
    @GetMapping("/flows")
    public Map<String, Object> flows() {
        return Map.of("scenes", registry.scenes());
    }

    /**
     * 综合性能对比：每个场景 × 每种架构各执行 runs 次。
     * 返回 {scene: {pipeline: {...}, dag: {...}, runs: N}}
     */
    @GetMapping("/benchmark")
    public Map<String, Object> benchmark(@RequestParam long userId,
                                         @RequestParam(defaultValue = "20") int runs) {
        Map<String, Object> result = new LinkedHashMap<>();
        for (Scene scene : Scene.values()) {
            result.put(scene.code(), Map.of(
                    "pipeline", measure(scene, userId, runs, "pipeline"),
                    "dag", measure(scene, userId, runs, "dag"),
                    "runs", runs));
        }
        return result;
    }

    private void execute(Scene scene, String mode, FlowContext ctx) {
        if ("dag".equalsIgnoreCase(mode)) {
            DagExecutor.execute(registry.dag(scene), ctx, dagExecutor);
        } else {
            registry.pipeline(scene).execute(ctx);
        }
    }

    private Map<String, Object> measure(Scene scene, long userId, int runs, String mode) {
        for (int i = 0; i < 3; i++) execute(scene, mode, new FlowContext(userId, 10));   // 预热
        long min = Long.MAX_VALUE, max = 0, total = 0;
        for (int i = 0; i < runs; i++) {
            long start = System.nanoTime();
            execute(scene, mode, new FlowContext(userId, 10));
            long cost = (System.nanoTime() - start) / 1_000_000;
            total += cost;
            min = Math.min(min, cost);
            max = Math.max(max, cost);
        }
        return Map.of("avgMs", total / runs, "minMs", min, "maxMs", max);
    }
}
