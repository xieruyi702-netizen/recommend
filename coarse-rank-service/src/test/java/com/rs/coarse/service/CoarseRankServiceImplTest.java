package com.rs.coarse.service;

import com.rs.api.ItemDTO;
import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class CoarseRankServiceImplTest {

    private final CoarseRankServiceImpl service = new CoarseRankServiceImpl();

    private ItemDTO item(long id, String author, double hotScore, LocalDateTime publishTime) {
        return new ItemDTO(id, "t" + id, "tag", author, hotScore, 0.05, publishTime);
    }

    @Test
    void shouldReturnEmptyForNullOrCandidates() {
        assertTrue(service.coarseRank(1L, null, 10).isEmpty());
        assertTrue(service.coarseRank(1L, List.of(), 10).isEmpty());
    }

    @Test
    void shouldDedupByIdKeepingFirstOccurrence() {
        ItemDTO first = item(1, "A", 10, LocalDateTime.now());
        ItemDTO dup = item(1, "A", 99, LocalDateTime.now());   // 同 id，热度不同
        ItemDTO other = item(2, "B", 50, LocalDateTime.now());

        List<ItemDTO> out = service.coarseRank(1L, new ArrayList<>(List.of(first, dup, other)), 10);

        assertEquals(2, out.size());
        assertEquals(10, out.stream().filter(i -> i.getId() == 1).findFirst().orElseThrow().getHotScore());
    }

    @Test
    void shouldSortByHotAndFreshnessThenTruncate() {
        LocalDateTime now = LocalDateTime.now();
        List<ItemDTO> candidates = List.of(
                item(1, "A", 10, now.minusHours(100)),   // 低热 + 旧
                item(2, "A", 90, now.minusHours(1)),     // 高热 + 新
                item(3, "B", 80, now.minusHours(200)));  // 高热 + 很旧

        List<ItemDTO> out = service.coarseRank(1L, candidates, 2);

        assertEquals(2, out.size());
        assertEquals(2, out.get(0).getId());
        assertEquals(3, out.get(1).getId());
    }

    @Test
    void shouldTruncateToRequestedSize() {
        LocalDateTime now = LocalDateTime.now();
        List<ItemDTO> candidates = new ArrayList<>();
        for (long i = 1; i <= 10; i++) candidates.add(item(i, "A", i * 10, now));

        assertEquals(5, service.coarseRank(1L, candidates, 5).size());
    }
}
