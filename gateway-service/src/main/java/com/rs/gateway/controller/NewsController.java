package com.rs.gateway.controller;

import com.rs.api.ItemDTO;
import com.rs.api.entity.Item;
import com.rs.gateway.mapper.ItemMapper;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.stream.Collectors;

/** 频道资讯流：按分类直接查询（推荐流之外的"频道页"） */
@RestController
@RequestMapping("/api/news")
public class NewsController {

    private final ItemMapper itemMapper;

    public NewsController(ItemMapper itemMapper) {
        this.itemMapper = itemMapper;
    }

    @GetMapping("/list")
    public List<ItemDTO> list(@RequestParam(defaultValue = "") String category,
                              @RequestParam(defaultValue = "20") int size) {
        return itemMapper.selectNews(category, Math.min(size, 50)).stream()
                .map(Item::toDTO)
                .collect(Collectors.toList());
    }
}
