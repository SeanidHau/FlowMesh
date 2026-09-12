package com.flowmesh.iam.persistence;

import static org.assertj.core.api.Assertions.assertThat;

import com.flowmesh.iam.support.PostgresIntegrationTest;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;

/**
 * 验证 IAM 的 Flyway 迁移能够创建身份认证所需的表结构。
 *
 * <p>该测试直接查询 PostgreSQL 的 information_schema，确保迁移在真实 PG 上执行成功。</p>
 */
class IamSchemaMigrationIntegrationTest extends PostgresIntegrationTest {

    /** 用于查询迁移后数据库元数据的 JDBC 客户端。 */
    @Autowired
    private JdbcTemplate jdbcTemplate;

    /**
     * 验证身份、授权与刷新令牌相关表和乐观锁列已由 Flyway 迁移创建。
     */
    @Test
    void shouldCreateIamIdentityAndAuthorizationTables() {
        List<String> tableNames = jdbcTemplate.queryForList(
                """
                SELECT table_name
                FROM information_schema.tables
                WHERE table_schema = 'iam'
                  AND table_name IN (
                    'tenants', 'iam_users', 'iam_roles', 'iam_user_roles', 'iam_refresh_tokens'
                  )
                """,
                String.class
        );

        List<String> versionColumns = jdbcTemplate.queryForList(
                """
                SELECT table_name
                FROM information_schema.columns
                WHERE table_schema = 'iam'
                  AND column_name = 'version'
                  AND table_name IN ('iam_users', 'iam_refresh_tokens')
                """,
                String.class
        );

        assertThat(tableNames).containsExactlyInAnyOrder(
                "tenants",
                "iam_users",
                "iam_roles",
                "iam_user_roles",
                "iam_refresh_tokens"
        );
        assertThat(versionColumns).containsExactlyInAnyOrder("iam_users", "iam_refresh_tokens");

        Integer refreshTenantColumns = jdbcTemplate.queryForObject(
                """
                SELECT COUNT(*)
                FROM information_schema.columns
                WHERE table_schema = 'iam'
                  AND table_name = 'iam_refresh_tokens'
                  AND column_name = 'tenant_id'
                """,
                Integer.class
        );
        assertThat(refreshTenantColumns).isEqualTo(1);
    }

    /**
     * 验证 IAM 的租户数据表均启用并强制执行 PostgreSQL RLS。
     */
    @Test
    void shouldForceRowLevelSecurityForTenantScopedTables() {
        List<String> protectedTables = jdbcTemplate.queryForList(
                """
                SELECT c.relname
                FROM pg_class c
                JOIN pg_namespace n ON n.oid = c.relnamespace
                WHERE n.nspname = 'iam'
                  AND c.relname IN ('iam_users', 'iam_user_roles', 'iam_refresh_tokens', 'iam_audit_events')
                  AND c.relrowsecurity = true
                  AND c.relforcerowsecurity = true
                """,
                String.class
        );

        assertThat(protectedTables).containsExactlyInAnyOrder(
                "iam_users", "iam_user_roles", "iam_refresh_tokens", "iam_audit_events"
        );
    }
}
