package com.rs.coarse;

import org.apache.dubbo.config.spring.context.annotation.EnableDubbo;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

@SpringBootApplication
@EnableDubbo
public class CoarseRankApplication {
    public static void main(String[] args) {
        SpringApplication.run(CoarseRankApplication.class, args);
    }
}
