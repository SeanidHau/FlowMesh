#!/usr/bin/env bash

# 作用：在临时 PostgreSQL 容器中验证生命周期清理的终态判断、RLS 绕过边界、批量删除和保留窗口。
# 脚本只操作临时容器，退出时删除容器和临时目录。

set -Eeuo pipefail

root_dir="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
container="flowmesh-postgres-retention-e2e-$$"
password="flowmesh-retention-e2e-password"

cleanup() {
  local status=$?
  docker rm -f "${container}" >/dev/null 2>&1 || true
  exit "${status}"
}
trap cleanup EXIT

docker run --detach --name "${container}" \
  --env POSTGRES_PASSWORD=postgres-password \
  --env POSTGRES_DB=flowmesh \
  --volume "${root_dir}:/workspace:ro" \
  postgres:16 >/dev/null

for attempt in $(seq 1 60); do
  if docker exec "${container}" psql -v ON_ERROR_STOP=1 -U postgres -d flowmesh \
    -c 'SELECT 1' >/dev/null 2>&1; then
    break
  fi
  if [[ "${attempt}" -eq 60 ]]; then
    echo 'PostgreSQL 生命周期测试容器未能就绪。' >&2
    exit 1
  fi
  sleep 1
done

docker exec --interactive "${container}" psql -v ON_ERROR_STOP=1 -U postgres -d flowmesh <<'SQL'
CREATE ROLE flowmesh_retention LOGIN PASSWORD 'flowmesh-retention-e2e-password'
  NOSUPERUSER NOCREATEDB NOCREATEROLE NOINHERIT BYPASSRLS;
CREATE SCHEMA supplier;
CREATE SCHEMA workflow;
CREATE SCHEMA risk;
CREATE SCHEMA audit;

CREATE TABLE supplier.supplier_outbox_events (
  id UUID PRIMARY KEY, published_at TIMESTAMPTZ, dead_lettered_at TIMESTAMPTZ,
  created_at TIMESTAMPTZ NOT NULL
);
CREATE TABLE supplier.supplier_outbox_replay_audits (
  id UUID PRIMARY KEY, created_at TIMESTAMPTZ NOT NULL
);
CREATE TABLE supplier.supplier_workflow_event_inbox (
  event_id UUID PRIMARY KEY, tenant_id TEXT NOT NULL, created_at TIMESTAMPTZ NOT NULL
);
CREATE TABLE supplier.supplier_idempotency_keys (
  id UUID PRIMARY KEY, tenant_id TEXT NOT NULL, created_at TIMESTAMPTZ NOT NULL
);
CREATE TABLE workflow.workflow_outbox_events (
  id UUID PRIMARY KEY, published_at TIMESTAMPTZ, dead_lettered_at TIMESTAMPTZ,
  created_at TIMESTAMPTZ NOT NULL
);
CREATE TABLE workflow.workflow_outbox_replay_audits (
  id UUID PRIMARY KEY, created_at TIMESTAMPTZ NOT NULL
);
CREATE TABLE workflow.workflow_risk_event_inbox (
  event_id UUID PRIMARY KEY, tenant_id TEXT NOT NULL, created_at TIMESTAMPTZ NOT NULL
);
CREATE TABLE risk.risk_outbox_events (
  id UUID PRIMARY KEY, published_at TIMESTAMPTZ, dead_lettered_at TIMESTAMPTZ,
  created_at TIMESTAMPTZ NOT NULL
);
CREATE TABLE audit.audit_event_inbox (
  event_id UUID PRIMARY KEY, tenant_id TEXT NOT NULL, created_at TIMESTAMPTZ NOT NULL
);

ALTER TABLE supplier.supplier_workflow_event_inbox ENABLE ROW LEVEL SECURITY;
ALTER TABLE supplier.supplier_workflow_event_inbox FORCE ROW LEVEL SECURITY;
ALTER TABLE workflow.workflow_risk_event_inbox ENABLE ROW LEVEL SECURITY;
ALTER TABLE workflow.workflow_risk_event_inbox FORCE ROW LEVEL SECURITY;
ALTER TABLE audit.audit_event_inbox ENABLE ROW LEVEL SECURITY;
ALTER TABLE audit.audit_event_inbox FORCE ROW LEVEL SECURITY;
CREATE POLICY supplier_tenant_policy ON supplier.supplier_workflow_event_inbox
  USING (tenant_id = current_setting('app.tenant_id', true));
CREATE POLICY workflow_tenant_policy ON workflow.workflow_risk_event_inbox
  USING (tenant_id = current_setting('app.tenant_id', true));
CREATE POLICY audit_tenant_policy ON audit.audit_event_inbox
  USING (tenant_id = current_setting('app.tenant_id', true));

