package com.flowmesh.workflow.persistence;

import static org.assertj.core.api.Assertions.assertThat;

import com.flowmesh.workflow.support.PostgresIntegrationTest;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

/**
 * 验证 workflow 核心投影和事件 Inbox 的租户隔离。
 *
 * <p>测试使用非超级用户连接，并在同一事务内设置租户上下文，确保 PostgreSQL 的
 * {@code FORCE ROW LEVEL SECURITY} 真正参与查询和写入。</p>
 */
class WorkflowRlsIntegrationTest extends PostgresIntegrationTest {

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Autowired
    private PlatformTransactionManager transactionManager;

    /**
     * 验证 workflow 的核心业务表和事件 Inbox 均启用并强制执行 RLS。
     */
    @Test
    void shouldForceRowLevelSecurityOnWorkflowTables() {
        assertRls("workflow.workflow_instances");
        assertRls("workflow.workflow_tasks");
        assertRls("workflow.workflow_risk_event_inbox");
        assertRls("workflow.workflow_supplement_event_inbox");
        assertPolicyHasWriteCheck("workflow_instances", "tenant_isolation_workflow_instances");
    }

    /**
     * 验证 tenant-b 无法读取 tenant-a 创建的 workflow 实例。
     */
    @Test
    void shouldIsolateWorkflowInstancesByTenant() {
        UUID instanceId = UUID.randomUUID();
        UUID applicationId = UUID.randomUUID();
        UUID sourceEventId = UUID.randomUUID();

        inTransaction("tenant-a", () -> jdbcTemplate.update(
            "INSERT INTO workflow.workflow_instances "
                + "(id, application_id, source_event_id, tenant_id, process_definition_key, "
                + "status, current_task, version, created_at, review_round) "
                + "VALUES (?, ?, ?, ?, 'supplier-onboarding', 'IN_PROGRESS', "
                + "'PURCHASER_REVIEW', 0, now(), 1)",
            instanceId, applicationId, sourceEventId, "tenant-a"
        ));

        Integer visibleToOtherTenant = inTransaction("tenant-b", () -> jdbcTemplate.queryForObject(
            "SELECT COUNT(*) FROM workflow.workflow_instances WHERE id = ?",
            Integer.class,
            instanceId
        ));
        assertThat(visibleToOtherTenant).isZero();

        inTransaction("tenant-a", () -> jdbcTemplate.update(
            "DELETE FROM workflow.workflow_instances WHERE id = ?", instanceId
        ));
    }

    /**
     * 验证 tenant-b 无法读取 tenant-a 创建的流程任务。
     */
    @Test
    void shouldIsolateWorkflowTasksByTenant() {
        UUID instanceId = UUID.randomUUID();
        UUID applicationId = UUID.randomUUID();
        UUID sourceEventId = UUID.randomUUID();
        UUID taskId = UUID.randomUUID();

        inTransaction("tenant-a", () -> {
            jdbcTemplate.update(
                "INSERT INTO workflow.workflow_instances "
                    + "(id, application_id, source_event_id, tenant_id, process_definition_key, "
                    + "status, current_task, version, created_at, review_round) "
                    + "VALUES (?, ?, ?, ?, 'supplier-onboarding', 'IN_PROGRESS', "
                    + "'PURCHASER_REVIEW', 0, now(), 1)",
                instanceId, applicationId, sourceEventId, "tenant-a"
            );
            jdbcTemplate.update(
                "INSERT INTO workflow.workflow_tasks "
                    + "(id, workflow_instance_id, tenant_id, application_id, task_key, status, "
                    + "created_at, round_no) VALUES (?, ?, ?, ?, 'PURCHASER_REVIEW', 'PENDING', now(), 1)",
                taskId, instanceId, "tenant-a", applicationId
            );
            return null;
        });

        Integer visibleToOtherTenant = inTransaction("tenant-b", () -> jdbcTemplate.queryForObject(
            "SELECT COUNT(*) FROM workflow.workflow_tasks WHERE id = ?",
            Integer.class,
            taskId
        ));
        assertThat(visibleToOtherTenant).isZero();

        inTransaction("tenant-a", () -> jdbcTemplate.update(
            "DELETE FROM workflow.workflow_tasks WHERE id = ?", taskId
        ));
        inTransaction("tenant-a", () -> jdbcTemplate.update(
            "DELETE FROM workflow.workflow_instances WHERE id = ?", instanceId
        ));
    }

    private void assertRls(String tableName) {
        Boolean[] flags = jdbcTemplate.queryForObject(
            "SELECT relrowsecurity, relforcerowsecurity FROM pg_class "
                + "WHERE oid = ?::regclass",
            (resultSet, rowNum) -> new Boolean[] {resultSet.getBoolean(1), resultSet.getBoolean(2)},
            tableName
        );
        assertThat(flags).as(tableName).containsExactly(true, true);
    }

    private void assertPolicyHasWriteCheck(String tableName, String policyName) {
        String withCheck = jdbcTemplate.queryForObject(
            "SELECT with_check FROM pg_policies "
                + "WHERE schemaname = 'workflow' AND tablename = ? AND policyname = ?",
            String.class,
            tableName,
            policyName
        );
        assertThat(withCheck).as(policyName).contains("app.tenant_id");
    }

    private <T> T inTransaction(String tenantId, java.util.function.Supplier<T> action) {
        return new TransactionTemplate(transactionManager).execute(status -> {
            jdbcTemplate.queryForObject(
                "SELECT set_config('app.tenant_id', ?, true)", String.class, tenantId
            );
            return action.get();
        });
    }
}
