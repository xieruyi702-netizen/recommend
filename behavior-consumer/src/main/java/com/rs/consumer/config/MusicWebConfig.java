package com.rs.consumer.config;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.config.annotation.ResourceHandlerRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

/** 音频/字幕静态资源：/music/** 直接映射本地音乐目录 */
@Configuration
public class MusicWebConfig implements WebMvcConfigurer {

    @Value("${music.dir:/data/music}")
    private String musicDir;

    @Override
    public void addResourceHandlers(ResourceHandlerRegistry registry) {
        registry.addResourceHandler("/music/**")
                .addResourceLocations("file:" + musicDir + "/");
    }
}
