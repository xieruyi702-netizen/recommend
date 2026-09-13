package com.rs.api.entity;

import java.io.Serializable;
import java.time.LocalDateTime;

/** items 表实体，字段与数据库列一一对应（驼峰映射下划线） */
public class Item implements Serializable {

    private Long id;
    private String title;
    private String tags;
    private String author;
    private Double hotScore;
    private Double ctr;
    private LocalDateTime publishTime;
    private String url;
    private String summary;

    public com.rs.api.ItemDTO toDTO() {
        com.rs.api.ItemDTO dto = new com.rs.api.ItemDTO(
                id == null ? 0 : id, title, tags, author,
                hotScore == null ? 0 : hotScore, ctr == null ? 0 : ctr, publishTime);
        dto.setUrl(url);
        dto.setSummary(summary);
        return dto;
    }

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }
    public String getTitle() { return title; }
    public void setTitle(String title) { this.title = title; }
    public String getTags() { return tags; }
    public void setTags(String tags) { this.tags = tags; }
    public String getAuthor() { return author; }
    public void setAuthor(String author) { this.author = author; }
    public Double getHotScore() { return hotScore; }
    public void setHotScore(Double hotScore) { this.hotScore = hotScore; }
    public Double getCtr() { return ctr; }
    public void setCtr(Double ctr) { this.ctr = ctr; }
    public LocalDateTime getPublishTime() { return publishTime; }
    public void setPublishTime(LocalDateTime publishTime) { this.publishTime = publishTime; }
    public String getUrl() { return url; }
    public void setUrl(String url) { this.url = url; }
    public String getSummary() { return summary; }
    public void setSummary(String summary) { this.summary = summary; }
}
