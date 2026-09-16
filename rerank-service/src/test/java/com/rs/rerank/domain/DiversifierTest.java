package com.rs.rerank.domain;

import com.rs.api.ItemDTO;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/** 纯领域规则测试：无 mock、无 Spring，直接验证打散算法 */
class DiversifierTest {

    private ItemDTO item(long id, String author, String tags) {
        return new ItemDTO(id, "t" + id, tags, author, 50, 0.05, null);
    }

    @Test
    void sameAuthorShouldNotBeConsecutive() {
        List<ItemDTO> out = Diversifier.diversify(List.of(
                item(1, "A", "t1"), item(2, "A", "t2"),
                item(3, "B", "t3"), item(4, "B", "t4")), 4);

        for (int i = 1; i < out.size(); i++) {
            assertNotEquals(out.get(i - 1).getAuthor(), out.get(i).getAuthor());
        }
    }

    @Test
    void sameTagShouldNotAppearMoreThanTwiceConsecutively() {
        List<ItemDTO> out = Diversifier.diversify(List.of(
                item(1, "A", "hit"), item(2, "B", "hit"), item(3, "C", "hit"),
                item(4, "D", "other")), 4);

        long consecutiveHit = 0, maxConsecutiveHit = 0;
        for (ItemDTO i : out) {
            if (i.tagSet().contains("hit")) {
                consecutiveHit++;
                maxConsecutiveHit = Math.max(maxConsecutiveHit, consecutiveHit);
            } else consecutiveHit = 0;
        }
        assertTrue(maxConsecutiveHit <= 2, "hit 标签最多连续 2 次，实际 " + maxConsecutiveHit);
    }

    @Test
    void shouldKeepResultSizeEvenWhenPoolSmallerThanTopN() {
        List<ItemDTO> out = Diversifier.diversify(List.of(item(1, "A", "x")), 5);
        assertEquals(1, out.size());
    }

    @Test
    void allSameAuthorShouldStillFillResult() {
        List<ItemDTO> out = Diversifier.diversify(List.of(
                item(1, "A", "t1"), item(2, "A", "t2"), item(3, "A", "t3")), 3);
        assertEquals(3, out.size(), "全部同作者时保底填满");
    }
}
