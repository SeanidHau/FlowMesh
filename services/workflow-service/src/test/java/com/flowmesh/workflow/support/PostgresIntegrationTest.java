package com.flowmesh.workflow.support;

import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

/**
 * workflow 集成测试基类，提供启用 RLS 的真实 PostgreSQL。
 */
@SpringBootTest
@ActiveProfiles("test")
@Testcontainers
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_CLASS)
public abstract class PostgresIntegrationTest {

    /**
     * 使用非超级用户连接，确保 RLS 策略实际生效。
     */
    @Container
    static final PostgreSQLContainer<?> POSTGRES = new PostgreSQLContainer<>("postgres:16")
        .withDatabaseName("flowmesh")
        .withUsername("postgres")
        .withPassword("postgres")
        .withInitScript("init-nosuperuser.sql");

    /**
     * 将 Spring 数据源指向测试容器。
     *
     * @param registry 动态属性注册器
     */
    @DynamicPropertySource
    static void configureDataSource(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", POSTGRES::getJdbcUrl);
        registry.add("spring.datasource.username", () -> "flowmesh_workflow");
        registry.add("spring.datasource.password", () -> "change-me-workflow");
    }
}
