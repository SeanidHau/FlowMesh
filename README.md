# FlowMesh（流织）

FlowMesh 是一个面向多租户 B2B SaaS 的云原生供应商准入与采购合同审批平台。当前版本使用 Java 21、Spring Boot、Spring Cloud Gateway、MyBatis、PostgreSQL、Apache RocketMQ、Vue 3 和 Electron，聚焦申请、审批、可靠消息和 Kubernetes 部署基础。

核心业务 MVP-4 已完成，并已形成生产化基线：在上述基础上接入统一 API Gateway、Gateway Redis 分布式限流、供应商材料对象存储、异步风控服务、通知审计服务、RocketMQ Outbox 认领租约、退避、死信与重放、跨服务对账、基础指标、Trace ID、IAM 登录限流、PostgreSQL 定时备份归档以及 Compose、Helm、CI 验证。当前仍需在目标集群完成外部依赖 HA、生产观测后端和恢复演练；Camunda、Redis 缓存和外部通知通道属于后续业务扩展。总体设计见 [DESIGN.md](DESIGN.md)。

## 当前能力边界

| 能力 | 当前状态 | 说明 |
| --- | --- | --- |
| API Gateway | 已实现 | 统一路由到 IAM、Supplier 和 Workflow，业务服务保持内网入口。 |
| Gateway Redis 分布式限流 | 已实现 | Redis Lua 令牌桶按客户端地址限流；额度耗尽返回 429，Redis 故障 fail-closed 返回 503。 |
| IAM、JWT、Refresh Token | 已实现 | 支持登录、刷新、登出、安全审计，以及多副本安全的失效令牌定期清理。 |
| 供应商申请与审批投影 | 已实现 | 支持四级顺序审批、幂等和 PostgreSQL RLS。 |
| 供应商材料 | 已实现 | MinIO 私有桶、文件头校验、SHA-256、ClamAV 扫描、短期下载 URL 和可执行生命周期策略。 |
| 异步风控 | 已实现 | 独立 risk-service 通过 RocketMQ 接收风控请求，以结果事件推进或终止 workflow；提供默认关闭的 FAIL/TIMEOUT 故障演练开关。 |
| 通知与审计 | 已实现 | 独立服务消费供应商启用事件，写入租户隔离审计记录和申请人站内通知；支持查询和幂等标记已读。 |
| RocketMQ | 已实现 | 主链使用 Outbox、认领租约、指数退避、失败终态、死信重放和基础发布指标；生产 Helm 支持 Producer/Consumer 独立凭据和 TLS。 |
| PostgreSQL | 已实现 | 各服务使用独立 Schema 和业务账号，并通过 Flyway 管理迁移；生产连接默认要求 TLS。 |
| Electron + Vue 工作台 | 已实现 | 支持桌面端和浏览器预览。 |
| Redis 登录限流 | 已实现 | IAM 使用 Lua 脚本按租户账号和客户端地址原子限流；本地默认降级放行，生产 Helm 默认 fail-closed，并默认启用 Redis TLS。 |
| Camunda、Redis 缓存 | 计划中 | 当前不参与运行链路，不能作为已部署能力对外宣称。 |
| Prometheus、Alertmanager、Grafana | 本地基线已实现 | 提供 Prometheus 抓取、Alertmanager 路由、告警规则和 Grafana 概览 Dashboard；生产环境仍需接入托管观测平台和通知渠道。 |
| DLQ 重放、跨服务对账 | 已实现 | 提供 OPERATIONS 受控重放、审计和申请/流程状态对账入口。 |
| 镜像供应链 | 已实现 | 主分支发布完整提交 SHA 镜像，并执行 Trivy 扫描和 Cosign keyless 签名；仓库提供 Kyverno 集群准入策略。 |
| OpenTelemetry Trace | 已提供可选出口 | 六个服务支持 Micrometer Tracing 和 OTLP/HTTP 导出；默认关闭，目标平台仍需提供 Collector、存储和查询后端。 |
| PostgreSQL 备份 | 已实现 | 提供备份镜像、Helm CronJob、S3 兼容对象存储上传、服务端加密、失败重试和恢复回归；生产平台仍需配置复制、生命周期和 RTO/RPO。 |

## 项目目标

- 支持供应商申请、材料提交、风控、采购初审、法务/财务并行会签、启用和通知。
- 使用 Transactional Outbox、持久化幂等、延迟重试、死信重放和对账保证最终一致性。
- 使用 JWT、RBAC、`tenant_id` 与 PostgreSQL RLS 实现多租户隔离。
- 使用 Docker Compose 本地运行，并通过 kind 和 Helm 验证 Kubernetes 部署。

## 开始阅读

| 文档 | 用途 |
| --- | --- |
| [总体设计](DESIGN.md) | 业务范围、服务边界、可靠性与交付目标 |
| [架构说明](docs/architecture.md) | 服务、数据和网络边界 |
| [事件契约](docs/event-contracts.md) | RocketMQ Topic、Tag、事件信封和版本规则 |
| [API 规范](docs/api-guidelines.md) | REST、错误响应、幂等和分页规则 |
| [安全规范](docs/security.md) | JWT、RBAC、RLS、Secret 和对象存储规则 |
| [测试策略](docs/testing-strategy.md) | 单元、集成、契约、E2E 和压测范围 |
| [运行手册](docs/runbook.md) | 启停、排障、DLQ 重放、对账和恢复步骤 |
| [生产依赖预检](scripts/validate-production-dependencies.sh) | 只读检查目标环境外部依赖的 TLS 与基础连通性 |
| [补齐需求与验收](docs/completion-requirements.md) | 当前阶段范围、验收标准和实施状态 |
| [生产化验收清单](docs/production-readiness.md) | 从 MVP-4 推进到生产级项目的完成标准 |

