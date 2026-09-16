package com.rs.consumer.domain;

/** Bv 号值对象：从任意输入（完整链接/带参数）中提取并校验，非法输入无法构造 */
public record Bvid(String value) {

    public Bvid {
        if (value == null || !value.matches("BV[0-9A-Za-z]{10}")) {
            throw new MusicDomainException("无法从链接中识别 BV 号");
        }
    }

    /** 从用户输入（整条链接/带参数/裸 BV 号）中提取 */
    public static Bvid from(String input) {
        if (input == null) throw new IllegalArgumentException("链接不能为空");
        var m = java.util.regex.Pattern.compile("(BV[0-9A-Za-z]{10})").matcher(input);
        if (!m.find()) throw new MusicDomainException("无法从链接中识别 BV 号");
        return new Bvid(m.group(1));
    }
}
