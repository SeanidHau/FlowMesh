package com.flowmesh.iam;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

/**
 * FlowMesh IAM 服务的 Spring Boot 启动入口。
 *
 * <p>IAM 服务负责用户身份、角色、租户归属，以及 Access Token 和 Refresh Token
 * 的生命周期。</p>
 */
@SpringBootApplication(scanBasePackages = "com.flowmesh")
public class IamServiceApplication {

    /**
     * 启动 IAM 服务。
     *
     * @param args Spring Boot 启动参数
     */
    public static void main(String[] args) {
        SpringApplication.run(IamServiceApplication.class, args);
    }
}
