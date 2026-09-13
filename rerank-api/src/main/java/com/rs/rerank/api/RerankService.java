package com.rs.rerank.api;

import com.rs.api.ItemDTO;
import java.util.List;

public interface RerankService {
    List<ItemDTO> rerank(long userId, List<ItemDTO> candidates, int topN);
}
