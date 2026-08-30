# FlowMesh（流织）

FlowMesh 是一个面向多租户 B2B SaaS 的云原生供应商准入与采购合同审批平台。项目以 Java 21、Spring Boot、Camunda 8 和 Apache RocketMQ 为核心，验证长流程编排、可靠消息、失败恢复、审计追踪和 Kubernetes 部署能力。

当前已完成 MVP-1：IAM 登录/刷新/登出、JWT 跨服务校验、供应商申请创建、持久化幂等和 PostgreSQL RLS 隔离。RocketMQ 事件链和流程编排将在下一阶段接入。总体设计见 [DESIGN.md](DESIGN.md)。

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
frontend/                    # Vue 3 + TypeScript 最小任务中心
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
docker compose --env-file .env -f infra/compose/docker-compose.yml up -d postgres
./mvnw test
```

IDEA 应打开仓库根目录 `/Users/shigureli/FlowMesh`，并使用 Java 21 导入根目录 `pom.xml`。
运行服务前先执行 `./mvnw install -DskipTests`，再分别运行 `IamServiceApplication`
或 `SupplierServiceApplication`。IAM 默认端口为 8081，supplier 默认端口为 8082。

根工程会校验 Java 21 与 Maven 3.9.x；不满足时构建会在开始阶段失败。

## 许可证

本项目使用 [Apache License 2.0](LICENSE)。
