# FlowMesh 生产化验收清单

本文是从 MVP-4 继续推进生产化的验收清单。只有代码、部署配置、测试和运行演练同时满足要求，项目才能称为生产级系统。

## 已完成的生产基线

- JWT 密钥、数据库密码和 Redis 密码不允许使用默认占位值。
- Servlet 业务服务关闭 Spring Security 默认用户自动配置，不生成未被业务使用的随机 Basic 用户。
- Kubernetes 应用 Pod 使用非 root、只读根文件系统、默认 Seccomp，并关闭 ServiceAccount Token 自动挂载。
- Helm 默认提供 CPU/内存 requests 和 limits、启动/就绪/存活探针、滚动更新和优雅终止配置。
- 生产 values 提供 gateway 和五个业务服务的双副本、PodDisruptionBudget、拓扑分散和基于 CPU 的 HPA 配置。
- 生产拓扑分散使用 `DoNotSchedule` 强制同一服务的副本跨 Kubernetes 节点分布；本地默认使用 `ScheduleAnyway`，兼容单节点开发环境。
- 生产 values 默认启用 Prometheus Operator 的 `ServiceMonitor` 和 `PrometheusRule`，并提供默认选择标签；发布到使用其他观测接入方式的平台时必须显式覆盖并通过运行时观测验收。
- Gateway 和 IAM 的 Redis 连接支持 ACL 用户名与密码认证；单密码 Redis 保持用户名为空，不改变本地 Compose 行为。
- 已补齐 `gateway-service`，统一暴露 `/api/iam/**`、`/api/supplier/**`、`/api/workflow/**` 和 `/api/notification/**`；业务服务保持 ClusterIP，Gateway 具备资源限制、探针和优雅终止配置。
- Gateway 已使用 Redis Lua 令牌桶对所有业务路由执行分布式限流，并暴露允许、拒绝和 Redis 故障指标；生产入口必须覆写配置的客户端地址请求头，避免公网请求伪造限流身份。
- Gateway 已按业务路由配置请求体上限：认证、流程和通知接口默认为 1 MB，供应商材料上传默认为 21 MB；入口限制与下游 Spring multipart 限制保持一致，防止超大请求先占用下游资源。
- Gateway readiness 已在 `production` profile 纳入 Redis 健康检查；Redis 不可用时实例不会继续接收入口流量。IAM 的 `production` profile 也将 Redis 纳入 readiness，避免登录限流依赖失效后继续接收认证请求；本地和测试 profile 不强制依赖 Redis。
- 生产 Helm 为 Gateway 单独渲染入站 NetworkPolicy，只允许指定 Ingress Controller 命名空间和监控命名空间访问，避免绕过 TLS、审计和入口限流直接调用 Gateway Service。
- Spring Boot 启用优雅停机、连接超时和请求体大小边界。
- supplier readiness 会在生产配置下检查 MinIO 材料桶和 ClamAV 扫描端口；依赖不可用时不接收新的材料请求。
- Redis 登录限流支持可配置的故障策略；本地默认降级放行，生产 Helm 默认 fail-closed，Redis 不可用时返回 `503`。
- IAM 会在所有副本中以带批量上限和保留窗口的任务清理过期/长期撤销的 Refresh Token，SQL 使用 `FOR UPDATE SKIP LOCKED` 避免多副本重复争抢，并暴露删除计数指标。
- 提供独立的 PostgreSQL 生命周期维护镜像和 Helm CronJob，使用非超级用户 `flowmesh_retention` 清理已发布 Outbox、DLQ、重放审计、Inbox 和请求幂等记录；SQL 使用固定表白名单、批量上限和 `FOR UPDATE SKIP LOCKED`，并通过 PostgreSQL E2E 验证强制 RLS 表的清理边界。
- 提供只读的 `flowmesh_retention` 角色权限预检，核验 `NOSUPERUSER`、`NOINHERIT`、`BYPASSRLS`、Schema 使用权限、清理白名单和行锁键列级 `UPDATE` 权限；契约测试和生命周期 PostgreSQL E2E 均会执行该预检。
- 所有服务日志统一输出 `traceId`，消息消费者会恢复事件信封中的 `traceId` 并在处理结束后清理线程上下文；supplier 对账调用 workflow 时会继续透传当前 `X-Trace-Id`，避免跨服务 HTTP 链路断裂。
- RocketMQ 消费者已暴露按消费者区分的处理耗时直方图，并提供消费处理 P95 超过 5 秒的 Prometheus 告警；观测配置校验会防止这条告警被误删。
- 提供 PostgreSQL custom-format 备份与恢复脚本；备份目录默认被 Git 忽略。
- 备份与恢复脚本统一校验并使用 `FLOWMESH_PG_SSLMODE` 和 `FLOWMESH_PG_CONNECT_TIMEOUT_SECONDS`；生产恢复不得退回未加密或无限等待的数据库连接。
- 提供只读 PostgreSQL 备份角色预检：要求备份账号为可登录的非超级用户、不可复制/建库/建角色、具备 `BYPASSRLS` 以保证全租户归档，业务表仅允许 `SELECT` 且不得拥有业务表；生产验收会执行该预检。
- 提供离线备份完整性校验脚本，并通过真实 PostgreSQL 容器 E2E 验证归档校验、角色密码不落盘和隔离数据库恢复；通过环境变量限制数据库连接池上限、连接超时和连接生命周期。
- 提供可发布的 PostgreSQL 备份镜像和 Helm CronJob：定时创建归档、上传到 S3 兼容对象存储、从远端确认三个归档对象可见后再写入 `_SUCCESS`、使用服务端加密、禁止并发执行并在失败时重试；生产 Helm 要求显式注入外部数据库地址、专用只读备份账号、备份目标和凭据 Secret。
- Outbox 发布器显式设置消息发送超时，在启动时校验“批量发送窗口 + 安全余量”不超过认领租约，并在每条消息发送前续租；若认领令牌已失效则跳过发送，避免参数调整或进程暂停导致的并发重复发布。
- 四个 RocketMQ 服务支持独立 Producer/Consumer 凭据、访问通道和 TLS 配置；生产 Helm 默认开启 Producer/Consumer TLS，并要求运行时 Secret 提供四组凭据键。
- 生产应用、SLA、备份和生命周期任务的 PostgreSQL 连接默认使用 `sslmode=verify-full`，Redis 连接默认启用 TLS；Helm 通过 `postgresql.caSecretName`、`backup.postgres.caSecretName` 和 `retention.postgres.caSecretName` 挂载外部 CA，并将 CA 文件传给 JDBC/`libpq` 完成服务端身份校验。
- 所有生产 PostgreSQL 预检和维护脚本在 `verify-ca`/`verify-full` 模式下都强制要求 `FLOWMESH_PG_SSLROOTCERT` 指向存在且可读的 CA 文件，禁止无意间退回 Runner 系统信任库。
- 生产 Helm 模式要求外部 Secret、外部镜像仓库和提交 SHA 镜像标签；未提供 `global.imageTag` 时渲染直接失败，避免部署可变或本地默认镜像。
- 五个业务服务统一启用 Flyway `validate-on-migrate`，并显式禁止 `clean`、乱序迁移和自动 baseline；生产 Helm 通过五个 pre-install/pre-upgrade Job 在应用发布前执行迁移，迁移校验失败时 Helm 发布失败且不会更新应用 Deployment。
- 五个业务服务的 Flyway 均使用独立的 `flowmesh_<service>_migrator` 账号；运行时业务账号不再承担 Schema 所有权或 DDL 权限。生产应用 Pod 显式关闭 Spring Boot Flyway，迁移账号密码只注入迁移 Job，并通过独立的迁移 Secret 与运行时 Secret 分离；新环境初始化脚本、测试容器和 Helm Secret 均要求对应迁移密码。
- Helm 支持通过 `global.imagePullSecrets` 引用私有镜像仓库凭据；生产发布入口可用 `FLOWMESH_IMAGE_PULL_SECRET_NAME` 注入 Secret 名称，凭据内容不进入 Helm 参数或日志。
- 生产覆盖值显式覆盖 PostgreSQL、Redis 和 RocketMQ NameServer 地址，阻止 Helm 合并时继承本地 Compose 服务名；发布流程仍必须替换占位地址为真实 HA 服务端点。
- CI 在 PR 构建六个应用镜像、一个备份镜像和一个生命周期维护镜像；在 `main` 推送时发布完整提交 SHA 和 `main` 标签，并为镜像生成 SBOM/构建证明，对完整 SHA 镜像执行 Trivy 漏洞扫描和 Cosign keyless 签名。
- 八个发布镜像的 Dockerfile 基础镜像均固定到已审核的多架构 `sha256` digest，并由 CI 契约阻止回退到可变基础镜像标签；基础镜像升级必须显式更新 digest、契约和扫描结果。
- Workflow SLA CronJob 使用的 PostgreSQL 客户端镜像在生产模式下必须由发布流程注入完整 `sha256` digest；Helm、生产发布脚本和 Kubernetes smoke 会拒绝仅使用可变版本标签。
- CI 已提供独立源码安全工作流：对 Java/Kotlin 和 TypeScript/JavaScript 执行 CodeQL，并在 Pull Request 中以高危级别阻断依赖审查失败；生产发布仍需结合组织级 Secret Scanning、Dependabot 告警处置和代码扫描告警基线。
- CI 已提供应用安全边界契约：校验认证入口、Actuator 匿名范围、运维与内部对账角色约束，以及 Gateway 不暴露 `/internal/` 路径，防止安全配置回归。
- supplier 与 workflow 的历史 RLS 策略已通过 Flyway 显式补齐 `WITH CHECK`，并由 PostgreSQL 集成测试读取 `pg_policies` 验证租户写入边界。
- 观测配置已定义 HTTP 可用性 99.9% 和 P95 1 秒 SLO，Prometheus recording rule 与告警在本地 Compose 和生产 PrometheusRule 中保持一致；目标平台仍需根据真实流量校准 SLO、错误预算和值班升级策略。
- 生产 Helm 模式强制启用 Ingress，并要求发布流程显式注入真实域名和 TLS Secret；缺失时渲染失败。
- Helm 提供可选的 Prometheus Operator `ServiceMonitor`，启用后统一抓取六个应用服务的 Actuator 指标。
- Helm 提供可选的 Prometheus Operator `PrometheusRule`，覆盖服务不可用、HTTP 5xx、Outbox 积压、死信、消费失败、消费延迟、确认失败、外部通知积压/死信/失败，以及 PostgreSQL 备份、生命周期清理和 Workflow SLA CronJob 长时间未成功执行告警；CronJob 告警依赖 kube-state-metrics，生产环境仍需配置 Alertmanager 路由和值班通知。
- Helm 提供可选的 Prometheus Operator `AlertmanagerConfig`，通过外部 Secret 注入 Webhook URL，统一分组 FlowMesh 告警并发送恢复通知；目标平台仍需让 Alertmanager 通过 `alertmanagerConfigSelector` 选择该资源，并完成通知接收端、静默策略和值班演练。
- 六个服务已提供可选 Micrometer Tracing 和 OTLP/HTTP 出口；默认关闭，生产启用时 Helm 要求显式提供 Collector 地址。
- 提供只读运行时观测预检：检查 Prometheus/Alertmanager 就绪、六个 FlowMesh 服务目标可见，以及关键告警规则已加载；生产验收默认强制执行该检查，非生产预检必须显式设置 `FLOWMESH_REQUIRE_RUNTIME_OBSERVABILITY=false` 才能跳过。
- 提供只读外部依赖 HA 拓扑预检：检查 PostgreSQL 主库复制数、Redis 主从可见性、至少两个 RocketMQ NameServer TLS 端点和对象存储 HTTPS；生产验收默认强制执行该检查，非生产预检必须显式设置 `FLOWMESH_REQUIRE_DEPENDENCY_HA=false` 才能跳过。
- 提供只读生产证据包校验和清单生成工具：要求目标环境归档 Kubernetes smoke、外部依赖 HA、运行时观测、应用恢复、备份恢复、压测、跨租户安全回归和告警路由报告；生成器只创建清单与 SHA-256 校验和，不伪造演练报告，校验器还要求每类报告包含对应检查命令、证据摘要、关键结果、目标环境标识和本次镜像提交 SHA，并通过清单与校验和防止缺项、跨环境拼接或篡改。生产验收默认强制执行该门禁，非生产预检必须显式设置 `FLOWMESH_REQUIRE_PRODUCTION_EVIDENCE=false` 才能跳过。
- 生产证据包目录以及其中的必需报告、清单和校验和必须是证据目录内的普通文件，校验器拒绝目录或文件符号链接，避免通过目录外文件拼接或替换验收材料。
- `.github/workflows/production-acceptance.yml` 会显式将上述三个门禁固定为 `true`，避免生产工作流依赖脚本默认值而被意外放宽；只有非生产手工预检才允许使用对应的 `false` 参数。
- 生产验收还会将 `FLOWMESH_IMAGE_TAG` 绑定到证据清单中的镜像提交 SHA；旧提交或其他环境生成的证据包不能用于当前版本验收。
- 审批退回补件和多轮重审已落地：workflow 持久化审批决定、意见和轮次，supplier 保存补件历史并通过 Outbox 通知下一轮初审；最多两轮，重复提交由幂等键吸收。
- 审批 SLA 已落地：独立 `flowmesh_workflow_sla` 非超级用户维护角色由 Helm CronJob 每 5 分钟扫描，第 20 小时写催办事件，第 24 小时创建运营升级任务；业务账号不承担跨租户扫描。
- SLA 升级使用 PostgreSQL 行锁和每个流程实例的 PL/pgSQL 子事务；乐观锁失败或 Outbox/任务插入异常时，任务状态、并行任务取消和流程状态会整体回滚，避免留下半完成升级。
- 风控拒绝闭环已落地：workflow 事务写入 `WorkflowRiskRejected`，supplier 幂等更新 `REJECTED` 终态，notification-audit-service 同步生成申请人通知与审计记录。
- 外部通知投递已落地：站内通知与投递队列同事务提交，Webhook 使用 HTTPS、HMAC 签名、幂等键、租约认领、指数退避和死信；队列通过专用 `flowmesh_audit_delivery` `BYPASSRLS` 角色的安全函数跨租户调度，业务账号不直接读取投递队列。
- IAM 登录成功、失败和登出均写入安全审计；审计表由 PostgreSQL 触发器拒绝 UPDATE、DELETE 和 TRUNCATE，应用层不能修改历史安全证据。

