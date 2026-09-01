#!/usr/bin/env bash

# 作用：使用真实 RocketMQ Broker 验证 supplier -> workflow -> supplier 主链路。
# 脚本只管理本次 Compose 项目创建的基础设施容器，退出时删除临时数据卷。

set -Eeuo pipefail

ROOT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
COMPOSE_FILE="${ROOT_DIR}/infra/compose/docker-compose.yml"
ENV_FILE="$(mktemp "${TMPDIR:-/tmp}/flowmesh-e2e.XXXXXX.env")"
BROKER_CONFIG="$(mktemp "${TMPDIR:-/tmp}/flowmesh-e2e-broker.XXXXXX.conf")"
COMPOSE_PROJECT="flowmesh-e2e-$$"
COMPOSE_ARGS=(-p "${COMPOSE_PROJECT}" --env-file "${ENV_FILE}" -f "${COMPOSE_FILE}")
APP_PIDS=()
LOG_DIR="$(mktemp -d "${TMPDIR:-/tmp}/flowmesh-e2e-logs.XXXXXX")"

cleanup() {
  for pid in "${APP_PIDS[@]}"; do
    kill "${pid}" >/dev/null 2>&1 || true
  done
  for pid in "${APP_PIDS[@]}"; do
    wait "${pid}" >/dev/null 2>&1 || true
  done
  docker compose "${COMPOSE_ARGS[@]}" down --remove-orphans -v >/dev/null 2>&1 || true
  rm -f "${ENV_FILE}"
  rm -f "${BROKER_CONFIG}"
  rm -rf "${LOG_DIR}"
}
trap cleanup EXIT

JWT_KEY="$(openssl rand -base64 32 | tr -d '\n')"
sed 's/brokerIP1 = rocketmq-broker/brokerIP1 = 127.0.0.1/' \
  "${ROOT_DIR}/infra/compose/rocketmq/broker.conf" > "${BROKER_CONFIG}"
cat > "${ENV_FILE}" <<EOF
POSTGRES_USER=flowmesh
POSTGRES_PASSWORD=flowmesh-e2e-postgres
POSTGRES_DB=flowmesh
IAM_DB_PASSWORD=flowmesh-e2e-iam
SUPPLIER_DB_PASSWORD=flowmesh-e2e-supplier
WORKFLOW_DB_PASSWORD=flowmesh-e2e-workflow
JWT_SIGNING_KEY=${JWT_KEY}
JWT_ISSUER=flowmesh-e2e
FLOWMESH_OUTBOX_ENABLED=true
FLOWMESH_SUPPLIER_CONSUMER_ENABLED=true
FLOWMESH_WORKFLOW_OUTBOX_ENABLED=true
FLOWMESH_WORKFLOW_CONSUMER_ENABLED=true
FLOWMESH_DEMO_DATA_ENABLED=true
ROCKETMQ_BROKER_CONFIG=${BROKER_CONFIG}
EOF

json_field() {
  python3 -c 'import json, sys; print(json.load(sys.stdin)[sys.argv[1]])' "$1"
}

wait_for_url() {
  local url="$1"
  local attempts=0
  until curl --fail --silent "$url" >/dev/null 2>&1; do
    attempts=$((attempts + 1))
    if [ "$attempts" -ge 60 ]; then
      echo "等待服务超时：${url}" >&2
      return 1
    fi
    sleep 2
  done
}

start_service() {
  local service="$1"
  local jar="$2"
  shift 2
  env "$@" java -jar "${ROOT_DIR}/${jar}" >"${LOG_DIR}/${service}.log" 2>&1 &
  APP_PIDS+=("$!")
}

login() {
  local username="$1"
  curl --fail --silent --show-error \
    -H 'Content-Type: application/json' \
    -d "{\"tenantId\":\"tenant-a\",\"username\":\"${username}\",\"password\":\"password123\"}" \
    http://localhost:8081/api/v1/auth/login | json_field accessToken
}

echo "打包三个 Java 服务并启动 PostgreSQL、RocketMQ..."
./mvnw -q -DskipTests package
docker compose "${COMPOSE_ARGS[@]}" up -d postgres rocketmq-namesrv rocketmq-volume-init rocketmq-broker
sleep 10

start_service "iam" "services/iam-service/target/iam-service-0.1.0-SNAPSHOT.jar" \
  SPRING_DATASOURCE_URL="jdbc:postgresql://localhost:5432/flowmesh?currentSchema=iam" \
  IAM_DB_USER=flowmesh_iam IAM_DB_PASSWORD=flowmesh-e2e-iam \
  JWT_ISSUER=flowmesh-e2e JWT_SIGNING_KEY="${JWT_KEY}" FLOWMESH_DEMO_DATA_ENABLED=true
