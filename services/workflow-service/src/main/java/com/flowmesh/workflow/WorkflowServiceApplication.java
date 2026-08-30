package com.flowmesh.workflow;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

/**
 * FlowMesh workflow 服务的 Spring Boot 启动入口。
 *
 * <p>当前 MVP 消费 {@code ApplicationSubmitted} 事件并保存流程实例投影，
 * 后续再将投影推进到 Camunda 流程实例。</p>
 */
@SpringBootApplication
public class WorkflowServiceApplication {

    /**
     * 启动 workflow 服务。
     *
     * @param args Spring Boot 启动参数
     */
    public static void main(String[] args) {
        SpringApplication.run(WorkflowServiceApplication.class, args);
    }
}
