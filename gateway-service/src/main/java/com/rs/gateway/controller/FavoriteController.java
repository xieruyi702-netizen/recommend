package com.rs.gateway.controller;

import com.rs.api.ItemDTO;
import com.rs.api.entity.Item;
import com.rs.gateway.mapper.FavoriteMapper;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/** 个人收藏：收藏/取消/收藏列表 */
@RestController
@RequestMapping("/api/favorite")
public class FavoriteController {

    private final FavoriteMapper favoriteMapper;

    public FavoriteController(FavoriteMapper favoriteMapper) {
        this.favoriteMapper = favoriteMapper;
    }

    @PostMapping("/toggle")
    public Map<String, Object> toggle(@RequestBody Map<String, Object> body) {
        long userId = ((Number) body.get("userId")).longValue();
        long itemId = ((Number) body.get("itemId")).longValue();
        Boolean liked = (Boolean) body.getOrDefault("liked", Boolean.TRUE);
        if (Boolean.TRUE.equals(liked)) {
            favoriteMapper.insertIgnore(userId, itemId);
        } else {
            favoriteMapper.delete(userId, itemId);
        }
        return Map.of("ok", true, "liked", liked);
    }

    @GetMapping("/list")
    public List<ItemDTO> list(@RequestParam long userId) {
        return favoriteMapper.selectByUserId(userId).stream()
                .map(Item::toDTO)
                .collect(Collectors.toList());
    }
}
