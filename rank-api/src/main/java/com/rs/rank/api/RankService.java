package com.rs.rank.api;

import com.rs.api.ItemDTO;
import java.util.List;

public interface RankService {
    List<ItemDTO> rank(long userId, List<ItemDTO> candidates, int size);
}
