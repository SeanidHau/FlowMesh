# FlowMesh 镜像准入策略

`verify-flowmesh-images.yaml` 是可选的 Kyverno `ClusterPolicy`，用于拒绝未通过 Cosign
keyless 签名校验的 FlowMesh 镜像，并把镜像标签转换为不可变 digest。

前置条件：

- 目标集群已安装 Kyverno，并允许访问 GHCR 和 Sigstore Rekor。
- CI 已使用 GitHub OIDC 为完整提交 SHA 镜像签名。
- 集群能够访问 `ghcr.io/seanidhau/flowmesh/*` 镜像。

应用策略：

```bash
kubectl apply -f infra/policies/kyverno/verify-flowmesh-images.yaml
kubectl get clusterpolicy verify-flowmesh-images
```

策略只匹配 FlowMesh 镜像，不影响同一集群中的其他工作负载。若企业使用私有 GHCR，需按
Kyverno 文档配置镜像仓库凭据；若 Sigstore 使用企业内部 Rekor，则修改策略中的 `rekor.url`。
