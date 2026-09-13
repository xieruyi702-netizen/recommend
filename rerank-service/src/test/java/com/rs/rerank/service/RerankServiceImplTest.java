package com.rs.rerank.service;

import com.rs.api.ItemDTO;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.redis.core.SetOperations;
import org.springframework.data.redis.core.StringRedisTemplate;

import java.time.Duration;
import java.util.List;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class RerankServiceImplTest {

    @Mock
    private StringRedisTemplate redis;
    @Mock
    private SetOperations<String, String> setOps;

    private RerankServiceImpl service;

    @BeforeEach
    void setUp() {
        service = new RerankServiceImpl(redis);
    }

    private ItemDTO item(long id, String author, String tags) {
        return new ItemDTO(id, "t" + id, tags, author, 50, 0.05, null);
    }

    @Test
    void shouldReturnEmptyForNullOrCandidates() {
        assertTrue(service.rerank(1L, null, 10).isEmpty());
        assertTrue(service.rerank(1L, List.of(), 10).isEmpty());
    }

    @Test
    void shouldFilterExposedItems() {
        when(redis.opsForSet()).thenReturn(setOps);
        when(setOps.members("exposed:1")).thenReturn(Set.of("1"));

        List<ItemDTO> out = service.rerank(1L,
                List.of(item(1, "A", "x"), item(2, "B", "y"), item(3, "C", "z")), 2);

        // id=1 已曝光被过滤，剩余 2、3 取前 2
        assertEquals(List.of(2L, 3L), out.stream().map(ItemDTO::getId).toList());
    }

    @Test
    void shouldBackfillWhenAllExposed() {
        when(redis.opsForSet()).thenReturn(setOps);
        when(setOps.members("exposed:1")).thenReturn(Set.of("1", "2", "3"));

        List<ItemDTO> candidates = List.of(item(1, "A", "x"), item(2, "B", "y"), item(3, "C", "z"));
        List<ItemDTO> out = service.rerank(1L, candidates, 3);

        // 全部已曝光时保底回填，不刷空
        assertEquals(3, out.size());
        assertTrue(candidates.containsAll(out));
    }

    @Test
    void shouldAvoidSameAuthorConsecutively() {
        when(redis.opsForSet()).thenReturn(setOps);
        when(setOps.members(anyString())).thenReturn(Set.of());

        List<ItemDTO> out = service.rerank(1L, List.of(
                item(1, "A", "t1"), item(2, "A", "t2"),
                item(3, "B", "t3"), item(4, "B", "t4")), 4);

        assertEquals(4, out.size());
        assertNotEquals(out.get(0).getAuthor(), out.get(1).getAuthor());
        assertNotEquals(out.get(1).getAuthor(), out.get(2).getAuthor());
        assertNotEquals(out.get(2).getAuthor(), out.get(3).getAuthor());
    }

    @Test
    @SuppressWarnings("unchecked")
    void shouldWriteExposedSetWithTtl() {
        when(redis.opsForSet()).thenReturn(setOps);
        when(setOps.members(anyString())).thenReturn(Set.of());

        service.rerank(1L, List.of(item(1, "A", "x"), item(2, "B", "y")), 2);

        verify(setOps).add("exposed:1", "1");
        verify(setOps).add("exposed:1", "2");
        verify(redis).expire(eq("exposed:1"), any(Duration.class));
    }
}
