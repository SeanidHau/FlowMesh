package com.flowmesh.iam.support;

import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.test.context.ActiveProfiles;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;

/**
 * 集成测试基类，提供 Testcontainers 真实 PostgreSQL 连接。
 *
 * <p>所有需要数据库的集成测试继承该类，通过 {@link ServiceConnection} 自动注入数据源，
 * 无需在 application-test.yml 中配置数据源。Flyway 在 iam schema 执行迁移。</p>
 */
@SpringBootTest
@ActiveProfiles("test")
public abstract class PostgresIntegrationTest {

    /**
     * 真实 PostgreSQL 容器，提供与生产环境一致的数据库行为。
     */
    @Container
    @ServiceConnection
    static final PostgreSQLContainer<?> POSTGRES = new PostgreSQLContainer<>("postgres:16")
            .withDatabaseName("flowmesh")
            .withUsername("flowmesh_iam")
            .withPassword("change-me-iam");
}
