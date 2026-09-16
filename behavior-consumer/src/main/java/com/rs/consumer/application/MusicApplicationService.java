package com.rs.consumer.application;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.rs.consumer.crawler.BiliAudioExtractor;
import com.rs.consumer.domain.Bvid;
import com.rs.consumer.domain.MusicDomainException;
import com.rs.consumer.entity.Music;
import com.rs.consumer.mapper.MusicMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Service;

import java.util.List;

/**
 * 音乐库应用服务：编排「提取 → 入库 → 发领域事件」与「删除」用例。
 * 不含业务规则（规则在 Music 聚合），不含技术细节（下载在 Extractor、存储在 Mapper）。
 */
@Service
public class MusicApplicationService {

    /** 领域事件：一首音乐被提取入库（其他上下文可订阅：推荐接入、统计、通知…） */
    public record MusicExtracted(String bvid, String title, String artist, int duration) {
    }

    public record ExtractOutcome(Music music, boolean duplicated) {
    }

    private static final Logger log = LoggerFactory.getLogger(MusicApplicationService.class);
    private static final String EVENT_TOPIC = "music-extracted";

    private final BiliAudioExtractor extractor;
    private final MusicMapper musicMapper;
    private final KafkaTemplate<String, String> kafkaTemplate;
    private final ObjectMapper objectMapper = new ObjectMapper();

    public MusicApplicationService(BiliAudioExtractor extractor, MusicMapper musicMapper,
                                   KafkaTemplate<String, String> kafkaTemplate) {
        this.extractor = extractor;
        this.musicMapper = musicMapper;
        this.kafkaTemplate = kafkaTemplate;
    }

    /** 提取 B 站音频入库；同一 bvid 幂等（已存在直接返回已有记录） */
    public ExtractOutcome extract(String url) {
        Bvid bvid = Bvid.from(url);
        Music existing = musicMapper.selectByBvid(bvid.value());
        if (existing != null) {
            return new ExtractOutcome(existing, true);
        }
        try {
            BiliAudioExtractor.BiliTrack track = extractor.extract(bvid.value());
            Music music = Music.extractedFrom(track.bvid(), track.title(), track.artist(),
                    track.duration(), track.cover(), track.filePath(), track.subtitle());
            musicMapper.insert(music);
            publishExtracted(music);
            return new ExtractOutcome(music, false);
        } catch (IllegalArgumentException | MusicDomainException e) {
            throw e;
        } catch (Exception e) {
            throw new MusicDomainException("提取失败: " + e.getMessage());
        }
    }

    public List<Music> list() {
        return musicMapper.selectAll();
    }

    /** 删除音乐：清记录 + 清音频/字幕文件 */
    public void delete(long id) {
        Music music = musicMapper.selectById(id);
        if (music == null) {
            throw new MusicDomainException("音乐不存在");
        }
        musicMapper.deleteById(id);
        deleteFile(music.getFilePath());
        if (music.getSubtitle() != null && !music.getSubtitle().isBlank()) {
            deleteFile(music.getSubtitle());
        }
    }

    private void deleteFile(String fileName) {
        try {
            java.nio.file.Files.deleteIfExists(extractor.resolveFile(fileName));
        } catch (Exception e) {
            log.warn("文件清理失败: {} - {}", fileName, e.getMessage());
        }
    }

    /** 领域事件发布失败不影响主流程（事件是通知性质，不是一致性的组成部分） */
    private void publishExtracted(Music music) {
        try {
            String payload = objectMapper.writeValueAsString(
                    new MusicExtracted(music.getBvid(), music.getTitle(), music.getArtist(), music.getDuration()));
            kafkaTemplate.send(EVENT_TOPIC, music.getBvid(), payload);
            log.info("已发布领域事件 {} : {}", EVENT_TOPIC, payload);
        } catch (Exception e) {
            log.warn("领域事件发布失败（不影响入库）: {}", e.getMessage());
        }
    }

    @SuppressWarnings("unused")
    private MusicDomainException notFound(long id) {
        return new MusicDomainException("音乐不存在 id=" + id);
    }
}
