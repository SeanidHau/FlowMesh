# Helm 与 kind 部署

`flowmesh/` Chart 部署 gateway、IAM、supplier、workflow、risk 和 notification-audit 六个应用服务。PostgreSQL、Redis 与 RocketMQ
作为外部依赖，通过 `values.yaml` 配置地址；演示环境使用单副本或单 Broker 拓扑，不代表生产
高可用部署。

## 安装应用服务

先确保集群可以访问六个应用镜像，并准备 PostgreSQL、RocketMQ 以及对应的数据库 Schema 和账号。
在仓库根目录执行：

```bash
helm lint infra/helm/flowmesh \
  --set global.jwtSigningKey="$JWT_SIGNING_KEY" \
  --set global.redisPassword="$REDIS_PASSWORD" \
  --set services.iam.dbPassword="$IAM_DB_PASSWORD" \
  --set services.iam.dbMigratorPassword="$IAM_DB_MIGRATOR_PASSWORD" \
  --set services.supplier.dbPassword="$SUPPLIER_DB_PASSWORD" \
  --set services.supplier.dbMigratorPassword="$SUPPLIER_DB_MIGRATOR_PASSWORD" \
  --set services.workflow.dbPassword="$WORKFLOW_DB_PASSWORD" \
  --set services.workflow.dbMigratorPassword="$WORKFLOW_DB_MIGRATOR_PASSWORD" \
  --set services.risk.dbPassword="$RISK_DB_PASSWORD" \
  --set services.risk.dbMigratorPassword="$RISK_DB_MIGRATOR_PASSWORD" \
  --set services.notificationAudit.dbPassword="$AUDIT_DB_PASSWORD" \
  --set services.notificationAudit.dbMigratorPassword="$AUDIT_DB_MIGRATOR_PASSWORD" \
  --set objectStorage.accessKey="$OBJECT_STORAGE_ACCESS_KEY" \
  --set objectStorage.secretKey="$OBJECT_STORAGE_SECRET_KEY"
helm upgrade --install flowmesh infra/helm/flowmesh \
  --set global.jwtSigningKey="$JWT_SIGNING_KEY" \
  --set global.redisPassword="$REDIS_PASSWORD" \
  --set services.iam.dbPassword="$IAM_DB_PASSWORD" \
  --set services.iam.dbMigratorPassword="$IAM_DB_MIGRATOR_PASSWORD" \
  --set services.supplier.dbPassword="$SUPPLIER_DB_PASSWORD" \
  --set services.supplier.dbMigratorPassword="$SUPPLIER_DB_MIGRATOR_PASSWORD" \
  --set services.workflow.dbPassword="$WORKFLOW_DB_PASSWORD" \
  --set services.workflow.dbMigratorPassword="$WORKFLOW_DB_MIGRATOR_PASSWORD" \
  --set services.risk.dbPassword="$RISK_DB_PASSWORD" \
  --set services.risk.dbMigratorPassword="$RISK_DB_MIGRATOR_PASSWORD" \
  --set services.notificationAudit.dbPassword="$AUDIT_DB_PASSWORD" \
  --set services.notificationAudit.dbMigratorPassword="$AUDIT_DB_MIGRATOR_PASSWORD" \
  --set objectStorage.accessKey="$OBJECT_STORAGE_ACCESS_KEY" \
  --set objectStorage.secretKey="$OBJECT_STORAGE_SECRET_KEY"
```

生产环境应使用生产覆盖值，并让 `global.existingSecret` 指向外部 Secret。生产覆盖值会启用审批 SLA CronJob，
该任务使用 `WORKFLOW_SLA_DB_PASSWORD` 连接独立维护账号。由于 PostgreSQL 客户端镜像属于运行时供应链，
生产部署还必须将已审核的 `postgres:16.15` 多架构镜像 digest 设置到 `WORKFLOW_SLA_IMAGE_DIGEST`，不能只使用版本标签：

```bash
helm upgrade --install flowmesh infra/helm/flowmesh \
  -f infra/helm/flowmesh/values-production.yaml \
  --set-string global.imageTag="$GITHUB_SHA" \
  --set-string workflowSla.imageDigest="$WORKFLOW_SLA_IMAGE_DIGEST" \
  --set 'networkPolicy.egress.externalCidrs[0]=10.20.0.0/16' \
  --set backup.postgres.host="postgres-primary.database.svc" \
  --set backup.s3.uri="s3://flowmesh-production-backups" \
  --set backup.credentialsSecret="flowmesh-backup-credentials" \
  --set retention.postgres.host="postgres-primary.database.svc" \
  --set retention.credentialsSecret="flowmesh-retention-credentials" \
  --set ingress.host="api.example.com" \
  --set 'ingress.tls[0].secretName=flowmesh-gateway-tls' \
  --set 'ingress.tls[0].hosts[0]=api.example.com' \
  --set global.existingSecret=flowmesh-runtime-secrets
```

