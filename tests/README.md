# 测试资产

本目录将保存 REST Client、端到端测试、事件契约测试和压测脚本。

测试场景以 [测试策略](../docs/testing-strategy.md) 和 README 的演示剧本为准。

真实 RocketMQ Broker 主链路验证：

```bash
./tests/rocketmq-e2e.sh
```

脚本会临时创建环境变量和 Broker 配置，打包六个 Java 服务，并只启动本次测试使用的
PostgreSQL、Redis 与真实 RocketMQ Broker。脚本通过 Gateway 访问业务 API，验证申请幂等、四级审批、消费回写、健康探针、
Prometheus 指标、风控结果、申请人通知、死信查询、受控重放、审计和跨服务对账；退出时停止并删除本次测试容器和数据卷。
本地验证完成后请按 [运行手册](../docs/runbook.md) 退出 Docker Desktop。

PostgreSQL 备份恢复回归：

```bash
./tests/postgres-backup-e2e.sh
```

该脚本只使用临时 PostgreSQL 容器，验证 custom-format 备份、离线校验、角色密码不落盘和恢复到独立数据库；退出时删除测试资源。

恢复连接参数的离线契约测试：

```bash
./tests/postgres-restore-contract.sh
```

该测试使用命令替身确认恢复脚本会传递 `FLOWMESH_PG_SSLMODE` 和
`FLOWMESH_PG_CONNECT_TIMEOUT_SECONDS`，并拒绝无效的 TLS 模式或连接超时。

备份对象存储上传分支的离线契约测试：

```bash
./tests/postgres-backup-upload-contract.sh
```

该脚本使用本地替身命令验证 S3 上传参数、KMS 服务端加密和失败后的部分目录清理，不访问真实云账号。

PostgreSQL 生命周期清理验证：

```bash
./tests/retention-cleanup-contract.sh
./tests/postgres-retention-e2e.sh
```

前者不连接数据库，验证删除表白名单、确认门禁和锁语义；后者使用临时 PostgreSQL 容器验证
90/30 天窗口、终态判断和强制 RLS 表清理。执行后脚本会删除测试容器。

外部通知可靠投递契约：

```bash
./tests/notification-delivery-contract.sh
```

Gateway 请求体边界契约：

```bash
./tests/gateway-request-size-contract.sh
```

该测试验证认证、流程、通知和供应商材料上传路由均配置入口请求体上限，避免新增路由时遗漏资源保护。

生产副本拓扑分散契约：

```bash
./tests/production-topology-spread-contract.sh
```

该测试验证生产副本使用 `DoNotSchedule` 跨节点分布；本地默认仍允许单节点共置，避免开发环境因只有一个节点而无法调度。

生产观测资源契约：

```bash
./tests/production-observability-contract.sh
```

该测试验证生产 values 默认启用 `ServiceMonitor` 和 `PrometheusRule`，并提供 Prometheus Operator 选择标签。

Redis ACL 配置契约：

```bash
./tests/redis-acl-contract.sh
```

该测试验证 Gateway、IAM、Compose 和 Helm 均支持 Redis ACL 用户名；单密码 Redis 保持用户名为空的兼容行为。

Workflow 审批 SLA PostgreSQL 回归：

```bash
./tests/workflow-sla-e2e.sh
```

该脚本验证 20 小时催办、并行审批超时升级、workflow instance 锁定、任务状态收敛和 SLA Outbox 事件写入；退出时删除临时容器。

对象存储生命周期配置契约测试：

```bash
./tests/object-storage-lifecycle-contract.sh
```

该测试离线验证专用材料桶的版本化、非当前版本保留期、删除标记清理和 HTTPS endpoint 门禁，不访问真实云账号。

性能与故障演练：

