package com.flowmesh.notificationaudit;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableScheduling;

/**
 * FlowMesh 通知与审计投影服务启动类。
 */
@SpringBootApplication(scanBasePackages = "com.flowmesh")
@EnableScheduling
public class NotificationAuditServiceApplication {

    /**
     * 启动通知与审计服务。
     *
     * @param args Spring Boot 启动参数
     */
    public static void main(String[] args) {
        SpringApplication.run(NotificationAuditServiceApplication.class, args);
    }
}
