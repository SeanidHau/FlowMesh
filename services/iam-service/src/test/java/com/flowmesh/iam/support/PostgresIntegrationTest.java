package com.flowmesh.iam.support;

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
 * <p>测试使用迁移账号执行 Flyway，再使用独立业务账号运行测试请求，验证运行时账号不依赖 DDL 权限。</p>
 */
@SpringBootTest
@ActiveProfiles("test")
@Testcontainers(disabledWithoutDocker = true)
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_CLASS)
public abstract class PostgresIntegrationTest {

    /**
     * 真实 PostgreSQL 容器，提供与生产环境一致的数据库行为。
     */
    @Container
    static final PostgreSQLContainer<?> POSTGRES = new PostgreSQLContainer<>("postgres:16")
            .withDatabaseName("flowmesh")
            .withUsername("postgres")
            .withPassword("postgres")
            .withInitScript("init-nosuperuser.sql");

    /**
     * 将 Flyway 和业务数据源分别指向对应的数据库账号。
     *
     * @param registry 动态属性注册器
     */
    @DynamicPropertySource
    static void configureDataSource(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", POSTGRES::getJdbcUrl);
        registry.add("spring.datasource.username", () -> "flowmesh_iam");
        registry.add("spring.datasource.password", () -> "change-me-iam");
        registry.add("spring.flyway.user", () -> "flowmesh_iam_migrator");
        registry.add("spring.flyway.password", () -> "change-me-iam-migrator");
    }
}