生产模式要求显式提供真实 Ingress 域名和已存在的 TLS Secret；未提供时 Helm 渲染失败。
镜像标签必须使用完整 Git 提交 SHA；主分支 CI 会为该标签执行 Trivy 漏洞扫描并生成 Cosign keyless 签名。

生产 Trace 导出默认关闭。接入 OpenTelemetry Collector 后，显式开启 Trace 和 OTLP 导出，并提供
Collector 的 OTLP/HTTP traces 地址：

```bash
helm upgrade --install flowmesh infra/helm/flowmesh \
  -f infra/helm/flowmesh/values-production.yaml \
  --set-string global.imageTag="$GITHUB_SHA" \
  --set-string workflowSla.imageDigest="$WORKFLOW_SLA_IMAGE_DIGEST" \
  --set 'networkPolicy.egress.externalCidrs[0]=10.20.0.0/16' \
  --set backup.postgres.host="postgres-primary.database.svc" \
  --set backup.s3.uri="s3://flowmesh-production-backups" \
  --set backup.credentialsSecret="flowmesh-backup-credentials" \
  --set retention.postgres.host="postgres-primary.database.svc" \
  --set retention.credentialsSecret="flowmesh-retention-credentials" \
  --set observability.tracing.enabled=true \
  --set observability.tracing.exportEnabled=true \
  --set observability.tracing.endpoint="http://otel-collector.observability:4318/v1/traces" \
  --set ingress.host=api.example.com \
  --set 'ingress.tls[0].secretName=flowmesh-gateway-tls' \
  --set 'ingress.tls[0].hosts[0]=api.example.com' \
  --set global.existingSecret=flowmesh-runtime-secrets
```

生产环境不得在未部署 Collector 时开启 OTLP 导出；Helm 会拒绝缺少地址的生产渲染。

生产环境建议预先创建包含 `JWT_SIGNING_KEY`、`REDIS_PASSWORD`、`IAM_DB_PASSWORD`、
`SUPPLIER_DB_PASSWORD`、`WORKFLOW_DB_PASSWORD`、`RISK_DB_PASSWORD`、`AUDIT_DB_PASSWORD`、`OBJECT_STORAGE_ACCESS_KEY`、
`OBJECT_STORAGE_SECRET_KEY`、`ROCKETMQ_PRODUCER_ACCESS_KEY`、`ROCKETMQ_PRODUCER_SECRET_KEY`、
`ROCKETMQ_CONSUMER_ACCESS_KEY`、`ROCKETMQ_CONSUMER_SECRET_KEY`、`WORKFLOW_SLA_DB_PASSWORD` 和
`NOTIFICATION_WEBHOOK_SIGNING_SECRET` 的 Secret，然后设置
`--set global.existingSecret=<secret-name>`。Chart 不会为缺少凭据或已知占位值的配置生成 Secret。

生产覆盖值默认启用外部通知投递。发布前必须将
`services.notificationAudit.notificationDelivery.webhookUrl` 覆盖为真实的 HTTPS 接收端点，并在外部 Secret
中提供 `NOTIFICATION_WEBHOOK_SIGNING_SECRET`；接收端应校验 HMAC 签名和 `Idempotency-Key`。数据库初始化时还要预先创建
`flowmesh_audit_delivery` 投递维护角色，并确保 `flowmesh_audit` 不继承该角色，具体权限以通知投递 Flyway 迁移为准。

生产覆盖值同时默认渲染 Prometheus Operator `AlertmanagerConfig`。请预先创建包含 `url` 键的
`flowmesh-alertmanager-webhook` Secret，并让 Alertmanager 的 `alertmanagerConfigSelector` 选择
`release: kube-prometheus-stack` 标签；Webhook URL 不得写入 values 或命令行。部署后必须执行一条测试告警，验证触发和恢复通知均能到达值班渠道。

生产覆盖值默认启用 RocketMQ Producer 和 Consumer 的 TLS，并将访问通道设为 `CLOUD`；如果使用自建
RocketMQ 集群，发布流程可以将 `rocketmq.accessChannel` 覆盖为 `LOCAL`，但仍必须保留 TLS，并为生产者、消费者
配置具备最小权限的独立凭据。四个消息服务都会从外部 Secret 读取这些凭据，缺少任一 Secret 键时 Pod 不会启动。

