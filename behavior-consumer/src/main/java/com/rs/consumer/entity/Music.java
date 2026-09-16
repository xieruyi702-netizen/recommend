package com.rs.consumer.entity;

import java.io.Serializable;
import java.time.LocalDateTime;

/** music 表实体（音乐聚合根）：工厂方法封装「提取入库」的领域规则 */
public class Music implements Serializable {

    /**
     * 从一次成功的 B 站音频提取构建聚合。
     * 领域规则：同一 bvid 只保留一份音乐。
     */
    public static Music extractedFrom(String bvid, String title, String artist,
                                      int duration, String cover, String filePath, String subtitle) {
        Music m = new Music();
        m.setBvid(bvid);
        m.setTitle(title);
        m.setArtist(artist);
        m.setDuration(duration);
        m.setCover(cover);
        m.setFilePath(filePath);
        m.setSubtitle(subtitle == null ? "" : subtitle);
        return m;
    }

    private Long id;
    private String title;
    private String artist;
    private String bvid;
    private Integer duration;
    private String filePath;
    private String cover;
    private String subtitle;
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
    public String getSubtitle() { return subtitle; }
    public void setSubtitle(String subtitle) { this.subtitle = subtitle; }
    public LocalDateTime getCreatedAt() { return createdAt; }
    public void setCreatedAt(LocalDateTime createdAt) { this.createdAt = createdAt; }
}
