package com.rs.behavior.api;

import java.io.Serializable;

/** 用户行为事件：expose 曝光 / click 点击 / like 点赞 */
public class BehaviorEvent implements Serializable {

    private long userId;
    private long itemId;
    private String action;    // expose | click | like
    private long timestamp;

    public BehaviorEvent() {}

    public BehaviorEvent(long userId, long itemId, String action, long timestamp) {
        this.userId = userId;
        this.itemId = itemId;
        this.action = action;
        this.timestamp = timestamp;
    }

    public long getUserId() { return userId; }
    public void setUserId(long userId) { this.userId = userId; }
    public long getItemId() { return itemId; }
    public void setItemId(long itemId) { this.itemId = itemId; }
    public String getAction() { return action; }
    public void setAction(String action) { this.action = action; }
    public long getTimestamp() { return timestamp; }
    public void setTimestamp(long timestamp) { this.timestamp = timestamp; }
}
