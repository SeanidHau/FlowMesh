# FlowMesh 补齐需求与验收标准

## 1. 文档目的

本文记录 MVP-3 之后需要补齐的能力、实施顺序和验收标准。只有代码、测试和运行验证同时满足要求，才将项目标记为“已完成”。本轮已完成 MVP-4 范围内的可靠消息、运营处置、可观测性和工程验证；后续产品能力仍单独保留。

## 2. 当前基线

MVP-3 已完成 IAM 认证、Supplier 申请、Workflow 审批投影、JWT/RBAC、PostgreSQL RLS、MyBatis 持久化和 RocketMQ Transactional Outbox 基础链路。

当前 Outbox 已具备数据库认领租约、指数退避、失败终态、死信查询、受控重放和审计；事件信封/业务 ID 校验、业务与消息指标、HTTP/事件 Trace 标识、依赖就绪探针、真实 RocketMQ E2E、并发竞争测试和跨服务对账均已接入。Redis 用于 Gateway 分布式入口限流和 IAM 登录尝试限流，数据库仍是认证与幂等的最终事实源。

## 3.1 本轮实施状态

| 状态 | 内容 |
| --- | --- |
| 已完成 | MVP-4 全部需求：消息契约与幂等、Outbox ACK/退避/死信、多实例认领、发送前续租、租约失效跳过发送、DLQ 查询/重放/审计、跨服务对账、业务与消息指标、Trace 标识、数据库和 RocketMQ 就绪探针、PostgreSQL 集成测试、真实 RocketMQ E2E、CI 和资源回收。 |
| 已完成 | Redis 登录尝试限流已完成；本地 Redis 故障时认证链路降级放行并记录告警，生产 Helm 默认 fail-closed 并返回 `503`，不改变数据库权威性。 |
| 已完成 | 供应商材料上传、MinIO 私有对象存储、文件安全校验和短期下载授权已接入；生产环境必须启用 ClamAV。 |
| 已完成 | 独立 risk-service 已接入 `RiskCheckRequested` / `RiskCheckCompleted` 事件链，流程先风控后审批；当前规则为可复现模拟规则。 |
| 已完成 | notification-audit-service 已消费 `SupplierActivated`，以 Inbox 幂等写入审计事件和申请人站内通知；提供按租户/用户隔离的查询和幂等标记已读接口；外部 Webhook 投递支持租约认领、发送前续租、HMAC 签名、幂等键、退避和死信。 |
| 已完成 | Workflow 审批任务已持久化；采购初审后法务与财务并行会签，API 返回 `availableTasks` / `completedTasks`，任务行锁和乐观锁共同防止重复完成及并发覆盖。 |
| 已完成 | RocketMQ 消费者已暴露处理耗时直方图，Prometheus 已增加消费 P95 延迟告警；观测配置脚本会校验关键告警集合。 |
| 已完成 | risk-service 提供默认关闭的 `FAIL` / `TIMEOUT` 故障注入，用于复现 RocketMQ 重试、DLQ 和人工处置场景；生产 Helm 显式关闭该开关。 |
| 已完成 | PostgreSQL 备份脚本不导出角色密码，并通过临时 PostgreSQL 容器 E2E 验证归档完整性、隔离数据库恢复和测试资源清理；CI 会执行该回归。 |
| 已完成 | 提供只读 PostgreSQL 备份角色权限预检，核验非超级用户、`BYPASSRLS`、业务表只读和 Schema 权限；生产验收会在备份恢复证据之外执行该门禁。 |
| 已完成 | PostgreSQL 备份镜像和 Helm CronJob 已支持定时执行、S3 兼容对象存储上传、服务端加密、并发互斥和失败重试；生产渲染要求显式提供外部数据库地址、备份 URI 和凭据 Secret。 |
| 已完成 | 提供对象存储生命周期配置脚本和离线契约测试；专用材料桶启用版本化，逻辑删除对象的非当前版本默认保留 7 天。 |
| 已完成 | 六个服务提供默认关闭的 Micrometer Tracing 和 OTLP/HTTP 导出配置；生产 Helm 在开启导出但缺少 Collector 地址时拒绝渲染。 |
| 已完成 | Helm 提供可选 Prometheus Operator `PrometheusRule` 和 `AlertmanagerConfig`；CI 验证启用时能够渲染关键服务、HTTP、消息告警规则及外部 Webhook Secret 引用。目标平台仍需让 Alertmanager 选择资源并验证通知。 |
| 已完成 | 生产 NetworkPolicy 已允许可配置监控命名空间访问 Actuator 指标端口，CI 验证生产渲染包含该入口。 |
| 已完成 | 提供只读 Kubernetes 生产 smoke test，验证发布后的 Deployment、提交 SHA 镜像、PDB/HPA/NetworkPolicy、Secret 和备份 CronJob。 |
| 已完成 | Gateway 已接入 Redis Lua 令牌桶限流，Redis 故障 fail-closed 返回 `503`、额度耗尽返回 `429`，并提供 Micrometer 指标、Prometheus 告警和本地 Alertmanager 路由基线。 |
| 已完成 | Gateway 和 IAM 的 `production` profile 将 Redis 健康检查纳入 readiness；依赖不可用时实例会从服务发现中摘除，本地和测试 profile 不被 Redis 强制阻塞，并由 `tests/readiness-dependency-contract.sh` 防止配置回归。 |
| 已完成 | IAM Refresh Token 已提供带保留窗口、批量上限和 `FOR UPDATE SKIP LOCKED` 的多副本安全清理任务，并通过 PostgreSQL 集成测试验证失效令牌删除不会影响有效令牌。 |
| 已完成 | 审批补件闭环已落地：审批意见、退回任务、申请人补件历史、幂等提交、最多两轮重审和跨服务 `SupplementRequested` / `SupplementSubmitted` 事件均已实现。 |
| 已完成 | 审批 SLA 已落地：20 小时催办、24 小时运营升级、独立维护角色、CronJob、Outbox 通知和申请人站内通知均已实现。 |
| 已完成 | 风控拒绝闭环已落地：workflow 的 `REJECTED` 终态通过 `WorkflowRiskRejected` 幂等同步到 supplier，并由 notification-audit-service 生成通知与审计记录。 |
| 已完成 | 已提供独立生命周期维护镜像和 Helm CronJob，按固定白名单清理已发布 Outbox、DLQ、重放审计、Inbox 和请求幂等记录，并通过契约测试与 PostgreSQL E2E 验证 RLS、终态判断和保留窗口。 |
| 已完成 | 提供只读生命周期角色权限预检，核验专用账号的非超级用户、不可继承、BYPASSRLS、白名单表和锁键列权限；CI 契约测试与 PostgreSQL 生命周期 E2E 会执行该预检。 |
| 已完成 | 生产外部依赖连接安全已补齐：RocketMQ Producer/Consumer 使用独立 Secret 凭据和 TLS，PostgreSQL 默认 `sslmode=verify-full` 并通过外部 CA Secret 校验服务端身份，Redis 默认启用 TLS；本地 Compose 保持关闭 TLS 的兼容默认值。 |
| 已完成 | 生产 PostgreSQL 预检、备份、恢复和生命周期维护脚本在 `verify-ca`/`verify-full` 模式下强制校验 `FLOWMESH_PG_SSLROOTCERT` 文件存在且可读，避免依赖 Runner 的隐式系统信任库。 |
| 已完成 | 提供只读外部依赖 HA 拓扑预检，检查 PostgreSQL 复制数、Redis 主从端点、至少两个 RocketMQ NameServer TLS 端点和对象存储 HTTPS；实际故障切换、备份恢复和 RTO/RPO 仍需目标平台执行并留存证据。 |
| 已完成 | 提供只读生产证据包门禁，要求目标环境提交 Kubernetes smoke、依赖 HA、运行时观测、应用恢复、备份恢复、压测、跨租户安全回归和告警路由报告，并使用清单和 SHA-256 校验和验证完整性；实际证据仍需由目标平台执行生成。 |
| 已完成 | 提供生产发布入口：先校验完整提交 SHA 镜像签名和生产 values，再执行 Helm 原子等待发布，最后执行只读 Kubernetes smoke；smoke 失败会自动回滚或清理首次安装资源；运行时凭据只通过预先创建的 Secret 引用。 |
| 已完成 | Workflow SLA CronJob 的 PostgreSQL 客户端镜像已纳入供应链门禁：生产 Helm 强制要求 `sha256` digest，发布脚本、Production acceptance 和 Kubernetes smoke 均绑定同一 digest，禁止回退到版本标签。 |
| 已完成 | IAM 登录/失败/登出安全审计已通过真实 PostgreSQL 持久化回归，审计表由数据库触发器拒绝 UPDATE、DELETE 和 TRUNCATE，保证历史安全证据只追加。 |
| 已完成 | IAM 用户、用户角色关系、Refresh Token 和安全审计表已启用 `FORCE ROW LEVEL SECURITY`；Refresh Token 固化租户归属，刷新/登出按请求租户建立上下文，清理任务逐租户执行，并由集成测试验证跨租户读写隔离。 |
| 已完成 | 提供受保护的生产发布工作流：仅手动触发，使用 `production` Environment 审批、部署并发互斥、完整提交 SHA 镜像校验和部署日志归档；工作流不接收或打印运行时凭据。 |
| 已完成 | 提供只读 GitHub 生产控制面预检：生产工作流使用 Environment 中的专用只读控制面 Token 检查 `production` Environment 审批/受保护分支策略和 `main` 分支保护；离线契约禁止预检调用变更 API，实际仓库控制仍需管理员配置并验收。 |
| 已完成 | 提供 Kubernetes 应用故障恢复演练：白名单组件 Pod 删除、Deployment 副本恢复、健康检查和 RTO 门禁均可重复执行；实际目标集群结果仍必须归档到生产证据包。 |
| 已完成 | Workflow SLA 已通过临时 PostgreSQL E2E 验证催办、并行审批实例锁定、任务状态收敛和 Outbox 写入；每个实例使用子事务保证乐观锁失败时整体回滚；CI 会执行该回归。 |
| 尚需目标平台证据 | 外部依赖 HA、托管观测后端、告警通知、备份恢复、RTO/RPO、压测和故障演练不能由本地 CI 代替；必须使用生产证据包门禁留存实际结果。 |
| 明确不纳入当前业务范围 | Camunda、Redis 缓存、Redis 短期幂等加速和外部邮件/短信通道；这些属于产品范围扩展，不是生产上线门禁。 |

