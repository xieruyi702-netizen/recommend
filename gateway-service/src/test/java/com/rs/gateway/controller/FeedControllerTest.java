package com.rs.gateway.controller;

import com.rs.api.ItemDTO;
import com.rs.gateway.client.RecommendClient;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDateTime;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class FeedControllerTest {

    @Mock
    private RecommendClient client;

    private ItemDTO item(long id) {
        return new ItemDTO(id, "t" + id, "科技", "A", 50, 0.05, LocalDateTime.now());
    }

    @Test
    void recommendShouldRunFunnelInOrder() {
        when(client.recall(anyLong(), eq(100))).thenReturn(List.of(item(1), item(2), item(3)));
        when(client.coarseRank(anyLong(), anyList(), eq(50)))
                .thenAnswer(inv -> inv.getArgument(1, List.class));
        when(client.rank(anyLong(), anyList(), eq(20)))
                .thenAnswer(inv -> inv.getArgument(1, List.class));
        when(client.rerank(anyLong(), anyList(), eq(2)))
                .thenAnswer(inv -> ((List<ItemDTO>) inv.getArgument(1)).subList(0, 2));

        List<ItemDTO> out = new FeedController(client).recommend(9L, 2);

        assertEquals(2, out.size());
        // 漏斗顺序与裁剪数量正确
        var inOrder = inOrder(client);
        inOrder.verify(client).recall(9L, 100);
        inOrder.verify(client).coarseRank(eq(9L), anyList(), eq(50));
        inOrder.verify(client).rank(eq(9L), anyList(), eq(20));
        inOrder.verify(client).rerank(eq(9L), anyList(), eq(2));
        ArgumentCaptor<List<ItemDTO>> rankInput = ArgumentCaptor.forClass(List.class);
        verify(client).rank(eq(9L), rankInput.capture(), eq(20));
        assertEquals(3, ((List<?>) rankInput.getValue()).size());
    }

    @Test
    @SuppressWarnings("unchecked")
    void recommendShouldPassThroughResults() {
        when(client.recall(anyLong(), anyInt())).thenReturn(List.of(item(1)));
        when(client.coarseRank(anyLong(), anyList(), anyInt())).thenReturn(List.of(item(1)));
        when(client.rank(anyLong(), anyList(), anyInt())).thenReturn(List.of(item(1)));
        when(client.rerank(anyLong(), anyList(), anyInt())).thenReturn(List.of(item(1)));

        List<ItemDTO> out = new FeedController(client).recommend(1L, 10);

        assertEquals(1, out.size());
        assertEquals(1, out.get(0).getId());
    }
}
