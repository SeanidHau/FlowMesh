# 安全规范

## 身份与会话

- Access Token 有效期为 15 分钟。
- Refresh Token 有效期为 7 天。数据库仅存储 Refresh Token 哈希，支持轮换与撤销。
- 登出、用户禁用和密码重置必须写入审计日志。
- 高风险运维接口除验证 JWT 外，必须在服务端重新验证 `OPERATIONS` 权限。

## 租户隔离

- `tenant_id` 是所有租户业务表的必填列。
- 应用事务入口设置当前租户上下文，MyBatis Mapper 执行带租户边界的明确 SQL。
- PostgreSQL RLS 是最终防线。业务连接在事务开始时设置当前租户。
- 迁移账号、系统管理账号和业务服务账号必须分离。业务服务不得使用绕过 RLS 的高权限账号。
- 任何越权 API、消息或文件访问必须拒绝，并保留审计记录。

IAM 的登录查询发生在用户尚未认证之前，因此当前 MVP 将 IAM Schema 作为 RLS 例外。补偿控制包括：
IAM 使用独立业务账号、登录按请求中的 `tenantId` 和用户名查询、下游业务 Schema 强制启用 RLS，
并通过 JWT 签名租户声明和认证审计记录保留安全边界。后续增加 IAM 管理接口时，必须重新评估并补充
认证前租户上下文策略。

## Secret 管理

- 本地开发使用 Git 忽略的 `.env`。
- Kubernetes 使用 Secret；CI 使用 GitHub Actions Secrets。
- 仓库只能提交 `.env.example` 和 Secret 模板，不能提交真实密钥、Token、证书或密码。
- Secret 不得写入镜像、日志、错误响应或测试快照。

## 文件访问

- MinIO Bucket 必须保持私有。
- 对象键格式为 `tenantId/applicationId/fileId`。
- 后端只在验证租户、角色、申请状态、类型和大小后签发短时预签名 URL。
- 首版接受 PDF、PNG、JPG，单文件最大 10 MB。
- 病毒扫描作为后续异步事件能力；未实现前不得宣称文件已通过病毒扫描。

## Kubernetes 安全基线

- 工作负载使用非 root 用户、只读根文件系统，并禁止特权提升。
- Secret 通过环境变量或挂载文件注入。
- 默认 NetworkPolicy 拒绝访问，只放通必要的服务和依赖链路。
- kind 是否实际执行 NetworkPolicy 取决于 CNI，运行手册必须注明当前环境条件。
