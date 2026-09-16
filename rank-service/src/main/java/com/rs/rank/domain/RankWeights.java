package com.rs.rank.domain;

/** 精排打分权重（rank_config 的领域表达） */
public record RankWeights(double ctr, double interest, double hot, double fresh) {

    public static RankWeights of(double ctr, double interest, double hot, double fresh) {
        return new RankWeights(ctr, interest, hot, fresh);
    }
}
