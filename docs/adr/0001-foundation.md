# ADR 0001：采用 Camunda 8、RocketMQ 与 Transactional Outbox

- 状态：已接受
- 日期：2026-08-13

## 背景

供应商准入是跨服务、包含人工审批和异步外部校验的长流程。项目需要同时展示流程编排、可靠消息和最终一致性。

## 决策

- 使用 Camunda 8 管理 BPMN、用户任务、定时器和流程推进。
- 使用 Apache RocketMQ 传递跨服务领域事件和异步任务。
- 生产主链使用 Transactional Outbox，不使用分布式 XA 事务。
- 所有消费者和 Worker 使用持久化幂等记录。
- RocketMQ 事务消息只用于独立的 `SupplierActivationAuditRecorded` 对照实验。

## 后果

- 业务数据库、Camunda 与 RocketMQ 不共享全局事务；跨系统动作必须可重试、可审计、可对账。
- Camunda 与业务状态可能出现短暂不一致，需要 `applicationId + processInstanceKey` 和定时对账支持恢复。
- 事务消息实验不会污染生产业务链，便于解释它与 Outbox 的适用边界。
