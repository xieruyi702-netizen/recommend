package com.rs.api;

import java.io.Serializable;
import java.time.LocalDateTime;

public class ItemDTO implements Serializable {

    private long id;
    private String title;
    private String tags;          // 逗号分隔，如 "科技,数码"
    private String author;
    private double hotScore;      // 热度分
    private double ctr;           // 历史点击率
    private LocalDateTime publishTime;
    private String recallSource;  // 召回来源：hot/tag/itemcf
    private double rankScore;     // 精排得分
    private String url;           // 新闻原文链接
    private String summary;       // 内容摘要

    public ItemDTO() {}

    public ItemDTO(long id, String title, String tags, String author,
                   double hotScore, double ctr, LocalDateTime publishTime) {
        this.id = id;
        this.title = title;
        this.tags = tags;
        this.author = author;
        this.hotScore = hotScore;
        this.ctr = ctr;
        this.publishTime = publishTime;
    }

    public long getId() { return id; }
    public void setId(long id) { this.id = id; }
    public String getTitle() { return title; }
    public void setTitle(String title) { this.title = title; }
    public String getTags() { return tags; }
    public void setTags(String tags) { this.tags = tags; }
    public String getAuthor() { return author; }
    public void setAuthor(String author) { this.author = author; }
    public double getHotScore() { return hotScore; }
    public void setHotScore(double hotScore) { this.hotScore = hotScore; }
    public double getCtr() { return ctr; }
    public void setCtr(double ctr) { this.ctr = ctr; }
    public LocalDateTime getPublishTime() { return publishTime; }
    public void setPublishTime(LocalDateTime publishTime) { this.publishTime = publishTime; }
    public String getRecallSource() { return recallSource; }
    public void setRecallSource(String recallSource) { this.recallSource = recallSource; }
    public double getRankScore() { return rankScore; }
    public void setRankScore(double rankScore) { this.rankScore = rankScore; }
    public String getUrl() { return url; }
    public void setUrl(String url) { this.url = url; }
    public String getSummary() { return summary; }
    public void setSummary(String summary) { this.summary = summary; }

    public java.util.Set<String> tagSet() {
        if (tags == null || tags.isBlank()) return java.util.Set.of();
        return new java.util.HashSet<>(java.util.Arrays.asList(tags.split(",")));
    }
}
