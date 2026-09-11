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
| `workflow-events` | `WorkflowTaskCompleted` | `workflow-service` | `supplier-service` | 回写审批结果 |
| `supplier-events` | `SupplierActivated` | `supplier-service` | `notification-audit-service` | 请求发送启用通知 |
| `workflow-events` | `SupplementRequested` | `workflow-service` | `supplier-service` | 驱动申请进入待补件 |
| `supplier-events` | `SupplementSubmitted` | `supplier-service` | `workflow-service` | 驱动下一轮采购初审 |
| `workflow-events` | `WorkflowTaskSlaReminderRequested` | SLA CronJob | `notification-audit-service` | 审批节点 20 小时催办 |
| `workflow-events` | `WorkflowTaskSlaEscalated` | SLA CronJob | `notification-audit-service` | 审批节点 24 小时超时通知 |
| `workflow-events` | `WorkflowRiskRejected` | `workflow-service` | `supplier-service`、`notification-audit-service` | 同步风控拒绝终态、通知与审计 |
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

当前版本已落地 `ApplicationSubmitted` 和风控事件链：supplier-service 在申请事务内写入 Outbox，
发布器收到 RocketMQ 同步发送成功结果后标记 `published_at`，workflow-service 以
`sourceEventId` 唯一约束实现重复消费幂等，并创建处于 `RISK_CHECKING` 的
`supplier-onboarding` 流程实例投影；workflow 再写入 `RiskCheckRequested`，risk-service
将结果和 `RiskCheckCompleted` 写入同一事务，workflow 以事件 Inbox 幂等地推进到采购审批或拒绝终态。

风控拒绝时，workflow-service 在更新流程为 `REJECTED` 的同一事务内写入 `WorkflowRiskRejected`；supplier-service
以 Inbox 幂等地将申请更新为 `REJECTED`，notification-audit-service 以同一事件生成申请人通知和不可变审计记录。

审批完成后，workflow-service 在推进流程实例的同一事务内写入 `WorkflowTaskCompleted`
Outbox；supplier-service 以事件 Inbox 去重、更新申请状态，完成运营节点后再写入
`SupplierActivated` Outbox；notification-audit-service 以事件 Inbox 去重，并将该事件写入
审计表和申请人站内通知表。邮件、短信等外部通道不在当前版本的事务边界内。

会签退回时，workflow-service 在同一事务中记录带 `decision`/`comment` 的 `RETURNED` 任务、取消同轮待办、
更新流程为 `SUPPLEMENT_REQUIRED` 并写入 `SupplementRequested`。supplier-service 幂等消费后更新申请状态；
申请人补件事务同时写入补件历史、申请状态、幂等响应和 `SupplementSubmitted` Outbox，workflow-service 再以
Inbox 幂等创建下一轮 `PURCHASER_REVIEW`。

SLA CronJob 使用独立的 `flowmesh_workflow_sla` 非超级用户维护账号，在固定批量和 `SKIP LOCKED` 约束下扫描待办，
将催办/升级事件写入 workflow Outbox；业务账号不具备跨租户扫描权限。

1. 业务事务提交时，同时写入 Outbox 记录。
2. Publisher 发送事件。只有收到 Broker ACK 后，才能标记 Outbox 已投递。
3. 消费者必须使用 `eventId + handlerType` 或等价的持久化唯一约束建立幂等记录。当前 workflow-service 以 `sourceEventId` 唯一约束实现 `ApplicationSubmitted` 投影幂等。业务更新、审计和新的 Outbox 记录必须在同一事务中提交。
4. 风控技术失败按 1 分钟、5 分钟、15 分钟延迟重试。三次失败后进入 DLQ 并创建 `OPERATIONS` 任务。
5. 人工重放必须记录 `originalEventId`、重放原因、操作者和结果。

## 事务消息对照实验

仅实验环境发送 `SupplierActivationAuditRecorded` 事务消息。消费者只写实验审计表，不得触发供应商状态变化、通知或其他生产主链副作用。
