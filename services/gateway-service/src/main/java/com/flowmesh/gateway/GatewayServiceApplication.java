package com.flowmesh.gateway;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

/**
 * FlowMesh API 网关启动类。
 *
 * <p>网关只负责统一入口和路由，JWT、租户和 RBAC 校验仍由下游服务执行。</p>
 */
@SpringBootApplication
public class GatewayServiceApplication {

    /**
     * 启动网关服务。
     *
     * @param args Spring Boot 启动参数
     */
    public static void main(String[] args) {
        SpringApplication.run(GatewayServiceApplication.class, args);
    }
}
