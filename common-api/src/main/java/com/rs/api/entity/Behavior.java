package com.rs.api.entity;

import java.io.Serializable;

/** behaviors 表实体 */
public class Behavior implements Serializable {

    private Long id;
    private Long userId;
    private Long itemId;
    private String action;
    private String eventId;

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }
    public Long getUserId() { return userId; }
    public void setUserId(Long userId) { this.userId = userId; }
    public Long getItemId() { return itemId; }
    public void setItemId(Long itemId) { this.itemId = itemId; }
    public String getAction() { return action; }
    public void setAction(String action) { this.action = action; }
    public String getEventId() { return eventId; }
    public void setEventId(String eventId) { this.eventId = eventId; }
}
