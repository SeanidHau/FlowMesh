# 作用：构建用于 Kubernetes CronJob 的 PostgreSQL 备份镜像。
# 镜像同时提供 PostgreSQL 客户端和 AWS CLI，支持 S3 兼容对象存储。
FROM postgres:16.15-bookworm@sha256:bb3e1a57e5407e0a5280b4211980a5e537f4abd234a87014ac979849a78dd825

RUN apt-get update \
    && apt-get install --yes --no-install-recommends awscli ca-certificates \
    && rm -f /usr/local/bin/gosu \
    && rm -rf /var/lib/apt/lists/*

COPY scripts/backup-postgres.sh /opt/flowmesh/scripts/backup-postgres.sh
COPY infra/backup/entrypoint.sh /usr/local/bin/flowmesh-postgres-backup

RUN chmod 0755 /opt/flowmesh/scripts/backup-postgres.sh /usr/local/bin/flowmesh-postgres-backup \
    && mkdir -p /backup /tmp/aws \
    && chown -R postgres:postgres /backup /tmp/aws /opt/flowmesh

USER postgres
WORKDIR /backup
ENTRYPOINT ["/usr/local/bin/flowmesh-postgres-backup"]
