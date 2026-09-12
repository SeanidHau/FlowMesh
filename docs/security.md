# 安全规范

## 身份与会话

- 业务服务使用无状态 JWT，不启用 HTTP Basic、表单登录和 Session；健康探针匿名访问，业务 API 默认需要认证。
- IAM、supplier、workflow 和 notification-audit 显式关闭 Spring Security 默认用户自动配置，不生成未被业务使用的随机 Basic 用户密码。
- Access Token 有效期为 15 分钟。
- Refresh Token 有效期为 7 天。数据库仅存储 Refresh Token 哈希，支持轮换与撤销。
- 登出、用户禁用和密码重置必须写入审计日志。
- IAM 安全审计记录只能追加；数据库触发器拒绝 UPDATE、DELETE 和 TRUNCATE，应用层不得提供修改历史审计的接口。
- 高风险运维接口除验证 JWT 外，必须在服务端重新验证 `OPERATIONS` 权限。

## 租户隔离

- `tenant_id` 是所有租户业务表的必填列。
- 应用事务入口设置当前租户上下文，MyBatis Mapper 执行带租户边界的明确 SQL。
- PostgreSQL RLS 是最终防线。业务连接在事务开始时设置当前租户。
- 迁移账号、系统管理账号和业务服务账号必须分离。业务服务不得使用绕过 RLS 的高权限账号。
- 生命周期维护任务使用独立的 `flowmesh_retention` 账号。该账号不是超级用户，只具备 `BYPASSRLS`、明确清理表的 `SELECT/DELETE` 权限，以及仅用于 `FOR UPDATE` 行锁键列的列级 `UPDATE` 权限，不得复用备份账号。
- PostgreSQL 备份使用独立的可登录非超级用户账号；该账号必须具备 `BYPASSRLS` 以归档完整租户数据，对业务表只允许 `SELECT`，不得拥有建库、建角色、复制或业务表写权限。发布前使用 `scripts/validate-backup-role.sh` 只读核验。
- 任何越权 API、消息或文件访问必须拒绝，并保留审计记录。

IAM 的登录查询发生在用户尚未认证之前，因此当前实现将 IAM Schema 作为 RLS 例外。补偿控制包括：
IAM 使用独立业务账号、登录按请求中的 `tenantId` 和用户名查询、下游业务 Schema 强制启用 RLS，
并通过 JWT 签名租户声明和认证审计记录保留安全边界。后续增加 IAM 管理接口时，必须重新评估并补充
认证前租户上下文策略。

## Secret 管理

- 本地开发使用 Git 忽略的 `.env`。
- Kubernetes 使用 Secret；CI 使用 GitHub Actions Secrets。
- 仓库只能提交 `.env.example` 和 Secret 模板，不能提交真实密钥、Token、证书或密码。
- Secret 不得写入镜像、日志、错误响应或测试快照。
- 主分支镜像使用完整 Git SHA、Trivy 和 Cosign keyless 签名；Kubernetes 可通过 `infra/policies/kyverno/verify-flowmesh-images.yaml` 拒绝未签名镜像。
- GitHub Actions 工作流统一固定到完整提交 SHA，并在行尾保留对应版本标签作为人工可读说明；CI 契约会拒绝可变版本标签。
- GitHub Actions 的 `actions/checkout` 统一关闭 `persist-credentials`，避免将工作流 Token 持久化到 Runner 工作区。

## 文件访问

- MinIO Bucket 必须保持私有。
- 对象键格式为 `tenantId/applicationId/randomObjectId.extension`，不使用用户原始文件名作为路径。
- 后端只在验证租户、角色、申请状态、类型和大小后签发短时预签名 URL。
- 当前接受 PDF、PNG、JPG 和 DOCX，单文件最大 20 MB；同时校验 MIME、文件头和 SHA-256。
- 生产环境必须启用 ClamAV 同步扫描；扫描引擎不可用时拒绝上传，未启用扫描的本地开发文件会标记为 `SKIPPED`。

## Kubernetes 安全基线

- 工作负载使用非 root 用户、只读根文件系统，并禁止特权提升。
- Secret 通过环境变量或挂载文件注入。
- 生产 NetworkPolicy 同时限制入站和出站，只放通同一发布内服务、集群 DNS，以及由发布流程注入的外部依赖 CIDR 和固定端口；发布入口拒绝 `0.0.0.0/0` 与 `::/0`，避免误放通全网出口。
- kind 是否实际执行 NetworkPolicy 取决于 CNI，运行手册必须注明当前环境条件。
