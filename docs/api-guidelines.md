# API 设计规范

## 基本约定

- REST API 使用 JSON，并以 `/api/v1` 作为路径前缀。
- 路径使用复数资源名，例如 `/api/v1/supplier-applications`。
- 认证使用 `Authorization: Bearer <access-token>`。
- 客户端不得通过 Header、请求体或路径指定有效 `tenantId`。服务端从已验证 JWT 解析租户上下文。
- `traceId` 由网关生成或透传，并返回到响应 Header。

## 创建与幂等

创建申请等非幂等写操作必须提供 `Idempotency-Key`：

```http
POST /api/v1/supplier-applications
Idempotency-Key: 3a0c3fb8-7d60-4c69-8b2b-8656c4a7d8ee
```

服务端以 `tenantId + userId + Idempotency-Key` 建立唯一记录并持久化首次响应快照。重复请求返回首次响应，不得创建第二个申请或流程实例。

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

## 分页与时间

- 列表接口使用游标分页，优先返回 `nextCursor`。
- 所有 API 时间字段使用 RFC 3339 UTC 格式。
- UI 再按用户或租户时区展示；首版默认 `Asia/Shanghai`。

## 兼容性

- 可兼容变更可以增加可选字段。
- 破坏性 REST 变更创建 `/api/v2`。
- 破坏性事件变更创建新事件类型或新消费者版本。
