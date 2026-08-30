# Docker Compose 本地环境

本目录保存 FlowMesh 的本地基础设施与服务编排文件。当前 Compose 提供 PostgreSQL，
并在首次启动时创建 IAM 和 supplier 服务使用的独立 Schema 与 NOSUPERUSER 账号。
Redis、MinIO、RocketMQ 和 Camunda 8 会在对应业务切片落地时加入。

本地环境使用仓库根目录的 `.env`。先复制 `.env.example`，再填写本地凭据。不要提交 `.env`。

```bash
cp .env.example .env
docker compose --env-file .env -f infra/compose/docker-compose.yml up -d postgres
docker compose -f infra/compose/docker-compose.yml ps
```

PostgreSQL 数据卷只会在第一次初始化时执行 `postgres/init` 脚本；若修改账号或 Schema
初始化逻辑，需要清理本地演示数据后重新创建卷。
