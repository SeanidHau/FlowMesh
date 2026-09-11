# FlowMesh Desktop

FlowMesh Desktop 是基于 Electron、Vue 3 和 TypeScript 的供应商准入工作台。渲染进程只通过
安全的 IPC 调用后端，API 请求由 Electron 主进程发出；渲染进程不直接访问 Node.js API。

## 前置条件

- Node.js 26 或更高版本
- npm 11 或更高版本
- 已启动 FlowMesh 后端服务

## 本地开发

在 `frontend` 目录执行：

```bash
npm install
VITE_DEMO_MODE=true npm run dev
```

只使用浏览器预览和联调时执行：

```bash
VITE_DEMO_MODE=true npm run dev:web
```

然后访问 `http://127.0.0.1:5173`。Web 模式通过 Vite 代理访问统一 Gateway，
因此不需要启动 Electron，也不会产生跨域请求。先按根目录文档启动后端服务即可。

生产构建后的浏览器预览：

```bash
npm run build:renderer
npm run preview
```

然后访问 `http://127.0.0.1:4173`。

开发模式默认通过 Gateway 访问后端：

| 服务 | 地址 |
| --- | --- |
| Gateway | `http://localhost:8080` |
| IAM | 由 Gateway 路由到内部服务 |
| supplier | 由 Gateway 路由到内部服务 |
| workflow | 由 Gateway 路由到内部服务 |
| notification | 由 Gateway 路由到内部服务 |

可以通过 `VITE_API_GATEWAY_URL` 覆盖 Gateway 地址：

```bash
VITE_API_GATEWAY_URL=http://localhost:8080 \
npm run dev
```

## 构建与打包

```bash
npm run typecheck
npm run build
npm run package
```

`npm run build` 生成 `dist/` 和 `dist-electron/`。`npm run package` 使用
`electron-builder` 生成当前平台的安装包，产物位于 `frontend/release/`。

打包后的桌面端默认访问 `http://127.0.0.1:8080` 上的 Gateway。部署到其他环境时，在启动桌面端前设置
`FLOWMESH_GATEWAY_URL`。

桌面端请求统一发送到 Gateway 的 `/api/{service}/api/v1/**` 路径，不直接访问业务服务。

本地通过 Compose 验证统一入口时使用 `http://localhost:8080`。申请详情页支持上传 PDF、PNG、JPG 和 DOCX 材料，
下载使用后端签发的短期 URL。
