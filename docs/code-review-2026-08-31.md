# FlowMesh 全项目代码审查（2026-08-31）

## 结论

当前 MVP 的分层、Javadoc、Flyway 迁移、RLS 测试和 Transactional Outbox 基础是扎实的；后端
Reactor 测试与 Electron 构建均已通过。上线或扩容前，必须优先修复已提交演示账号、Outbox 多副本
并发、供应商申请的伪乐观锁和 Electron IPC 来源校验问题。

本次为只读审查：未修改业务代码、配置、迁移或测试；仅新增本报告。未跟踪的 `.pi/` 与 `claude.md`
不属于本次审查范围，也未被读取或纳入提交。

## 验证范围与结果

| 范围 | 结果 | 说明 |
| --- | --- | --- |
| Java 代码、测试、Flyway 迁移、Maven 配置 | 通过 | `./mvnw test`：28 个测试通过；包含 PostgreSQL Testcontainers 集成测试。 |
| Vue/Electron 代码与 TypeScript 类型 | 通过 | `frontend/npm run build` 通过，包含 `vue-tsc --noEmit`、Vite 和 Electron 主进程编译。 |
| Docker Compose | 通过 | `docker compose -f infra/compose/docker-compose.yml config --quiet` 通过。 |
| Helm Chart | 未执行 | 本机未安装 `helm`，无法执行 `helm lint infra/helm/flowmesh`。 |
| RocketMQ 端到端、双副本并发、DLQ/重放 | 未覆盖 | 当前测试禁用 RocketMQ 自动配置，未覆盖真实 Broker 与多实例竞争。 |

优先级含义：P0 为已知可被利用的严重安全风险；P1 为上线或扩容前应解决的问题；P2 为近期应排期；P3 为质量和可维护性优化。

## 必须优先处理

### P0：生产 Flyway 迁移写入固定的可登录演示账号

证据：[`V3__seed_dual_tenant_data.sql`](../services/iam-service/src/main/resources/db/migration/V3__seed_dual_tenant_data.sql)
第 1–40 行会创建两个 `ACTIVE` 租户、多个固定 UUID 用户，以及所有人都已知的
`password123` BCrypt 哈希。该迁移会在任何新数据库上自动执行。桌面端还在
[`App.vue`](../frontend/src/App.vue) 第 25、53、215 行将这些凭据作为默认演示登录信息。

风险：生产数据库会出现公开账号，攻击者可以直接获得多个审批角色权限。这不是“示例配置”，而是
版本化生产数据迁移，因此仅依赖部署人员“不使用它”无法消除风险。

建议：

1. 若尚未在任何共享环境执行 V3，在首次共享部署前移除该种子迁移内容，改为显式的本地演示初始化。
2. 若 V3 已在任何共享环境执行，不要篡改历史迁移；新增迁移禁用或删除这些演示用户并撤销其 Refresh Token，随后轮换 JWT 密钥和数据库密码。
3. 将演示数据移到 `dev`/`test` 专用初始化脚本或 Testcontainers fixture；桌面端仅在明确的 `demo` 构建模式展示一键填充账号。
4. 添加回归检查：生产 profile 的迁移后不得存在演示用户名或默认密码哈希。

### P1：Outbox 发布器在多副本下会重复发送，且长事务没有退避或认领机制

证据：

- supplier 发布器在 [`OutboxPublisher.java`](../services/supplier-service/src/main/java/com/flowmesh/supplier/messaging/OutboxPublisher.java) 第 50–77 行直接查询所有未发布记录并同步发送。
- workflow 发布器在 [`WorkflowOutboxPublisher.java`](../services/workflow-service/src/main/java/com/flowmesh/workflow/messaging/WorkflowOutboxPublisher.java) 第 47–70 行使用同一模式。
- 两个仓储都以 `findTop100ByPublishedAtIsNullOrderByCreatedAtAsc` 查询，没有 `FOR UPDATE SKIP LOCKED`、租约或状态认领。
- Helm 的副本数是可配置项（[`values.yaml`](../infra/helm/flowmesh/values.yaml) 第 16–39 行），因此扩容后多个 scheduler 会读取同一批事件。

