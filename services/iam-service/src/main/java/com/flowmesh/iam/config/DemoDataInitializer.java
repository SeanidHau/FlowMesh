package com.flowmesh.iam.config;

import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.core.io.ClassPathResource;
import org.springframework.jdbc.datasource.init.ResourceDatabasePopulator;
import org.springframework.stereotype.Component;

import javax.sql.DataSource;

/**
 * 在显式开启时初始化本地演示账号。
 *
 * <p>演示数据不属于 Flyway 生产迁移，只有设置
 * {@code FLOWMESH_DEMO_DATA_ENABLED=true} 时才会执行。</p>
 */
@Component
@ConditionalOnProperty(name = "flowmesh.demo-data.enabled", havingValue = "true")
public class DemoDataInitializer implements ApplicationRunner {

    private final DataSource dataSource;

    /**
     * 创建演示数据初始化器。
     *
     * @param dataSource IAM 数据源
     */
    public DemoDataInitializer(DataSource dataSource) {
        this.dataSource = dataSource;
    }

    @Override
    public void run(ApplicationArguments args) {
        ResourceDatabasePopulator populator = new ResourceDatabasePopulator(
            new ClassPathResource("db/demo/seed_demo_data.sql")
        );
        populator.execute(dataSource);
    }
}
