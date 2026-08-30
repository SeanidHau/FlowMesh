package com.flowmesh.supplier.rls;

import static org.assertj.core.api.Assertions.assertThat;

import com.flowmesh.supplier.support.PostgresIntegrationTest;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.annotation.Transactional;

/**
 * 验证 supplier 业务表的 RLS 隔离。
 *
 * <p>断言两表 ENABLE+FORCE RLS，并验证跨租户查询返回 0 行。
 * 测试方法使用 @Transactional 保证 set_config 与查询在同一事务内。</p>
 */
@Transactional
class SupplierRlsIsolationIntegrationTest extends PostgresIntegrationTest {

    @Autowired
    private JdbcTemplate jdbcTemplate;

    /**
     * 验证两表启用了 RLS 且 FORCE 生效。
     */
    @Test
    void shouldEnableAndForceRlsOnBusinessTables() {
        Boolean applicationsRls = jdbcTemplate.queryForObject(
                "SELECT relrowsecurity FROM pg_class WHERE relname = 'supplier_applications'",
                Boolean.class
        );
        Boolean applicationsForce = jdbcTemplate.queryForObject(
                "SELECT relforcerowsecurity FROM pg_class WHERE relname = 'supplier_applications'",
                Boolean.class
        );
        Boolean idempotencyRls = jdbcTemplate.queryForObject(
                "SELECT relrowsecurity FROM pg_class WHERE relname = 'supplier_idempotency_keys'",
                Boolean.class
        );
        Boolean idempotencyForce = jdbcTemplate.queryForObject(
                "SELECT relforcerowsecurity FROM pg_class WHERE relname = 'supplier_idempotency_keys'",
                Boolean.class
        );

        assertThat(applicationsRls).isTrue();
        assertThat(applicationsForce).isTrue();
        assertThat(idempotencyRls).isTrue();
        assertThat(idempotencyForce).isTrue();
    }

    /**
     * 验证 set_config app.tenant_id=tenant-a 后，tenant-b 注入的行不可见。
     */
    @Test
    void shouldIsolateCrossTenantDataFromTenantA() {
        UUID tenantBAppId = insertApplicationAs("tenant-b", "跨租户供应商B");

        setTenantContext("tenant-a");

        Integer count = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM supplier_applications",
                Integer.class
        );
        assertThat(count).isZero();

        // 清理
        setTenantContext("tenant-b");
        jdbcTemplate.update("DELETE FROM supplier_applications WHERE id = ?", tenantBAppId);
    }

    /**
     * 验证 set_config app.tenant_id=tenant-b 后，tenant-a 注入的行不可见。
     */
    @Test
    void shouldIsolateCrossTenantDataFromTenantB() {
        UUID tenantAAppId = insertApplicationAs("tenant-a", "跨租户供应商A");

        setTenantContext("tenant-b");

        Integer count = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM supplier_applications",
                Integer.class
        );
        assertThat(count).isZero();

        // 清理
        setTenantContext("tenant-a");
        jdbcTemplate.update("DELETE FROM supplier_applications WHERE id = ?", tenantAAppId);
    }

    private void setTenantContext(String tenantId) {
        jdbcTemplate.queryForObject(
                "SELECT set_config('app.tenant_id', ?, true)",
                String.class,
                tenantId
        );
    }

    private UUID insertApplicationAs(String tenantId, String supplierName) {
        UUID id = UUID.randomUUID();
        setTenantContext(tenantId);
        jdbcTemplate.update(
                """
                INSERT INTO supplier_applications
                    (id, tenant_id, applicant_user_id, supplier_name, status, state_version, created_at, updated_at)
                VALUES (?, ?, ?, ?, 'SUBMITTED', 0, now(), now())
                """,
                id, tenantId, UUID.randomUUID(), supplierName
        );
        return id;
    }
}
