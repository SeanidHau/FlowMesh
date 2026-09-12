# 作用：构建用于 Kubernetes CronJob 的 FlowMesh 数据生命周期维护镜像。
FROM postgres:16.15-bookworm@sha256:bb3e1a57e5407e0a5280b4211980a5e537f4abd234a87014ac979849a78dd825

COPY scripts/cleanup-flowmesh-retention.sh /opt/flowmesh/scripts/cleanup-flowmesh-retention.sh
COPY scripts/validate-retention-role.sh /opt/flowmesh/scripts/validate-retention-role.sh
COPY infra/retention/entrypoint.sh /usr/local/bin/flowmesh-retention

# 显式刷新 libpcre2，避免 PostgreSQL 基础层中的旧安全包进入发布镜像。
RUN apt-get update \
    && apt-get install --yes --no-install-recommends libpcre2-8-0 \
    && rm -rf /var/lib/apt/lists/* \
    && rm -f /usr/local/bin/gosu \
    && chmod 0755 /opt/flowmesh/scripts/cleanup-flowmesh-retention.sh \
    /opt/flowmesh/scripts/validate-retention-role.sh \
    /usr/local/bin/flowmesh-retention

USER postgres
ENTRYPOINT ["/usr/local/bin/flowmesh-retention"]