## 3. MVP-4 范围

### 3.1 事件契约与消息可靠性

| 编号 | 需求 | 验收标准 |
| --- | --- | --- |
| MSG-01 | 消费者校验事件信封 | 校验 `eventId`、`eventType`、`schemaVersion`、`aggregateId`、`tenantId`、`occurredAt`、`traceId` 和 `payload` 必填字段。 |
| MSG-02 | 校验业务关联关系 | `aggregateId` 与载荷中的业务 ID 不一致时拒绝处理，并记录可定位的错误信息。 |
| MSG-03 | 发布器可靠性测试 | 单元测试覆盖 ACK 后标记、发送失败、指数退避和失败终态；真实 RocketMQ E2E 验证 Broker ACK 后主链路最终完成。 |
| MSG-04 | 多实例竞争测试 | PostgreSQL 集成测试并发调用两个发布器，验证租约和 `FOR UPDATE SKIP LOCKED` 下同一事件只被认领一次。 |
| MSG-05 | 消费幂等测试 | 申请幂等、流程投影幂等、重复审批和真实 RocketMQ 主链路均通过；消费者以 Inbox/业务唯一约束吸收重复事件。 |

### 3.2 DLQ 与运营处置

| 编号 | 需求 | 验收标准 |
| --- | --- | --- |
| OPS-01 | 查询失败事件 | 可按租户、事件类型、聚合 ID 和失败状态查询。 |
| OPS-02 | 受控重放 | 只有 `OPERATIONS` 角色可以重放；必须填写原因；重放生成新的 `eventId` 并保留 `originalEventId`。 |
| OPS-03 | 重放审计 | 记录操作者、租户、原因、原事件、重放事件、前后状态和时间。 |
| OPS-04 | 状态对账 | 可以识别申请、流程实例、Inbox 和 Outbox 之间的差异，并生成待处置记录。 |

