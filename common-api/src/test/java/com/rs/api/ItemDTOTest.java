package com.rs.api;

import com.rs.api.entity.Item;
import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;

import static org.junit.jupiter.api.Assertions.*;

class ItemDTOTest {

    @Test
    void toDTOShouldMapAllDbFields() {
        Item item = new Item();
        item.setId(7L);
        item.setTitle("标题");
        item.setTags("科技,数码");
        item.setAuthor("人民网");
        item.setHotScore(88.5);
        item.setCtr(0.12);
        item.setPublishTime(LocalDateTime.of(2026, 9, 1, 10, 0));
        item.setUrl("http://example.com/7");
        item.setSummary("摘要");

        ItemDTO dto = item.toDTO();

        assertEquals(7, dto.getId());
        assertEquals("标题", dto.getTitle());
        assertEquals("科技,数码", dto.getTags());
        assertEquals("人民网", dto.getAuthor());
        assertEquals(88.5, dto.getHotScore());
        assertEquals(0.12, dto.getCtr());
        assertEquals(LocalDateTime.of(2026, 9, 1, 10, 0), dto.getPublishTime());
        assertEquals("http://example.com/7", dto.getUrl());
        assertEquals("摘要", dto.getSummary());
    }

    @Test
    void toDTOShouldTolerateNullNumbers() {
        Item item = new Item();
        item.setId(null);
        item.setHotScore(null);
        item.setCtr(null);

        ItemDTO dto = item.toDTO();

        assertEquals(0, dto.getId());
        assertEquals(0.0, dto.getHotScore());
        assertEquals(0.0, dto.getCtr());
    }

    @Test
    void tagSetShouldSplitAndIgnoreBlank() {
        assertEquals(2, new ItemDTO(1, "t", "科技,数码", "a", 0, 0, null).tagSet().size());
        assertTrue(new ItemDTO(1, "t", "", "a", 0, 0, null).tagSet().isEmpty());
        assertTrue(new ItemDTO(1, "t", null, "a", 0, 0, null).tagSet().isEmpty());
    }
}