## 目录

```text
services/                    # Maven 后端服务模块
frontend/                    # Electron + Vue 3 + TypeScript 桌面工作台
infra/compose/               # Docker Compose 本地开发环境和可选观测组件
infra/helm/                  # kind 使用的 Helm Chart
docs/                        # 架构、规范、ADR、运行手册
tests/                       # REST Client、E2E、契约和压测脚本
dashboards/                  # Grafana Dashboard JSON
scripts/                     # 初始化、验证、备份和恢复脚本
```

## 开发约定

- 业务代码按阶段实现、审查和验证；测试代码、说明文档和工程配置与业务代码一起维护。
- 每个阶段完成后执行验证、创建 Git 提交，并推送到远程仓库。
- 真实密钥、密码、Token、`.env` 和构建产物不得提交。

## 本地构建

项目统一使用 Maven Wrapper，避免团队成员的 Maven 版本不一致。请在 IDEA 和终端中选择 Java 21，然后在仓库根目录执行：

```bash
cp .env.example .env
openssl rand -base64 32
# 将生成的值写入 .env 的 JWT_SIGNING_KEY
./infra/compose/validate-env.sh .env
docker compose --env-file .env -f infra/compose/docker-compose.yml up -d postgres
./mvnw test
```

本地测试若需要执行 Testcontainers 集成测试，需要临时启动 Docker Desktop；Docker 未启动时，
相关集成测试会被 JUnit 明确标记为 skipped，不会伪装成通过。测试完成后，如果启动过 Compose，
执行 `docker compose --env-file .env -f infra/compose/docker-compose.yml down`，再退出 Docker
Desktop；平时不要让 Docker 常驻后台，后续需要集成测试或本地环境时再启动。

IDEA 应打开仓库根目录 `/Users/shigureli/FlowMesh`，并使用 Java 21 导入根目录 `pom.xml`。
运行服务前先执行 `./mvnw install -DskipTests`，再分别运行 `GatewayServiceApplication`、
`IamServiceApplication`、`SupplierServiceApplication`、`WorkflowServiceApplication` 或
`RiskServiceApplication`。
Gateway 默认端口为 8080，IAM 默认端口为 8081，supplier 默认端口为 8082，workflow 默认端口为 8083。

要演示消息闭环：启动 PostgreSQL 和 RocketMQ 后，将 `FLOWMESH_OUTBOX_ENABLED` 与
`FLOWMESH_WORKFLOW_CONSUMER_ENABLED`、`FLOWMESH_SUPPLIER_CONSUMER_ENABLED`、
`FLOWMESH_WORKFLOW_OUTBOX_ENABLED`、`FLOWMESH_RISK_CONSUMER_ENABLED`、
`FLOWMESH_RISK_OUTBOX_ENABLED` 设为 `true`，再启动 supplier、workflow 和 risk 服务。

完整链路为：supplier 创建申请并写入 Outbox → workflow 创建风控中的流程实例 → risk-service
返回 PASS/REJECT → 通过后角色完成审批 →
workflow 写入审批完成 Outbox → supplier 更新申请状态；运营节点完成后状态为 `ENABLED`，
并生成 `SupplierActivated` 通知事件。

登录后可使用 workflow-service 查询和推进当前租户下的流程实例：

```text
GET  http://localhost:8083/api/v1/workflow-instances/{applicationId}
POST http://localhost:8083/api/v1/workflow-instances/{applicationId}/tasks
     {"taskKey":"PURCHASER_REVIEW"}
```

任务按 `PURCHASER_REVIEW`、`LEGAL_REVIEW`、`FINANCE_REVIEW`、
`OPERATIONS_ACTIVATION` 顺序推进，Token 中缺少对应角色时返回 `403`。

根工程会校验 Java 21 与 Maven 3.9.x；不满足时构建会在开始阶段失败。

## 启动完整本地环境

在仓库根目录执行以下命令可以构建并启动 PostgreSQL、RocketMQ、六个 Java 服务和 API Gateway：

```bash
cp .env.example .env
# 将 .env 中的 JWT_SIGNING_KEY 替换为 openssl rand -base64 32 的输出
docker compose --env-file .env -f infra/compose/docker-compose.yml up -d --build
```

确认服务健康后，在另一个终端启动桌面端：

```bash
cd frontend
npm install
VITE_DEMO_MODE=true npm run dev
```

首次启动 PostgreSQL 时会创建服务 Schema 和业务账号。需要重新执行初始化脚本时，先阅读
[运行手册](docs/runbook.md)中的数据卷说明；不要在未确认数据用途的情况下删除 `pgdata`。

## 许可证

本项目使用 [Apache License 2.0](LICENSE)。
