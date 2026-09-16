package com.rs.rerank.service;

import com.rs.api.ItemDTO;
import com.rs.rerank.domain.ExposureStore;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class RerankServiceImplTest {

    @Mock
    private ExposureStore exposureStore;

    private ItemDTO item(long id, String author, String tags) {
        return new ItemDTO(id, "t" + id, tags, author, 50, 0.05, null);
    }

    @Test
    void shouldReturnEmptyForNullOrCandidates() {
        RerankServiceImpl service = new RerankServiceImpl(exposureStore);
        assertTrue(service.rerank(1L, null, 10).isEmpty());
        assertTrue(service.rerank(1L, List.of(), 10).isEmpty());
    }

    @Test
    void shouldFilterExposedItems() {
        when(exposureStore.exposedIds(1L)).thenReturn(Set.of("1"));

        List<ItemDTO> out = new RerankServiceImpl(exposureStore).rerank(1L,
                List.of(item(1, "A", "x"), item(2, "B", "y"), item(3, "C", "z")), 2);

        assertEquals(List.of(2L, 3L), out.stream().map(ItemDTO::getId).toList());
    }

    @Test
    void shouldBackfillWhenAllExposed() {
        when(exposureStore.exposedIds(1L)).thenReturn(Set.of("1", "2", "3"));

        List<ItemDTO> candidates = List.of(item(1, "A", "x"), item(2, "B", "y"), item(3, "C", "z"));
        List<ItemDTO> out = new RerankServiceImpl(exposureStore).rerank(1L, candidates, 3);

        assertEquals(3, out.size());
        assertTrue(candidates.containsAll(out));
    }

    @Test
    void shouldAvoidSameAuthorConsecutively() {
        when(exposureStore.exposedIds(1L)).thenReturn(Set.of());

        List<ItemDTO> out = new RerankServiceImpl(exposureStore).rerank(1L, List.of(
                item(1, "A", "t1"), item(2, "A", "t2"),
                item(3, "B", "t3"), item(4, "B", "t4")), 4);

        assertEquals(4, out.size());
        assertNotEquals(out.get(0).getAuthor(), out.get(1).getAuthor());
        assertNotEquals(out.get(1).getAuthor(), out.get(2).getAuthor());
        assertNotEquals(out.get(2).getAuthor(), out.get(3).getAuthor());
    }

    @Test
    void shouldRecordExposedSet() {
        when(exposureStore.exposedIds(1L)).thenReturn(Set.of());

        new RerankServiceImpl(exposureStore).rerank(1L, List.of(item(1, "A", "x"), item(2, "B", "y")), 2);

        verify(exposureStore).record(1L, List.of(1L, 2L));
    }
}
