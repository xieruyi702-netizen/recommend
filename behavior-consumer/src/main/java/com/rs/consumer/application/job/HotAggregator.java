package com.rs.consumer.application.job;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * 多平台热点聚合定时任务：人民网 RSS + B站排行榜 + 抖音热点榜。
 * 按 URL 唯一键增量去重入库；热度/点击率初始化随机基线，由用户行为实时修正。
 */
@Component
public class HotAggregator {

    private static final Logger log = LoggerFactory.getLogger(HotAggregator.class);
    private static final Map<String, String> RSS_FEEDS = Map.of(
            "politics", "时政", "finance", "财经", "it", "科技", "sports", "体育", "ent", "娱乐"
    );
    private static final Pattern ITEM = Pattern.compile("<item>(.*?)</item>", Pattern.DOTALL);
    private static final Pattern TITLE = Pattern.compile("<title><!\\[CDATA\\[(.*?)\\]\\]></title>", Pattern.DOTALL);
    private static final Pattern LINK = Pattern.compile("<link>(.*?)</link>");
    private static final Pattern DESC = Pattern.compile("<description><!\\[CDATA\\[(.*?)\\]\\]></description>", Pattern.DOTALL);

    private final com.rs.consumer.infrastructure.persistence.ItemMapper itemMapper;
    private final org.springframework.data.redis.core.StringRedisTemplate redis;
    private final HttpClient http = HttpClient.newBuilder()
            .connectTimeout(Duration.ofSeconds(15)).build();
    private final Random random = new Random();

    public HotAggregator(com.rs.consumer.infrastructure.persistence.ItemMapper itemMapper, org.springframework.data.redis.core.StringRedisTemplate redis) {
        this.itemMapper = itemMapper;
        this.redis = redis;
    }

    /** 启动后先聚合一次，之后每 30 分钟执行 */
    @jakarta.annotation.PostConstruct
    @Scheduled(cron = "0 */30 * * * *")
    public void crawl() {
        int total = crawlPeopleRss() + crawlBilibili() + crawlDouyin();
        log.info("热点聚合完成，本次新增 {} 条", total);
    }

    /** 人民网各频道 RSS */
    public int crawlPeopleRss() {
        int inserted = 0;
        for (Map.Entry<String, String> feed : RSS_FEEDS.entrySet()) {
            try {
                String xml = get("http://www.people.com.cn/rss/" + feed.getKey() + ".xml");
                Matcher m = ITEM.matcher(xml);
                List<com.rs.api.entity.Item> batch = new ArrayList<>();
                while (m.find() && batch.size() < 30) {
                    String block = m.group(1);
                    String title = unescape(first(TITLE, block));
                    String url = first(LINK, block);
                    if (title.isBlank() || url.isBlank()) continue;
                    String summary = unescape(stripTags(first(DESC, block)));
                    if (summary.length() > 200) summary = summary.substring(0, 200);
                    batch.add(newItem(title, feed.getValue() + "," + feed.getKey(), "人民网",
                            20 + random.nextDouble() * 80, 0.02 + random.nextDouble() * 0.16, url, summary));
                }
                inserted += save(batch);
            } catch (Exception e) {
                log.warn("人民网抓取失败: {} - {}", feed.getKey(), e.getMessage());
            }
        }
        return inserted;
    }

    /** B站全站排行榜：取前 25 条视频（JSON 严格解析，保证 bvid 与标题同条目配对） */
    public int crawlBilibili() {
        try {
            String body = get("https://api.bilibili.com/x/web-interface/ranking/v2?rid=0");
            var root = new com.fasterxml.jackson.databind.ObjectMapper().readTree(body);
            var list = root.path("data").path("list");
            List<com.rs.api.entity.Item> batch = new ArrayList<>();
            for (var node : list) {
                if (batch.size() >= 25) break;
                String bvid = node.path("bvid").asText("");
                String title = node.path("title").asText("").replace("<em class=\"keyword\">", "").replace("</em>", "");
                String up = node.path("owner").path("name").asText("");
                if (bvid.isBlank() || title.isBlank()) continue;
                log.info("B站解析样本#{}: bvid={} title={} up={}", batch.size(), bvid, title, up);
                String url = "https://www.bilibili.com/video/" + bvid;
                batch.add(newItem(title, "B站,热榜,视频", "B站排行榜·" + up,
                        60 + random.nextDouble() * 40, 0.05 + random.nextDouble() * 0.15, url,
                        "B站全站排行榜视频 · UP主：" + up));
            }
            return save(batch);
        } catch (Exception e) {
            log.warn("B站抓取失败: {}", e.getMessage());
            return 0;
        }
    }

    /** 抖音热点榜：取前 25 个热词，链接到搜索页 */
    public int crawlDouyin() {
        try {
            String body = get("https://www.iesdouyin.com/web/api/v2/hotsearch/billboard/word/");
            Matcher m = Pattern.compile("\\{\"word\":\"([^\"]+)\"").matcher(body);
            List<com.rs.api.entity.Item> batch = new ArrayList<>();
            while (m.find() && batch.size() < 25) {
                String word = m.group(1);
                String url = "https://www.douyin.com/search/" +
                        URLEncoder.encode(word, StandardCharsets.UTF_8);
                batch.add(newItem(word, "抖音,热榜,热点", "抖音热点榜",
                        60 + random.nextDouble() * 40, 0.05 + random.nextDouble() * 0.15, url,
                        "抖音热点榜实时热词"));
            }
            return save(batch);
        } catch (Exception e) {
            log.warn("抖音抓取失败: {}", e.getMessage());
            return 0;
        }
    }

    private int save(List<com.rs.api.entity.Item> batch) {
        if (batch.isEmpty()) return 0;
        int inserted = itemMapper.batchInsertIgnore(batch);
        // 新内容入库即进入 Redis 热度榜（NX：已存在的不覆盖行为修正后的分值）
        for (com.rs.api.entity.Item item : batch) {
            try {
                Long id = itemMapper.selectIdByUrl(item.getUrl());
                if (id != null) {
                    redis.opsForZSet().addIfAbsent("hot:rank", String.valueOf(id), item.getHotScore());
                }
            } catch (Exception ignored) {
            }
        }
        return inserted;
    }

    private com.rs.api.entity.Item newItem(String title, String tags, String author,
                                           double hotScore, double ctr, String url, String summary) {
        com.rs.api.entity.Item item = new com.rs.api.entity.Item();
        item.setTitle(title);
        item.setTags(tags);
        item.setAuthor(author);
        item.setHotScore(hotScore);
        item.setCtr(ctr);
        item.setUrl(url);
        item.setSummary(summary);
        return item;
    }

    private String get(String url) throws Exception {
        return http.send(HttpRequest.newBuilder(URI.create(url))
                        .timeout(Duration.ofSeconds(20))
                        .header("User-Agent", "Mozilla/5.0 (Macintosh; Intel Mac OS X 10_15_7)")
                        .GET().build(),
                HttpResponse.BodyHandlers.ofString()).body();
    }

    private String first(Pattern p, String s) {
        Matcher m = p.matcher(s);
        return m.find() ? m.group(1).trim() : "";
    }

    private String stripTags(String s) {
        return s == null ? "" : s.replaceAll("<[^>]+>", "");
    }

    private String unescape(String s) {
        return s.replace("\\u003c", "<").replace("\\u003e", ">")
                .replace("&lt;", "<").replace("&gt;", ">").replace("&quot;", "\"")
                .replace("&#39;", "'").replace("&amp;", "&")
                .replace("<em class=\"keyword\">", "").replace("</em>", "");
    }
}
