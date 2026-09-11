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

备份对象存储上传分支的离线契约测试：

```bash
./tests/postgres-backup-upload-contract.sh
```

该脚本使用本地替身命令验证 S3 上传参数、KMS 服务端加密和失败后的部分目录清理，不访问真实云账号。

性能与故障演练：

```bash
FLOWMESH_ACCESS_TOKEN='<短期 Access Token>' k6 run tests/k6/supplier-onboarding.js
FLOWMESH_CHAOS_CONFIRM=YES \
  ./tests/fault-drills/verify-service-recovery.sh risk-service http://localhost:8084/actuator/health
```

故障演练会短暂停止并重启指定 Compose 服务；不要在共享环境直接执行。

目标 Kubernetes 集群发布后的只读验收：

```bash
FLOWMESH_IMAGE_TAG="$GITHUB_SHA" \
FLOWMESH_K8S_NAMESPACE=flowmesh \
FLOWMESH_HELM_RELEASE=flowmesh \
./tests/kubernetes-production-smoke.sh
```

脚本验证 Deployment 可用性、提交 SHA 镜像、安全上下文、探针、资源 requests/limits、PDB/HPA/NetworkPolicy、
Gateway Ingress Controller 入站边界、运行时 Secret 必需键（包括 RocketMQ Producer/Consumer 凭据）、备份凭据 Secret 和备份 CronJob
并发与截止时间。设置 `FLOWMESH_EXPECT_PROMETHEUS_RULE=true` 时，还会验证 `ServiceMonitor` 和
`PrometheusRule`。该脚本只读集群，不替代数据库、RocketMQ、Redis 和对象存储的故障切换演练。

目标生产网络中的外部依赖只读预检：

```bash
./scripts/validate-production-dependencies.sh
```

该脚本检查 PostgreSQL readiness/TLS、Redis TLS/PING、RocketMQ NameServer TLS 握手和对象存储 HTTPS；凭据通过环境变量注入，
脚本不会输出密码，也不会执行写操作。仓库中的 `production-dependencies-preflight-contract.sh` 使用命令替身离线验证其安全门禁。