风险：重复消息虽然被当前消费者的唯一约束/Inbox 大体吸收，但会造成额外 Broker 压力、告警噪声和
重复消费。发布循环还在一个数据库事务中执行最多 100 次、每次最长 3 秒的远程调用；故障时会长期占用
数据库连接。失败事件每秒立刻重试，既没有 `next_attempt_at`，也没有最大次数、DLQ 或人工重放路径，
与 [`docs/event-contracts.md`](event-contracts.md) 第 61–65 行的约定不一致。

建议：

1. 在短事务内以 `FOR UPDATE SKIP LOCKED` 或“状态 + 租约到期时间”原子认领有限批次；网络发送放在认领事务外，成功后以条件更新确认。
2. 增加 `next_attempt_at`、指数退避、最大尝试次数和可观测的失败终态；达到阈值后进入可审计 DLQ/人工重放流程。
3. 保留消费者幂等，新增双实例并发发布、Broker ACK 后数据库提交失败、连续发送失败及 DLQ 重放的集成测试。

### P1：`supplier_applications.state_version` 并不提供乐观锁

证据：[`SupplierApplication.java`](../services/supplier-service/src/main/java/com/flowmesh/supplier/domain/SupplierApplication.java)
第 11 行称 `state_version` 为“乐观锁版本”，第 35–36 行只是普通列，第 101 行手工递增；实体没有
`@Version`。相比之下，[`WorkflowInstance.java`](../services/workflow-service/src/main/java/com/flowmesh/workflow/domain/WorkflowInstance.java)
第 52 行正确使用了 `@Version`。

风险：并发处理审批完成事件时，两个事务可能基于旧状态更新同一申请，后提交的写入会覆盖前一个状态，
却不会得到乐观锁异常。前端展示的 `stateVersion` 也会误导使用者，以为它代表数据库并发控制。

建议：

1. 将该字段改为 JPA `@Version` 管理，或使用带 `state_version` 条件的显式原子更新；两者择一，不要只手工 `++`。
2. 将乐观锁异常映射为 `409`，让 HTTP 客户端重试；消息消费者则抛出可重试异常。
3. 新增两个审批事件并发更新同一申请的测试，验证只允许一个状态推进成功且不会丢失状态。

### P1：同一 `Idempotency-Key` 的并发首请求不能稳定回放首个响应

证据：[`SupplierApplicationService.java`](../services/supplier-service/src/main/java/com/flowmesh/supplier/application/SupplierApplicationService.java)
第 85–145 行先查询幂等记录，再创建申请和记录。数据库唯一约束能阻止两条幂等记录，但两个并发事务都
可能在第 85 行看不到记录；其中一个在第 112 行因唯一约束失败并由全局异常处理器返回 `500`，而不是
返回第一次请求的 `201` 响应。现有幂等测试是顺序执行，未覆盖此路径。

建议：

1. 让数据库唯一约束成为并发仲裁点：捕获唯一冲突后，在新的只读事务重新读取记录，并按指纹回放或返回 `409`。
2. 也可先原子创建“处理中”记录，再由持有者写入最终响应；要定义处理中超时和调用方重试语义。
3. 校验 `Idempotency-Key` 最大 128 字符，避免数据库长度异常变成 `500`。
4. 新增同一租户、用户、键的并发双请求测试，断言恰好一条申请、两个请求均得到确定的业务响应。

### P1：Electron IPC 的“可信来源”采用字符串前缀匹配，可被构造 URL 绕过

证据：[`frontend/electron/main.ts`](../frontend/electron/main.ts) 第 59–63 行用
`url.startsWith(DEV_SERVER_URL)` 判断开发模式来源；第 82–94 行据此允许 IPC 代理请求；第 124–127 行
也依赖同一判断放行导航。诸如 `http://127.0.0.1:5173@attacker.example/` 的 URL 可以满足字符串前缀，
但实际主机不是本地 Vite 服务。

风险：若渲染进程被导航到构造的外部页，该页可调用已暴露的 IPC bridge，通过主进程向内部 IAM、supplier、workflow
端口发起请求，形成权限更高的本地请求代理。

建议：

1. 用 `new URL(url)` 后比较完整 `origin`（协议、主机和端口），不要使用 `startsWith`。
2. 打包模式只允许应用实际加载的资源路径或专用安全 protocol，而不是所有 `file://` URL。
3. 将来源判定提取为纯函数，覆盖正常开发地址、伪造前缀、含用户信息 URL、不同端口和打包资源路径的单元测试。

### P1：部署模板允许使用已知数据库密码，且默认 JWT 密钥会导致启动失败

