package com.flowmesh.notificationaudit.support;

import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

/**
 * 通知审计集成测试基类，使用非超级用户验证真实 PostgreSQL RLS。
 */
@SpringBootTest
@ActiveProfiles("test")
@Testcontainers(disabledWithoutDocker = true)
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_CLASS)
public abstract class PostgresIntegrationTest {

    /**
     * 创建带有 audit 业务账号的 PostgreSQL 测试容器。
     */
    @Container
    static final PostgreSQLContainer<?> POSTGRES = new PostgreSQLContainer<>("postgres:16")
        .withDatabaseName("flowmesh")
        .withUsername("postgres")
        .withPassword("postgres")
        .withInitScript("init-nosuperuser.sql");

    /**
     * 将测试数据源切换到非超级用户。
     *
     * @param registry 动态属性注册器
     */
    @DynamicPropertySource
    static void configureDataSource(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", POSTGRES::getJdbcUrl);
        registry.add("spring.datasource.username", () -> "flowmesh_audit");
        registry.add("spring.datasource.password", () -> "change-me-audit");
    }
}
