#!/usr/bin/env bash
# 作用：验证 Helm Chart 的应用和维护 Pod 都支持可选的私有镜像仓库凭据。

set -euo pipefail

repo_root="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
values_file="${repo_root}/infra/helm/flowmesh/values.yaml"
helpers_file="${repo_root}/infra/helm/flowmesh/templates/_helpers.tpl"

grep -F -- 'imagePullSecrets: []' "${values_file}" >/dev/null
grep -F -- 'define "flowmesh.imagePullSecrets"' "${helpers_file}" >/dev/null

for template in \
  gateway.yaml iam.yaml supplier.yaml workflow.yaml risk.yaml notification-audit.yaml \
  backup-cronjob.yaml retention-cronjob.yaml workflow-sla-cronjob.yaml; do
  template_file="${repo_root}/infra/helm/flowmesh/templates/${template}"
  grep -F -- 'include "flowmesh.imagePullSecrets"' "${template_file}" >/dev/null || {
    printf 'Helm 模板缺少 imagePullSecrets 支持：%s\n' "${template}" >&2
    exit 1
  }
done

echo 'Image pull secret contract passed.'
