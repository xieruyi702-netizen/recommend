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
    private final String sessdata;   // B 站登录凭证：配置后可抓取 CC/AI 字幕（匿名拿不到）

    public BiliAudioExtractor(@Value("${music.dir:/data/music}") String musicDir,
                              @Value("${bili.sessdata:}") String sessdata) {
        this.musicDir = Paths.get(musicDir);
        this.sessdata = sessdata;
    }

    /** 是否安装了 ffmpeg（有则把 AAC 转成 MP3，通用性更好） */
    private boolean ffmpegAvailable() {
        try {
            Process p = new ProcessBuilder("ffmpeg", "-version").redirectErrorStream(true).start();
            p.getInputStream().readAllBytes();
            return p.waitFor(5, java.util.concurrent.TimeUnit.SECONDS) && p.exitValue() == 0;
        } catch (Exception e) {
            return false;
        }
    }

    /** m4a → mp3，失败时保留原 m4a */
    private Path toMp3(Path m4a) throws Exception {
        Path mp3 = m4a.resolveSibling(m4a.getFileName().toString().replaceAll("\\.m4a$", ".mp3"));
        Process p = new ProcessBuilder(
                "ffmpeg", "-y", "-i", m4a.toString(), "-codec:a", "libmp3lame", "-q:a", "2", mp3.toString())
                .redirectErrorStream(true).start();
        p.getInputStream().readAllBytes();
        if (!p.waitFor(120, java.util.concurrent.TimeUnit.SECONDS) || p.exitValue() != 0
                || !Files.exists(mp3) || Files.size(mp3) < 1024) {
            p.destroyForcibly();
            Files.deleteIfExists(mp3);
            return m4a;
        }
        Files.delete(m4a);
        return mp3;
    }

    /** 解析音乐目录下的文件路径 */
    public Path resolveFile(String fileName) {
        return musicDir.resolve(fileName);
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
        java.util.List<String> candidates = new java.util.ArrayList<>();
        addIfNotBlank(candidates, best.path("base_url").asText());
        for (JsonNode u : best.path("backup_url")) addIfNotBlank(candidates, u.asText());
        for (JsonNode a : audio) {
            if (a == best) continue;
            addIfNotBlank(candidates, a.path("base_url").asText());
            for (JsonNode u : a.path("backup_url")) addIfNotBlank(candidates, u.asText());
        }
        if (candidates.isEmpty()) throw new IllegalStateException("未取到音频流地址");

        Path file = musicDir.resolve(bvid + ".m4a");
        Exception last = null;
        boolean downloaded = false;
        for (String audioUrl : candidates) {
            try {
                download(audioUrl, file);
                downloaded = true;
                break;
            } catch (Exception e) {
                last = e;
                log.warn("音频源下载失败，尝试备用地址: {}", e.getMessage());
            }
        }
        if (!downloaded) {
            throw new IllegalStateException("音频下载被 B 站临时限制，请几分钟后再试");
        }
        if (ffmpegAvailable()) {
            file = toMp3(file);
        }

        Music music = new Music();
        music.setTitle(data.path("title").asText(bvid));
        music.setArtist(data.path("owner").path("name").asText(""));
        music.setBvid(bvid);
        music.setDuration(data.path("duration").asInt(0));
        music.setCover(data.path("pic").asText(""));
        music.setFilePath(file.getFileName().toString());

        // 字幕：CC / AI 字幕（JSON）→ WebVTT；没有则留空
        try {
            String subtitleUrl = findSubtitleUrl(bvid, cid);
            if (subtitleUrl != null) {
                music.setSubtitle(saveSubtitle(subtitleUrl, bvid));
            }
        } catch (Exception e) {
            log.warn("字幕抓取失败（不影响音频）: {}", e.getMessage());
        }
        return music;
    }

    /** player API 找一条字幕（优先中文 CC，其次 AI 字幕），返回字幕 JSON 地址 */
    private String findSubtitleUrl(String bvid, long cid) throws Exception {
        JsonNode player = getJson("https://api.bilibili.com/x/player/v2?bvid=" + bvid + "&cid=" + cid);
        JsonNode subs = player.path("data").path("subtitle").path("subtitles");
        if (!subs.isArray() || subs.isEmpty()) return null;
        JsonNode pick = null;
        for (JsonNode sub : subs) {
            if (sub.path("lan").asText().startsWith("zh")) { pick = sub; break; }
            if (pick == null) pick = sub;
        }
        if (pick == null) return null;
        String url = pick.path("subtitle_url").asText();
        return url.startsWith("//") ? "https:" + url : url;
    }

    /** 字幕 JSON（body: from/to/content）转 WebVTT 保存，返回文件名 */
    private String saveSubtitle(String subtitleUrl, String bvid) throws Exception {
        JsonNode json = getJson(subtitleUrl);
        JsonNode body = json.path("body");
        if (!body.isArray() || body.isEmpty()) return null;
        StringBuilder vtt = new StringBuilder("WEBVTT\n\n");
        for (JsonNode line : body) {
            vtt.append(vttTime(line.path("from").asDouble())).append(" --> ")
               .append(vttTime(line.path("to").asDouble())).append("\n")
               .append(line.path("content").asText()).append("\n\n");
        }
        Path file = musicDir.resolve(bvid + ".vtt");
        Files.writeString(file, vtt.toString());
        return file.getFileName().toString();
    }

    private String vttTime(double seconds) {
        int total = (int) seconds;
        int ms = (int) Math.round((seconds - total) * 1000);
        return String.format("%02d:%02d:%02d.%03d", total / 3600, total % 3600 / 60, total % 60, ms);
    }

    private void addIfNotBlank(java.util.List<String> list, String v) {
        if (v != null && !v.isBlank()) list.add(v);
    }

    private JsonNode getJson(String url) throws Exception {
        HttpRequest.Builder builder = HttpRequest.newBuilder(URI.create(url))
                .timeout(Duration.ofSeconds(20))
                .header("User-Agent", UA)
                .header("Referer", REFERER);
        if (sessdata != null && !sessdata.isBlank()) {
            builder.header("Cookie", "SESSDATA=" + sessdata);
        }
        return mapper.readTree(http.send(builder.GET().build(),
                HttpResponse.BodyHandlers.ofString()).body());
    }

    private void download(String url, Path target) throws Exception {
        Files.createDirectories(target.getParent());
        Path tmp = target.resolveSibling(target.getFileName() + ".part");
        HttpRequest req = HttpRequest.newBuilder(URI.create(url))
                .timeout(Duration.ofMinutes(5))
                .header("User-Agent", UA)
                .header("Referer", REFERER)
                .GET().build();
        HttpResponse<InputStream> resp = http.send(req, HttpResponse.BodyHandlers.ofInputStream());
        if (resp.statusCode() != 200) {
            resp.body().close();
            throw new IllegalStateException("下载失败 HTTP " + resp.statusCode());
        }
        try (InputStream in = resp.body()) {
            Files.copy(in, tmp, StandardCopyOption.REPLACE_EXISTING);
        }
        if (Files.size(tmp) < 1024) {
            Files.deleteIfExists(tmp);
            throw new IllegalStateException("下载的音频内容为空");
        }
        Files.move(tmp, target, StandardCopyOption.REPLACE_EXISTING);
        log.info("B站音频已保存: {} ({} KB)", target, Files.size(target) / 1024);
    }
}
