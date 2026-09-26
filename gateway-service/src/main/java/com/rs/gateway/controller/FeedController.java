package com.rs.gateway.controller;

import com.rs.api.ItemDTO;
import com.rs.gateway.engine.DagExecutor;
import com.rs.gateway.engine.DagFlow;
import com.rs.gateway.engine.FlowContext;
import com.rs.gateway.engine.Pipeline;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;
import java.util.concurrent.ExecutorService;

@RestController
@RequestMapping("/api/feed")
public class FeedController {

    private final Pipeline pipeline;
    private final DagFlow dagFlow;
    private final ExecutorService dagExecutor;

    public FeedController(Pipeline pipeline, DagFlow dagFlow,
                          @Qualifier("dagExecutor") ExecutorService dagExecutor) {
        this.pipeline = pipeline;
        this.dagFlow = dagFlow;
        this.dagExecutor = dagExecutor;
    }

    /**
     * 推荐主链路：召回(100) → 粗排(50) → 精排(20) → 重排(size)。
     * mode = pipeline（线性顺序流，默认）| dag（依赖图拓扑执行，同层并行）。
     */
    @GetMapping("/recommend")
    public List<ItemDTO> recommend(@RequestParam long userId,
                                   @RequestParam(defaultValue = "10") int size,
                                   @RequestParam(defaultValue = "pipeline") String mode) {
        FlowContext ctx = new FlowContext(userId, size);
        if ("dag".equalsIgnoreCase(mode)) {
            DagExecutor.execute(dagFlow, ctx, dagExecutor);
        } else {
            pipeline.execute(ctx);
        }
        return ctx.itemList(FlowContext.RESULT);
    }

    /**
     * 性能对比：两种架构各执行 runs 次，返回平均/最小/最大耗时（ms）。
     * 当前漏斗是线性链路，两者差距反映引擎调度开销；
     * 当 DAG 出现可并行算子时，收益会在这里体现。
     */
    @GetMapping("/benchmark")
    public Map<String, Object> benchmark(@RequestParam long userId,
                                         @RequestParam(defaultValue = "20") int runs) {
        return Map.of(
                "pipeline", measure(userId, runs, "pipeline"),
                "dag", measure(userId, runs, "dag"),
                "runs", runs
        );
    }

    private Map<String, Object> measure(long userId, int runs, String mode) {
        // 预热 3 次，排除首次连接/缓存影响
        for (int i = 0; i < 3; i++) recommend(userId, 10, mode);
        long min = Long.MAX_VALUE, max = 0, total = 0;
        for (int i = 0; i < runs; i++) {
            long start = System.nanoTime();
            recommend(userId, 10, mode);
            long cost = (System.nanoTime() - start) / 1_000_000;
            total += cost;
            min = Math.min(min, cost);
            max = Math.max(max, cost);
        }
        return Map.of("avgMs", total / runs, "minMs", min, "maxMs", max);
    }
}
