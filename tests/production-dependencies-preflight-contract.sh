#!/usr/bin/env bash
# 作用：离线验证生产依赖预检会拒绝明文 PostgreSQL 和关闭 TLS 的 Redis。
set -euo pipefail

repo_root="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"

if FLOWMESH_PG_HOST=postgres-primary.internal \
  FLOWMESH_PG_USER=flowmesh \
  FLOWMESH_PG_SSLMODE=disable \
  FLOWMESH_REDIS_HOST=redis-primary.internal \
  FLOWMESH_REDIS_PASSWORD=test-redis-password \
  FLOWMESH_ROCKETMQ_NAMESRV_ADDR=namesrv-0.internal:9876 \
  FLOWMESH_OBJECT_STORAGE_ENDPOINT=https://objects.internal \
    "${repo_root}/scripts/validate-production-dependencies.sh" >/dev/null 2>&1; then
  echo '生产依赖预检不应允许 PostgreSQL 明文模式。' >&2
  exit 1
fi

if FLOWMESH_PG_HOST=postgres-primary.internal \
  FLOWMESH_PG_USER=flowmesh \
  FLOWMESH_PG_SSLMODE=require \
  FLOWMESH_REDIS_HOST=redis-primary.internal \
  FLOWMESH_REDIS_PASSWORD=test-redis-password \
  FLOWMESH_REDIS_TLS=false \
  FLOWMESH_ROCKETMQ_NAMESRV_ADDR=namesrv-0.internal:9876 \
  FLOWMESH_OBJECT_STORAGE_ENDPOINT=https://objects.internal \
    "${repo_root}/scripts/validate-production-dependencies.sh" >/dev/null 2>&1; then
  echo '生产依赖预检不应允许关闭 Redis TLS。' >&2
  exit 1
fi

echo 'Production dependency preflight contract passed.'
