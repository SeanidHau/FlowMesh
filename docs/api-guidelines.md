# API 设计规范

## 基本约定

- REST API 使用 JSON，并以 `/api/v1` 作为路径前缀。
- 路径使用复数资源名，例如 `/api/v1/supplier-applications`。
- 认证使用 `Authorization: Bearer <access-token>`。
- 业务 API 客户端不得通过 Header、请求体或路径指定有效 `tenantId`。服务端从已验证 JWT 解析租户上下文。
- `traceId` 由网关生成或透传，并返回到响应 Header。

认证接口是认证前置场景的例外：`login` 使用请求体中的 `tenantId` 选择认证查询范围；`refresh` 和
`logout` 也要求请求体提供 `tenantId`，用于在解析不透明 Refresh Token 前建立 PostgreSQL RLS 上下文。
服务端会校验令牌所属用户的实际租户，成功登录或刷新后只在签名 JWT 中返回可信租户声明。

## 认证接口

| 方法 | 路径 | 请求体 | 说明 |
| --- | --- | --- | --- |
| `POST` | `/api/v1/auth/login` | `tenantId`、`username`、`password` | 按租户和用户名登录 |
| `POST` | `/api/v1/auth/refresh` | `tenantId`、`refreshToken` | 按租户建立 RLS 上下文后轮换令牌 |
| `POST` | `/api/v1/auth/logout` | `tenantId`、`refreshToken` | 按租户建立 RLS 上下文后撤销当前令牌 |

`tenantId` 最大长度为 64 个字符；`refreshToken` 只以哈希形式保存，不能写入日志、审计记录或错误响应。
`username` 最大长度为 64 个字符，`password` 最大长度为 128 个字符，`refreshToken` 最大长度为 128 个字符。
认证请求中的必填字段为空时返回 `400`，登录限流触发时返回 `429`
并携带按限流窗口计算的 `Retry-After` 秒数。

## 创建与幂等

创建申请等非幂等写操作必须提供 `Idempotency-Key`：

```http
POST /api/v1/supplier-applications
Idempotency-Key: 3a0c3fb8-7d60-4c69-8b2b-8656c4a7d8ee
```

服务端以 `tenantId + userId + Idempotency-Key` 建立唯一记录并持久化首次响应快照。重复请求返回首次响应，不得创建第二个申请或流程实例。
`supplierName` 最大长度为 255 个字符，超过限制时返回 `400`。

## 审批流程

workflow-service 提供以下最小审批接口：

| 方法 | 路径 | 说明 |
| --- | --- | --- |
| `GET` | `/api/v1/workflow-instances/{applicationId}` | 查询当前租户可见的流程实例 |
| `POST` | `/api/v1/workflow-instances/{applicationId}/tasks` | 完成当前角色任务并推进流程 |

任务请求体使用不超过 64 个字符的待办任务键，例如 `{"taskKey":"PURCHASER_REVIEW","decision":"APPROVE"}`。服务端从 JWT
读取租户和角色；任务键不在响应的 `availableTasks` 中返回 `409`，角色不足返回 `403`。
采购初审完成后，`LEGAL_REVIEW` 和 `FINANCE_REVIEW` 可以同时出现在 `availableTasks`；
两个会签任务都完成后才生成 `OPERATIONS_ACTIVATION`。

流程响应至少包含以下状态字段：

```json
{
  "status": "IN_PROGRESS",
  "currentTask": "LEGAL_REVIEW",
  "availableTasks": ["LEGAL_REVIEW", "FINANCE_REVIEW"],
  "completedTasks": ["PURCHASER_REVIEW"]
}
```

`currentTask` 是面向旧客户端和摘要展示的流程指针，不代表唯一可操作任务；客户端应使用
`availableTasks` 决定当前角色可执行的任务，并使用 `completedTasks` 渲染历史进度。

审批节点支持以下决定：

| `decision` | 说明 |
| --- | --- |
| `APPROVE` | 完成当前审批节点；为空时兼容旧客户端，按通过处理。 |
| `RETURN_FOR_SUPPLEMENT` | 退回申请人补件；法务/财务节点必须填写 `comment`，运营处置节点不可使用。 |

申请人补件接口：

```http
POST /api/v1/supplier-applications/{applicationId}/supplements
Authorization: Bearer <access-token>
Idempotency-Key: supplement-round-1
Content-Type: application/json

{"comment":"已补充近三个月的合规证明"}
```

补件请求必须由原申请人发起，服务端最多允许两轮补件；成功后返回 `SUBMITTED`，并通过
`SupplementSubmitted` 事件通知 workflow 开启下一轮采购初审。

## 站内通知

notification-audit-service 提供当前登录用户的通知查询和已读操作：

| 方法 | 路径 | 说明 |
| --- | --- | --- |
| `GET` | `/api/v1/notifications?limit=20` | 查询当前租户、当前用户的最近通知 |
| `PUT` | `/api/v1/notifications/{notificationId}/read` | 将当前用户可见的通知标记为已读，重复调用幂等 |

服务端同时按 JWT 中的租户和用户条件更新，不能通过修改路径参数读取或更新其他用户的通知；资源不存在或不属于当前用户统一返回 `404`。

## 并发更新

- 审批、状态转换和运维操作必须携带资源版本或命令 ID。
- 数据库乐观锁是最终并发裁决。Redis 锁仅用于降低并发冲突。
- 审批命令处理中返回 `202 Accepted` 时，响应必须包含可查询的 `approvalCommandId`。

## 错误响应

```json
{
  "code": "SUPPLIER_APPLICATION_STATE_CONFLICT",
  "message": "当前申请状态不允许执行此操作。",
  "traceId": "trace-id",
  "details": []
}
```

| 状态码 | 使用场景 |
| --- | --- |
| `400` | 请求格式或字段校验失败 |
| `401` | 缺少、失效或无效 Token |
| `403` | 角色、租户或资源访问不允许 |
| `404` | 当前租户下不存在资源 |
| `409` | 状态冲突、乐观锁冲突或幂等键用途冲突 |
| `422` | 请求语义合法但不满足业务规则 |
| `429` | 触发网关限流 |
| `500` | 未预期服务错误 |
| `503` | 依赖不可用或暂时无法处理 |

非法 JSON、查询参数类型错误或缺少必填查询参数统一返回 `INVALID_REQUEST`；未预期的服务端异常统一返回
`INTERNAL_ERROR`，响应不包含堆栈、SQL、凭据或下游依赖的内部详情。

## 分页与时间

- 列表接口使用游标分页，优先返回 `nextCursor`。
- 所有 API 时间字段使用 RFC 3339 UTC 格式。
- UI 再按用户或租户时区展示；首版默认 `Asia/Shanghai`。

## 兼容性

- 可兼容变更可以增加可选字段。
- 破坏性 REST 变更创建 `/api/v2`。
- 破坏性事件变更创建新事件类型或新消费者版本。
