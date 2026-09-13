package com.rs.coarse.api;

import com.rs.api.ItemDTO;
import java.util.List;

public interface CoarseRankService {
    List<ItemDTO> coarseRank(long userId, List<ItemDTO> candidates, int size);
}
