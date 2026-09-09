# Helm 与 kind 部署

`flowmesh/` Chart 部署 gateway、IAM、supplier 和 workflow 四个应用服务。PostgreSQL、Redis 与 RocketMQ
作为外部依赖，通过 `values.yaml` 配置地址；演示环境使用单副本或单 Broker 拓扑，不代表生产
高可用部署。

## 安装应用服务

先确保集群可以访问三个镜像，并准备 PostgreSQL、RocketMQ 以及对应的数据库 Schema 和账号。
在仓库根目录执行：

```bash
helm lint infra/helm/flowmesh \
  --set global.jwtSigningKey="$JWT_SIGNING_KEY" \
  --set global.redisPassword="$REDIS_PASSWORD" \
  --set services.iam.dbPassword="$IAM_DB_PASSWORD" \
  --set services.supplier.dbPassword="$SUPPLIER_DB_PASSWORD" \
  --set services.workflow.dbPassword="$WORKFLOW_DB_PASSWORD"
helm upgrade --install flowmesh infra/helm/flowmesh \
  --set global.jwtSigningKey="$JWT_SIGNING_KEY" \
  --set global.redisPassword="$REDIS_PASSWORD" \
  --set services.iam.dbPassword="$IAM_DB_PASSWORD" \
  --set services.supplier.dbPassword="$SUPPLIER_DB_PASSWORD" \
  --set services.workflow.dbPassword="$WORKFLOW_DB_PASSWORD"
```

生产环境应使用生产覆盖值，并让 `global.existingSecret` 指向外部 Secret：

```bash
helm upgrade --install flowmesh infra/helm/flowmesh \
  -f infra/helm/flowmesh/values-production.yaml \
  --set global.existingSecret=flowmesh-runtime-secrets
```

生产环境建议预先创建包含 `JWT_SIGNING_KEY`、`REDIS_PASSWORD`、`IAM_DB_PASSWORD`、
`SUPPLIER_DB_PASSWORD` 和 `WORKFLOW_DB_PASSWORD` 的 Secret，然后设置
`--set global.existingSecret=<secret-name>`。Chart 不会为缺少凭据或已知占位值的配置生成 Secret。

检查部署状态：

```bash
kubectl get deploy,svc,pods -l app.kubernetes.io/instance=flowmesh
```

Chart 默认启用三个消费者和 Outbox。只有 gateway 应作为外部 API 入口，业务服务保持 ClusterIP。
应用 Pod 使用非 root 用户、只读根文件系统、默认
Seccomp 配置、资源请求/限制、启动/就绪/存活探针、拓扑分散和优雅终止配置。生产覆盖值启用
双副本、PodDisruptionBudget 和基于 CPU 的 HPA；集群必须安装 Metrics Server 才能使用 HPA。
`postgresql.host`、`redis.host`、`rocketmq.namesrvAddr`、镜像地址和端口均可在自定义 values
文件中覆盖。
