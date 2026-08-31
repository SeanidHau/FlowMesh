package com.flowmesh.supplier.support;

import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

/**
 * 集成测试基类，提供 Testcontainers 真实 PostgreSQL 连接。
 *
 * <p>容器使用 postgres 超级用户启动，init 脚本创建 NOSUPERUSER 业务账号 flowmesh_supplier
 * 及 supplier schema。数据源使用 flowmesh_supplier 连接，使 RLS FORCE 策略生效。</p>
 */
@SpringBootTest
@ActiveProfiles("test")
@Testcontainers
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_CLASS)
public abstract class PostgresIntegrationTest {

    /**
     * 真实 PostgreSQL 容器，init 脚本创建 NOSUPERUSER 业务账号。
     */
    @Container
    static final PostgreSQLContainer<?> POSTGRES = new PostgreSQLContainer<>("postgres:16")
            .withDatabaseName("flowmesh")
            .withUsername("postgres")
            .withPassword("postgres")
            .withInitScript("init-nosuperuser.sql");

    /**
     * 覆盖数据源连接，使用 NOSUPERUSER 业务账号。
     *
     * @param registry 动态属性注册器
     */
    @DynamicPropertySource
    static void configureDataSource(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", POSTGRES::getJdbcUrl);
        registry.add("spring.datasource.username", () -> "flowmesh_supplier");
        registry.add("spring.datasource.password", () -> "change-me-supplier");
    }
}
