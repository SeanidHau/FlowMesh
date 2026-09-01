# FlowMesh 补齐需求与验收标准

## 1. 文档目的

本文记录 MVP-3 之后需要补齐的能力、实施顺序和验收标准。只有代码、测试和运行验证同时满足要求，才将项目标记为“已完成”。本轮已完成 MVP-4 范围内的可靠消息、运营处置、可观测性和工程验证；后续产品能力仍单独保留。

## 2. 当前基线

MVP-3 已完成 IAM 认证、Supplier 申请、Workflow 审批投影、JWT/RBAC、PostgreSQL RLS、MyBatis 持久化和 RocketMQ Transactional Outbox 基础链路。

当前 Outbox 已具备数据库认领租约、指数退避、失败终态、死信查询、受控重放和审计；事件信封/业务 ID 校验、业务与消息指标、HTTP/事件 Trace 标识、依赖就绪探针、真实 RocketMQ E2E、并发竞争测试和跨服务对账均已接入。

## 3.1 本轮实施状态

| 状态 | 内容 |
| --- | --- |
| 已完成 | MVP-4 全部需求：消息契约与幂等、Outbox ACK/退避/死信、多实例认领、DLQ 查询/重放/审计、跨服务对账、业务与消息指标、Trace 标识、数据库和 RocketMQ 就绪探针、PostgreSQL 集成测试、真实 RocketMQ E2E、CI 和资源回收。 |
| 明确不纳入本轮 | Camunda、Redis、MinIO、独立风险/通知服务、完整监控平台和生产级高可用，详见后续产品能力。 |

## 3. MVP-4 范围

### 3.1 事件契约与消息可靠性

| 编号 | 需求 | 验收标准 |
| --- | --- | --- |
| MSG-01 | 消费者校验事件信封 | 校验 `eventId`、`eventType`、`schemaVersion`、`aggregateId`、`tenantId`、`occurredAt`、`traceId` 和 `payload` 必填字段。 |
| MSG-02 | 校验业务关联关系 | `aggregateId` 与载荷中的业务 ID 不一致时拒绝处理，并记录可定位的错误信息。 |
| MSG-03 | 发布器可靠性测试 | 单元测试覆盖 ACK 后标记、发送失败、指数退避和失败终态；真实 RocketMQ E2E 验证 Broker ACK 后主链路最终完成。 |
| MSG-04 | 多实例竞争测试 | PostgreSQL 集成测试并发调用两个发布器，验证租约和 `FOR UPDATE SKIP LOCKED` 下同一事件只被认领一次。 |
| MSG-05 | 消费幂等测试 | 申请幂等、流程投影幂等、重复审批和真实 RocketMQ 主链路均通过；消费者以 Inbox/业务唯一约束吸收重复事件。 |

### 3.2 DLQ 与运营处置

| 编号 | 需求 | 验收标准 |
| --- | --- | --- |
| OPS-01 | 查询失败事件 | 可按租户、事件类型、聚合 ID 和失败状态查询。 |
| OPS-02 | 受控重放 | 只有 `OPERATIONS` 角色可以重放；必须填写原因；重放生成新的 `eventId` 并保留 `originalEventId`。 |
| OPS-03 | 重放审计 | 记录操作者、租户、原因、原事件、重放事件、前后状态和时间。 |
| OPS-04 | 状态对账 | 可以识别申请、流程实例、Inbox 和 Outbox 之间的差异，并生成待处置记录。 |

### 3.3 可观测性

| 编号 | 需求 | 验收标准 |
| --- | --- | --- |
| OBS-01 | 业务指标 | 已暴露申请创建、审批节点完成、重复事件、Outbox 待发送和死信数量指标。 |
| OBS-02 | 消息指标 | 已暴露发布/消费成功失败、重试和死信计数，以及 Outbox 当前待发送/死信 Gauge。 |
| OBS-03 | 链路标识 | HTTP 入口生成/透传 `traceId`；事件信封携带 `traceId`、`eventId`、`tenantId`，日志不输出消息密文或 JWT。 |
| OBS-04 | 健康检查 | Actuator 区分 liveness/readiness；readiness 纳入 PostgreSQL 和 RocketMQ NameServer TCP 检查。 |

### 3.4 测试与交付

| 编号 | 需求 | 验收标准 |
| --- | --- | --- |
| QA-01 | PostgreSQL 集成测试 | Docker 可用时执行 `./mvnw -q test`，本轮完整通过。 |
| QA-02 | RocketMQ 集成测试 | `tests/rocketmq-e2e.sh` 使用 Compose 真实 RocketMQ Broker 和本机 JAR 验证跨服务主链路、死信运维闭环和对账。 |
| QA-03 | CI 校验 | Maven、前端构建、Helm lint 和模板渲染均已写入 GitHub Actions。 |
| QA-04 | 资源回收 | 本地验证结束后停止本任务启动的 Compose 容器，并关闭 Docker Desktop；后续验证前再按需启动。 |

## 4. 后续产品能力

以下能力不纳入 MVP-4 的“已完成”判定，但保留为后续迭代需求：

- Camunda 8 BPMN 流程编排。
- Redis 缓存、限流或短期幂等加速。
- MinIO 供应商材料上传、预签名 URL 和文件安全检查。
- 独立风险服务、通知服务和业务审计服务。
- Prometheus、Grafana、OpenTelemetry 的完整监控与告警平台。
- RocketMQ、PostgreSQL 多副本高可用、备份恢复和 Chaos Mesh 故障演练。

这些组件只有在对应业务场景、数据边界和测试环境明确后再接入，不为了扩充简历技术栈而提前引入。

## 5. 完成定义

每个需求必须同时具备：

1. 业务代码或配置实现。
2. 针对关键分支的自动化测试。
3. 文档、运行手册和面试问答与实际行为一致。
4. 在可用环境中完成构建或集成验证，并记录环境限制。
5. 阶段性成果提交 Git；若远程网络可用则推送到远程仓库。
