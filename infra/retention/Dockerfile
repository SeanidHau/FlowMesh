# 作用：构建用于 Kubernetes CronJob 的 FlowMesh 数据生命周期维护镜像。
FROM postgres:16-bookworm

COPY scripts/cleanup-flowmesh-retention.sh /opt/flowmesh/scripts/cleanup-flowmesh-retention.sh
COPY scripts/validate-retention-role.sh /opt/flowmesh/scripts/validate-retention-role.sh
COPY infra/retention/entrypoint.sh /usr/local/bin/flowmesh-retention

RUN chmod 0755 /opt/flowmesh/scripts/cleanup-flowmesh-retention.sh \
    /opt/flowmesh/scripts/validate-retention-role.sh \
    /usr/local/bin/flowmesh-retention

USER postgres
ENTRYPOINT ["/usr/local/bin/flowmesh-retention"]
