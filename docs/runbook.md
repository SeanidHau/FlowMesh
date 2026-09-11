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

# 发布前批量校验六个应用镜像
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

备份完成后必须在同一台具备 PostgreSQL 客户端工具和 SHA-256 校验工具的机器上校验归档目录。备份脚本会生成
`checksums.sha256`，校验步骤同时检查文件摘要和 custom-format 归档目录：

```bash
./scripts/verify-postgres-backup.sh ./backups/postgres/<timestamp>
```

该校验只读取备份文件，不连接数据库；正式恢复仍需在隔离目标库中执行并记录恢复耗时。

恢复必须在隔离的目标数据库执行，并显式确认，避免误覆盖生产数据：

```bash
export FLOWMESH_CONFIRM_RESTORE=YES
export FLOWMESH_PG_PASSWORD='由 Secret Manager 注入'
./scripts/restore-postgres.sh ./backups/postgres/<timestamp>
```

`globals.sql` 包含角色定义和权限信息，必须由数据库管理员审核后执行。RocketMQ 当前仍为单
Broker 演示拓扑；生产环境还需要多 Broker、持久卷、跨故障域部署和恢复演练。
