package com.flowmesh.workflow;

import com.flowmesh.common.migration.FlowMeshFlywayMigration;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableScheduling;

/**
 * FlowMesh workflow 服务的 Spring Boot 启动入口。
 *
 * <p>当前实现消费 {@code ApplicationSubmitted} 事件并保存流程实例投影，
 * 后续可在保持事件契约不变的前提下将投影推进到 Camunda 流程实例。</p>
 */
@SpringBootApplication(scanBasePackages = "com.flowmesh")
@EnableScheduling
public class WorkflowServiceApplication {

    /**
     * 启动 workflow 服务。
     *
     * @param args Spring Boot 启动参数
     */
    public static void main(String[] args) {
        if (FlowMeshFlywayMigration.isRequested()) {
            System.exit(FlowMeshFlywayMigration.migrate("workflow"));
            return;
        }
        SpringApplication.run(WorkflowServiceApplication.class, args);
    }
}
