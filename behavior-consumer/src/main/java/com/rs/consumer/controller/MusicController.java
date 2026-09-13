package com.rs.consumer.controller;

import com.rs.consumer.crawler.BiliAudioExtractor;
import com.rs.consumer.entity.Music;
import com.rs.consumer.mapper.MusicMapper;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

/** 音乐库：B 站链接提取音频、列表展示（对应前端"音乐提取"页） */
@RestController
@RequestMapping("/api/music")
public class MusicController {

    private final BiliAudioExtractor extractor;
    private final MusicMapper musicMapper;

    public MusicController(BiliAudioExtractor extractor, MusicMapper musicMapper) {
        this.extractor = extractor;
        this.musicMapper = musicMapper;
    }

    /** 提取 B 站视频音频并入库（同一视频幂等，重复提取直接返回已有记录） */
    @PostMapping("/extract")
    public Map<String, Object> extract(@RequestBody Map<String, String> body) {
        String url = body.getOrDefault("url", "");
        try {
            String bvid = extractor.extractBvid(url);
            Music existing = musicMapper.selectByBvid(bvid);
            if (existing != null) {
                return Map.of("ok", true, "msg", "该视频已提取过", "music", existing);
            }
            Music music = extractor.extract(bvid);
            musicMapper.insert(music);
            return Map.of("ok", true, "msg", "提取成功", "music", music);
        } catch (IllegalArgumentException e) {
            return Map.of("ok", false, "msg", e.getMessage());
        } catch (Exception e) {
            return Map.of("ok", false, "msg", "提取失败: " + e.getMessage());
        }
    }

    @GetMapping("/list")
    public List<Music> list() {
        return musicMapper.selectAll();
    }
}
