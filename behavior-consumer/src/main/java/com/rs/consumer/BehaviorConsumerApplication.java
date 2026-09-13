package com.rs.consumer;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

@SpringBootApplication
@org.springframework.scheduling.annotation.EnableScheduling
public class BehaviorConsumerApplication {
    public static void main(String[] args) {
        SpringApplication.run(BehaviorConsumerApplication.class, args);
    }
}
