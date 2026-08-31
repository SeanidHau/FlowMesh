# FlowMesh（流织）

FlowMesh 是一个面向多租户 B2B SaaS 的云原生供应商准入与采购合同审批平台。当前 MVP 使用 Java 21、Spring Boot、MyBatis、PostgreSQL、Apache RocketMQ、Vue 3 和 Electron，聚焦申请、审批、可靠消息和 Kubernetes 部署基础。

当前已完成 MVP-3：IAM 登录/刷新/登出与认证审计、JWT 跨服务校验、供应商申请创建、持久化幂等、PostgreSQL RLS 隔离，以及通过 RocketMQ Outbox 驱动 workflow-service 创建幂等流程投影，并支持采购、法务、财务、运营四个角色按顺序推进审批。Camunda、Redis、MinIO、风险服务和通知审计服务仍属于后续阶段。总体设计见 [DESIGN.md](DESIGN.md)。

## 当前能力边界

| 能力 | 当前状态 | 说明 |
| --- | --- | --- |
| IAM、JWT、Refresh Token | 已实现 | 支持登录、刷新、登出和认证安全审计。 |
| 供应商申请与审批投影 | 已实现 | 支持四级顺序审批、幂等和 PostgreSQL RLS。 |
| RocketMQ | 已实现 | 主链使用 Outbox、认领租约、指数退避和失败终态；真实 Broker E2E 仍需补充。 |
| PostgreSQL | 已实现 | 三个服务使用独立 Schema 和业务账号。 |
| Electron + Vue 工作台 | 已实现 | 支持桌面端和浏览器预览。 |
| Camunda、Redis、MinIO | 计划中 | 当前不参与运行链路，不能作为已部署能力对外宣称。 |
| Prometheus、Grafana、OpenTelemetry、DLQ 重放、对账 | 计划中 | 当前仅保留设计和部分数据结构，运行组件及运维入口待补齐。 |

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

## 目录

```text
services/                    # Maven 后端服务模块
frontend/                    # Electron + Vue 3 + TypeScript 桌面工作台
infra/compose/               # Docker Compose 本地开发环境
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

IDEA 应打开仓库根目录 `/Users/shigureli/FlowMesh`，并使用 Java 21 导入根目录 `pom.xml`。
运行服务前先执行 `./mvnw install -DskipTests`，再分别运行 `IamServiceApplication`、
`SupplierServiceApplication` 或 `WorkflowServiceApplication`。IAM 默认端口为 8081，
supplier 默认端口为 8082，workflow 默认端口为 8083。

要演示消息闭环：启动 PostgreSQL 和 RocketMQ 后，将 `FLOWMESH_OUTBOX_ENABLED` 与
`FLOWMESH_WORKFLOW_CONSUMER_ENABLED`、`FLOWMESH_SUPPLIER_CONSUMER_ENABLED`、
`FLOWMESH_WORKFLOW_OUTBOX_ENABLED` 设为 `true`，再启动 supplier 和 workflow 服务。

完整链路为：supplier 创建申请并写入 Outbox → workflow 创建流程实例 → 角色完成审批 →
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

在仓库根目录执行以下命令可以构建并启动 PostgreSQL、RocketMQ 和三个 Java 服务：

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