生产覆盖值同时将应用和备份 PostgreSQL 的 `sslMode` 设为 `require`、Redis `sslEnabled` 设为 `true`，防止应用和备份任务到外部数据库、缓存的
连接回落到明文。若目标平台提供完整 CA 校验，应将 PostgreSQL 覆盖为 `verify-full`，并通过受控挂载提供 CA；本地 Compose
默认使用 `disable` 和关闭 Redis TLS，仅用于本地演示。

IAM 默认每小时清理保留超过 30 天的过期或撤销 Refresh Token；可通过
`services.iam.refreshTokenCleanup.intervalMs`、`retention` 和 `batchSize` 调整，生产环境不应关闭该任务。

生产环境还必须创建备份凭据 Secret。该 Secret 至少包含 `POSTGRES_PASSWORD`；如果集群不使用
云厂商工作负载身份，还需要包含 `AWS_ACCESS_KEY_ID`、`AWS_SECRET_ACCESS_KEY`，临时凭据可以额外
提供 `AWS_SESSION_TOKEN`。`backup.serviceAccountName` 用于绑定工作负载身份，不能把长期云凭据
写入 Helm values。

生产环境还必须预先创建生命周期维护账号 `flowmesh_retention` 和独立凭据 Secret。该账号必须是
非超级用户、具备 `BYPASSRLS`，并由服务迁移授予清理表的最小 `SELECT/DELETE` 权限，以及仅用于
`FOR UPDATE` 行锁键列的列级 `UPDATE` 权限；Secret 至少包含 `RETENTION_DB_PASSWORD`。生命周期 CronJob 默认每天运行一次，清理已发布 Outbox、DLQ、重放审计、Inbox
和请求幂等记录，不复用备份账号。发布时必须显式提供：

```bash
--set retention.postgres.host="postgres-primary.database.svc" \
--set retention.credentialsSecret="flowmesh-retention-credentials"
```

启用生产备份时，`backup.postgres.host` 和 `backup.s3.uri` 必须指向真实外部服务。CronJob 使用
`concurrencyPolicy: Forbid`，单次执行有明确截止时间，失败会按 `backoffLimit` 重试；备份完成后才
清理临时卷。示例：

```bash
helm upgrade --install flowmesh infra/helm/flowmesh \
  -f infra/helm/flowmesh/values-production.yaml \
  --set-string global.imageTag="$GITHUB_SHA" \
  --set-string workflowSla.imageDigest="$WORKFLOW_SLA_IMAGE_DIGEST" \
  --set backup.postgres.host="postgres-primary.database.svc" \
  --set backup.s3.uri="s3://flowmesh-production-backups" \
  --set backup.credentialsSecret="flowmesh-backup-credentials" \
  --set retention.postgres.host="postgres-primary.database.svc" \
  --set retention.credentialsSecret="flowmesh-retention-credentials" \
  --set backup.serviceAccountName="flowmesh-backup" \
  --set ingress.host="api.example.com" \
  --set 'ingress.tls[0].secretName=flowmesh-gateway-tls' \
  --set 'ingress.tls[0].hosts[0]=api.example.com' \
  --set global.existingSecret=flowmesh-runtime-secrets
```

对象存储地址和 PostgreSQL 地址属于部署环境配置，不能使用 Compose 中的 `postgres`、`localhost`
或单节点 MinIO 地址冒充生产备份目标。

检查部署状态：

```bash
kubectl get deploy,svc,pods -l app.kubernetes.io/instance=flowmesh
```