start_service "supplier" "services/supplier-service/target/supplier-service-0.1.0-SNAPSHOT.jar" \
  SPRING_DATASOURCE_URL="jdbc:postgresql://localhost:5432/flowmesh?currentSchema=supplier" \
  SUPPLIER_DB_USER=flowmesh_supplier SUPPLIER_DB_PASSWORD=flowmesh-e2e-supplier \
  JWT_ISSUER=flowmesh-e2e JWT_SIGNING_KEY="${JWT_KEY}" ROCKETMQ_NAMESRV_ADDR=localhost:9876 \
  FLOWMESH_OUTBOX_ENABLED=true FLOWMESH_SUPPLIER_CONSUMER_ENABLED=true \
  FLOWMESH_WORKFLOW_BASE_URL=http://localhost:8083
start_service "workflow" "services/workflow-service/target/workflow-service-0.1.0-SNAPSHOT.jar" \
  SPRING_DATASOURCE_URL="jdbc:postgresql://localhost:5432/flowmesh?currentSchema=workflow" \
  WORKFLOW_DB_USER=flowmesh_workflow WORKFLOW_DB_PASSWORD=flowmesh-e2e-workflow \
  JWT_ISSUER=flowmesh-e2e JWT_SIGNING_KEY="${JWT_KEY}" ROCKETMQ_NAMESRV_ADDR=localhost:9876 \
  FLOWMESH_WORKFLOW_OUTBOX_ENABLED=true FLOWMESH_WORKFLOW_CONSUMER_ENABLED=true

wait_for_url http://localhost:8081/actuator/health
wait_for_url http://localhost:8082/actuator/health
wait_for_url http://localhost:8083/actuator/health
wait_for_url http://localhost:8081/actuator/health/liveness
wait_for_url http://localhost:8082/actuator/health/readiness
wait_for_url http://localhost:8083/actuator/health/readiness

APPLICANT_TOKEN="$(login applicant-a)"
PURCHASER_TOKEN="$(login purchaser-a)"
LEGAL_TOKEN="$(login legal-a)"
FINANCE_TOKEN="$(login finance-a)"
OPERATIONS_TOKEN="$(login operations)"

APPLICATION_ID="$(curl --fail --silent --show-error \
  -H "Authorization: Bearer ${APPLICANT_TOKEN}" \
  -H 'Content-Type: application/json' \
  -H 'Idempotency-Key: rocketmq-e2e-application' \
  -d '{"supplierName":"RocketMQ E2E Supplier"}' \
  http://localhost:8082/api/v1/supplier-applications | json_field id)"

REPLAYED_APPLICATION_ID="$(curl --fail --silent --show-error \
  -H "Authorization: Bearer ${APPLICANT_TOKEN}" \
  -H 'Content-Type: application/json' \
  -H 'Idempotency-Key: rocketmq-e2e-application' \
  -d '{"supplierName":"RocketMQ E2E Supplier"}' \
  http://localhost:8082/api/v1/supplier-applications | json_field id)"
if [ "${APPLICATION_ID}" != "${REPLAYED_APPLICATION_ID}" ]; then
  echo "幂等请求没有返回同一个申请标识。" >&2
  exit 1
fi

WORKFLOW_URL="http://localhost:8083/api/v1/workflow-instances/${APPLICATION_ID}"
for attempt in $(seq 1 60); do
  if curl --fail --silent --show-error -H "Authorization: Bearer ${PURCHASER_TOKEN}" "${WORKFLOW_URL}" >/dev/null; then
    break
  fi
  if [ "$attempt" -eq 60 ]; then
    echo "RocketMQ 未能将申请投影为 workflow 实例。" >&2
    exit 1
  fi
  sleep 2
done

complete_task() {
  local token="$1"
  curl --fail --silent --show-error \
    -H "Authorization: Bearer ${token}" \
    -H 'Content-Type: application/json' \
    -d "{\"taskKey\":\"$2\"}" \
    -X POST "${WORKFLOW_URL}/tasks" >/dev/null
}

complete_task "${PURCHASER_TOKEN}" PURCHASER_REVIEW
complete_task "${LEGAL_TOKEN}" LEGAL_REVIEW
complete_task "${FINANCE_TOKEN}" FINANCE_REVIEW
complete_task "${OPERATIONS_TOKEN}" OPERATIONS_ACTIVATION

