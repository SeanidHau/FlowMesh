# FlowMesh 生产化验收清单

本文是从 MVP-4 继续推进生产化的验收清单。只有代码、部署配置、测试和运行演练同时满足要求，项目才能称为生产级系统。

## 已完成的生产基线

- JWT 密钥、数据库密码和 Redis 密码不允许使用默认占位值。
- Kubernetes 应用 Pod 使用非 root、只读根文件系统、默认 Seccomp，并关闭 ServiceAccount Token 自动挂载。
- Helm 默认提供 CPU/内存 requests 和 limits、启动/就绪/存活探针、滚动更新和优雅终止配置。
- 生产 values 提供三个服务的双副本、PodDisruptionBudget、拓扑分散和基于 CPU 的 HPA 配置。
- Spring Boot 启用优雅停机、连接超时和请求体大小边界。
- RocketMQ 消费线程在处理事件时恢复事件 `traceId` 到 MDC，并在处理结束后清理线程上下文。
- 提供 PostgreSQL custom-format 备份与恢复脚本；备份目录默认被 Git 忽略。

验证命令：

```bash
bash -n scripts/backup-postgres.sh scripts/restore-postgres.sh
helm lint infra/helm/flowmesh \
  --set-string global.jwtSigningKey="$HELM_TEST_JWT" \
  --set-string global.redisPassword="$HELM_TEST_REDIS_PASSWORD" \
  --set-string services.iam.dbPassword="$HELM_TEST_IAM_PASSWORD" \
  --set-string services.supplier.dbPassword="$HELM_TEST_SUPPLIER_PASSWORD" \
  --set-string services.workflow.dbPassword="$HELM_TEST_WORKFLOW_PASSWORD"
./mvnw -q -DskipTests package
```

## 尚未完成的生产能力

### 依赖高可用

- PostgreSQL 主备、自动切换、连接池上限和恢复演练。
- RocketMQ 多 Broker、持久卷、跨故障域部署和消息恢复演练。
- Redis 哨兵或托管 Redis；Redis 只能作为登录限流的派生状态。

### 平台与网络

- 对外网关、TLS 终止、统一限流、审计和服务间网络策略。
- Kubernetes NetworkPolicy、镜像仓库、镜像签名和运行时漏洞扫描。
- Metrics Server 依赖和真实集群中的 HPA/PDB 演练。

### 可观测性与恢复

- Prometheus/Grafana 告警规则、日志聚合和 OpenTelemetry Trace 后端。
- PostgreSQL 备份定时化、异地保存、定期恢复验证和恢复时间目标记录。
- RocketMQ 堆积、DLQ、对账差异和审批超时的告警剧本。

### 业务闭环

- 材料上传与对象存储安全检查。
- 风控、通知和独立审计服务的真实运行链路。
- 高并发压测、故障注入和跨租户安全回归。

## 完成判定

后续每一项能力都必须同时提供：

1. 可运行的实现或部署配置。
2. 自动化测试或可重复的运维演练。
3. 失败恢复、权限边界和数据一致性说明。
4. CI 门禁和运行手册更新。
5. 有证据的验证结果，而不是仅有设计文档。
