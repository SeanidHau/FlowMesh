# 运行手册

本手册用于本地 Compose 演示环境。生产部署需要替换默认凭据，并根据实际基础设施补充备份、
网络和高可用配置。

## 前置条件

- 本地运行使用 Docker Compose。
- Kubernetes 演示使用 kind 和 Helm。
- 真实凭据存储在 `.env`、Kubernetes Secret 或 GitHub Actions Secrets，不进入仓库。

## 启动本地环境

1. 复制环境变量示例并设置 `JWT_SIGNING_KEY`。

   ```bash
   cp .env.example .env
   openssl rand -base64 32
   # 将输出写入 .env 的 JWT_SIGNING_KEY
   ```

2. 构建并启动基础设施和 Java 服务。

   ```bash
   docker compose --env-file .env -f infra/compose/docker-compose.yml up -d --build
   docker compose --env-file .env -f infra/compose/docker-compose.yml ps
   ```

3. 检查三个服务的健康状态。

   ```bash
   curl -fsS http://localhost:8081/actuator/health
   curl -fsS http://localhost:8082/actuator/health
   curl -fsS http://localhost:8083/actuator/health
   ```

4. 启动 Electron 工作台。

   ```bash
   cd frontend
   npm install
   npm run dev
   ```

Compose 中的 Java 服务默认开启 Outbox 和 RocketMQ 消费者。桌面端默认访问宿主机的
`8081`、`8082` 和 `8083` 端口。

## 停止与数据卷

停止容器但保留演示数据：

```bash
docker compose --env-file .env -f infra/compose/docker-compose.yml down
```

只有在确认不再需要本地数据时，才删除 PostgreSQL 和 RocketMQ 数据卷：

```bash
docker compose --env-file .env -f infra/compose/docker-compose.yml down -v
```

删除数据卷不可逆。重新执行 `up` 后，PostgreSQL 才会再次执行初始化脚本。

## 日常检查

1. 检查 Gateway、各业务服务、Camunda、RocketMQ、PostgreSQL、Redis 和 MinIO 的健康状态。
2. 检查 Outbox 待投递数量、RocketMQ 消费失败数和 DLQ 数量。
3. 检查状态对账差异、超过 SLA 的审批任务和 `PENDING` 审批命令。
4. 使用 `traceId`、`tenantId`、`applicationId` 或 `eventId` 在日志和 Trace 中定位问题。

## DLQ 重放

前置条件：操作者拥有 `OPERATIONS` 角色，并已确认消息的业务影响。

1. 在运维页面查看原始事件、失败原因、尝试次数和关联业务状态。
2. 填写重放原因并二次确认。
3. 系统生成新的 `eventId`，同时关联 `originalEventId`。
4. 检查重放后的消费记录、业务状态和审计记录。

不要通过手写 SQL 修改消费记录、Outbox 状态、审批快照或供应商状态。

## 风控故障处置

1. 检查风险事件的三次延迟重试记录。
2. 确认 `risk-service` 的故障注入开关是否仍处于启用状态。
3. 如果消息已进入 DLQ，使用 DLQ 重放流程重放，或创建人工风控结论。
4. 人工通过、人工拒绝和流程终止必须填写原因。

## 对账处置

1. 查看差异类型、发现时间、关联 `applicationId`、`processInstanceKey` 和 `eventId`。
2. 对于已提交但未投递的 Outbox，允许系统自动重试或由 `OPERATIONS` 确认重放。
3. 对于审批结论或供应商状态差异，创建人工处置任务。不得自动覆盖业务结论。
4. 处置完成后记录前后状态、操作者和验证结果。

## 备份与恢复

当前 Compose 已提供 PostgreSQL 和 RocketMQ 的持久化卷；备份脚本、MinIO、Camunda 和 Kubernetes
恢复演练仍属于后续交付项。RocketMQ 为单 Broker 演示环境，不承诺灾备能力。
