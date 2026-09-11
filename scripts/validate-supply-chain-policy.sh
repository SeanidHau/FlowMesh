#!/usr/bin/env bash
# 作用：校验 Kyverno 镜像签名策略包含不可变摘要和 GitHub OIDC 身份约束。

set -euo pipefail

policy_file="${1:-infra/policies/kyverno/verify-flowmesh-images.yaml}"
if [[ ! -f "${policy_file}" ]]; then
  echo "Kyverno 策略文件不存在：${policy_file}" >&2
  exit 2
fi

ruby - "${policy_file}" <<'RUBY'
require "yaml"

policy = YAML.load_file(ARGV.fetch(0))
raise "策略类型错误" unless policy.fetch("kind") == "ClusterPolicy"
rule = policy.fetch("spec").fetch("rules").first
verify_image = rule.fetch("verifyImages").first
reference = verify_image.fetch("imageReferences").first
raise "未限制 FlowMesh 镜像仓库" unless reference == "ghcr.io/seanidhau/flowmesh/*:*"
raise "必须要求镜像签名" unless verify_image.fetch("required") == true
raise "必须校验镜像 digest" unless verify_image.fetch("verifyDigest") == true
entry = verify_image.fetch("attestors").first.fetch("entries").first.fetch("keyless")
raise "缺少 GitHub OIDC issuer" unless entry.fetch("issuer") == "https://token.actions.githubusercontent.com"
raise "缺少受信任工作流 subject" unless entry.fetch("subject").include?("SeanidHau/FlowMesh/.github/workflows/ci.yml@refs/heads/main")
puts "Kyverno 镜像签名策略结构校验通过。"
RUBY
