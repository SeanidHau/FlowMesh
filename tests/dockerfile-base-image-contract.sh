#!/usr/bin/env bash
# 作用：确保所有发布镜像的 Dockerfile 基础镜像固定到已审核的多架构 digest。

set -euo pipefail

repo_root="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"

ruby - "${repo_root}" <<'RUBY'
root = ARGV.fetch(0)
dockerfiles = Dir[File.join(root, "**", "Dockerfile*")].reject do |path|
  path.include?(File.join("target", "")) || path.include?(File.join("node_modules", ""))
end.sort

expected = {
  "maven:3.9-eclipse-temurin-21" => "sha256:a972570be789ee5c9fa23446a8914ac7327560b5c022f662cfa9452aef829f18",
  "eclipse-temurin:21-jre-noble" => "sha256:d35199d74a3b2dff1bfb435d9adde4ef974e74a7d2a4fcb3079063c55926edb5",
  "postgres:16.15-bookworm" => "sha256:bb3e1a57e5407e0a5280b4211980a5e537f4abd234a87014ac979849a78dd825"
}

failures = []
dockerfiles.each do |path|
  File.readlines(path, chomp: true).each_with_index do |line, index|
    next unless line.match?(/^\s*FROM\s+/i)

    tokens = line.split
    from_index = tokens.index { |token| token.casecmp("FROM").zero? }
    image = tokens.fetch(from_index + 1)
    next if image == "scratch" || image.start_with?("--from=")

    name, digest = image.split("@", 2)
    expected_digest = expected[name]
    if expected_digest.nil? || digest != expected_digest
      failures << "#{path}:#{index + 1}: #{image}"
    end
  end
end

raise "Dockerfile 基础镜像未固定到已审核 digest：\n#{failures.join("\n")}" unless failures.empty?
raise "未发现 Dockerfile，供应链门禁未生效" if dockerfiles.empty?

puts "Dockerfile immutable base image contract passed (#{dockerfiles.length} files)."
RUBY
