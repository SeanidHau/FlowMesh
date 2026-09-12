package com.flowmesh.iam.persistence;

import static org.assertj.core.api.Assertions.assertThat;

import com.flowmesh.iam.support.PostgresIntegrationTest;
import com.flowmesh.iam.rls.TenantRlsInitializer;
import java.util.Optional;
import java.util.function.Supplier;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

/**
 * 验证 V3 双租户种子数据正确落地。
 *
 * <p>断言两租户存在、五角色存在、每租户用户存在且 password123 BCrypt 校验通过。</p>
 */
class SeedDataIntegrationTest extends PostgresIntegrationTest {

    /** 用于查询种子数据的 JDBC 客户端。 */
    @Autowired
    private JdbcTemplate jdbcTemplate;

    /** IAM RLS 租户上下文初始化器。 */
    @Autowired
    private TenantRlsInitializer tenantRlsInitializer;

    /** 用于让每个断言在独立事务中设置 RLS 上下文。 */
    @Autowired
    private PlatformTransactionManager transactionManager;

    /**
     * 验证两租户和五角色已由 V3 迁移落地。
     */
    @Test
    void shouldSeedTwoTenantsAndFiveRoles() {
        Integer tenantCount = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM tenants WHERE id IN ('tenant-a', 'tenant-b')",
                Integer.class
        );
        assertThat(tenantCount).isEqualTo(2);

        Integer roleCount = jdbcTemplate.queryForObject(
                """
                SELECT COUNT(*) FROM iam_roles
                WHERE code IN ('APPLICANT','PURCHASER','LEGAL','FINANCE','OPERATIONS')
                """,
                Integer.class
        );
        assertThat(roleCount).isEqualTo(5);
    }

    /**
     * 验证每个租户的演示用户数量和租户内用户名唯一性。
     */
    @Test
    void shouldSeedUsersWithTenantLocalUniqueUsernames() {
        Integer tenantAUsers = queryInTenant("tenant-a", () -> jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM iam_users WHERE tenant_id = 'tenant-a'",
                Integer.class
        ));
        assertThat(tenantAUsers).isGreaterThanOrEqualTo(5);

        Integer tenantBUsers = queryInTenant("tenant-b", () -> jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM iam_users WHERE tenant_id = 'tenant-b'",
                Integer.class
        ));
        assertThat(tenantBUsers).isGreaterThanOrEqualTo(2);

        assertThat(queryInTenant("tenant-a", () -> jdbcTemplate.queryForObject(
                "SELECT COUNT(DISTINCT username) FROM iam_users", Integer.class
        ))).isEqualTo(tenantAUsers);
        assertThat(queryInTenant("tenant-b", () -> jdbcTemplate.queryForObject(
                "SELECT COUNT(DISTINCT username) FROM iam_users", Integer.class
        ))).isEqualTo(tenantBUsers);
    }

    /**
     * 验证种子用户的 password123 BCrypt 哈希可通过校验。
     */
    @Test
    void shouldVerifySeedPasswordHashes() {
        PasswordEncoder encoder = new BCryptPasswordEncoder();

        String applicantHash = queryInTenant("tenant-a", () -> jdbcTemplate.queryForObject(
                "SELECT password_hash FROM iam_users WHERE username = 'applicant-a'",
                String.class
        ));
        assertThat(encoder.matches("password123", applicantHash)).isTrue();

        String operationsHash = queryInTenant("tenant-a", () -> jdbcTemplate.queryForObject(
                "SELECT password_hash FROM iam_users WHERE username = 'operations'",
                String.class
        ));
        assertThat(encoder.matches("password123", operationsHash)).isTrue();

        String applicantBHash = queryInTenant("tenant-b", () -> jdbcTemplate.queryForObject(
                "SELECT password_hash FROM iam_users WHERE username = 'applicant-b'",
                String.class
        ));
        assertThat(encoder.matches("password123", applicantBHash)).isTrue();
    }

    /**
     * 验证用户-角色绑定正确：applicant-a 绑定 APPLICANT，operations 绑定 OPERATIONS。
     */
    @Test
    void shouldSeedUserRoleBindings() {
        Optional<String> applicantRole = Optional.ofNullable(queryInTenant("tenant-a", () ->
                jdbcTemplate.queryForObject(
                        """
                        SELECT r.code FROM iam_user_roles ur
                        JOIN iam_users u ON u.id = ur.user_id
                        JOIN iam_roles r ON r.id = ur.role_id
                        WHERE u.username = 'applicant-a'
                        """,
                        String.class
                )));
        assertThat(applicantRole).hasValue("APPLICANT");

        Optional<String> operationsRole = Optional.ofNullable(queryInTenant("tenant-a", () ->
                jdbcTemplate.queryForObject(
                        """
                        SELECT r.code FROM iam_user_roles ur
                        JOIN iam_users u ON u.id = ur.user_id
                        JOIN iam_roles r ON r.id = ur.role_id
                        WHERE u.username = 'operations'
                        """,
                        String.class
                )));
        assertThat(operationsRole).hasValue("OPERATIONS");
    }

    /**
     * 在独立事务中设置 RLS 上下文并执行租户范围查询。
     *
     * @param tenantId 当前租户
     * @param query 查询逻辑
     * @param <T> 查询结果类型
     * @return 查询结果
     */
    private <T> T queryInTenant(String tenantId, Supplier<T> query) {
        return new TransactionTemplate(transactionManager).execute(status -> {
            tenantRlsInitializer.initialize(tenantId);
            return query.get();
        });
    }
}
