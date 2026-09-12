# 贡献指南

FlowMesh 是一个面向多租户 B2B SaaS 的供应商准入与采购合同审批平台。贡献应同时满足功能正确性、租户隔离、失败恢复和可验证性要求。

## 开始前

- 使用 Java 21。
- 后端统一使用仓库根目录的 Maven Wrapper：`./mvnw`。
- 前端工作目录为 `frontend/`，使用 Node.js 和 npm。
- 只有 PostgreSQL、RocketMQ 或 Testcontainers 集成测试需要时才启动 Docker。验证结束后停止本次启动的容器，并关闭 Docker Desktop。
- 不要提交 `.env`、真实凭据、证书、构建产物、备份文件或本地 IDE 配置。

## 开发与验证

1. 从最新的 `main` 创建工作分支。
2. 修改业务代码时保持服务边界，不直接访问其他服务的数据表。
3. 为关键业务分支、权限边界、幂等和失败恢复补充自动化测试。
4. 在仓库根目录执行：

   ```bash
   ./mvnw -q test
   npm --prefix frontend run build
   bash scripts/validate-production-readiness.sh
   git diff --check
   ```

5. 如果本机没有 Docker，必须明确记录集成测试的环境限制；不能把未执行的集成测试报告为通过。GitHub Actions 会在具备 Docker 的环境中执行完整回归。
6. 修改 Helm 或 Kubernetes 配置时，额外执行 `helm lint infra/helm/flowmesh` 和合法配置渲染检查。

## 代码与文档约定

- Java 公共类、方法和配置项使用 Javadoc，说明参数、返回值和失败语义。
- API、事件、数据库迁移、运行手册和面试文档必须与实际实现同步。
- 事件消费者必须保留幂等、租户校验和不可重试坏消息的处置路径。
- 生产配置不得使用示例凭据、默认 JWT 密钥或本地依赖地址。
- 生产发布、恢复演练和证据包不得绕过审批或跳过必需检查。

## 提交与 Pull Request

提交信息使用简短的 Conventional Commits 风格，例如 `feat: add supplier document scan` 或 `fix: bound outbox retry delay`。

Pull Request 描述至少包含：

- 变更目的和影响范围；
- 数据库迁移、兼容性和回滚说明；
- 已执行的测试命令及结果；
- 安全、租户隔离、幂等和可观测性影响；
- 必要的运行手册、架构文档和事件契约更新。

涉及 `.github/`、`infra/`、`scripts/`、数据库迁移、安全边界或生产配置的变更，应由对应的 CODEOWNERS 审查。合并前必须通过 CI 和 Security 工作流。

## 生产发布

生产发布只能使用主分支上已经通过 CI、漏洞扫描和镜像签名的提交 SHA。发布、验收和恢复演练均通过手动工作流执行，并依赖 GitHub `production` Environment 审批和目标平台证据。具体步骤见 [生产环境实施手册](docs/production-environment.md) 与 [运行手册](docs/runbook.md)。
