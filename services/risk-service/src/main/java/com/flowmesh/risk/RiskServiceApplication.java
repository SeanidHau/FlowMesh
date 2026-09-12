package com.flowmesh.risk;

import com.flowmesh.common.migration.FlowMeshFlywayMigration;
import com.flowmesh.risk.config.RiskFaultInjectionProperties;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.scheduling.annotation.EnableScheduling;

/**
 * FlowMesh 异步风控服务启动类。
 */
@SpringBootApplication(scanBasePackages = {"com.flowmesh.risk", "com.flowmesh.common.health"})
@EnableScheduling
@EnableConfigurationProperties(RiskFaultInjectionProperties.class)
public class RiskServiceApplication {

    /**
     * 启动风控服务。
     *
     * @param args Spring Boot 启动参数
     */
    public static void main(String[] args) {
        if (FlowMeshFlywayMigration.isRequested()) {
            System.exit(FlowMeshFlywayMigration.migrate("risk"));
            return;
        }
        SpringApplication.run(RiskServiceApplication.class, args);
    }
}
