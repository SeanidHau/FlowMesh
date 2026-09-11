# 脚本目录

本目录将保存以下可执行脚本：

- `bootstrap.sh`：初始化本地开发环境。
- `verify.sh`：执行格式、测试和基础验证。
- `backup-postgres.sh`：导出 PostgreSQL 数据库和角色定义。
- `restore-postgres.sh`：将 PostgreSQL custom-format 备份恢复到目标数据库。
- `verify-postgres-backup.sh`：不连接数据库，校验备份文件完整性和可读性。
- `infra/backup/entrypoint.sh`：在备份容器中调用 PostgreSQL 备份和对象存储上传流程。
- `validate-production-config.sh`：检查生产 Helm 覆盖值是否启用外部依赖、NetworkPolicy 和安全扫描。
- `validate-production-dependencies.sh`：在目标生产网络内只读检查 PostgreSQL、Redis、RocketMQ NameServer 和对象存储的连接安全与基础可达性。
- `configure-object-storage-lifecycle.sh`：为专用材料桶启用版本化，并配置逻辑删除对象的非当前版本保留期。
- `cleanup-flowmesh-retention.sh`：使用专用维护账号按白名单批量清理终态消息、死信、重放审计、Inbox 和幂等记录。
- `validate-retention-role.sh`：只读核验生命周期维护账号的角色属性、RLS 能力和精确表/列权限。
- `verify-flowmesh-images.sh`：部署前验证六个应用镜像、备份镜像和生命周期维护镜像均具备受信任 GitHub Actions 签名。
- `run-production-acceptance.sh`：串联生产发布后的只读验收并生成不可覆盖的证据报告。
- `create-production-evidence-manifest.sh`：为目标平台已生成的证据报告创建不可覆盖的清单和 SHA-256 校验和。
- `validate-production-evidence.sh`：只读校验目标环境证据包的必需报告、通过状态、校验和和敏感信息边界。
- `validate-runtime-observability.sh`：只读检查目标 Prometheus、Alertmanager、FlowMesh targets 和关键告警规则。
- `validate-production-ha.sh`：只读检查 PostgreSQL、Redis、RocketMQ NameServer 和对象存储的生产 HA 拓扑证据。
- `validate-observability.sh`：校验 Prometheus 配置和 Grafana Dashboard 的基本结构。
- `validate-supply-chain-policy.sh`：校验 Kyverno 镜像签名准入策略的仓库、digest 和 OIDC 约束。

备份完成后执行 `./scripts/verify-postgres-backup.sh ./backups/postgres/<timestamp>`，确认
SHA-256 校验清单和 custom-format 归档均可读取。`backup-postgres.sh` 使用
`--no-role-passwords` 导出全局对象，不把数据库角色密码写入归档。恢复脚本会在写入目标数据库前再次执行同一校验。

在具备 Docker 的环境中执行真实备份恢复回归：

```bash
./tests/postgres-backup-e2e.sh
```

该脚本在临时 PostgreSQL 容器中写入探针数据，创建备份，校验归档，恢复到独立数据库，最后读取恢复后的数据并删除测试资源。

观测配置校验不会启动 Prometheus 或 Grafana，也不替代真实环境验证：

```bash
./scripts/validate-observability.sh
```

目标环境发布后可使用 `run-production-acceptance.sh` 串联镜像签名、Kubernetes smoke、外部依赖 TLS 预检和生命周期角色权限预检；生产验收默认还会检查目标 Prometheus、Alertmanager、六个 FlowMesh 服务目标和关键告警规则，
检查 PostgreSQL 主库复制数、Redis 主从端点、至少两个 RocketMQ NameServer TLS 端点和对象存储 HTTPS，
并校验目标环境证据包中的 Kubernetes、依赖 HA、运行时观测、恢复、备份恢复、压测、跨租户安全回归和告警路由报告，
再将每项结果写入不可覆盖的 Markdown 报告。非生产预检只有在显式设置对应 `FLOWMESH_REQUIRE_*` 变量为 `false` 时才会跳过检查。
该脚本只读，不执行集群写操作或故障切换；具体变量和示例见 [运行手册](../docs/runbook.md)。

目标平台完成各项演练后，使用 `create-production-evidence-manifest.sh` 为已有报告生成
`manifest.md` 和 `checksums.sha256`；该工具不会创建或修改任何演练报告，且会在报告校验失败时删除本次生成的清单与校验和：

```bash
FLOWMESH_EVIDENCE_DIR='./artifacts/flowmesh-production-evidence' \
FLOWMESH_EVIDENCE_ENVIRONMENT='production-cn-shanghai' \
FLOWMESH_EVIDENCE_OPERATOR='oncall@example.invalid' \
FLOWMESH_IMAGE_TAG="$GITHUB_SHA" \
./scripts/create-production-evidence-manifest.sh
```

备份文件默认写入被 Git 忽略的 `backups/` 目录。生产环境使用 Helm `CronJob` 定时执行备份。备份镜像
通过 `FLOWMESH_PG_SSLMODE` 控制 PostgreSQL 传输加密；生产覆盖值默认使用 `require`，目标平台提供 CA 后可改为
`verify-full`。备份镜像包含 PostgreSQL 客户端和 AWS CLI，脚本会先创建 custom-format 归档，再将三个归档文件上传到 S3 兼容对象存储，
最后上传 `_SUCCESS` 标记。恢复工具或平台只应使用存在 `_SUCCESS` 标记的备份前缀。
生产 Helm 默认开启远端对象校验：三个归档对象均通过 S3 `head-object` 确认可见后，才会上传 `_SUCCESS` 标记；
因此恢复流程不会把部分上传目录当成完整备份。
默认使用 `AES256` 服务端加密，也可以通过 `FLOWMESH_BACKUP_S3_SSE=aws:kms` 和
`FLOWMESH_BACKUP_S3_KMS_KEY_ID` 使用 KMS 密钥。恢复前必须完成审批和目标数据库隔离确认；对象存储仍需
由平台配置跨故障域复制和访问审计策略。FlowMesh 提供生命周期配置脚本，执行前必须确认材料桶为专用桶：

```bash
FLOWMESH_OBJECT_STORAGE_BUCKET=flowmesh-documents \
FLOWMESH_OBJECT_STORAGE_ENDPOINT='https://object-storage.example.com' \
FLOWMESH_OBJECT_STORAGE_NONCURRENT_RETENTION_DAYS=7 \
./scripts/configure-object-storage-lifecycle.sh
```

生产部署前执行镜像签名校验：

```bash
FLOWMESH_IMAGE_TAG="$GITHUB_SHA" ./scripts/verify-flowmesh-images.sh
```

生命周期清理通过独立的 `flowmesh_retention` 账号执行。该账号不是超级用户，但必须具备
`BYPASSRLS`，并由各服务迁移仅授予消息和幂等表的 `SELECT/DELETE` 权限，以及仅用于 `FOR UPDATE`
行锁键列的列级 `UPDATE` 权限。生产 Helm 会创建每日
CronJob；执行前必须在目标数据库预置该账号，并将密码放入独立 Secret，不要复用备份账号。

部署或轮换凭据后执行只读预检：

```bash
FLOWMESH_PG_HOST=postgres-primary.database.svc \
FLOWMESH_PG_DATABASE=flowmesh \
FLOWMESH_RETENTION_DB_PASSWORD="$RETENTION_DB_PASSWORD" \
./scripts/validate-retention-role.sh
```

预检会拒绝超级用户、可建库/建角色、角色继承、额外业务表 `SELECT` 权限和非锁键列 `UPDATE`
权限；它不会创建角色、修改授权或执行删除。