### 3.3 可观测性

| 编号 | 需求 | 验收标准 |
| --- | --- | --- |
| OBS-01 | 业务指标 | 已暴露申请创建、审批节点完成、重复事件、Outbox 待发送和死信数量指标。 |
| OBS-02 | 消息指标 | 已暴露发布/消费成功失败、重试和死信计数、Outbox 当前待发送/死信 Gauge，以及按消费者区分的消费处理耗时直方图。 |
| OBS-03 | 链路标识 | HTTP 入口生成/透传 `traceId`；事件信封携带 `traceId`、`eventId`、`tenantId`，日志不输出消息密文或 JWT。 |
| OBS-04 | 健康检查 | Actuator 区分 liveness/readiness；readiness 纳入 PostgreSQL 和 RocketMQ NameServer TCP 检查。 |

### 3.4 测试与交付

| 编号 | 需求 | 验收标准 |
| --- | --- | --- |
| QA-01 | PostgreSQL 集成测试 | Docker 可用时执行 `./mvnw -q test`；supplier、workflow、risk 和通知审计服务均有真实 PostgreSQL RLS 集成测试。 |
| QA-02 | RocketMQ 集成测试 | `tests/rocketmq-e2e.sh` 使用 Compose 的 PostgreSQL、Redis、真实 RocketMQ Broker 和本机 JAR 验证跨服务主链路、死信运维闭环和对账。 |
| QA-03 | CI 校验 | Maven、前端构建、Helm lint 和模板渲染均已写入 GitHub Actions。 |
| QA-04 | 应用安全边界回归 | CI 校验认证入口、Actuator 匿名范围、运维与内部对账角色约束，以及 Gateway 不暴露 `/internal/` 路径。 |
| QA-05 | 资源回收 | 本地验证结束后停止本任务启动的 Compose 容器，并关闭 Docker Desktop；后续验证前再按需启动。 |

