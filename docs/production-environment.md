# 生产环境实施手册

本文说明如何把 FlowMesh 部署到真实 Kubernetes 生产环境。本文不提供任何真实凭据，也不会把本地 Compose 拓扑当作生产 HA 证据。

## 1. 适用范围

本文适用于以下发布流程：

1. 生产平台预先创建 PostgreSQL、Redis、RocketMQ、对象存储、ClamAV、Prometheus、Alertmanager 和 Kubernetes 集群。
2. 生产平台预先创建 Kubernetes Secret、TLS Secret、GitHub `production` Environment 和自托管 Runner。
3. GitHub Actions 使用完整 Git 提交 SHA 构建、扫描并签名镜像。
4. 生产发布工作流调用 `scripts/deploy-production.sh`，生产验收工作流调用 `scripts/run-production-acceptance.sh`。

生产发布入口不会创建数据库、Redis、RocketMQ 或对象存储，也不会通过命令行传递业务凭据。缺少任一外部依赖或 Secret 时，应停止发布并补齐平台配置。

## 2. 目标平台前置条件

### 2.1 Kubernetes

- Kubernetes 集群已启用 NetworkPolicy、Metrics Server 和默认 Seccomp 支持。
- 集群已安装 Ingress Controller，并且 Ingress Controller 会覆盖可信的客户端地址请求头。
- 集群已安装 Kyverno，并应用 `infra/policies/kyverno/verify-flowmesh-images.yaml`。
- 集群已安装 Prometheus Operator；如果不使用 Prometheus Operator，发布时将 `expect_prometheus_rule` 设置为 `false`，并由平台单独配置指标抓取和告警规则。
- 自托管 Runner 同时具备 `bash`、`helm`、`kubectl`、`cosign`、`curl`、`openssl`、`psql`、`pg_isready`、`redis-cli` 和 `ruby`。
- 自托管 Runner 标签为 `self-hosted`、`linux`、`flowmesh-production`，并且只能访问目标生产集群和依赖网络。

### 2.2 外部依赖

生产环境必须提供可验证的 HA 拓扑：

| 依赖 | 最低要求 | 应用侧约束 |
| --- | --- | --- |
| PostgreSQL | 主库复制数和故障切换机制可验证 | 使用 TLS；备份账号和生命周期账号必须独立于业务账号 |
| Redis | 主从或托管 HA 端点可验证 | 使用 TLS；仅承载限流等派生状态，不承载认证事实 |
| RocketMQ | 至少两个 NameServer，Broker 和持久卷跨故障域 | Producer、Consumer 使用独立凭据和 TLS |
| 对象存储 | HTTPS、版本化和跨故障域复制策略 | 材料桶使用私有访问和生命周期策略 |
| ClamAV | 扫描服务可用且生产配置启用 | 扫描不可用时拒绝材料上传 |
| 观测平台 | Prometheus、Alertmanager、日志和 Trace 后端可查询 | 关键告警必须有通知接收端和值班流程 |

## 3. Kubernetes Secret

生产平台应在发布前创建以下 Secret。Secret 名称通过 GitHub Environment Variables 注入，Secret 内容不能写入仓库、Helm values 或 GitHub Actions 日志。

### 3.1 应用运行时 Secret

`FLOWMESH_RUNTIME_SECRET_NAME` 指向的 Secret 至少需要包含：

- JWT 签名密钥。
- Redis 密码或认证信息。
- IAM、Supplier、Workflow、Risk、Notification Audit 的 PostgreSQL 密码。
- IAM、Supplier、Workflow、Risk、Notification Audit 的 Flyway 迁移账号密码；迁移账号必须与运行时业务账号不同。
- RocketMQ Producer 和 Consumer 的独立凭据。
- RocketMQ TLS 所需的认证信息。
- MinIO/对象存储访问密钥。
- ClamAV 相关认证或连接配置（如果目标平台启用认证）。
- 外部通知 Webhook 签名密钥。

