# 运行手册

本手册用于本地 Compose 演示环境。生产部署必须提供真实凭据，并根据实际基础设施补充备份、
网络、高可用和 DLQ 运维入口。

## 前置条件

- 本地运行使用 Docker Compose。
- Kubernetes 演示使用 kind 和 Helm。
- 真实凭据存储在 `.env`、Kubernetes Secret 或 GitHub Actions Secrets，不进入仓库。

生产镜像必须使用完整 Git 提交 SHA 标签。主分支 CI 会先扫描该不可变标签，再生成 Cosign
keyless 签名；部署前可按工作流身份校验签名：

```bash
cosign verify \
  --certificate-identity-regexp 'https://github.com/SeanidHau/FlowMesh/.github/workflows/ci.yml@refs/heads/main' \
  --certificate-oidc-issuer 'https://token.actions.githubusercontent.com' \
  "ghcr.io/seanidhau/flowmesh/iam-service:${GITHUB_SHA}"

# 发布前批量校验六个应用镜像、备份镜像和生命周期维护镜像
FLOWMESH_IMAGE_TAG="${GITHUB_SHA}" ./scripts/verify-flowmesh-images.sh
```

## 启动本地环境

1. 复制环境变量示例并设置 `JWT_SIGNING_KEY`。

   ```bash
   cp .env.example .env
   openssl rand -base64 32
   # 将输出写入 .env 的 JWT_SIGNING_KEY
   ./infra/compose/validate-env.sh .env
   ```

2. 构建并启动基础设施和 Java 服务。

   ```bash
   docker compose --env-file .env -f infra/compose/docker-compose.yml up -d --build
   docker compose --env-file .env -f infra/compose/docker-compose.yml ps
   ```

3. 检查 Gateway 和五个业务服务的健康状态。

   ```bash
   curl -fsS http://localhost:8080/actuator/health
   curl -fsS http://localhost:8081/actuator/health
   curl -fsS http://localhost:8082/actuator/health
   curl -fsS http://localhost:8083/actuator/health
   curl -fsS http://localhost:8084/actuator/health
   curl -fsS http://localhost:8085/actuator/health
   ```

4. 启动 Electron 工作台。

   ```bash
   cd frontend
   npm install
   VITE_DEMO_MODE=true npm run dev
   ```

Compose 中的 Java 服务默认开启 Outbox 和 RocketMQ 消费者，Gateway 监听宿主机 `8080`，MinIO
负责供应商材料对象存储。默认不开启 ClamAV；需要验证完整材料安全链路时执行：

```bash
docker compose --env-file .env -f infra/compose/docker-compose.yml --profile documents up -d --build
```

并将 `.env` 中的 `FLOWMESH_FILE_SCAN_ENABLED` 设置为 `true`。
桌面端默认通过 Gateway 访问宿主机的 `8080` 端口；本地调试时才直接访问
`8081` 至 `8085` 的服务端口。

需要本地查看 Prometheus/Grafana 指标时，可额外启用 `observability` profile，访问
Prometheus `http://localhost:9090` 和 Grafana `http://localhost:3000`：

```bash
docker compose --env-file .env -f infra/compose/docker-compose.yml --profile observability up -d prometheus grafana
```

## OpenTelemetry Trace

Trace 采集和 OTLP 导出默认关闭。只有在目标环境已提供 OpenTelemetry Collector 时，才同时开启以下配置：

```bash
FLOWMESH_TRACING_ENABLED=true
FLOWMESH_OTEL_EXPORT_ENABLED=true
OTEL_EXPORTER_OTLP_TRACES_ENDPOINT=http://otel-collector.observability:4318/v1/traces
```

`FLOWMESH_TRACING_SAMPLING_PROBABILITY` 使用 `0.0` 到 `1.0` 的采样比例。先在非生产环境验证 Collector 接收、Trace 查询和出口 NetworkPolicy，再在生产环境启用。Collector 不可用时，不要把导出失败误判为业务请求失败；应根据平台的丢弃、重试和告警策略处置。

## Refresh Token 生命周期维护

IAM 默认每小时清理已过期或已撤销超过 30 天的 Refresh Token，每次最多处理 1000 条；清理指标为
`flowmesh_iam_refresh_token_cleanup_total`。生产环境可通过 Helm 的
`services.iam.refreshTokenCleanup.retention`、`batchSize` 和 `intervalMs` 调整保留窗口与执行频率，
但不应关闭清理任务。若清理持续积压，应先检查 PostgreSQL 锁等待和连接池，再适当降低批量大小或安排维护窗口。

