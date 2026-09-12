package com.flowmesh.iam.config;

import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.core.io.ClassPathResource;
import org.springframework.jdbc.datasource.init.ResourceDatabasePopulator;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.ConnectionCallback;
import org.springframework.stereotype.Component;

import java.sql.Connection;

/**
 * 在显式开启时初始化本地演示账号。
 *
 * <p>演示数据不属于 Flyway 生产迁移，只有设置
 * {@code FLOWMESH_DEMO_DATA_ENABLED=true} 时才会执行。</p>
 */
@Component
@ConditionalOnProperty(name = "flowmesh.demo-data.enabled", havingValue = "true")
public class DemoDataInitializer implements ApplicationRunner {

    private final JdbcTemplate jdbcTemplate;

    /**
     * 创建演示数据初始化器。
     *
     * @param jdbcTemplate IAM 数据源对应的 JDBC 模板
     */
    public DemoDataInitializer(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    @Override
    public void run(ApplicationArguments args) {
        ResourceDatabasePopulator populator = new ResourceDatabasePopulator(
            new ClassPathResource("db/demo/seed_demo_data.sql")
        );
        // 作用：让演示数据脚本在单个显式事务中运行，保证 SET LOCAL 的租户上下文不泄漏到连接池。
        jdbcTemplate.execute((ConnectionCallback<Void>) connection -> populateInTransaction(populator, connection));
    }

    /**
     * 在独立连接事务中执行演示数据脚本。
     *
     * @param populator SQL 脚本执行器
     * @param connection 当前数据库连接
     * @return 始终返回 {@code null}
     * @throws java.sql.SQLException 数据库事务执行失败
     */
    private Void populateInTransaction(ResourceDatabasePopulator populator, Connection connection)
        throws java.sql.SQLException {
        boolean originalAutoCommit = connection.getAutoCommit();
        try {
            connection.setAutoCommit(false);
            populator.populate(connection);
            connection.commit();
        } catch (RuntimeException | java.sql.SQLException exception) {
            connection.rollback();
            throw exception;
        } finally {
            connection.setAutoCommit(originalAutoCommit);
        }
        return null;
    }
}