证据：

- [`infra/compose/docker-compose.yml`](../infra/compose/docker-compose.yml) 第 7、68、95、126 行提供 `change-me*` 默认密码，并在第 70、97、128 行提供非随机 JWT 占位符。
- [`infra/helm/flowmesh/values.yaml`](../infra/helm/flowmesh/values.yaml) 第 3–6、21–22、29–30、37–38 行同样提供默认值；[`secret.yaml`](../infra/helm/flowmesh/templates/secret.yaml) 会把它们直接写入 Kubernetes Secret。
- [`JwtService.java`](../services/flowmesh-common/src/main/java/com/flowmesh/common/security/JwtService.java) 第 93–101 行要求至少 32 字节随机数据的 Base64 密钥，因此默认 JWT 值会使应用无法启动。

风险：Helm 命令若遗漏 `--set`，会部署可预测的数据库凭据或直接得到不可用服务。Compose 的默认值也容易被误用于非本地环境。

建议：

1. Helm 改为引用预先创建的 Secret，或使用 Helm `required` 明确拒绝空值、占位符和已知默认值；不要由 Chart 生成默认生产凭据。
2. Compose 把不安全默认值限制到明确的 `local` profile，并在启动前校验 JWT Base64 长度与非默认密码。
3. CI 增加 `helm lint` 与渲染测试，验证缺失 Secret 时失败、提供合法 Secret 时可渲染。

### P1：安全规范要求的登录/登出审计尚未落地

证据：[`docs/security.md`](security.md) 第 5–8 行要求登录、登出、用户禁用和密码重置写审计日志。
[`AuthApplicationService.java`](../services/iam-service/src/main/java/com/flowmesh/iam/application/auth/AuthApplicationService.java)
第 94 行只更新 `lastLoginAt`，第 148–156 行仅输出应用日志；项目中没有审计实体、迁移或事件生产者。

风险：出现凭据滥用、权限争议或安全事件时，没有不可篡改的操作证据，也无法满足已声明的安全基线。

建议：先明确审计落点（独立 audit 服务或 append-only IAM 表），至少记录 actor、tenant、动作、目标、结果、
发生时间、traceId 和事件 ID；登录失败也应记录为安全事件，但不能记录密码或原始 Token。审计写入与关键
状态变更应在同一事务或通过 Outbox 可靠投递。

## 近期排期

### P2：IAM 没有 PostgreSQL RLS 这一最终隔离层

`iam_users` 含 `tenant_id`，但 [`V1__create_iam_schema.sql`](../services/iam-service/src/main/resources/db/migration/V1__create_iam_schema.sql)
没有启用 RLS 或策略；supplier/workflow 已使用 `ENABLE/FORCE ROW LEVEL SECURITY`。若 IAM 被定义为租户数据的
权威服务，这与 [`docs/security.md`](security.md) 第 12–16 行的“RLS 最终防线”不一致。

建议：明确 IAM 是否是 RLS 例外。若不是，应为带 `tenant_id` 的 IAM 表设计认证前/认证后的租户上下文策略，
并添加跨租户集成测试；若是例外，应在安全规范中说明理由、补偿控制和审查范围，避免形成隐性例外。

### P2：事件消费者只校验事件类型，未验证契约版本和关键字段关联

`WorkflowEventProjectionService` 的 [`readEvent`](../services/workflow-service/src/main/java/com/flowmesh/workflow/application/WorkflowEventProjectionService.java)
第 59–68 行，以及 `WorkflowTaskCompletedService` 的 [`readEvent`](../services/supplier-service/src/main/java/com/flowmesh/supplier/application/WorkflowTaskCompletedService.java)
第 99–108 行，都未验证 `schemaVersion`、`payload` 的必填字段、`aggregateId` 与 payload 中业务 ID 的一致性。
这与 [`docs/event-contracts.md`](event-contracts.md) 第 40–49 行的契约和“消费者必须验证归属”不完全一致。

建议：以版本化 DTO/JSON Schema 解析消息，显式拒绝未知不兼容版本；校验 UUID、租户格式、任务枚举和
`aggregateId` 一致性。对不可重试的坏消息要有隔离记录，避免无限重试。

### P2：workflow 的异常响应与数据库可空性存在不一致

