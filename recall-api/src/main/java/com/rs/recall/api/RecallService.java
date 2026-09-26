package com.rs.recall.api;

import com.rs.api.ItemDTO;
import java.util.List;

public interface RecallService {

    /** 多路召回聚合（内部并行三路并合并去重），供不需要感知多路细节的消费方使用 */
    List<ItemDTO> recall(long userId, int size);

    /** 热度召回（独立暴露：供 DAG 按路并行调用与独立扩缩容） */
    List<ItemDTO> recallHot(long userId, int size);

    /** 标签召回：用户兴趣标签匹配 */
    List<ItemDTO> recallByTag(long userId, int size);

    /** ItemCF 召回：行为共现 */
    List<ItemDTO> recallItemCf(long userId, int size);
}