## Kubernetes 发布后验收

如果需要一次性执行镜像签名、Kubernetes smoke、外部依赖预检和生命周期维护角色预检，并将结果留存为不可覆盖的报告，
在目标生产运维环境执行：

```bash
FLOWMESH_ACCEPTANCE_REPORT="./artifacts/production-acceptance-$(date -u +%Y%m%dT%H%M%SZ).md" \
FLOWMESH_IMAGE_TAG="$GITHUB_SHA" \
FLOWMESH_K8S_NAMESPACE=flowmesh \
FLOWMESH_HELM_RELEASE=flowmesh \
FLOWMESH_EXPECT_PROMETHEUS_RULE=true \
FLOWMESH_REQUIRE_RUNTIME_OBSERVABILITY=true \
./scripts/run-production-acceptance.sh
```

该编排脚本不会替代 PostgreSQL、Redis、RocketMQ 和对象存储的 HA、故障切换、恢复或 RTO/RPO 演练；验收失败时仍会保留报告，
便于发布记录和故障处置。`FLOWMESH_REQUIRE_RUNTIME_OBSERVABILITY=true` 时还要求提供
`FLOWMESH_PROMETHEUS_URL` 和 `FLOWMESH_ALERTMANAGER_URL`，并只读验证观测后端已就绪、六个服务目标可见且关键告警已加载。
默认要求目标集群提供 Prometheus Operator 的 `ServiceMonitor` 和 `PrometheusRule`；如果使用其他监控接入方式，
可显式设置 `FLOWMESH_EXPECT_PROMETHEUS_RULE=false`。脚本会调用生命周期角色预检，因此必须同时提供 `FLOWMESH_RETENTION_DB_PASSWORD` 以及外部依赖预检所需环境变量。

发布完成后，在能够访问目标集群的运维环境执行只读 smoke test：

```bash
FLOWMESH_IMAGE_TAG="$GITHUB_SHA" \
FLOWMESH_K8S_NAMESPACE=flowmesh \
FLOWMESH_HELM_RELEASE=flowmesh \
./tests/kubernetes-production-smoke.sh
```

在同一运维环境、且能够访问外部依赖的网络位置执行只读连接预检。该脚本不会写入任何依赖：

```bash
FLOWMESH_PG_HOST='postgres-primary.database.svc' \
FLOWMESH_PG_USER='flowmesh' \
FLOWMESH_PG_SSLMODE=require \
FLOWMESH_REDIS_HOST='redis-primary.cache.svc' \
FLOWMESH_REDIS_PASSWORD="$REDIS_PASSWORD" \
FLOWMESH_ROCKETMQ_NAMESRV_ADDR='namesrv-0.messaging.svc:9876,namesrv-1.messaging.svc:9876' \
FLOWMESH_OBJECT_STORAGE_ENDPOINT='https://object-storage.example.com' \
./scripts/validate-production-dependencies.sh
```

如果使用私有 CA，为 RocketMQ TLS 预检设置 `FLOWMESH_ROCKETMQ_CA_FILE`；预检通过不代表已经完成多副本
故障切换、备份恢复或 RTO/RPO 验收，这些仍需按目标平台剧本执行并留存结果。

默认检查六个 Deployment、提交 SHA 镜像、PDB、HPA、NetworkPolicy、运行时 Secret（包括
`WORKFLOW_SLA_DB_PASSWORD`）、RocketMQ ACL/TLS、PostgreSQL TLS、PostgreSQL 备份 CronJob、Workflow SLA CronJob、
生命周期清理 Secret 和 CronJob。Prometheus Operator 已安装且启用了对应资源时，增加：

```bash
FLOWMESH_EXPECT_PROMETHEUS_RULE=true \
FLOWMESH_IMAGE_TAG="$GITHUB_SHA" \
./tests/kubernetes-production-smoke.sh
```

smoke test 还会校验每个 Pod 的非 root、只读根文件系统、默认 Seccomp、探针和资源限制；如果生产
Ingress Controller 不在 `ingress-nginx` 命名空间，执行前设置 `FLOWMESH_INGRESS_NAMESPACE` 为实际命名空间。

smoke test 只读取集群状态，不证明 PostgreSQL、Redis、RocketMQ、对象存储已经完成故障切换；这些依赖
仍需按目标平台的 HA、RocketMQ ACL/TLS 连通性和恢复剧本单独演练。

