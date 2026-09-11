# FlowMesh 生产化验收清单

本文是从 MVP-4 继续推进生产化的验收清单。只有代码、部署配置、测试和运行演练同时满足要求，项目才能称为生产级系统。

## 已完成的生产基线

- JWT 密钥、数据库密码和 Redis 密码不允许使用默认占位值。
- Kubernetes 应用 Pod 使用非 root、只读根文件系统、默认 Seccomp，并关闭 ServiceAccount Token 自动挂载。
- Helm 默认提供 CPU/内存 requests 和 limits、启动/就绪/存活探针、滚动更新和优雅终止配置。
- 生产 values 提供 gateway 和五个业务服务的双副本、PodDisruptionBudget、拓扑分散和基于 CPU 的 HPA 配置。
- 已补齐 `gateway-service`，统一暴露 `/api/iam/**`、`/api/supplier/**`、`/api/workflow/**` 和 `/api/notification/**`；业务服务保持 ClusterIP，Gateway 具备资源限制、探针和优雅终止配置。
- Gateway 已使用 Redis Lua 令牌桶对所有业务路由执行分布式限流，并暴露允许、拒绝和 Redis 故障指标；生产入口必须覆写配置的客户端地址请求头，避免公网请求伪造限流身份。
- 生产 Helm 为 Gateway 单独渲染入站 NetworkPolicy，只允许指定 Ingress Controller 命名空间和监控命名空间访问，避免绕过 TLS、审计和入口限流直接调用 Gateway Service。
- Spring Boot 启用优雅停机、连接超时和请求体大小边界。
- supplier readiness 会在生产配置下检查 MinIO 材料桶和 ClamAV 扫描端口；依赖不可用时不接收新的材料请求。
- Redis 登录限流支持可配置的故障策略；本地默认降级放行，生产 Helm 默认 fail-closed，Redis 不可用时返回 `503`。
- IAM 会在所有副本中以带批量上限和保留窗口的任务清理过期/长期撤销的 Refresh Token，SQL 使用 `FOR UPDATE SKIP LOCKED` 避免多副本重复争抢，并暴露删除计数指标。
- 所有服务日志统一输出 `traceId`，消息消费者会恢复事件信封中的 `traceId` 并在处理结束后清理线程上下文。
- RocketMQ 消费者已暴露按消费者区分的处理耗时直方图，并提供消费处理 P95 超过 5 秒的 Prometheus 告警；观测配置校验会防止这条告警被误删。
- 提供 PostgreSQL custom-format 备份与恢复脚本；备份目录默认被 Git 忽略。
- 提供离线备份完整性校验脚本，并通过真实 PostgreSQL 容器 E2E 验证归档校验、角色密码不落盘和隔离数据库恢复；通过环境变量限制数据库连接池上限、连接超时和连接生命周期。
- 提供可发布的 PostgreSQL 备份镜像和 Helm CronJob：定时创建归档、上传到 S3 兼容对象存储、使用服务端加密、禁止并发执行并在失败时重试；生产 Helm 要求显式注入外部数据库地址、备份目标和凭据 Secret。
- Outbox 发布器显式设置消息发送超时，并在启动时校验“批量发送窗口 + 安全余量”不超过认领租约，避免参数调整后出现租约过期导致的并发重复发布。
- 四个 RocketMQ 服务支持独立 Producer/Consumer 凭据、访问通道和 TLS 配置；生产 Helm 默认开启 Producer/Consumer TLS，并要求运行时 Secret 提供四组凭据键。
- 生产应用和备份 PostgreSQL 连接默认使用 `sslmode=require`，Redis 连接默认启用 TLS；目标平台配置 CA 后可进一步使用 PostgreSQL `verify-full` 完成服务端身份校验。
- 生产 Helm 模式要求外部 Secret、外部镜像仓库和提交 SHA 镜像标签；未提供 `global.imageTag` 时渲染直接失败，避免部署可变或本地默认镜像。
- 生产覆盖值显式覆盖 PostgreSQL、Redis 和 RocketMQ NameServer 地址，阻止 Helm 合并时继承本地 Compose 服务名；发布流程仍必须替换占位地址为真实 HA 服务端点。
- CI 在 PR 构建六个应用镜像和一个备份镜像；在 `main` 推送时发布完整提交 SHA 和 `main` 标签，并为镜像生成 SBOM/构建证明，对完整 SHA 镜像执行 Trivy 漏洞扫描和 Cosign keyless 签名。
- 生产 Helm 模式强制启用 Ingress，并要求发布流程显式注入真实域名和 TLS Secret；缺失时渲染失败。
- Helm 提供可选的 Prometheus Operator `ServiceMonitor`，启用后统一抓取六个应用服务的 Actuator 指标。
- Helm 提供可选的 Prometheus Operator `PrometheusRule`，覆盖服务不可用、HTTP 5xx、Outbox 积压、死信、消费失败、消费延迟和确认失败告警；生产环境仍需配置 Alertmanager 路由和值班通知。
- 六个服务已提供可选 Micrometer Tracing 和 OTLP/HTTP 出口；默认关闭，生产启用时 Helm 要求显式提供 Collector 地址。

