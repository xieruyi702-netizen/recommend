package com.rs.rerank.application;

import com.rs.api.ItemDTO;
import com.rs.rerank.api.RerankService;
import com.rs.rerank.domain.Diversifier;
import com.rs.rerank.domain.ExposureStore;
import org.apache.dubbo.config.annotation.DubboService;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;

/**
 * 重排应用编排：过滤已曝光 → 不足则保底回填 → 打散 → 记录曝光。
 * 规则细节分别在 Diversifier（打散）与 ExposureStore 实现（曝光），本类只做编排。
 */
@DubboService
public class RerankServiceImpl implements RerankService {

    private final ExposureStore exposureStore;

    public RerankServiceImpl(ExposureStore exposureStore) {
        this.exposureStore = exposureStore;
    }

    @Override
    public List<ItemDTO> rerank(long userId, List<ItemDTO> candidates, int topN) {
        if (candidates == null || candidates.isEmpty()) return List.of();

        Set<String> exposed = exposureStore.exposedIds(userId);
        List<ItemDTO> pool = new ArrayList<>(
                candidates.stream().filter(i -> !exposed.contains(String.valueOf(i.getId()))).toList());
        // 过滤后不足 topN，用被过滤的候选保底回填（避免刷空）
        for (ItemDTO c : candidates) {
            if (pool.size() >= topN || pool.size() >= candidates.size()) break;
            if (!pool.contains(c)) pool.add(c);
        }

        List<ItemDTO> result = Diversifier.diversify(pool, topN);
        exposureStore.record(userId, result.stream().map(ItemDTO::getId).toList());
        return result;
    }
}
