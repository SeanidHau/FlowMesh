package com.flowmesh.risk;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableScheduling;

/**
 * FlowMesh 异步风控服务启动类。
 */
@SpringBootApplication
@EnableScheduling
public class RiskServiceApplication {

    /**
     * 启动风控服务。
     *
     * @param args Spring Boot 启动参数
     */
    public static void main(String[] args) {
        SpringApplication.run(RiskServiceApplication.class, args);
    }
}
