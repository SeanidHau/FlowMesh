# Docker Compose 本地环境

本目录保存 FlowMesh 的本地基础设施与服务编排文件。当前 Compose 提供 PostgreSQL、Redis、MinIO、
RocketMQ 单节点拓扑和六个 Java 应用服务，并在首次启动 PostgreSQL 时创建各应用服务使用的独立
Schema 与 NOSUPERUSER 账号。Prometheus 和 Grafana 通过 `observability` profile 按需启动。

本地环境使用仓库根目录的 `.env`。先复制 `.env.example`，再填写本地凭据。不要提交 `.env`。

```bash
cp .env.example .env
./infra/compose/validate-env.sh .env
docker compose --env-file .env -f infra/compose/docker-compose.yml up -d postgres redis rocketmq-namesrv rocketmq-broker
docker compose -f infra/compose/docker-compose.yml ps
```

启动完整本地链路时，使用仓库根目录的运行手册。需要查看指标和告警时，先启动基础环境，再启用
观测 profile：

```bash
docker compose --env-file .env -f infra/compose/docker-compose.yml --profile observability up -d prometheus alertmanager grafana
```

Prometheus 地址为 `http://localhost:9090`，Alertmanager 地址为 `http://localhost:9093`，Grafana 地址为 `http://localhost:3000`。Grafana 使用
`.env` 中的 `GRAFANA_ADMIN_USER` 和 `GRAFANA_ADMIN_PASSWORD`，并自动加载
`dashboards/flowmesh-overview.json`。

本地 Alertmanager 只保留告警并在 UI 展示，不发送邮件、短信或企业 IM。生产环境必须替换
`alertmanager.yml` 中的 receiver、分组和静默策略，并配置通知渠道、值班责任和告警恢复演练。

PostgreSQL 数据卷只会在第一次初始化时执行 `postgres/init` 脚本；若修改账号或 Schema
初始化逻辑，需要清理本地演示数据后重新创建卷。

宿主机运行 Java 服务时使用 `ROCKETMQ_NAMESRV_ADDR=localhost:9876`；IAM 服务的登录限流连接
`REDIS_HOST=localhost` 和 `REDIS_PORT=6379`。如果后续把服务也
放入 Compose 网络，再改为 `rocketmq-namesrv:9876`。Compose 中各服务的 Outbox 发布器和消费者
默认开启，申请提交后会经过 workflow 的异步风控请求与 risk-service 的风控结果事件链路。supplier
在生产配置下还会检查 MinIO 材料桶；启用文件扫描时，同时检查 ClamAV TCP 端口。

Gateway 默认通过 Redis 令牌桶对业务路由执行全局限流。生产 Ingress 必须覆写
`FLOWMESH_GATEWAY_CLIENT_IP_HEADER` 指定的请求头（默认 `X-Real-IP`），不要让公网客户端直接决定该值。