验证命令：

```bash
bash -n scripts/backup-postgres.sh scripts/restore-postgres.sh
helm lint infra/helm/flowmesh \
  --set-string global.jwtSigningKey="$HELM_TEST_JWT" \
  --set-string global.redisPassword="$HELM_TEST_REDIS_PASSWORD" \
  --set-string services.iam.dbPassword="$HELM_TEST_IAM_PASSWORD" \
  --set-string services.iam.dbMigratorPassword="$HELM_TEST_IAM_MIGRATOR_PASSWORD" \
  --set-string services.supplier.dbPassword="$HELM_TEST_SUPPLIER_PASSWORD" \
  --set-string services.supplier.dbMigratorPassword="$HELM_TEST_SUPPLIER_MIGRATOR_PASSWORD" \
  --set-string services.workflow.dbPassword="$HELM_TEST_WORKFLOW_PASSWORD" \
  --set-string services.workflow.dbMigratorPassword="$HELM_TEST_WORKFLOW_MIGRATOR_PASSWORD" \
  --set-string services.risk.dbPassword="$HELM_TEST_RISK_PASSWORD" \
  --set-string services.risk.dbMigratorPassword="$HELM_TEST_RISK_MIGRATOR_PASSWORD" \
  --set-string services.notificationAudit.dbPassword="$HELM_TEST_AUDIT_PASSWORD" \
  --set-string services.notificationAudit.dbMigratorPassword="$HELM_TEST_AUDIT_MIGRATOR_PASSWORD" \
  --set-string objectStorage.secretKey="$HELM_TEST_OBJECT_STORAGE_SECRET"
./mvnw -q -DskipTests package
```