GRANT USAGE ON SCHEMA supplier, workflow, risk, audit TO flowmesh_retention;
GRANT SELECT, DELETE ON supplier.supplier_outbox_events,
  supplier.supplier_outbox_replay_audits,
  supplier.supplier_workflow_event_inbox,
  supplier.supplier_idempotency_keys,
  workflow.workflow_outbox_events,
  workflow.workflow_outbox_replay_audits,
  workflow.workflow_risk_event_inbox,
  risk.risk_outbox_events,
  audit.audit_event_inbox TO flowmesh_retention;
GRANT UPDATE (id) ON supplier.supplier_outbox_events,
  supplier.supplier_outbox_replay_audits,
  supplier.supplier_idempotency_keys,
  workflow.workflow_outbox_events,
  workflow.workflow_outbox_replay_audits,
  risk.risk_outbox_events TO flowmesh_retention;
GRANT UPDATE (event_id) ON supplier.supplier_workflow_event_inbox,
  workflow.workflow_risk_event_inbox,
  audit.audit_event_inbox TO flowmesh_retention;

INSERT INTO supplier.supplier_outbox_events VALUES
  ('00000000-0000-0000-0000-000000000001', now() - interval '100 days', NULL, now() - interval '100 days'),
  ('00000000-0000-0000-0000-000000000002', NULL, now() - interval '40 days', now() - interval '40 days'),
  ('00000000-0000-0000-0000-000000000003', NULL, NULL, now() - interval '100 days'),
  ('00000000-0000-0000-0000-000000000004', now() - interval '10 days', NULL, now() - interval '10 days');
INSERT INTO supplier.supplier_outbox_replay_audits VALUES
  ('00000000-0000-0000-0000-000000000011', now() - interval '100 days');
INSERT INTO supplier.supplier_workflow_event_inbox VALUES
  ('00000000-0000-0000-0000-000000000021', 'tenant-a', now() - interval '100 days');
INSERT INTO supplier.supplier_idempotency_keys VALUES
  ('00000000-0000-0000-0000-000000000031', 'tenant-a', now() - interval '100 days');

INSERT INTO workflow.workflow_outbox_events VALUES
  ('00000000-0000-0000-0000-000000000041', now() - interval '100 days', NULL, now() - interval '100 days'),
  ('00000000-0000-0000-0000-000000000042', NULL, now() - interval '40 days', now() - interval '40 days'),
  ('00000000-0000-0000-0000-000000000043', NULL, NULL, now() - interval '100 days'),
  ('00000000-0000-0000-0000-000000000044', now() - interval '10 days', NULL, now() - interval '10 days');
INSERT INTO workflow.workflow_outbox_replay_audits VALUES
  ('00000000-0000-0000-0000-000000000051', now() - interval '100 days');
INSERT INTO workflow.workflow_risk_event_inbox VALUES
  ('00000000-0000-0000-0000-000000000061', 'tenant-a', now() - interval '100 days');

INSERT INTO risk.risk_outbox_events VALUES
  ('00000000-0000-0000-0000-000000000071', now() - interval '100 days', NULL, now() - interval '100 days'),
  ('00000000-0000-0000-0000-000000000072', NULL, now() - interval '40 days', now() - interval '40 days'),
  ('00000000-0000-0000-0000-000000000073', NULL, NULL, now() - interval '100 days'),
  ('00000000-0000-0000-0000-000000000074', now() - interval '10 days', NULL, now() - interval '10 days');
INSERT INTO audit.audit_event_inbox VALUES
  ('00000000-0000-0000-0000-000000000081', 'tenant-a', now() - interval '100 days');
SQL

docker exec \
  --env FLOWMESH_RETENTION_CONFIRM=YES \
  --env FLOWMESH_RETENTION_DB_PASSWORD="${password}" \
  --env FLOWMESH_PG_HOST=127.0.0.1 \
  --env FLOWMESH_PG_PORT=5432 \
  --env FLOWMESH_PG_DATABASE=flowmesh \
  --env FLOWMESH_PG_SSLMODE=disable \
  --env FLOWMESH_RETENTION_DB_USER=flowmesh_retention \
  "${container}" bash -c 'cd /workspace && ./scripts/validate-retention-role.sh && ./scripts/cleanup-flowmesh-retention.sh'

assert_count() {
  local table="$1"
  local expected="$2"
  local actual
  actual="$(docker exec "${container}" psql -At -U postgres -d flowmesh \
    -c "SELECT count(*) FROM ${table}")"
  if [[ "${actual}" != "${expected}" ]]; then
    echo "${table} 清理结果错误，期望 ${expected}，实际 ${actual}。" >&2
    exit 1
  fi
}

assert_count supplier.supplier_outbox_events 2
assert_count supplier.supplier_outbox_replay_audits 0
assert_count supplier.supplier_workflow_event_inbox 0
assert_count supplier.supplier_idempotency_keys 0
assert_count workflow.workflow_outbox_events 2
assert_count workflow.workflow_outbox_replay_audits 0
assert_count workflow.workflow_risk_event_inbox 0
assert_count risk.risk_outbox_events 2
assert_count audit.audit_event_inbox 0

echo 'PostgreSQL retention cleanup E2E passed.'