### 应用服务恢复演练

在隔离 Compose 环境执行单服务恢复演练，并把结果保存为不可覆盖的报告：

```bash
FLOWMESH_CHAOS_CONFIRM=YES \
FLOWMESH_DRILL_EXPECTED_RTO_SECONDS=30 \
FLOWMESH_DRILL_REPORT=./artifacts/risk-service-recovery-$(date +%Y%m%d%H%M%S).md \
./tests/fault-drills/verify-service-recovery.sh risk-service http://localhost:8084/actuator/health
```

报告至少应记录服务、健康检查地址、UTC 开始时间、恢复耗时和目标 RTO；执行人还应补充消息积压、错误率、
影响范围和是否触发告警。脚本会先验证停止后健康检查确实失败，再使用单调时钟测量恢复时间；
该演练只验证应用进程停止后的恢复，不代表 PostgreSQL、Redis、RocketMQ 或对象存储已具备故障切换能力。

## 停止与数据卷

停止容器但保留演示数据：

```bash
docker compose --env-file .env -f infra/compose/docker-compose.yml down
```

如果本次只为测试临时启动了 Docker Desktop，容器停止后退出 Docker Desktop：

```bash
osascript -e 'quit app "Docker"'
```

后续需要 Testcontainers 或 Compose 时再启动 Docker Desktop。不要为了停止服务删除数据卷。

只有在确认不再需要本地数据时，才删除 PostgreSQL 和 RocketMQ 数据卷：

```bash
docker compose --env-file .env -f infra/compose/docker-compose.yml down -v
```

删除数据卷不可逆。重新执行 `up` 后，PostgreSQL 才会再次执行初始化脚本。

## 日常检查

1. 检查 IAM、supplier、workflow、RocketMQ 和 PostgreSQL 的健康状态。
2. 检查 Outbox 待投递数量、失败次数和 `dead_lettered_at` 非空记录。
3. 使用 `traceId`、`tenantId`、`applicationId` 或 `eventId` 在日志中定位问题。

### 消息与幂等数据生命周期

生产 Helm 会每日运行 `flowmesh-retention` CronJob。任务使用独立的 `flowmesh_retention` 账号，并按以下默认窗口清理：

- 已发布 Outbox：90 天。
- 已进入 DLQ 的 Outbox：30 天，以 `dead_lettered_at` 计算。
- 重放审计、Inbox 和请求幂等记录：90 天。

任务使用 `FOR UPDATE SKIP LOCKED` 和固定批量上限，多个任务实例不会争抢同一批记录。待发送
Outbox、业务申请、审批快照、`audit_events` 和通知不在清理范围内。任务失败时先检查 CronJob 日志、
数据库锁等待和维护账号权限；不要直接执行未经过评审的删除 SQL。

部署或轮换凭据后，先执行只读角色预检，确认 CronJob 使用的账号与迁移授予的最小权限一致：

```bash
FLOWMESH_PG_HOST=postgres-primary.database.svc \
FLOWMESH_PG_DATABASE=flowmesh \
FLOWMESH_RETENTION_DB_PASSWORD="$RETENTION_DB_PASSWORD" \
./scripts/validate-retention-role.sh
```

预检失败时不得直接放宽权限；应核对 `flowmesh_retention` 的 `NOSUPERUSER`、`NOINHERIT`、
`BYPASSRLS` 属性，以及各服务生命周期迁移是否已在目标数据库执行。

## DLQ 重放

前置条件：操作者拥有 `OPERATIONS` 角色，并已确认消息的业务影响。

1. 在运维页面查看原始事件、失败原因、尝试次数和关联业务状态。
2. 填写重放原因并二次确认。
3. 系统生成新的 `eventId`，同时关联 `originalEventId`。
4. 检查重放后的消费记录、业务状态和审计记录。

不要通过手写 SQL 修改消费记录、Outbox 状态、审批快照或供应商状态。

## 风控故障处置

故障注入默认关闭，只能在隔离演练环境显式开启。`FAIL` 模式立即抛出异常，`TIMEOUT` 模式等待配置的
延迟后抛出异常；两种模式都在写入风控结果前失败，不会制造伪造的风控结论。

```bash
FLOWMESH_RISK_FAULT_INJECTION_ENABLED=true \
FLOWMESH_RISK_FAULT_INJECTION_MODE=FAIL \
docker compose --env-file .env -f infra/compose/docker-compose.yml up -d --build risk-service
```