## 仍需由目标生产平台提供或完成的能力

### 依赖高可用

- PostgreSQL 主备、自动切换和恢复演练；当前应用已设置连接池上限、连接超时和可选 CA 校验，但不替代数据库侧 HA。生产 values 已禁止回落到本地 `postgres` 服务名，并要求备份 CronJob 使用独立只读账号。
- RocketMQ 多 Broker、持久卷、跨故障域部署和消息恢复演练；当前 Helm 使用外部 NameServer 地址并支持 TLS/ACL，不能把单 Broker Compose 拓扑当作 HA 证据。
- Redis 哨兵或托管 Redis；Redis 只能作为登录限流的派生状态，生产 values 已禁止回落到本地 `redis` 服务名并默认启用 TLS，但仍需在目标平台完成 HA 和证书轮换演练。

### 平台与网络

- Gateway 的真实入口仍需由目标集群提供会覆写客户端地址请求头的 Ingress Controller 和证书 Secret；仓库已提供并验收 Gateway 入站 NetworkPolicy。
- Helm 生产覆盖值已提供业务服务入口、Prometheus 指标抓取入口和出站白名单 NetworkPolicy；发布流程必须注入监控命名空间和外部依赖 CIDR，且拒绝 `0.0.0.0/0`、`::/0` 默认路由，仍需在目标 CNI 和真实集群完成连通性演练。
- 已提供 Kyverno 镜像签名准入策略；目标集群仍需安装 Kyverno、应用策略，并配置持续运行时漏洞扫描平台。
- Metrics Server 依赖和真实集群中的 HPA/PDB 演练。

