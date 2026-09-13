package com.rs.consumer.entity;

import java.io.Serializable;
import java.time.LocalDateTime;

/** music 表实体：提取入库的 B 站音频 */
public class Music implements Serializable {

    private Long id;
    private String title;
    private String artist;
    private String bvid;
    private Integer duration;
    private String filePath;
    private String cover;
    private LocalDateTime createdAt;

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }
    public String getTitle() { return title; }
    public void setTitle(String title) { this.title = title; }
    public String getArtist() { return artist; }
    public void setArtist(String artist) { this.artist = artist; }
    public String getBvid() { return bvid; }
    public void setBvid(String bvid) { this.bvid = bvid; }
    public Integer getDuration() { return duration; }
    public void setDuration(Integer duration) { this.duration = duration; }
    public String getFilePath() { return filePath; }
    public void setFilePath(String filePath) { this.filePath = filePath; }
    public String getCover() { return cover; }
    public void setCover(String cover) { this.cover = cover; }
    public LocalDateTime getCreatedAt() { return createdAt; }
    public void setCreatedAt(LocalDateTime createdAt) { this.createdAt = createdAt; }
}
