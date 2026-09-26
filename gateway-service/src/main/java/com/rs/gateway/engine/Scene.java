package com.rs.gateway.engine;

/** 请求场景：不同场景对应不同的流（Pipeline / DAG）组装 */
public enum Scene {

    HOME("home"),               // 首页信息流：完整 8 算子链路
    RELATED("related"),         // 相关推荐：低延迟轻量链路
    COLD_START("cold_start");   // 冷启动用户：跳过画像与加权

    private final String code;

    Scene(String code) {
        this.code = code;
    }

    public String code() {
        return code;
    }

    public static Scene fromCode(String code) {
        for (Scene s : values()) {
            if (s.code.equalsIgnoreCase(code)) return s;
        }
        throw new IllegalArgumentException("未知场景: " + code + "，可选 home/related/cold_start");
    }
}
