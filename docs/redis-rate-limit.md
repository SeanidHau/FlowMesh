# Redis 登录限流实施说明

## 目标

第一阶段仅为 `iam-service` 的登录接口增加分布式失败次数限流，使用 Redis 共享计数，避免多实例部署时每个实例各自计数。

Redis 只负责性能和风控辅助，不承载认证事实：用户、租户、密码和 Refresh Token 仍以 PostgreSQL 为准。Redis 不可用时限流器必须记录告警并降级放行，不能让 Redis 故障阻断正常登录。

## 限流策略

| 维度 | Key 组成 | 默认阈值 | 窗口 |
| --- | --- | ---: | --- |
| 账号 | 租户标识 + 规范化用户名 | 5 次失败 | 1 分钟 |
| 客户端 | 客户端地址 | 30 次失败 | 1 分钟 |

- 只统计认证失败，不统计成功登录。
- 账号和客户端任一维度超限，返回 `429 Too Many Requests`。
- 登录成功后清除账号维度计数，不清除客户端维度计数。
- Key 不得包含密码、Access Token 或 Refresh Token。
- 使用 Redis 原子脚本完成“递增 + 首次设置过期时间”，避免多实例竞争造成窗口失效。

## Java 实现边界

由业务代码编写以下部分：

1. `LoginRateLimitProperties`：绑定 `flowmesh.security.login-rate-limit` 配置。
2. `LoginRateLimiter`：使用 `StringRedisTemplate` 执行检查、记录失败和清除账号计数。
3. `LoginRateLimitExceededException`：表示任一限流维度超限。
4. `AuthController`：在调用认证应用服务前检查限流；认证失败后记录失败；成功后清除账号计数。
5. `GlobalExceptionHandler`：将限流异常映射为统一的 `429` 错误响应。

实现时应保留 Javadoc，并明确 Redis 异常的降级行为。不要把 Redis 查询结果当作用户是否存在或密码是否正确的依据，避免引入账号枚举问题。

## 验收标准

- 同一个账号连续失败 5 次后，第 6 次在窗口内返回 `429`。
- 同一客户端地址连续失败 30 次后，第 31 次在窗口内返回 `429`。
- 成功登录后，该账号的失败计数清零。
- 两个 IAM 实例共享 Redis 时，计数结果一致。
- Redis 停止时登录接口仍可完成认证，日志中有明确告警。
- 认证集成测试、Redis 限流单元测试和 Compose 健康检查均通过。