实际键名以 `infra/helm/flowmesh/templates/` 中的 Secret 引用为准。发布前应使用 Kubernetes Secret 检查脚本和发布后的 smoke test 验证必需键存在；不要在终端打印 Secret 内容。

### 3.2 维护任务 Secret

生产平台还需要创建以下独立 Secret：

- `FLOWMESH_BACKUP_SECRET_NAME`：PostgreSQL 备份账号、对象存储访问密钥和备份加密配置。
- `FLOWMESH_RETENTION_SECRET_NAME`：生命周期维护账号密码。
- Workflow SLA 维护账号所需的 Secret 键。
- `FLOWMESH_POSTGRES_CA_SECRET_NAME` 指向的 PostgreSQL CA Secret：默认名称为 `flowmesh-postgresql-ca`，必须包含 `ca.crt`；生产 Helm 默认使用 `verify-full`，缺少该 Secret 时应用不会就绪。

备份账号必须通过 `scripts/validate-backup-role.sh` 验证；生命周期账号必须通过 `scripts/validate-retention-role.sh` 验证。两个账号都不得使用 `postgres` 或业务账号。

## 4. GitHub Environment 配置

GitHub `production` Environment 需要启用人工审批、分支保护和部署记录。下表列出工作流使用的配置名；值由目标平台管理员填写。

### 4.1 Variables

| 配置名 | 用途 |
| --- | --- |
| `FLOWMESH_RUNTIME_SECRET_NAME` | 应用运行时 Secret 名称 |
| `FLOWMESH_BACKUP_SECRET_NAME` | 备份 CronJob Secret 名称 |
| `FLOWMESH_RETENTION_SECRET_NAME` | 生命周期维护 CronJob Secret 名称 |
| `FLOWMESH_INGRESS_NAMESPACE` | Ingress Controller 命名空间 |
| `FLOWMESH_MONITORING_NAMESPACE` | Prometheus 命名空间 |
| `FLOWMESH_PROMETHEUS_RELEASE` | Prometheus Operator release 标签 |
| `FLOWMESH_INGRESS_HOST` | 生产 API 域名 |
| `FLOWMESH_INGRESS_TLS_SECRET_NAME` | API TLS Secret 名称 |
| `FLOWMESH_POSTGRES_HOST` | 应用 PostgreSQL 地址 |
| `FLOWMESH_POSTGRES_CA_SECRET_NAME` | PostgreSQL CA Secret 名称，默认 `flowmesh-postgresql-ca` |
| `FLOWMESH_REDIS_HOST` | Redis 地址 |
| `FLOWMESH_ROCKETMQ_NAMESRV_ADDR` | 逗号分隔的 NameServer 地址，至少两个 |
| `FLOWMESH_OBJECT_STORAGE_ENDPOINT` | HTTPS 对象存储地址 |
| `FLOWMESH_CLAMAV_HOST` | ClamAV 地址 |
| `FLOWMESH_BACKUP_POSTGRES_HOST` | 备份数据库地址 |
| `FLOWMESH_BACKUP_POSTGRES_USER` | 专用只读备份账号 |
| `FLOWMESH_BACKUP_S3_URI` | 备份对象存储 URI |
| `FLOWMESH_NOTIFICATION_WEBHOOK_URL` | HTTPS 外部通知 Webhook |
| `FLOWMESH_NETWORK_POLICY_EXTERNAL_CIDRS` | 外部依赖出口 CIDR，逗号分隔 |
| `FLOWMESH_IMAGE_PULL_SECRET_NAME` | 可选，私有镜像仓库的 Kubernetes image pull Secret 名称 |
| `FLOWMESH_WORKFLOW_SLA_IMAGE_DIGEST` | PostgreSQL 客户端镜像的不可变 `sha256:` digest，必须与审核的 `postgres:16.15` 多架构镜像一致 |

生产验收工作流还需要以下只读检查变量：

