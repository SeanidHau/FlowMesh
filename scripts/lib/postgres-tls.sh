#!/usr/bin/env bash
# 作用：为 PostgreSQL 客户端脚本统一校验 TLS 模式和服务端身份校验所需的 CA 文件。

flowmesh_require_postgres_ca() {
  local sslmode="$1"
  local rootcert="${2:-}"

  case "${sslmode}" in
    verify-ca|verify-full)
      [[ -n "${rootcert}" ]] || {
        printf 'PostgreSQL %s 模式必须设置 FLOWMESH_PG_SSLROOTCERT。\n' "${sslmode}" >&2
        return 2
      }
      [[ -f "${rootcert}" && -r "${rootcert}" ]] || {
        printf 'PostgreSQL CA 文件不存在或不可读：%s\n' "${rootcert}" >&2
        return 2
      }
      ;;
  esac
}
