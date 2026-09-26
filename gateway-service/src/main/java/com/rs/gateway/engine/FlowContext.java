package com.rs.gateway.engine;

import com.rs.api.ItemDTO;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

/** 推荐流上下文：算子之间通过 key 传递中间结果（并发安全，DAG 并行算子共享同一 ctx） */
public class FlowContext {

    public static final String CANDIDATES = "candidates";   // 召回结果（合并后）
    public static final String RECALL_HOT = "recallHot";     // 热度路召回
    public static final String RECALL_TAG = "recallTag";     // 标签路召回
    public static final String RECALL_CF = "recallCf";       // ItemCF 路召回
    public static final String COARSED = "coarsed";         // 粗排结果
    public static final String RANKED = "ranked";           // 精排结果
    public static final String RESULT = "result";           // 重排结果（最终输出）
    public static final String METRICS_WRITTEN = "metricsWritten"; // 漏斗指标已写入（契约标记）
    public static final String PROFILE = "profile";         // 用户兴趣标签
    public static final String FILTERED = "filtered";       // 收藏过滤后的候选
    public static final String BOOSTED = "boosted";         // 兴趣加权重排后的列表

    private final long userId;
    private final int size;
    private final Map<String, Object> attributes = new ConcurrentHashMap<>();
    private final Map<String, Long> opCostMs = new ConcurrentHashMap<>();     // 算子耗时 trace
    private final Set<String> degradedOps = ConcurrentHashMap.newKeySet();   // 降级/跳过的算子
    private final long startNanos = System.nanoTime();

    public FlowContext(long userId, int size) {
        this.userId = userId;
        this.size = size;
    }

    /** 引擎整体预算：超预算后剩余非关键算子将被跳过（降级） */
    public long elapsedMs() {
        return (System.nanoTime() - startNanos) / 1_000_000;
    }

    public void recordOp(String name, long costMs, boolean degraded) {
        opCostMs.put(name, costMs);
        if (degraded) degradedOps.add(name);
    }

    /** 算子耗时 trace（算子名 → 毫秒），debug 接口透出 */
    public Map<String, Long> opTrace() {
        return Map.copyOf(opCostMs);
    }

    /** 发生降级/被跳过的算子 */
    public Set<String> degradedOps() {
        return Set.copyOf(degradedOps);
    }

    public void degradedOpsAdd(String name) {
        degradedOps.add(name);
    }

    public long getUserId() { return userId; }
    public int getSize() { return size; }

    @SuppressWarnings("unchecked")
    public <T> T get(String key) { return (T) attributes.get(key); }

    public void set(String key, Object value) { attributes.put(key, value); }

    @SuppressWarnings("unchecked")
    public List<ItemDTO> itemList(String key) { return (List<ItemDTO>) attributes.getOrDefault(key, List.of()); }

    /** 沿特征链取第一个非空列表（适配不同场景下算子组合的差异） */
    @SafeVarargs
    public final List<ItemDTO> firstNonEmptyList(String... keys) {
        for (String key : keys) {
            List<ItemDTO> v = itemList(key);
            if (!v.isEmpty()) return v;
        }
        return List.of();
    }
}