for attempt in $(seq 1 60); do
  application_status="$(curl --fail --silent --show-error \
    -H "Authorization: Bearer ${APPLICANT_TOKEN}" \
    "http://localhost:8082/api/v1/supplier-applications/${APPLICATION_ID}" | json_field status)"
  if [ "${application_status}" = "ENABLED" ]; then
    break
  fi
  if [ "$attempt" -eq 60 ]; then
    echo "Workflow 完成事件未能驱动 supplier 进入 ENABLED。当前状态：${application_status}" >&2
    exit 1
  fi
  sleep 2
done

DEAD_LETTER_ID="$(python3 -c 'import uuid; print(uuid.uuid4())')"
docker compose "${COMPOSE_ARGS[@]}" exec -T postgres psql -U flowmesh -d flowmesh \
  -v ON_ERROR_STOP=1 -c "
    INSERT INTO supplier.supplier_outbox_events
      (id, tenant_id, aggregate_id, topic, tag, payload, attempt_count, last_error,
       published_at, next_attempt_at, claimed_until, claim_token, dead_lettered_at,
       original_event_id, created_at)
    VALUES (
      '${DEAD_LETTER_ID}'::uuid, 'tenant-a', '${APPLICATION_ID}'::uuid, 'supplier-events', 'SupplierActivated',
      jsonb_build_object(
        'eventId', '${DEAD_LETTER_ID}', 'eventType', 'SupplierActivated', 'schemaVersion', 1,
        'tenantId', 'tenant-a', 'aggregateId', '${APPLICATION_ID}',
        'occurredAt', now(), 'traceId', 'rocketmq-e2e'
      ), 5, 'seeded dead-letter for E2E', NULL, now(), NULL, NULL, now(), NULL, now()
    )" >/dev/null

DEAD_LETTERS="$(curl --fail --silent --show-error \
  -H "Authorization: Bearer ${OPERATIONS_TOKEN}" \
  "http://localhost:8082/api/v1/operations/outbox/dead-letters?eventType=SupplierActivated&aggregateId=${APPLICATION_ID}")"
if ! printf '%s' "${DEAD_LETTERS}" | python3 -c \
  'import json, sys; expected=sys.argv[1]; assert any(item["eventId"] == expected for item in json.load(sys.stdin))' "${DEAD_LETTER_ID}"; then
  echo "死信查询没有返回 E2E 注入的事件。" >&2
  exit 1
fi

REPLAY_RESPONSE="$(curl --fail --silent --show-error \
  -H "Authorization: Bearer ${OPERATIONS_TOKEN}" \
  -H "X-Trace-Id: rocketmq-e2e-replay" \
  -H 'Content-Type: application/json' \
  -d '{"reason":"验证受控重放和审计链路"}' \
  -X POST "http://localhost:8082/api/v1/operations/outbox/${DEAD_LETTER_ID}/replay")"
REPLAY_EVENT_ID="$(printf '%s' "${REPLAY_RESPONSE}" | json_field replayEventId)"
if [ "${REPLAY_EVENT_ID}" = "${DEAD_LETTER_ID}" ]; then
  echo "重放没有生成新的 eventId。" >&2
  exit 1
fi
AUDIT_COUNT="$(docker compose "${COMPOSE_ARGS[@]}" exec -T postgres psql -U flowmesh -d flowmesh -At \
  -c "SELECT COUNT(*) FROM supplier.supplier_outbox_replay_audits WHERE original_event_id = '${DEAD_LETTER_ID}'::uuid AND replay_event_id = '${REPLAY_EVENT_ID}'::uuid" | tr -d '[:space:]')"
if [ "${AUDIT_COUNT}" != "1" ]; then
  echo "重放审计记录数量不正确：${AUDIT_COUNT}" >&2
  exit 1
fi

RECONCILIATION_RESULT="$(curl --fail --silent --show-error \
  -H "Authorization: Bearer ${OPERATIONS_TOKEN}" \
  "http://localhost:8082/api/v1/operations/reconciliation/${APPLICATION_ID}")"
if [ "$(printf '%s' "${RECONCILIATION_RESULT}" | json_field consistent)" != "True" ]; then
  echo "完成后的跨服务对账未达到一致状态：${RECONCILIATION_RESULT}" >&2
  exit 1
fi
curl --fail --silent --show-error http://localhost:8082/actuator/prometheus \
  | grep -q 'flowmesh_outbox_pending'
curl --fail --silent --show-error http://localhost:8083/actuator/prometheus \
  | grep -q 'flowmesh_messaging_consumed'

echo "RocketMQ E2E 通过：${APPLICATION_ID} 已完成四级审批并进入 ENABLED。"
