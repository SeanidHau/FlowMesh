# 脚本目录

本目录将保存以下可执行脚本：

- `bootstrap.sh`：初始化本地开发环境。
- `verify.sh`：执行格式、测试和基础验证。
- `backup-postgres.sh`：导出 PostgreSQL 数据库和角色定义。
- `restore-postgres.sh`：将 PostgreSQL custom-format 备份恢复到目标数据库。
- `verify-postgres-backup.sh`：不连接数据库，校验备份文件完整性和可读性。
- `validate-production-config.sh`：检查生产 Helm 覆盖值是否启用外部依赖、NetworkPolicy 和安全扫描。
- `verify-flowmesh-images.sh`：部署前验证六个提交 SHA 镜像均具备受信任 GitHub Actions 签名。
- `validate-observability.sh`：校验 Prometheus 配置和 Grafana Dashboard 的基本结构。
- `validate-supply-chain-policy.sh`：校验 Kyverno 镜像签名准入策略的仓库、digest 和 OIDC 约束。

备份完成后执行 `./scripts/verify-postgres-backup.sh ./backups/postgres/<timestamp>`，确认
SHA-256 校验清单和 custom-format 归档均可读取。恢复脚本会在写入目标数据库前再次执行同一校验。

观测配置校验不会启动 Prometheus 或 Grafana，也不替代真实环境验证：

```bash
./scripts/validate-observability.sh
```

备份文件默认写入被 Git 忽略的 `backups/` 目录。生产环境应将备份目录同步到独立、加密且具备生命周期策略的对象存储；恢复前必须完成审批和目标数据库隔离确认。

生产部署前执行镜像签名校验：

```bash
FLOWMESH_IMAGE_TAG="$GITHUB_SHA" ./scripts/verify-flowmesh-images.sh
```