Chart 默认启用三个消费者和 Outbox。只有 gateway 应作为外部 API 入口，业务服务保持 ClusterIP。
应用 Pod 使用非 root 用户、只读根文件系统、默认
Seccomp 配置、资源请求/限制、启动/就绪/存活探针、拓扑分散和优雅终止配置。生产覆盖值启用
双副本、PodDisruptionBudget 和基于 CPU 的 HPA；集群必须安装 Metrics Server 才能使用 HPA。
`postgresql.host`、`redis.host`、`rocketmq.namesrvAddr`、镜像地址和端口均可在自定义 values
文件中覆盖。需要对外提供 HTTP API 时，设置 `ingress.enabled=true`、域名、TLS Secret
和 Ingress Controller 注解；Ingress 只转发到 gateway，业务服务仍保持 ClusterIP。
生产覆盖值只部署应用服务，PostgreSQL、Redis、RocketMQ、MinIO 和 ClamAV 必须由云托管服务或
经过 HA 验证的独立集群提供；发布前执行 `bash scripts/validate-production-config.sh`。
生产覆盖值还会启用 NetworkPolicy：IAM、Supplier 和 Workflow 只接受 Gateway 和监控命名空间的指标抓取入口，
Workflow 额外接受 Supplier 的内部状态回写请求；出站策略只允许同一发布内服务、集群 DNS
以及通过 `networkPolicy.egress.externalCidrs` 注入的外部依赖 CIDR 和固定端口。启用前应确认
集群 CNI 支持 NetworkPolicy，并根据 Prometheus Operator 的实际命名空间设置
`networkPolicy.monitoringNamespace`，再根据 PostgreSQL、Redis、RocketMQ、MinIO 和 ClamAV 的实际地址注入 CIDR。
Gateway 还只接受 `networkPolicy.ingressNamespace` 指定的 Ingress Controller 命名空间入口；如果平台使用其他命名空间，
必须在生产覆盖值中显式修改该字段，不能通过直接暴露 Gateway Service 绕过 Ingress。

生产覆盖值还会显式覆盖 PostgreSQL、Redis 和 RocketMQ NameServer 地址，防止继承本地 Compose 服务名；
这些 `*.internal.example.com` 仅是安全占位值，发布流程必须替换为真实的 HA 服务地址后再执行 Helm 发布。

如果目标集群安装了 Prometheus Operator，可通过以下参数启用六个应用 Service 的统一指标抓取：

```bash
helm upgrade --install flowmesh infra/helm/flowmesh \
  -f infra/helm/flowmesh/values-production.yaml \
  --set-string global.imageTag="$GITHUB_SHA" \
  --set-string workflowSla.imageDigest="$WORKFLOW_SLA_IMAGE_DIGEST" \
  --set 'networkPolicy.egress.externalCidrs[0]=10.20.0.0/16' \
  --set backup.postgres.host="postgres-primary.database.svc" \
  --set backup.s3.uri="s3://flowmesh-production-backups" \
  --set backup.credentialsSecret="flowmesh-backup-credentials" \
  --set retention.postgres.host="postgres-primary.database.svc" \
  --set retention.credentialsSecret="flowmesh-retention-credentials" \
  --set observability.serviceMonitor.enabled=true \
  --set observability.serviceMonitor.labels.release=kube-prometheus-stack \
  --set 'ingress.tls[0].secretName=flowmesh-gateway-tls' \
  --set 'ingress.tls[0].hosts[0]=api.example.com' \
  --set ingress.host=api.example.com \
  --set global.existingSecret=flowmesh-runtime-secrets
```

未安装 Prometheus Operator 时保持该选项关闭，并使用目标平台的 Service Discovery 或静态抓取配置。

如果需要同时加载 FlowMesh 告警规则，可在同一发布中启用 `PrometheusRule`。该资源覆盖服务不可用、
HTTP 5xx、Outbox 积压、死信、消费失败、消费延迟、Outbox 确认失败、外部通知积压/死信/失败，以及 PostgreSQL 备份、生命周期清理和
Workflow SLA CronJob 长时间未成功执行的告警。CronJob 告警依赖目标集群安装 kube-state-metrics；目标平台仍需配置
Alertmanager 路由、通知渠道和明确的值班责任：

```bash
helm upgrade --install flowmesh infra/helm/flowmesh \
  -f infra/helm/flowmesh/values-production.yaml \
  --set-string global.imageTag="$GITHUB_SHA" \
  --set-string workflowSla.imageDigest="$WORKFLOW_SLA_IMAGE_DIGEST" \
  --set 'networkPolicy.egress.externalCidrs[0]=10.20.0.0/16' \
  --set backup.postgres.host="postgres-primary.database.svc" \
  --set backup.s3.uri="s3://flowmesh-production-backups" \
  --set backup.credentialsSecret="flowmesh-backup-credentials" \
  --set retention.postgres.host="postgres-primary.database.svc" \
  --set retention.credentialsSecret="flowmesh-retention-credentials" \
  --set observability.prometheusRule.enabled=true \
  --set observability.prometheusRule.labels.release=kube-prometheus-stack \
  --set ingress.host="api.example.com" \
  --set 'ingress.tls[0].secretName=flowmesh-gateway-tls' \
  --set 'ingress.tls[0].hosts[0]=api.example.com' \
  --set global.existingSecret=flowmesh-runtime-secrets
```
