package com.flowmesh.iam.persistence;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;

/**
 * 验证 IAM 的首个 Flyway 迁移能够创建身份认证所需的表结构。
 *
 * <p>该测试直接查询测试数据库元数据，避免迁移文件因语法或表结构回退而在
 * 认证功能开发阶段才暴露问题。</p>
 */
@SpringBootTest
class IamSchemaMigrationIntegrationTest {

    /** 用于查询迁移后数据库元数据的 JDBC 客户端。 */
    @Autowired
    private JdbcTemplate jdbcTemplate;

    /**
     * 验证身份、授权与刷新令牌相关表和乐观锁列已由 V1 迁移创建。
     */
    @Test
    void shouldCreateIamIdentityAndAuthorizationTables() {
        List<String> tableNames = jdbcTemplate.queryForList(
                """
                SELECT table_name
                FROM information_schema.tables
                WHERE table_schema = 'PUBLIC'
                  AND table_name IN (
                    'TENANTS', 'IAM_USERS', 'IAM_ROLES', 'IAM_USER_ROLES', 'IAM_REFRESH_TOKENS'
                  )
                """,
                String.class
        );

        List<String> versionColumns = jdbcTemplate.queryForList(
                """
                SELECT table_name
                FROM information_schema.columns
                WHERE table_schema = 'PUBLIC'
                  AND column_name = 'VERSION'
                  AND table_name IN ('IAM_USERS', 'IAM_REFRESH_TOKENS')
                """,
                String.class
        );

        assertThat(tableNames).containsExactlyInAnyOrder(
                "TENANTS",
                "IAM_USERS",
                "IAM_ROLES",
                "IAM_USER_ROLES",
                "IAM_REFRESH_TOKENS"
        );
        assertThat(versionColumns).containsExactlyInAnyOrder("IAM_USERS", "IAM_REFRESH_TOKENS");
    }
}
