package com.rs.consumer.controller;

import com.rs.consumer.crawler.HotAggregator;
import com.rs.consumer.mapper.ItemMapper;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

/** 手动触发采集（前端"抓取最新"按钮） */
@RestController
@RequestMapping("/api/crawler")
public class CrawlerController {

    private final HotAggregator crawler;
    private final ItemMapper itemMapper;

    public CrawlerController(HotAggregator crawler, ItemMapper itemMapper) {
        this.crawler = crawler;
        this.itemMapper = itemMapper;
    }

    @PostMapping("/refresh")
    public Map<String, Object> refresh() {
        crawler.crawl();
        long total = itemMapper.count();
        return Map.of("ok", true, "msg", "采集完成", "totalNews", total);
    }
}
