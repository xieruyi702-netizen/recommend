package com.rs.api.entity;

import java.io.Serializable;

/** rank_config 表实体：精排打分权重 */
public class RankConfig implements Serializable {

    private Long id;
    private Double wCtr;
    private Double wInterest;
    private Double wHot;
    private Double wFresh;

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }
    public Double getWCtr() { return wCtr; }
    public void setWCtr(Double wCtr) { this.wCtr = wCtr; }
    public Double getWInterest() { return wInterest; }
    public void setWInterest(Double wInterest) { this.wInterest = wInterest; }
    public Double getWHot() { return wHot; }
    public void setWHot(Double wHot) { this.wHot = wHot; }
    public Double getWFresh() { return wFresh; }
    public void setWFresh(Double wFresh) { this.wFresh = wFresh; }
}
