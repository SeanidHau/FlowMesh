# 事件契约

## 适用规则

- 使用 JSON 事件信封。
- 事件必须包含 `tenantId`、关联聚合 ID 和 Trace 信息。
- 新版本只能新增可选字段。删除、重命名或改变字段语义时，创建新事件类型或新消费者版本。
- 生产主链使用 Transactional Outbox。RocketMQ 事务消息只用于独立对照实验。

## Topic 与 Tag

| Topic | Tag | 生产者 | 消费者 | 用途 |
| --- | --- | --- | --- | --- |
| `supplier-events` | `ApplicationSubmitted` | `supplier-service` | `workflow-service` | 触发流程启动 |
| `supplier-events` | `SupplierActivated` | `supplier-service` | `notification-audit-service` | 请求发送启用通知 |
| `supplier-events` | `SupplementRequested` | `supplier-service` | `workflow-service` | 驱动补件分支 |
| `risk-events` | `RiskCheckRequested` | `workflow-service` | `risk-service` | 请求风险校验 |
| `risk-events` | `RiskCheckCompleted` | `risk-service` | `workflow-service` | 返回业务风险结果 |
| `risk-events` | `RiskCheckFailed` | `risk-service` | `workflow-service` | 记录技术失败和重试 |
| `notification-events` | `SupplierActivationNotificationRequested` | `notification-audit-service` | `notification-audit-service` | 通知处理命令 |
| `audit-events` | `AuditRecorded` | 各服务 | `notification-audit-service` | 异步审计投影 |
| `audit-events` | `ReplayRequested` | `notification-audit-service` | 对应服务 | 请求可审计重放 |

## 统一信封

```json
{
  "eventId": "2b803a03-ae8a-4244-b350-1a5c3f8b27fb",
  "eventType": "SupplierActivated",
  "schemaVersion": 1,
  "tenantId": "tenant-a",
  "aggregateId": "application-id",
  "occurredAt": "2026-08-13T10:00:00Z",
  "traceId": "trace-id",
  "payload": {}
}
```

| 字段 | 类型 | 说明 |
| --- | --- | --- |
| `eventId` | UUID | 当前投递唯一标识。人工重放必须生成新值。 |
| `eventType` | string | 事件类型。 |
| `schemaVersion` | integer | 事件结构版本，从 `1` 开始。 |
| `tenantId` | string | 可信租户标识。消费者必须验证归属。 |
| `aggregateId` | string | 申请或供应商等业务聚合 ID。 |
| `occurredAt` | RFC 3339 UTC string | 业务事件发生时间。 |
| `traceId` | string | 链路追踪关联标识。 |
| `payload` | object | 事件业务数据。 |

## 投递、重试与重放

1. 业务事务提交时，同时写入 Outbox 记录。
2. Publisher 发送事件。只有收到 Broker ACK 后，才能标记 Outbox 已投递。
3. 消费者使用 `eventId + handlerType` 建立幂等记录。业务更新、审计和新的 Outbox 记录必须在同一事务中提交。
4. 风控技术失败按 1 分钟、5 分钟、15 分钟延迟重试。三次失败后进入 DLQ 并创建 `OPERATIONS` 任务。
5. 人工重放必须记录 `originalEventId`、重放原因、操作者和结果。

## 事务消息对照实验

仅实验环境发送 `SupplierActivationAuditRecorded` 事务消息。消费者只写实验审计表，不得触发供应商状态变化、通知或其他生产主链副作用。
