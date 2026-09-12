#!/usr/bin/env bash
# 作用：防止生产 Workflow SLA CronJob 回退到可变的 PostgreSQL 镜像标签。

set -euo pipefail

repo_root="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
template="${repo_root}/infra/helm/flowmesh/templates/workflow-sla-cronjob.yaml"
helper="${repo_root}/infra/helm/flowmesh/templates/_helpers.tpl"
values="${repo_root}/infra/helm/flowmesh/values-production.yaml"

grep -F -- 'flowmesh.workflowSlaImageReference' "${template}" >/dev/null
grep -F -- 'workflowSla.imageDigest is required in production mode' "${helper}" >/dev/null
grep -Eq '^  imageDigest:[[:space:]]*""[[:space:]]*$' "${values}"

echo 'Workflow SLA image contract passed.'
