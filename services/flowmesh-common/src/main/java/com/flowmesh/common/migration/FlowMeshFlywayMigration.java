package com.flowmesh.common.migration;

import org.flywaydb.core.Flyway;

/**
 * 提供 FlowMesh 的独立 Flyway 迁移入口。
 *
 * <p>生产 Kubernetes 迁移 Job 复用业务服务镜像，但不会启动业务 Spring Context，
 * 只使用专用迁移账号执行当前服务镜像内的 {@code db/migration} 资源。这样运行时
 * Deployment 不需要持有 Flyway 迁移密码。</p>
 */
public final class FlowMeshFlywayMigration {

    /** 迁移模式开关。 */
    private static final String MIGRATION_MODE = "FLOWMESH_MIGRATION_MODE";

    /** 迁移连接 URL。 */
    private static final String JDBC_URL = "FLOWMESH_MIGRATION_JDBC_URL";

    /** 迁移账号。 */
    private static final String USER = "FLOWMESH_MIGRATION_USER";

    /** 迁移密码。 */
    private static final String PASSWORD = "FLOWMESH_MIGRATION_PASSWORD";

    private FlowMeshFlywayMigration() {
        // 工具类不允许实例化。
    }

    /**
     * 判断当前进程是否被要求以迁移模式运行。
     *
     * @return 环境变量明确为 {@code true} 时返回 {@code true}
     */
    public static boolean isRequested() {
        return Boolean.parseBoolean(System.getenv(MIGRATION_MODE));
    }

    /**
     * 执行当前服务的数据库迁移。
     *
     * @param schema 当前服务对应的 PostgreSQL Schema
     * @return 迁移成功返回 {@code 0}，失败返回 {@code 1}
     */
    public static int migrate(String schema) {
        try {
            Flyway.configure()
                    .dataSource(required(JDBC_URL), required(USER), required(PASSWORD))
                    .schemas(schema)
                    .defaultSchema(schema)
                    .locations("classpath:db/migration")
                    .createSchemas(true)
                    .validateOnMigrate(true)
                    .cleanDisabled(true)
                    .outOfOrder(false)
                    .baselineOnMigrate(false)
                    .load()
                    .migrate();
            System.out.printf("FlowMesh Flyway migration succeeded: schema=%s%n", schema);
            return 0;
        } catch (RuntimeException exception) {
            // 不输出连接 URL、账号或异常详情，避免迁移 Job 日志泄露连接信息。
            System.err.printf("FlowMesh Flyway migration failed: schema=%s, error=%s%n",
                    schema, exception.getClass().getSimpleName());
            return 1;
        }
    }

    /**
     * 读取必需的迁移环境变量。
     *
     * @param name 环境变量名称
     * @return 非空环境变量值
     */
    private static String required(String name) {
        String value = System.getenv(name);
        if (value == null || value.isBlank()) {
            throw new IllegalStateException(name + " is required");
        }
        return value;
    }
}
