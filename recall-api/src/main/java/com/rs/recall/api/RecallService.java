package com.rs.recall.api;

import com.rs.api.ItemDTO;
import java.util.List;

public interface RecallService {
    List<ItemDTO> recall(long userId, int size);
}