验证命令：

```bash
bash -n scripts/backup-postgres.sh scripts/restore-postgres.sh
helm lint infra/helm/flowmesh \
  --set-string global.jwtSigningKey="$HELM_TEST_JWT" \
  --set-string global.redisPassword="$HELM_TEST_REDIS_PASSWORD" \
  --set-string services.iam.dbPassword="$HELM_TEST_IAM_PASSWORD" \
  --set-string services.supplier.dbPassword="$HELM_TEST_SUPPLIER_PASSWORD" \
  --set-string services.workflow.dbPassword="$HELM_TEST_WORKFLOW_PASSWORD" \
  --set-string services.risk.dbPassword="$HELM_TEST_RISK_PASSWORD" \
  --set-string services.notificationAudit.dbPassword="$HELM_TEST_AUDIT_PASSWORD" \
  --set-string objectStorage.secretKey="$HELM_TEST_OBJECT_STORAGE_SECRET"
./mvnw -q -DskipTests package
```

## 仍需由目标生产平台提供或完成的能力

### 依赖高可用

- PostgreSQL 主备、自动切换和恢复演练；当前应用已设置连接池上限和连接超时，但不替代数据库侧 HA。生产 values 已禁止回落到本地 `postgres` 服务名。
- RocketMQ 多 Broker、持久卷、跨故障域部署和消息恢复演练；当前 Helm 使用外部 NameServer 地址并支持 TLS/ACL，不能把单 Broker Compose 拓扑当作 HA 证据。
- Redis 哨兵或托管 Redis；Redis 只能作为登录限流的派生状态，生产 values 已禁止回落到本地 `redis` 服务名并默认启用 TLS，但仍需在目标平台完成 HA 和证书轮换演练。

### 平台与网络

- Gateway 的真实入口仍需由目标集群提供会覆写客户端地址请求头的 Ingress Controller 和证书 Secret；仓库已提供并验收 Gateway 入站 NetworkPolicy。
- Helm 生产覆盖值已提供业务服务入口、Prometheus 指标抓取入口和出站白名单 NetworkPolicy；发布流程必须注入监控命名空间和外部依赖 CIDR，仍需在目标 CNI 和真实集群完成连通性演练。
- 已提供 Kyverno 镜像签名准入策略；目标集群仍需安装 Kyverno、应用策略，并配置持续运行时漏洞扫描平台。
- Metrics Server 依赖和真实集群中的 HPA/PDB 演练。

### 可观测性与恢复

- 已提供 Prometheus 抓取配置、可选 ServiceMonitor、服务/Outbox/死信/Gateway 限流告警、Grafana Dashboard 和本地 Alertmanager 路由基线；生产环境仍需接入托管 Prometheus、Grafana、Alertmanager、日志聚合、OpenTelemetry Collector 和 Trace 后端。
- 消息消费耗时已纳入 Prometheus 指标和告警；生产环境仍需根据实际 SLO 调整阈值，并完成告警通知路由和值班演练。
- PostgreSQL 备份已经提供 Helm CronJob、S3 上传、服务端加密、失败重试和 CI 恢复回归；目标平台仍需配置对象存储跨故障域复制、生命周期、定期恢复验证和实际 RTO/RPO 记录。
- RocketMQ 堆积、DLQ、对账差异和审批超时的告警剧本。

### 业务闭环

- 已实现材料上传、私有对象存储、文件头校验、SHA-256、ClamAV 扫描和短期下载授权；生产环境仍需完成对象存储生命周期、备份和权限策略演练。
- 已实现独立 risk-service 的异步 PASS/REJECT 运行链路，以及 notification-audit-service 的通知/审计投影链路；仍需在目标环境完成外部通知通道、保留策略和恢复演练。
- risk-service 已提供默认关闭的 `FAIL` / `TIMEOUT` 受控故障注入，用于验证消息重试、DLQ 和人工处置；生产 Helm 会显式关闭该开关。
- 高并发压测、故障注入和跨租户安全回归。仓库已提供 k6 压测脚本和显式确认的服务恢复演练脚本，但必须在目标环境执行并留存结果。
- 提供只读 Kubernetes 生产 smoke test，验证六个 Deployment、提交 SHA 镜像、安全上下文、探针、资源限制、PDB/HPA/NetworkPolicy、Gateway Ingress 边界、运行时 Secret 必需键和备份 CronJob；目标环境仍需实际执行并留存输出。
- 提供只读外部依赖 preflight，检查 PostgreSQL/Redis/RocketMQ TLS 和对象存储 HTTPS；目标环境仍需执行并留存输出，且该检查不替代 HA、故障切换和恢复演练。

## 完成判定

后续每一项能力都必须同时提供：

1. 可运行的实现或部署配置。
2. 自动化测试或可重复的运维演练。
3. 失败恢复、权限边界和数据一致性说明。
4. CI 门禁和运行手册更新。
5. 有证据的验证结果，而不是仅有设计文档。
