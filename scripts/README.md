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
- `verify-flowmesh-images.sh`：部署前验证六个应用镜像和一个备份镜像均具备受信任 GitHub Actions 签名。
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

备份文件默认写入被 Git 忽略的 `backups/` 目录。生产环境使用 Helm `CronJob` 定时执行备份。备份镜像
通过 `FLOWMESH_PG_SSLMODE` 控制 PostgreSQL 传输加密；生产覆盖值默认使用 `require`，目标平台提供 CA 后可改为
`verify-full`。备份镜像包含 PostgreSQL 客户端和 AWS CLI，脚本会先创建 custom-format 归档，再将三个归档文件上传到 S3 兼容对象存储，
最后上传 `_SUCCESS` 标记。恢复工具或平台只应使用存在 `_SUCCESS` 标记的备份前缀。
默认使用 `AES256` 服务端加密，也可以通过 `FLOWMESH_BACKUP_S3_SSE=aws:kms` 和
`FLOWMESH_BACKUP_S3_KMS_KEY_ID` 使用 KMS 密钥。恢复前必须完成审批和目标数据库隔离确认；对象存储仍需
由平台配置跨故障域复制、生命周期和访问审计策略。

生产部署前执行镜像签名校验：

```bash
FLOWMESH_IMAGE_TAG="$GITHUB_SHA" ./scripts/verify-flowmesh-images.sh
```
