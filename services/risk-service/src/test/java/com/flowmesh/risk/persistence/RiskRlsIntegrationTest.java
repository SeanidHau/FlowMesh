package com.flowmesh.risk.persistence;

import static org.assertj.core.api.Assertions.assertThat;

import com.flowmesh.risk.support.PostgresIntegrationTest;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

/**
 * 验证 risk 迁移已启用 RLS，且业务账号无法读取其他租户的数据。
 */
class RiskRlsIntegrationTest extends PostgresIntegrationTest {

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Autowired
    private PlatformTransactionManager transactionManager;

    /**
     * 验证表启用并强制执行 RLS。
     */
    @Test
    void shouldForceRowLevelSecurity() {
        Boolean[] flags = jdbcTemplate.queryForObject(
            "SELECT relrowsecurity, relforcerowsecurity FROM pg_class "
                + "WHERE oid = 'risk.risk_results'::regclass",
            (resultSet, rowNum) -> new Boolean[] {resultSet.getBoolean(1), resultSet.getBoolean(2)}
        );
        assertThat(flags).containsExactly(true, true);
    }

    /**
     * 验证租户上下文只允许读取当前租户风险结果。
     */
    @Test
    void shouldIsolateRiskResultsByTenant() {
        UUID applicationId = UUID.randomUUID();
        inTransaction("tenant-a", () -> jdbcTemplate.update(
            "INSERT INTO risk.risk_results "
                + "(id, tenant_id, application_id, decision, reason, created_at) "
                + "VALUES (?, ?, ?, 'PASS', 'test', now())",
            UUID.randomUUID(), "tenant-a", applicationId
        ));

        Integer visibleToOtherTenant = inTransaction("tenant-b", () -> jdbcTemplate.queryForObject(
            "SELECT COUNT(*) FROM risk.risk_results WHERE application_id = ?",
            Integer.class,
            applicationId
        ));
        assertThat(visibleToOtherTenant).isZero();
    }

    private <T> T inTransaction(String tenantId, java.util.function.Supplier<T> action) {
        return new TransactionTemplate(transactionManager).execute(status -> {
            jdbcTemplate.queryForObject(
                "SELECT set_config('app.tenant_id', ?, true)", String.class, tenantId
            );
            return action.get();
        });
    }

    private void inTransaction(String tenantId, Runnable action) {
        new TransactionTemplate(transactionManager).executeWithoutResult(status -> {
            jdbcTemplate.queryForObject(
                "SELECT set_config('app.tenant_id', ?, true)", String.class, tenantId
            );
            action.run();
        });
    }
}
