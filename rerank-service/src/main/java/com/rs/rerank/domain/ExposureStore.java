package com.rs.rerank.domain;

import java.util.List;
import java.util.Set;

/**
 * 曝光记录的端口（interface 在领域层，实现在基础设施层——依赖倒置）：
 * 领域逻辑只声明"我需要知道谁看过什么"，不关心 Redis 还是别的存储。
 */
public interface ExposureStore {

    /** 该用户已曝光过的物料 id（字符串形式） */
    Set<String> exposedIds(long userId);

    /** 记录一批曝光（含 7 天过期策略，由实现决定） */
    void record(long userId, List<Long> itemIds);
}