### 可观测性与恢复

- 已提供 Prometheus 抓取配置、可选 ServiceMonitor、服务/Outbox/死信/Gateway 限流告警、Grafana Dashboard、本地 Alertmanager 路由基线和生产 AlertmanagerConfig；生产环境仍需接入托管 Prometheus、Grafana、Alertmanager、日志聚合、OpenTelemetry Collector 和 Trace 后端，并验证通知接收。
- 消息消费耗时已纳入 Prometheus 指标和告警；生产环境仍需根据实际 SLO 调整阈值，并完成告警通知路由和值班演练。
- SLA CronJob 已提供独立维护账号、最小表权限、`SKIP LOCKED` 和 Outbox 事件；超时升级同时锁定任务行与 workflow instance，避免并行审批推进时留下半完成状态，并通过临时 PostgreSQL E2E 验证催办、升级和状态收敛。Helm PrometheusRule 已提供 SLA CronJob 长时间未成功告警。目标平台仍需轮换 `WORKFLOW_SLA_DB_PASSWORD`、验证数据库连接 TLS，并完成告警和值班演练。
- PostgreSQL 备份已经提供 Helm CronJob、S3 上传、服务端加密、失败重试和 CI 恢复回归；目标平台仍需配置对象存储跨故障域复制、生命周期、定期恢复验证和实际 RTO/RPO 记录。
- PostgreSQL 消息与幂等记录的保留策略已提供 Helm CronJob、角色权限预检和 CI/E2E 验证；目标平台仍需预置 `flowmesh_retention` 角色、轮换 Secret，并按实际合规要求调整 90/30 天窗口。
- RocketMQ 堆积、DLQ、对账差异和审批超时的告警剧本。

