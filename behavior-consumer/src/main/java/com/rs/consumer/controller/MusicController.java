package com.rs.consumer.controller;

import com.rs.consumer.application.MusicApplicationService;
import com.rs.consumer.domain.MusicDomainException;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

/** 音乐库接口：只做协议适配，用例编排全部委托应用服务 */
@RestController
@RequestMapping("/api/music")
public class MusicController {

    private final MusicApplicationService musicService;

    public MusicController(MusicApplicationService musicService) {
        this.musicService = musicService;
    }

    /** 提取 B 站视频音频并入库（同一视频幂等） */
    @PostMapping("/extract")
    public Map<String, Object> extract(@RequestBody Map<String, String> body) {
        try {
            var outcome = musicService.extract(body.getOrDefault("url", ""));
            return Map.of("ok", true,
                    "msg", outcome.duplicated() ? "该视频已提取过" : "提取成功",
                    "music", outcome.music());
        } catch (MusicDomainException e) {
            return Map.of("ok", false, "msg", e.getMessage());
        }
    }

    @GetMapping("/list")
    public Object list() {
        return musicService.list();
    }

    /** 删除音乐：同时移除音频/字幕文件 */
    @DeleteMapping("/{id}")
    public Map<String, Object> delete(@PathVariable long id) {
        try {
            musicService.delete(id);
            return Map.of("ok", true, "msg", "已删除");
        } catch (MusicDomainException e) {
            return Map.of("ok", false, "msg", e.getMessage());
        }
    }
}