```bash
FLOWMESH_ACCESS_TOKEN='<短期 Access Token>' k6 run tests/k6/supplier-onboarding.js
FLOWMESH_CHAOS_CONFIRM=YES \
  FLOWMESH_DRILL_EXPECTED_RTO_SECONDS=30 \
  FLOWMESH_DRILL_REPORT=./artifacts/risk-service-recovery.md \
  ./tests/fault-drills/verify-service-recovery.sh risk-service http://localhost:8084/actuator/health
```

故障演练会短暂停止并重启指定 Compose 服务；不要在共享环境直接执行。设置
`FLOWMESH_DRILL_EXPECTED_RTO_SECONDS` 后，恢复耗时超过目标会失败；设置
`FLOWMESH_DRILL_REPORT` 后会生成不可覆盖的 Markdown 证据报告。报告只证明应用服务恢复耗时，不能替代数据库、Redis、RocketMQ
或对象存储的故障切换演练。离线门禁可执行：

```bash
./tests/fault-drills/verify-service-recovery-contract.sh
```

目标 Kubernetes 集群发布后的只读验收：

```bash
FLOWMESH_IMAGE_TAG="$GITHUB_SHA" \
FLOWMESH_K8S_NAMESPACE=flowmesh \
FLOWMESH_HELM_RELEASE=flowmesh \
./tests/kubernetes-production-smoke.sh
```

脚本验证 Deployment 可用性、提交 SHA 镜像、安全上下文、探针、资源 requests/limits、PDB/HPA/NetworkPolicy、
Gateway Ingress Controller 入站边界、运行时 Secret 必需键（包括 SLA 维护账号和 RocketMQ Producer/Consumer 凭据）、备份凭据 Secret 和备份 CronJob
并发与截止时间，以及实际 Pod 注入的 PostgreSQL、Redis 和 RocketMQ TLS 配置。设置 `FLOWMESH_EXPECT_PROMETHEUS_RULE=true` 时，还会验证 `ServiceMonitor` 和
`PrometheusRule`。该脚本只读集群，不替代数据库、RocketMQ、Redis 和对象存储的故障切换演练。

目标生产环境的发布后只读验收可通过 `scripts/run-production-acceptance.sh` 统一执行；脚本会把镜像签名、Kubernetes smoke、
外部依赖预检、生命周期角色预检、运行时观测和目标环境证据包校验的结果写入不可覆盖的 Markdown 报告。
生产验收默认执行这些检查；非生产预检只有在显式设置对应 `FLOWMESH_REQUIRE_*` 变量为 `false` 时才会跳过，
并通过 `scripts/validate-production-evidence.sh` 校验目标环境的完整证据包。

生产发布入口的离线契约检查：

```bash
./tests/production-deploy-contract.sh
```

该检查确保发布脚本先验证镜像签名和生产配置，再使用 Helm 原子等待发布，并在发布后执行 Kubernetes smoke；
同时拒绝通过命令行传递运行时 Secret。

生产证据包离线契约检查：

```bash
./tests/production-evidence-contract.sh
```

证据包必须包含目标 Kubernetes、依赖 HA、运行时观测、应用恢复、备份恢复、压测、跨租户安全回归和告警路由报告，
每份报告明确记录 `PASS`；先由 `scripts/create-production-evidence-manifest.sh` 生成清单和
`checksums.sha256`，再执行完整性校验。生成器不会创建报告，也不会替代真实目标环境演练。

不连接集群的 smoke test 契约检查：

```bash
./tests/kubernetes-production-smoke-contract.sh
```

该检查验证关键 Deployment、运行时 Secret（包括 SLA 维护账号）和只读约束仍被 smoke test 覆盖。

目标生产网络中的外部依赖只读预检：

```bash
./scripts/validate-production-dependencies.sh
```

该脚本检查 PostgreSQL readiness/TLS、Redis TLS/PING、RocketMQ NameServer TLS 握手和对象存储 HTTPS；凭据通过环境变量注入，
脚本不会输出密码，也不会执行写操作。仓库中的 `production-dependencies-preflight-contract.sh` 使用命令替身离线验证其安全门禁。
