package com.rs.gateway.engine;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/** 引擎基础设施：DAG 并行执行线程池 */
@Configuration
public class FlowConfig {

    @Bean(destroyMethod = "shutdown")
    public ExecutorService dagExecutor() {
        return Executors.newFixedThreadPool(8);
    }
}
