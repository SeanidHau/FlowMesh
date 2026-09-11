# Grafana Dashboard

可直接导入的本地 Grafana Dashboard 位于
[`infra/compose/grafana/dashboards/flowmesh-overview.json`](../infra/compose/grafana/dashboards/flowmesh-overview.json)，
Compose 的 `observability` profile 会自动加载它。

Dashboard 覆盖服务健康、HTTP 请求/5xx、Outbox 堆积、RocketMQ 消费失败、Outbox 确认失败和死信；
对账差异与审批 SLA 仍应接入业务专用指标后再增加面板，避免用不存在的指标制造“绿色”假象。
