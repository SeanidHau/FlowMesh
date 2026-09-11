#!/usr/bin/env bash
# 作用：部署前验证应用和备份镜像均使用完整提交 SHA，并由受信任的 GitHub Actions 工作流签名。

set -euo pipefail

image_tag="${FLOWMESH_IMAGE_TAG:-${GITHUB_SHA:-}}"
identity_regexp="${COSIGN_CERTIFICATE_IDENTITY_REGEXP:-https://github.com/SeanidHau/FlowMesh/.github/workflows/ci.yml@refs/heads/main}"
oidc_issuer="${COSIGN_CERTIFICATE_OIDC_ISSUER:-https://token.actions.githubusercontent.com}"

if [[ ! "${image_tag}" =~ ^[0-9a-f]{40}$ ]]; then
  echo 'FLOWMESH_IMAGE_TAG 必须是 40 位小写 Git 提交 SHA。' >&2
  exit 2
fi

services=(
  gateway-service
  iam-service
  supplier-service
  workflow-service
  risk-service
  notification-audit-service
  postgres-backup
  postgres-retention
)

for service in "${services[@]}"; do
  image="ghcr.io/seanidhau/flowmesh/${service}:${image_tag}"
  cosign verify \
    --certificate-identity-regexp "${identity_regexp}" \
    --certificate-oidc-issuer "${oidc_issuer}" \
    "${image}" >/dev/null
  echo "镜像签名校验通过：${image}"
done
