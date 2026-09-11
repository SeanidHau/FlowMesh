package com.flowmesh.notificationaudit.persistence;

import static org.assertj.core.api.Assertions.assertThat;

import com.flowmesh.notificationaudit.support.PostgresIntegrationTest;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

/**
 * 验证通知审计表启用 RLS，且租户上下文不会串读。
 */
class AuditRlsIntegrationTest extends PostgresIntegrationTest {

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Autowired
    private PlatformTransactionManager transactionManager;

    /**
     * 验证审计事件 Inbox 启用并强制执行 RLS。
     */
    @Test
    void shouldForceRowLevelSecurity() {
        Boolean[] flags = jdbcTemplate.queryForObject(
            "SELECT relrowsecurity, relforcerowsecurity FROM pg_class "
                + "WHERE oid = 'audit.audit_event_inbox'::regclass",
            (resultSet, rowNum) -> new Boolean[] {resultSet.getBoolean(1), resultSet.getBoolean(2)}
        );
        assertThat(flags).containsExactly(true, true);
    }

    /**
     * 验证不同租户无法读取对方的审计 Inbox 记录。
     */
    @Test
    void shouldIsolateAuditInboxByTenant() {
        UUID eventId = UUID.randomUUID();
        UUID applicationId = UUID.randomUUID();
        inTransaction("tenant-a", () -> jdbcTemplate.update(
            "INSERT INTO audit.audit_event_inbox(event_id, tenant_id, aggregate_id) VALUES (?, ?, ?)",
            eventId, "tenant-a", applicationId
        ));

        Integer visibleToOtherTenant = inTransaction("tenant-b", () -> jdbcTemplate.queryForObject(
            "SELECT COUNT(*) FROM audit.audit_event_inbox WHERE event_id = ?",
            Integer.class,
            eventId
        ));
        assertThat(visibleToOtherTenant).isZero();
    }

    /**
     * 验证已读更新同时受数据库 RLS 和用户条件约束保护。
     */
    @Test
    void shouldRestrictNotificationReadUpdateToTenantAndUser() {
        UUID notificationId = UUID.randomUUID();
        UUID sourceEventId = UUID.randomUUID();
        UUID userId = UUID.randomUUID();
        inTransaction("tenant-a", () -> jdbcTemplate.update(
            "INSERT INTO audit.notifications "
                + "(id, source_event_id, tenant_id, recipient_user_id, notification_type, title, content, status) "
                + "VALUES (?, ?, ?, ?, ?, ?, ?, 'UNREAD')",
            notificationId, sourceEventId, "tenant-a", userId,
            "SUPPLIER_ACTIVATED", "title", "content"
        ));

        Integer crossTenantUpdate = inTransaction("tenant-b", () -> jdbcTemplate.update(
            "UPDATE audit.notifications SET status = 'READ' WHERE id = ? AND tenant_id = ? AND recipient_user_id = ?",
            notificationId, "tenant-a", userId
        ));
        assertThat(crossTenantUpdate).isZero();

        Integer ownUserUpdate = inTransaction("tenant-a", () -> jdbcTemplate.update(
            "UPDATE audit.notifications SET status = 'READ', read_at = now() "
                + "WHERE id = ? AND tenant_id = ? AND recipient_user_id = ?",
            notificationId, "tenant-a", userId
        ));
        assertThat(ownUserUpdate).isEqualTo(1);
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
