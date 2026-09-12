# Redis 登录尝试限流实施说明

## 目标

当前 Redis 限流包含两层：Gateway 对所有业务路由执行分布式令牌桶限流，IAM 对登录接口执行按账号和客户端地址的尝试次数限流。两者都使用 Redis 共享状态，避免多实例部署时每个实例各自计数。

## Gateway 请求限流

Gateway 使用项目内的 Redis Lua 令牌桶过滤器，默认每秒补充 100 个令牌、桶容量 200、每个请求消耗 1 个令牌。限流 key 优先读取部署配置指定的可信客户端地址请求头，缺失时回退到 TCP 对端地址。

生产环境的 Ingress 必须覆写 `X-Real-IP`（或通过 `FLOWMESH_GATEWAY_CLIENT_IP_HEADER` 指定的其他请求头），并且不应把该请求头原样透传给公网客户端；否则调用方可以伪造多个地址绕过限流。Redis 不可用时过滤器返回 `503`，令牌耗尽时返回 `429`，避免在入口保护失效时继续放大流量。

Redis 只负责性能和风控辅助，不承载认证事实：用户、租户、密码和 Refresh Token 仍以 PostgreSQL 为准。IAM 登录限流本地默认在 Redis 不可用时记录告警并降级放行；生产 Helm 默认关闭降级放行，Redis 不可用时返回 `503`，避免登录保护被故障绕过。Gateway 入口限流始终 fail-closed。

## 限流策略

| 维度 | Key 组成 | 默认阈值 | 窗口 |
| --- | --- | ---: | --- |
| 账号 | 租户标识 + 规范化用户名 | 5 次尝试 | 1 分钟 |
| 客户端 | 客户端地址 | 30 次尝试 | 1 分钟 |

- 账号和客户端维度在认证前原子占用登录尝试额度；成功登录后清除账号维度计数，客户端维度仍按窗口累计。
- 账号和客户端任一维度超限，返回 `429 Too Many Requests`。
- `429` 响应的 `Retry-After` 使用配置的 `window` 计算，单位为秒；不足一秒时向上取整，且最小为 1 秒。
- Key 不得包含密码、Access Token 或 Refresh Token。
- 使用 Redis Lua 脚本原子完成“阈值检查 + 双维度递增 + 首次设置过期时间”，避免并发请求绕过阈值或窗口失效。

## Java 实现边界

以下部分已经完成：

1. `LoginRateLimitProperties`：绑定 `flowmesh.security.login-rate-limit` 配置。
2. `LoginRateLimiter`：使用 `StringRedisTemplate` 执行双维度原子检查、占用额度和清除账号计数。
3. `LoginRateLimitExceededException`：表示任一限流维度超限。
4. `AuthController`：在调用认证应用服务前检查限流；成功后清除账号计数。
5. `GlobalExceptionHandler`：将限流异常映射为统一的 `429` 错误响应。
6. `FLOWMESH_LOGIN_RATE_LIMIT_FAIL_OPEN`：控制 Redis 故障时的可用性/安全性取舍；生产值为 `false`。
7. 限流启用时，服务启动会拒绝非正数的账号阈值、客户端阈值或时间窗口，避免错误配置导致保护失效或所有请求被拒绝。

实现时应保留 Javadoc，并明确 Redis 异常的降级行为。不要把 Redis 查询结果当作用户是否存在或密码是否正确的依据，避免引入账号枚举问题。

## 验收标准

- 同一个账号连续失败 5 次后，第 6 次在窗口内返回 `429`。
- 同一客户端地址连续失败 30 次后，第 31 次在窗口内返回 `429`。
- 成功登录后，该账号的登录尝试计数清零。
- 两个 IAM 实例共享 Redis 时，计数结果一致。
- 本地 fail-open 模式下 Redis 停止时登录接口仍可完成认证，日志中有明确告警；生产 fail-closed 模式返回 `503`。
- 认证集成测试、Redis 限流单元测试和 Compose 健康检查均通过。
