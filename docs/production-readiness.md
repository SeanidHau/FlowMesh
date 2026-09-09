# FlowMesh 生产化验收清单

本文是从 MVP-4 继续推进生产化的验收清单。只有代码、部署配置、测试和运行演练同时满足要求，项目才能称为生产级系统。

## 已完成的生产基线

- JWT 密钥、数据库密码和 Redis 密码不允许使用默认占位值。
- Kubernetes 应用 Pod 使用非 root、只读根文件系统、默认 Seccomp，并关闭 ServiceAccount Token 自动挂载。
- Helm 默认提供 CPU/内存 requests 和 limits、启动/就绪/存活探针、滚动更新和优雅终止配置。
- 生产 values 提供 gateway 和五个业务服务的双副本、PodDisruptionBudget、拓扑分散和基于 CPU 的 HPA 配置。
- 已补齐 `gateway-service`，统一暴露 `/api/iam/**`、`/api/supplier/**` 和 `/api/workflow/**`；业务服务保持 ClusterIP，Gateway 具备资源限制、探针和优雅终止配置。
- Spring Boot 启用优雅停机、连接超时和请求体大小边界。
- 所有服务日志统一输出 `traceId`，消息消费者会恢复事件信封中的 `traceId` 并在处理结束后清理线程上下文。
- 提供 PostgreSQL custom-format 备份与恢复脚本；备份目录默认被 Git 忽略。
- 提供离线备份完整性校验脚本，并通过环境变量限制数据库连接池上限、连接超时和连接生命周期。
- CI 在 PR 构建六项服务镜像，在 `main` 推送时将带提交 SHA 和 `main` 标签的镜像发布到 GHCR。

验证命令：

```bash
bash -n scripts/backup-postgres.sh scripts/restore-postgres.sh
helm lint infra/helm/flowmesh \
  --set-string global.jwtSigningKey="$HELM_TEST_JWT" \
  --set-string global.redisPassword="$HELM_TEST_REDIS_PASSWORD" \
  --set-string services.iam.dbPassword="$HELM_TEST_IAM_PASSWORD" \
  --set-string services.supplier.dbPassword="$HELM_TEST_SUPPLIER_PASSWORD" \
  --set-string services.workflow.dbPassword="$HELM_TEST_WORKFLOW_PASSWORD" \
  --set-string objectStorage.secretKey="$HELM_TEST_OBJECT_STORAGE_SECRET"
./mvnw -q -DskipTests package
```

## 仍需由目标生产平台提供或完成的能力

### 依赖高可用

- PostgreSQL 主备、自动切换和恢复演练；当前应用已设置连接池上限和连接超时，但不替代数据库侧 HA。生产 values 已禁止回落到本地 `postgres` 服务名。
- RocketMQ 多 Broker、持久卷、跨故障域部署和消息恢复演练；当前 Helm 使用外部 NameServer 地址，不能把单 Broker Compose 拓扑当作 HA 证据。
- Redis 哨兵或托管 Redis；Redis 只能作为登录限流的派生状态，生产 values 已禁止回落到本地 `redis` 服务名。

### 平台与网络

- Gateway 的 TLS 终止、外部 Ingress、统一限流、审计和服务间网络策略；Helm 已提供可选 Ingress 路由模板。
- Helm 生产覆盖值已提供业务服务入口 NetworkPolicy；仍需在目标 CNI 和真实集群完成连通性演练。
- 镜像仓库、镜像签名和运行时漏洞扫描。
- Metrics Server 依赖和真实集群中的 HPA/PDB 演练。

### 可观测性与恢复

- 已提供 Prometheus 抓取配置和服务/Outbox/死信告警样例；生产环境仍需接入托管 Prometheus、Grafana、日志聚合和 OpenTelemetry Trace 后端。
- PostgreSQL 备份定时化、异地保存、定期恢复验证和恢复时间目标记录。
- RocketMQ 堆积、DLQ、对账差异和审批超时的告警剧本。

### 业务闭环

- 已实现材料上传、私有对象存储、文件头校验、SHA-256、ClamAV 扫描和短期下载授权；生产环境仍需完成对象存储生命周期、备份和权限策略演练。
- 已实现独立 risk-service 的异步 PASS/REJECT 运行链路，以及 notification-audit-service 的通知/审计投影链路；仍需在目标环境完成外部通知通道、保留策略和恢复演练。
- 高并发压测、故障注入和跨租户安全回归。仓库已提供 k6 压测脚本和显式确认的服务恢复演练脚本，但必须在目标环境执行并留存结果。

## 完成判定

后续每一项能力都必须同时提供：

1. 可运行的实现或部署配置。
2. 自动化测试或可重复的运维演练。
3. 失败恢复、权限边界和数据一致性说明。
4. CI 门禁和运行手册更新。
5. 有证据的验证结果，而不是仅有设计文档。
