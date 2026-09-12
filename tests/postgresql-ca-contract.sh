#!/usr/bin/env bash
# 作用：验证 PostgreSQL verify-ca/verify-full 的 CA 注入链路覆盖应用和维护任务。

set -Eeuo pipefail

root_dir="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
chart_dir="${root_dir}/infra/helm/flowmesh"
helpers="${chart_dir}/templates/_helpers.tpl"

assert_contains() {
  local file="$1"
  local text="$2"
  if ! grep -F -- "${text}" "${file}" >/dev/null; then
    printf 'PostgreSQL CA 契约失败：%s 缺少 %s\n' "${file}" "${text}" >&2
    exit 1
  fi
}

assert_contains "${chart_dir}/values.yaml" 'caSecretName: ""'
assert_contains "${chart_dir}/values.yaml" 'caSecretKey: ca.crt'
assert_contains "${chart_dir}/values-production.yaml" 'sslMode: verify-full'
assert_contains "${chart_dir}/values-production.yaml" 'caSecretName: flowmesh-postgresql-ca'

ruby - "${chart_dir}/values-production.yaml" <<'RUBY'
require "yaml"

values = YAML.load_file(ARGV.fetch(0))
config = values.fetch("postgresql")
raise "postgresql 必须使用 verify-full" unless config.fetch("sslMode") == "verify-full"
raise "postgresql 必须配置 CA Secret" unless config.fetch("caSecretName") == "flowmesh-postgresql-ca"
%w[backup retention].each do |section|
  config = values.fetch(section).fetch("postgres")
  raise "#{section} 必须使用 verify-full" unless config.fetch("sslMode") == "verify-full"
  raise "#{section} 必须配置 CA Secret" unless config.fetch("caSecretName") == "flowmesh-postgresql-ca"
end
RUBY
assert_contains "${helpers}" 'sslrootcert=/etc/flowmesh/postgresql/ca.crt'
assert_contains "${helpers}" 'postgresql.caSecretName is required'
assert_contains "${helpers}" 'mode: 0444'

for schema_template in iam supplier workflow risk notification-audit; do
  template="${chart_dir}/templates/${schema_template}.yaml"
  assert_contains "${template}" 'flowmesh.postgresqlJdbcUrl'
  assert_contains "${template}" 'flowmesh.postgresqlCaVolumeMount'
  assert_contains "${template}" 'flowmesh.postgresqlCaVolume'
done

assert_contains "${chart_dir}/templates/workflow-sla-cronjob.yaml" 'name: PGSSLROOTCERT'
assert_contains "${chart_dir}/templates/workflow-sla-cronjob.yaml" 'flowmesh.postgresqlCaVolume'
assert_contains "${chart_dir}/templates/backup-cronjob.yaml" 'name: FLOWMESH_PG_SSLROOTCERT'
assert_contains "${chart_dir}/templates/backup-cronjob.yaml" 'flowmesh.postgresqlCaVolume'
assert_contains "${chart_dir}/templates/retention-cronjob.yaml" 'name: FLOWMESH_PG_SSLROOTCERT'
assert_contains "${chart_dir}/templates/retention-cronjob.yaml" 'flowmesh.postgresqlCaVolume'

for script in backup-postgres.sh restore-postgres.sh cleanup-flowmesh-retention.sh \
  validate-backup-role.sh validate-retention-role.sh validate-production-dependencies.sh \
  validate-production-ha.sh; do
  assert_contains "${root_dir}/scripts/${script}" 'PGSSLROOTCERT'
done

printf 'PostgreSQL CA contract passed.\n'