`FLOWMESH_PG_HOST`、`FLOWMESH_PG_PORT`、`FLOWMESH_PG_DATABASE`、`FLOWMESH_PG_USER`、`FLOWMESH_PG_SSLMODE`、`FLOWMESH_PG_SSLROOTCERT`、`FLOWMESH_BACKUP_DB_USER`、`FLOWMESH_RETENTION_DB_USER`、`FLOWMESH_REDIS_HOST`、`FLOWMESH_REDIS_PORT`、`FLOWMESH_REDIS_USER`、`FLOWMESH_ROCKETMQ_NAMESRV_ADDR`、`FLOWMESH_ROCKETMQ_CA_FILE`、`FLOWMESH_OBJECT_STORAGE_ENDPOINT`、`FLOWMESH_HA_EXPECTED_PG_REPLICAS`、`FLOWMESH_HA_REDIS_HOSTS`、`FLOWMESH_HA_EXPECTED_REDIS_REPLICAS`、`FLOWMESH_PROMETHEUS_URL`、`FLOWMESH_ALERTMANAGER_URL`、`FLOWMESH_INGRESS_NAMESPACE`、`FLOWMESH_RUNTIME_SECRET_NAME`、`FLOWMESH_BACKUP_SECRET_NAME`、`FLOWMESH_BACKUP_POSTGRES_USER`、`FLOWMESH_RETENTION_SECRET_NAME` 和 `FLOWMESH_POSTGRES_CA_SECRET_NAME`。

### 4.2 Secrets

至少需要配置以下 GitHub Environment Secrets：

- `FLOWMESH_PG_PASSWORD`
- `FLOWMESH_BACKUP_DB_PASSWORD`
- `FLOWMESH_RETENTION_DB_PASSWORD`
- `FLOWMESH_REDIS_PASSWORD`

如果目标 Redis 使用 ACL，还需要在 Helm 发布参数中设置 `redis.username`；单密码认证模式保持为空。

如果目标平台要求 RocketMQ、对象存储或其他依赖通过 GitHub Actions 读取证书内容，应使用 GitHub Secret 或 Runner 的受保护文件路径，并确认脚本不会把内容写入报告。证书文件路径可以通过变量传入，但证书内容不能写入变量值。

## 5. 首次上线顺序

### 5.1 发布前

1. 目标平台创建 Kubernetes namespace、运行时 Secret、维护任务 Secret 和 Ingress TLS Secret。
2. 平台管理员创建五个业务账号、五个 Flyway 迁移账号、备份账号、生命周期账号和 Workflow SLA 账号，并执行 Flyway 初始化所需的 Schema 所有权与默认权限准备工作；业务账号不得拥有 Schema DDL 权限。已有数据库先执行 `scripts/prepare-postgres-migration-roles.sh`，新数据库由 Compose 初始化脚本或平台初始化流程创建对应角色。
3. 执行 `scripts/validate-production-dependencies.sh`，确认 PostgreSQL、Redis、RocketMQ 和对象存储满足 TLS 与连通性要求。
4. 执行 `scripts/validate-backup-role.sh` 和 `scripts/validate-retention-role.sh`，确认维护账号满足最小权限要求。
5. 在 GitHub `production` Environment 配置 Variables、Secrets、审批规则和自托管 Runner 标签。
6. 使用待发布提交 SHA 检查 GHCR 镜像签名：

   ```bash
   FLOWMESH_IMAGE_TAG=<40 位提交 SHA> ./scripts/verify-flowmesh-images.sh
   ```

### 5.2 部署

1. 从 `main` 分支手动触发 `Production deploy` 工作流。
2. 输入待发布的完整 40 位提交 SHA、namespace、Helm release 和 Prometheus Operator 选项。
3. 通过 `production` Environment 审批。
4. 工作流执行 `helm upgrade --install --atomic --wait`，随后执行 Kubernetes smoke test。
5. 检查部署日志、Pod 就绪状态、Ingress、PDB、HPA、NetworkPolicy、镜像提交 SHA 和 CronJob 配置。

