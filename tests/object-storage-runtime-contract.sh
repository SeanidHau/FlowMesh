#!/usr/bin/env bash

# 作用：验证对象存储桶自动创建策略在本地默认值、生产覆盖值和应用容器之间保持一致。
# 该测试不访问对象存储，只检查生产配置不会让应用运行账号隐式执行建桶操作。

set -Eeuo pipefail

repo_root="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
production_values="${repo_root}/infra/helm/flowmesh/values-production.yaml"
local_values="${repo_root}/infra/helm/flowmesh/values.yaml"
supplier_template="${repo_root}/infra/helm/flowmesh/templates/supplier.yaml"
application_config="${repo_root}/services/supplier-service/src/main/resources/application.yml"

grep -Fxq '  autoCreateBucket: false' "${production_values}"
grep -Fxq '  autoCreateBucket: true' "${local_values}"
grep -Fq 'FLOWMESH_OBJECT_STORAGE_AUTO_CREATE_BUCKET' "${supplier_template}"
grep -Fq 'auto-create-bucket: ${FLOWMESH_OBJECT_STORAGE_AUTO_CREATE_BUCKET:true}' "${application_config}"

echo 'Object storage runtime contract passed.'
