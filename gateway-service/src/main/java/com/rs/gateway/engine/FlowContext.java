package com.rs.gateway.engine;

import com.rs.api.ItemDTO;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/** 推荐流上下文：算子之间通过 key 传递中间结果（并发安全，DAG 并行算子共享同一 ctx） */
public class FlowContext {

    public static final String CANDIDATES = "candidates";   // 召回结果
    public static final String COARSED = "coarsed";         // 粗排结果
    public static final String RANKED = "ranked";           // 精排结果
    public static final String RESULT = "result";           // 重排结果（最终输出）
    public static final String PROFILE = "profile";         // 用户兴趣标签
    public static final String FILTERED = "filtered";       // 收藏过滤后的候选
    public static final String BOOSTED = "boosted";         // 兴趣加权重排后的列表

    private final long userId;
    private final int size;
    private final Map<String, Object> attributes = new ConcurrentHashMap<>();

    public FlowContext(long userId, int size) {
        this.userId = userId;
        this.size = size;
    }

    public long getUserId() { return userId; }
    public int getSize() { return size; }

    @SuppressWarnings("unchecked")
    public <T> T get(String key) { return (T) attributes.get(key); }

    public void set(String key, Object value) { attributes.put(key, value); }

    @SuppressWarnings("unchecked")
    public List<ItemDTO> itemList(String key) { return (List<ItemDTO>) attributes.getOrDefault(key, List.of()); }
}