如果 GHCR 或目标镜像仓库不是公开可读，先创建包含拉取权限的 Kubernetes Secret，并在生产发布工作流的 Environment Variable 中设置
`FLOWMESH_IMAGE_PULL_SECRET_NAME`。该变量只传递 Secret 名称，不传递仓库凭据；发布入口会将它注入应用和维护 CronJob 的 Pod。

发布后不得直接修改生产 Pod。配置变更必须提交代码或 Helm values，并重新执行同一发布流程。

### 5.3 验收

生产验收必须使用与部署相同的镜像提交 SHA，并且证据包必须由目标平台实际生成。证据包至少包含：

`kubernetes-smoke.md`、`dependency-ha.md`、`runtime-observability.md`、`service-recovery.md`、`backup-restore.md`、`load-test.md`、`security-regression.md` 和 `alert-routing.md`。

每一份报告都必须在正文中包含完全一致的绑定字段，防止把不同版本或不同集群的报告拼接到同一个清单：

```text
- 环境标识：`production-cluster-a`
- 镜像提交：`<与发布相同的 40 位提交 SHA>`
```

使用以下命令生成清单和校验和。命令不会生成或修改演练报告：

```bash
FLOWMESH_EVIDENCE_DIR=/secure/path/flowmesh-evidence \
FLOWMESH_EVIDENCE_ENVIRONMENT=production-cluster-a \
FLOWMESH_EVIDENCE_OPERATOR=operator-name \
FLOWMESH_IMAGE_TAG=<与发布相同的 40 位提交 SHA> \
./scripts/create-production-evidence-manifest.sh
```

确认清单生成成功后，从 `main` 分支手动触发 `Production acceptance` 工作流，并填写同一个 `evidence_environment`。执行 `Production recovery drill` 时还必须填写实际已部署的 `image_tag` 和同一个 `evidence_environment`；工作流通过环境变量传递手工输入，避免将输入直接拼入 Shell。该验收工作流会强制开启运行时观测、外部依赖 HA 和生产证据包门禁；任一报告缺失、环境或镜像绑定不一致、失败、校验和不一致或包含敏感信息，验收都会失败。

## 6. 回滚与停止条件

### 6.1 自动回滚

生产发布后的 smoke test 失败时，`scripts/deploy-production.sh` 会执行以下动作：

- 已存在 Helm release：回滚到发布前 revision。
- 首次安装：卸载本次应用资源并保留 Helm history。
- 自动回滚失败：停止后续操作，由值班人员人工处置。

### 6.2 必须停止发布的情况

- 镜像签名校验失败。
- 生产 values 仍包含示例地址或本地依赖地址。
- 外部依赖 HA 预检失败。
- 备份账号或生命周期账号权限预检失败。
- Secret 缺少必需键。
- Kubernetes smoke test 失败。
- 告警路由或备份恢复证据未通过。

停止发布后，保留工作流日志和验收报告。不要删除失败现场，直到完成故障分析和审计归档。

## 7. 完成判定

FlowMesh 只有同时满足以下条件，才能标记为生产完成：

1. 目标 Kubernetes 集群已完成不可变镜像部署和 smoke test。
2. PostgreSQL、Redis、RocketMQ 和对象存储已完成 HA 或恢复演练。
3. Prometheus、Alertmanager、日志和 Trace 后端已接入，并完成通知和值班演练。
4. 备份恢复、RTO/RPO、压测、跨租户安全回归和应用 Pod 恢复报告均为 `PASS`。
5. 证据包已由 `scripts/create-production-evidence-manifest.sh` 生成清单和 SHA-256 校验和，并通过 `scripts/validate-production-evidence.sh`。
6. `Production acceptance` 工作流通过，并且报告中的镜像提交 SHA 与发布版本一致。

没有目标平台证据时，只能称为「生产化代码基线」，不能称为「已完成生产上线」。