1. 检查风险事件的 RocketMQ 重试记录和消费者失败指标。
2. 确认 `risk-service` 的故障注入开关仅在隔离演练环境中启用。
3. 如果消息已进入 DLQ，使用 DLQ 重放流程重放，或创建人工风控结论。
4. 演练完成后关闭故障注入并重启 `risk-service`，确认 readiness 恢复。
5. 人工通过、人工拒绝和流程终止必须填写原因。

## 对账处置

1. 查看差异类型、发现时间、关联 `applicationId`、`processInstanceKey` 和 `eventId`。
2. 对于已提交但未投递的 Outbox，允许系统自动重试或由 `OPERATIONS` 确认重放。
3. 对于审批结论或供应商状态差异，创建人工处置任务。不得自动覆盖业务结论。
4. 处置完成后记录前后状态、操作者和验证结果。

## 备份与恢复

PostgreSQL 备份使用 custom format，同时导出角色定义。备份文件必须写入独立、加密且具备
生命周期策略的存储，不得提交到 Git：

```bash
export FLOWMESH_PG_PASSWORD='由 Secret Manager 注入'
export FLOWMESH_BACKUP_ROOT='./backups/postgres'
./scripts/backup-postgres.sh
```

需要上传到 S3 兼容对象存储时，在执行备份前设置上传参数。脚本会在归档创建成功后上传整个时间戳目录；
上传失败时会删除本次未完成的目录，避免把部分归档误当成可恢复备份：

```bash
export FLOWMESH_BACKUP_S3_URI='s3://flowmesh-production-backups'
export AWS_REGION='cn-shanghai'
export FLOWMESH_BACKUP_S3_SSE='aws:kms'
export FLOWMESH_BACKUP_S3_KMS_KEY_ID='alias/flowmesh-backup'
export FLOWMESH_BACKUP_CLEANUP_LOCAL=true
./scripts/backup-postgres.sh
```

生产环境通过 Helm CronJob 调度同一脚本。脚本会在三个归档文件上传成功后再上传 `_SUCCESS` 标记；
CronJob 使用独立备份镜像，禁止同一时间运行多个备份，
并在失败时按 `backoffLimit` 重试。备份凭据应通过 Kubernetes Secret 或云厂商工作负载身份提供，
不能写入 values 文件。

### 材料对象生命周期

材料桶必须是 FlowMesh 专用桶。生产初始化或变更时执行一次生命周期配置：

```bash
FLOWMESH_OBJECT_STORAGE_BUCKET=flowmesh-documents \
FLOWMESH_OBJECT_STORAGE_ENDPOINT='https://object-storage.example.com' \
FLOWMESH_OBJECT_STORAGE_NONCURRENT_RETENTION_DAYS=7 \
./scripts/configure-object-storage-lifecycle.sh
```

脚本会启用版本化，并将非当前版本保留 7 天；应用删除材料时先生成删除标记，生命周期任务再清理历史版本和过期删除标记。
执行前必须确认桶不包含其他业务数据；脚本拒绝非 HTTPS 对象存储 endpoint。跨故障域复制、访问审计和云平台策略验证仍由平台负责。

备份完成后必须在同一台具备 PostgreSQL 客户端工具和 SHA-256 校验工具的机器上校验归档目录。备份脚本会生成
`checksums.sha256`，并使用 `--no-role-passwords` 导出全局对象，避免角色密码进入备份文件。校验步骤同时检查文件摘要和 custom-format 归档目录：

```bash
./scripts/verify-postgres-backup.sh ./backups/postgres/<timestamp>
```

该校验只读取备份文件，不连接数据库；正式恢复仍需在隔离目标库中执行并记录恢复耗时。仓库还提供真实 PostgreSQL 备份恢复回归：

```bash
./tests/postgres-backup-e2e.sh
```

该回归会将探针数据恢复到独立数据库，并在退出时删除临时容器和目录。

恢复必须在隔离的目标数据库执行，并显式确认，避免误覆盖生产数据：

```bash
export FLOWMESH_CONFIRM_RESTORE=YES
export FLOWMESH_PG_PASSWORD='由 Secret Manager 注入'
./scripts/restore-postgres.sh ./backups/postgres/<timestamp>
```

`globals.sql` 包含角色定义和权限信息，必须由数据库管理员审核后执行。RocketMQ 当前仍为单
Broker 演示拓扑；生产环境还需要多 Broker、持久卷、跨故障域部署和恢复演练。
