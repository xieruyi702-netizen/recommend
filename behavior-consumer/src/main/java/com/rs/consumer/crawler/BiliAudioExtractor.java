package com.rs.consumer.crawler;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.rs.consumer.entity.Music;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.io.InputStream;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardCopyOption;
import java.time.Duration;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * B 站音频提取：参考 river723/bilibili-audio-extractor 与 miniwangdali/BilibiliMusicExtractor
 * 的通用做法，Java 原生实现（不依赖 you-get / ffmpeg）：
 *   1. view API   (bvid)            → 标题/UP主/时长/cid/封面
 *   2. playurl API (cid, fnval=16)  → DASH 流，取最高码率 audio 的 baseUrl（m4s/m4a，AAC）
 *   3. 下载音频流：B 站 CDN 校验 Referer，必须带 https://www.bilibili.com/ 否则 403
 * 浏览器（Chrome/Edge/Safari）可直接播放 AAC 封装的 m4a，暂不做 MP3 转码。
 */
@Component
public class BiliAudioExtractor {

    private static final Logger log = LoggerFactory.getLogger(BiliAudioExtractor.class);
    private static final Pattern BVID = Pattern.compile("(BV[0-9A-Za-z]{10})");
    private static final String UA = "Mozilla/5.0 (Macintosh; Intel Mac OS X 10_15_7) AppleWebKit/537.36";
    private static final String REFERER = "https://www.bilibili.com/";

    private final ObjectMapper mapper = new ObjectMapper();
    private final HttpClient http = HttpClient.newBuilder()
            .connectTimeout(Duration.ofSeconds(15)).followRedirects(HttpClient.Redirect.NORMAL).build();
    private final Path musicDir;

    public BiliAudioExtractor(@Value("${music.dir:/data/music}") String musicDir) {
        this.musicDir = Paths.get(musicDir);
    }

    /** 从用户输入中提取 bvid（支持整条链接/带参数） */
    public String extractBvid(String input) {
        Matcher m = BVID.matcher(input == null ? "" : input);
        if (!m.find()) throw new IllegalArgumentException("无法从链接中识别 BV 号");
        return m.group(1);
    }

    /** 提取音频并保存到音乐目录，返回元数据（不落库） */
    public Music extract(String bvid) throws Exception {
        JsonNode view = getJson("https://api.bilibili.com/x/web-interface/view?bvid=" + bvid);
        if (view.path("code").asInt() != 0) {
            throw new IllegalArgumentException("视频不存在或不可访问: " + view.path("message").asText());
        }
        JsonNode data = view.path("data");
        long cid = data.path("cid").asLong();

        JsonNode play = getJson("https://api.bilibili.com/x/player/playurl?bvid=" + bvid
                + "&cid=" + cid + "&fnval=16");
        JsonNode audio = play.path("data").path("dash").path("audio");
        if (!audio.isArray() || audio.isEmpty()) {
            throw new IllegalStateException("该视频没有可提取的独立音频流（DASH）");
        }
        // 取码率最高的音轨
        JsonNode best = audio.get(0);
        for (JsonNode a : audio) {
            if (a.path("bandwidth").asLong() > best.path("bandwidth").asLong()) best = a;
        }
        String audioUrl = best.path("base_url").asText();
        if (audioUrl.isBlank()) throw new IllegalStateException("未取到音频流地址");

        Path file = musicDir.resolve(bvid + ".m4a");
        download(audioUrl, file);

        Music music = new Music();
        music.setTitle(data.path("title").asText(bvid));
        music.setArtist(data.path("owner").path("name").asText(""));
        music.setBvid(bvid);
        music.setDuration(data.path("duration").asInt(0));
        music.setCover(data.path("pic").asText(""));
        music.setFilePath(file.getFileName().toString());
        return music;
    }

    private JsonNode getJson(String url) throws Exception {
        HttpRequest req = HttpRequest.newBuilder(URI.create(url))
                .timeout(Duration.ofSeconds(20))
                .header("User-Agent", UA)
                .header("Referer", REFERER)
                .GET().build();
        return mapper.readTree(http.send(req, HttpResponse.BodyHandlers.ofString()).body());
    }

    private void download(String url, Path target) throws Exception {
        Files.createDirectories(target.getParent());
        Path tmp = target.resolveSibling(target.getFileName() + ".part");
        HttpRequest req = HttpRequest.newBuilder(URI.create(url))
                .timeout(Duration.ofMinutes(5))
                .header("User-Agent", UA)
                .header("Referer", REFERER)
                .GET().build();
        try (InputStream in = http.send(req, HttpResponse.BodyHandlers.ofInputStream()).body()) {
            Files.copy(in, tmp, StandardCopyOption.REPLACE_EXISTING);
        }
        if (Files.size(tmp) < 1024) {
            Files.deleteIfExists(tmp);
            throw new IllegalStateException("下载的音频文件为空（可能被风控），请稍后重试");
        }
        Files.move(tmp, target, StandardCopyOption.REPLACE_EXISTING);
        log.info("B站音频已保存: {} ({} KB)", target, Files.size(target) / 1024);
    }
}
