# 测试资产

本目录将保存 REST Client、端到端测试、事件契约测试和压测脚本。

测试场景以 [测试策略](../docs/testing-strategy.md) 和 README 的四个演示剧本为准。

真实 RocketMQ Broker 主链路验证：

```bash
./tests/rocketmq-e2e.sh
```

脚本会临时创建环境变量和 Broker 配置，打包三个 Java 服务，并只启动本次测试使用的
PostgreSQL、Redis 与真实 RocketMQ Broker。脚本验证申请幂等、四级审批、消费回写、健康探针、
Prometheus 指标、死信查询、受控重放、审计和跨服务对账；退出时停止并删除本次测试容器和数据卷。
本地验证完成后请按 [运行手册](../docs/runbook.md) 退出 Docker Desktop。
