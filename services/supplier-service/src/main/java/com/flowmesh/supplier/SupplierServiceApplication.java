package com.flowmesh.supplier;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableScheduling;

/**
 * FlowMesh supplier 服务的 Spring Boot 启动入口。
 *
 * <p>supplier 服务负责供应商准入申请的创建、幂等控制和租户隔离。</p>
 */
@SpringBootApplication(scanBasePackages = "com.flowmesh")
@EnableScheduling
public class SupplierServiceApplication {

    /**
     * 启动 supplier 服务。
     *
     * @param args Spring Boot 启动参数
     */
    public static void main(String[] args) {
        SpringApplication.run(SupplierServiceApplication.class, args);
    }
}