### 业务闭环

- 已实现材料上传、私有对象存储、文件头校验、SHA-256、ClamAV 扫描和短期下载授权；生产环境仍需完成对象存储生命周期、备份和权限策略演练。
- 已提供对象存储生命周期配置脚本和离线契约测试：专用材料桶启用版本化，逻辑删除对象的非当前版本默认保留 7 天；目标平台仍需执行脚本并验证跨故障域复制、访问审计和实际清理结果。
- 已实现独立 risk-service 的异步 PASS/REJECT 运行链路，以及 notification-audit-service 的通知/审计投影和可靠外部通知投递链路；目标环境仍需配置实际 Webhook、轮换签名密钥、验证签名接收和完成恢复演练。
- risk-service 已提供默认关闭的 `FAIL` / `TIMEOUT` 受控故障注入，用于验证消息重试、DLQ 和人工处置；生产 Helm 会显式关闭该开关。
- 高并发压测、故障注入和跨租户安全回归。仓库已提供 k6 压测脚本和显式确认的服务恢复演练脚本，但必须在目标环境执行并留存结果。
- 提供只读 Kubernetes 生产 smoke test，验证六个 Deployment、提交 SHA 镜像、安全上下文、探针、资源限制、PDB/HPA/NetworkPolicy、Gateway Ingress 边界、运行时 Secret 必需键、PostgreSQL CA Secret 的 `ca.crt` 与实际 Pod 挂载、实际 Pod 的 PostgreSQL/Redis/RocketMQ TLS 配置、备份 CronJob 和 AlertmanagerConfig 的外部 Webhook Secret 引用；目标环境仍需实际执行并留存输出。
- 提供 Kubernetes 应用故障恢复演练脚本：仅允许对白名单组件删除 Pod，验证旧 Pod 消失、Deployment `ReadyReplicas` 恢复、入口健康检查和目标 RTO；本地 Compose 故障脚本不再作为生产 Kubernetes 恢复证据。
- Kubernetes 恢复演练工作流要求输入已部署镜像提交 SHA 和目标环境标识，并通过环境变量传递所有手工输入，避免 Shell 注入；生成的报告可直接绑定到当前生产证据包。
- 提供受保护的 `Production recovery drill` 工作流：仅手动触发、仅从 `main` 执行、必须经过 `production` Environment 审批并输入 `YES`，执行后归档恢复报告。
- 提供只读生产验收编排脚本，统一执行镜像签名、Kubernetes smoke、外部依赖 TLS 预检和生命周期角色权限预检，并生成不可覆盖的 Markdown 证据报告。
- 提供仅手动触发、绑定 GitHub `production` Environment 审批的自托管 Runner 验收工作流；工作流只执行上述只读验收并上传报告，不包含部署、迁移、故障切换或 `FLOWMESH_REQUIRE_*` 绕过路径，并且只允许从 `main` 分支执行。
- 提供独立生产发布入口：签名校验、生产配置校验、`helm upgrade --install --atomic --wait --wait-for-jobs` 和发布后 Kubernetes smoke 必须串联执行；Flyway 迁移 Job 失败会阻止应用更新，smoke 失败时自动回滚到升级前 revision，首次安装失败则卸载应用资源并保留 Helm 历史；运行时凭据只通过预先创建的 Secret 引用，不通过命令行传递。
- 提供仅手动触发、绑定同一 `production` Environment 的生产发布工作流；工作流只允许从 `main` 分支执行，使用并发互斥防止同时发布，复用生产发布入口，并将部署日志归档供审计追踪。
- 生产验收编排脚本可选要求目标环境证据包；未提供真实目标环境的 HA、观测、恢复、备份、压测、安全回归和告警路由证据时，不得将版本标记为生产完成。
- 生产验收编排脚本可选执行运行时 Prometheus/Alertmanager 预检，但仍不替代平台侧告警通知、日志聚合、Trace 后端和值班演练。
- 提供只读外部依赖 preflight，检查 PostgreSQL/Redis/RocketMQ TLS 和对象存储 HTTPS；目标环境仍需执行并留存输出，且该检查不替代 HA、故障切换和恢复演练。
- 提供 `scripts/validate-production-readiness.sh` 作为本地生产化仓库门禁入口；它不启动 Docker，也不连接外部服务，可在提交前复现全部静态检查和契约测试。

## 完成判定

后续每一项能力都必须同时提供：

1. 可运行的实现或部署配置。
2. 自动化测试或可重复的运维演练。
3. 失败恢复、权限边界和数据一致性说明。
4. CI 门禁和运行手册更新。
5. 有证据的验证结果，而不是仅有设计文档。
