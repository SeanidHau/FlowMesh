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
npm run dev
```

只使用浏览器预览和联调时执行：

```bash
npm run dev:web
```

然后访问 `http://127.0.0.1:5173`。Web 模式通过 Vite 代理访问三个后端服务，
因此不需要启动 Electron，也不会产生跨域请求。先按根目录文档启动后端服务即可。

生产构建后的浏览器预览：

```bash
npm run build:renderer
npm run preview
```

然后访问 `http://127.0.0.1:4173`。

开发模式默认访问以下后端地址：

| 服务 | 地址 |
| --- | --- |
| IAM | `http://localhost:8081` |
| supplier | `http://localhost:8082` |
| workflow | `http://localhost:8083` |

可以通过环境变量覆盖地址：

```bash
FLOWMESH_IAM_URL=http://localhost:8081 \
FLOWMESH_SUPPLIER_URL=http://localhost:8082 \
FLOWMESH_WORKFLOW_URL=http://localhost:8083 \
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

打包后的桌面端默认访问 `localhost` 上的三个后端端口。部署到其他环境时，在启动桌面端前设置
`FLOWMESH_IAM_URL`、`FLOWMESH_SUPPLIER_URL` 和 `FLOWMESH_WORKFLOW_URL`。