- [`WorkflowInstance.java`](../services/workflow-service/src/main/java/com/flowmesh/workflow/domain/WorkflowInstance.java) 第 48–50 行声明 `currentTask` 不可空，
  但 [`V4__allow_completed_workflow_without_current_task.sql`](../services/workflow-service/src/main/resources/db/migration/V4__allow_completed_workflow_without_current_task.sql) 第 1–4 行允许完成态为 `NULL`。
- [`GlobalExceptionHandler.java`](../services/workflow-service/src/main/java/com/flowmesh/workflow/api/GlobalExceptionHandler.java) 仅处理部分业务异常，未将乐观锁或数据完整性错误映射到统一 `ErrorResponse`/`409`。

建议：统一实体元数据与数据库约束；针对 `OptimisticLockingFailureException`、约束冲突和未预期异常补齐稳定的
错误码，再通过 API 集成测试锁定契约。

### P2：桌面端登出吞掉网络失败，用户无法重试撤销 Refresh Token

[`frontend/src/api.ts`](../frontend/src/api.ts) 第 58–66 行无条件吞掉登出请求异常并清空本地 session。
网络暂时失败时，服务端 Refresh Token 仍有效，而用户界面已无法再次执行撤销。

建议：在撤销失败时保留可重试状态并提示用户，或持久化一个待撤销标记，在下一次可联网时重试；同时在服务端
明确“仅撤销当前会话”还是“撤销该用户全部会话”的产品语义。

### P2：声明的可靠性、可观测性与当前 MVP 实现范围需要同步

README 与 DESIGN 中列出 Redis、MinIO、Camunda、Prometheus/Grafana、OpenTelemetry、DLQ、对账和恢复等目标，
但当前 Compose 服务仅包括 PostgreSQL、RocketMQ 和三个 Java 服务，代码中也没有相应运行实现；`.env.example`
第 13–16 行仍保留 Redis/MinIO 密码变量。

建议：在 README 加一张“已实现 / 计划中 / 不在 MVP”能力表；删除当前未使用的环境变量，或在 Compose 与代码实际接入
后再提供它们。这样能避免演示、部署和面试说明出现能力超卖。

### P2：测试尚未覆盖消息和并发的关键失败路径

现有集成测试对 PostgreSQL RLS、认证、顺序幂等和流程推进覆盖良好；但测试 profile 显式排除了 RocketMQ
自动配置，因此未覆盖 Broker ACK、重复投递、乱序、不可解析消息、发布器退避、双副本抢占以及 IPC 来源校验。

建议：下一阶段引入 RocketMQ Testcontainers 或专用 Compose E2E job，并补充并发测试、契约测试和桌面端主进程
纯函数测试。不要为了测试而引入新的业务抽象；把 URL 来源校验和事件解析提取成小型纯函数即可。

## 可维护性与简化建议

### P3：删除 workflow 服务中未使用的 PasswordEncoder Bean

[`workflow-service` 的 `SecurityConfiguration.java`](../services/workflow-service/src/main/java/com/flowmesh/workflow/config/SecurityConfiguration.java)
第 81–89 行创建 `BCryptPasswordEncoder`，但 workflow 服务既不登录也不校验密码，仓库内没有对此 Bean 的使用。

建议：删除该 Bean 与相关 import。密码编码只应保留在 IAM，能减少无关职责与误导性配置。

### P3：JWT 过滤器不应把所有异常都归类为 401

[`JwtAuthenticationFilter.java`](../services/flowmesh-common/src/main/java/com/flowmesh/common/security/JwtAuthenticationFilter.java)
第 61–74 行捕获 `Exception`。签名、过期和 claim 格式错误可以返回 401，但序列化器故障等服务器错误也会被伪装为
认证失败，影响排障与监控。

建议：仅捕获 JWT 解析和声明校验相关异常；其他异常按 500 记录并交给统一异常处理。无需为此增加复杂的抽象层。

## 推荐修复顺序

1. 处理 P0 演示账号，并轮换任何已暴露环境的 JWT/数据库凭据。
2. 修复 Electron 来源校验和 Helm/Compose Secret 失败保护。
3. 为 Outbox 增加认领、退避、DLQ 和双副本测试。
4. 用真实乐观锁与并发幂等仲裁完善申请状态链路。
5. 落地认证审计、事件契约校验与 IAM RLS 决策。
6. 最后清理未使用配置/Bean，并让 README 的能力声明与 MVP 保持一致。
