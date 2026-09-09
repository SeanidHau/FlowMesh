# 脚本目录

本目录将保存以下可执行脚本：

- `bootstrap.sh`：初始化本地开发环境。
- `verify.sh`：执行格式、测试和基础验证。
- `backup-postgres.sh`：导出 PostgreSQL 数据库和角色定义。
- `restore-postgres.sh`：将 PostgreSQL custom-format 备份恢复到目标数据库。
- `verify-postgres-backup.sh`：不连接数据库，校验备份文件完整性和可读性。
- `validate-production-config.sh`：检查生产 Helm 覆盖值是否启用外部依赖、NetworkPolicy 和安全扫描。

备份完成后执行 `./scripts/verify-postgres-backup.sh ./backups/postgres/<timestamp>`，确认
custom-format 归档可被 `pg_restore` 读取。

备份文件默认写入被 Git 忽略的 `backups/` 目录。生产环境应将备份目录同步到独立、加密且具备生命周期策略的对象存储；恢复前必须完成审批和目标数据库隔离确认。
