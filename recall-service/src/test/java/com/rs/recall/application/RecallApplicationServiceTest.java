package com.rs.recall.application;

import com.rs.api.ItemDTO;
import com.rs.api.entity.Item;
import com.rs.recall.infrastructure.persistence.RecallMapper;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.ZSetOperations;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class RecallApplicationServiceTest {

    @Mock
    private RecallMapper recallMapper;
    @Mock
    private StringRedisTemplate redis;
    @Mock
    private ZSetOperations<String, String> zSetOps;

    private RecallApplicationService service() {
        return new RecallApplicationService(recallMapper, redis);
    }

    private Item item(long id, String tags) {
        Item i = new Item();
        i.setId(id);
        i.setTitle("t" + id);
        i.setTags(tags);
        i.setAuthor("A" + id);
        i.setHotScore(50.0);
        i.setCtr(0.05);
        i.setPublishTime(LocalDateTime.now());
        return i;
    }

    private ItemDTO dto(long id) {
        return new ItemDTO(id, "t" + id, "tag", "A" + id, 50, 0.05, LocalDateTime.now());
    }

    @Test
    void recallByHotShouldReadFromRedisZsetThenLoadItems() {
        when(redis.opsForZSet()).thenReturn(zSetOps);
        when(zSetOps.reverseRange("hot:rank", 0, 1)).thenReturn(Set.of("1", "2"));
        when(recallMapper.selectByIds(anyList())).thenReturn(List.of(item(1, "x"), item(2, "y")));

        List<ItemDTO> out = service().recall(9L, 2);

        assertEquals(2, out.size());
        assertTrue(out.stream().allMatch(i -> "hot".equals(i.getRecallSource())));
    }

    @Test
    void recallByTagShouldSplitUserInterestsAndMatchTags() {
        when(redis.opsForZSet()).thenReturn(zSetOps);
        when(zSetOps.reverseRange(anyString(), anyLong(), anyLong())).thenReturn(Set.of());
        when(recallMapper.selectByHot(2)).thenReturn(List.of(item(1, "x")));
        when(recallMapper.selectInterestTags(9L)).thenReturn("科技, 数码");
        when(recallMapper.selectClickedTags(9L, 5)).thenReturn(List.of());
        when(recallMapper.selectByTags(eq(List.of("科技", "数码")), eq(2)))
                .thenReturn(List.of(item(3, "科技"), item(1, "x")));

        List<ItemDTO> out = service().recall(9L, 2);

        // 热度路 id=1 + 标签路 id=3、id=1，id=1 重复只保留一条
        assertEquals(2, out.size());
        assertTrue(out.stream().anyMatch(i -> i.getId() == 3));
        assertEquals("tag", out.stream().filter(i -> i.getId() == 3)
                .findFirst().orElseThrow().getRecallSource());
    }

    @Test
    void recallByTagShouldSkipUserWithoutInterests() {
        when(redis.opsForZSet()).thenReturn(zSetOps);
        when(zSetOps.reverseRange(anyString(), anyLong(), anyLong())).thenReturn(Set.of());
        when(recallMapper.selectByHot(2)).thenReturn(List.of());
        when(recallMapper.selectInterestTags(9L)).thenReturn("");
        when(recallMapper.selectClickedTags(9L, 5)).thenReturn(List.of());

        assertTrue(service().recall(9L, 2).isEmpty());
    }

    @Test
    void recallByItemCFShouldFlattenClickedTagStrings() {
        when(redis.opsForZSet()).thenReturn(zSetOps);
        when(zSetOps.reverseRange(anyString(), anyLong(), anyLong())).thenReturn(Set.of());
        when(recallMapper.selectByHot(2)).thenReturn(List.of());
        when(recallMapper.selectInterestTags(9L)).thenReturn("");
        // 每条记录是逗号分隔的多标签，应拍平去重后查询
        when(recallMapper.selectClickedTags(9L, 5)).thenReturn(List.of("B站,热榜", "科技,热榜"));
        when(recallMapper.selectByTags(eq(List.of("B站", "热榜", "科技")), eq(2)))
                .thenReturn(List.of(item(5, "热榜")));

        List<ItemDTO> out = service().recall(9L, 2);

        assertEquals(1, out.size());
        assertEquals("itemcf", out.get(0).getRecallSource());
    }

    @Test
    void loadItemsShouldIgnoreEmptyIds() {
        when(redis.opsForZSet()).thenReturn(zSetOps);
        when(zSetOps.reverseRange(anyString(), anyLong(), anyLong())).thenReturn(Set.of());
        when(recallMapper.selectByHot(2)).thenReturn(List.of());
        when(recallMapper.selectInterestTags(9L)).thenReturn("");
        when(recallMapper.selectClickedTags(9L, 5)).thenReturn(List.of());

        assertTrue(service().recall(9L, 2).isEmpty());
        verify(recallMapper, never()).selectByIds(anyList());
    }
}
