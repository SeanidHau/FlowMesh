# 测试策略

## 原则

- 每个状态转换、幂等边界和失败恢复路径必须有自动化测试。
- 单元测试验证业务规则；集成测试验证真实依赖行为；E2E 验证跨服务流程。
- 测试使用独立数据库、队列与对象前缀，不得依赖开发环境遗留数据。

## 测试层级

| 层级 | 目标 | 最低覆盖 |
| --- | --- | --- |
| 单元测试 | 状态机、权限、幂等、重试决策 | 所有受控状态转换和异常分支 |
| 集成测试 | PostgreSQL、RLS、MyBatis、Outbox | 使用 Testcontainers；RocketMQ 真实 Broker 由独立 Compose E2E 验证 |
| 契约测试 | REST 和事件兼容性 | 至少验证一次新增可选事件字段 |
| E2E | 完整流程和故障恢复 | 主链路、幂等、健康、DLQ 运维和对账剧本 |
| 压测 | 创建申请和风控消费 | 记录环境、场景、吞吐和延迟 |

## 必测场景

1. 正常准入：采购通过、法务/财务并行通过、供应商启用、通知完成。
2. 补件：法务退回，申请人补件后回到采购初审，历史意见保留。
3. 风控故障：连续超时，三次延迟重试后进入 DLQ，运营重放或人工决策后流程收敛。
4. 重复投递：同一事件多次到达时，只产生一次业务副作用。
5. Outbox 延迟：业务事务已提交但 Publisher 延迟时，最终投递且不重复。
6. 多租户越权：API、消息和文件读取均被拒绝并留审计。
7. 审批部分成功：审批快照与 Camunda 任务发生部分成功时，对账可恢复 `PENDING` 命令。

## 质量门禁

当前已执行的后端验证为：

```bash
./mvnw -q test
```

该命令会启动 PostgreSQL Testcontainers，验证 Flyway、MyBatis、RLS、认证、Outbox 竞争和核心业务集成测试。
真实 Broker 场景使用 `./tests/rocketmq-e2e.sh`，脚本只启动 PostgreSQL 和 RocketMQ，三个服务使用本机打包的 JAR 连接真实 Broker。
Helm 校验使用临时凭据执行 `helm lint` 和 `helm template`，不提交任何真实密钥。

- Docker 只在 Testcontainers 或 Compose 验证期间启动。
- 验证结束后停止本任务启动的 Compose 容器，并退出 Docker Desktop，避免后台持续占用资源。

- Maven Enforcer 检查 Java 与依赖版本。
- Spotless 或 Checkstyle 检查格式。
- OWASP Dependency-Check 或 Dependabot 检查依赖漏洞。
- Apache-2.0 许可证头检查适用于源代码文件。

漏洞误报必须在白名单中记录依赖、CVE、理由、责任人和复核时间。不得静默忽略。
