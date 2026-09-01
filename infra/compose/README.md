# Docker Compose 本地环境

本目录保存 FlowMesh 的本地基础设施与服务编排文件。当前 Compose 提供 PostgreSQL、Redis 和
RocketMQ 单节点拓扑，并在首次启动 PostgreSQL 时创建 IAM 和 supplier 服务使用的独立
Schema 与 NOSUPERUSER 账号。

本地环境使用仓库根目录的 `.env`。先复制 `.env.example`，再填写本地凭据。不要提交 `.env`。

```bash
cp .env.example .env
./infra/compose/validate-env.sh .env
docker compose --env-file .env -f infra/compose/docker-compose.yml up -d postgres redis rocketmq-namesrv rocketmq-broker
docker compose -f infra/compose/docker-compose.yml ps
```

PostgreSQL 数据卷只会在第一次初始化时执行 `postgres/init` 脚本；若修改账号或 Schema
初始化逻辑，需要清理本地演示数据后重新创建卷。

宿主机运行 Java 服务时使用 `ROCKETMQ_NAMESRV_ADDR=localhost:9876`；IAM 服务的登录限流连接
`REDIS_HOST=localhost` 和 `REDIS_PORT=6379`。如果后续把服务也
放入 Compose 网络，再改为 `rocketmq-namesrv:9876`。supplier 的 Outbox 发布器默认关闭，
设置 `FLOWMESH_OUTBOX_ENABLED=true` 后才会定时向 `supplier-events:ApplicationSubmitted`
发布事件。