## 4. 后续产品能力

以下能力不纳入当前 MVP 的“已完成”判定，但保留为后续迭代需求：

- Camunda 8 BPMN 流程编排。
- Redis 缓存、短期幂等加速；登录尝试限流属于当前迭代范围。
- 外部邮件/短信通道的具体供应商适配；当前迭代已完成通用 HTTPS Webhook 投递、HMAC 签名、幂等键、重试和死信闭环。
- 生产级托管 Prometheus、Grafana、日志聚合和 OpenTelemetry Trace 后端；仓库已提供本地基线和 AlertmanagerConfig，但真实平台接入、资源选择和告警通知属于上线前必需证据。
- RocketMQ、PostgreSQL 多副本高可用、对象存储跨故障域复制、备份恢复和 Chaos 故障演练；这些不是“可选功能”，而是生产环境验收项。

Camunda 和 Redis 缓存只有在对应业务场景、数据边界和测试环境明确后再接入，不为了扩充简历技术栈而提前引入。

## 5. 完成定义

每个需求必须同时具备：

1. 业务代码或配置实现。
2. 针对关键分支的自动化测试。
3. 文档、运行手册和面试问答与实际行为一致。
4. 在可用环境中完成构建或集成验证，并记录环境限制。
5. 阶段性成果提交 Git；若远程网络可用则推送到远程仓库。
