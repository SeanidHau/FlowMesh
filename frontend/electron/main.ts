import { app, BrowserWindow, ipcMain, session } from 'electron';
import path from 'node:path';
import { fileURLToPath } from 'node:url';
import { isTrustedSender } from './security.js';

const __dirname = path.dirname(fileURLToPath(import.meta.url));
const DEV_SERVER_URL = 'http://127.0.0.1:5173';
const PACKAGED_ENTRY_PATH = path.join(__dirname, '../dist/index.html');

type ApiService = 'iam' | 'supplier' | 'workflow' | 'notification';

interface ApiRequest {
  service: ApiService;
  path: string;
  method?: 'GET' | 'POST' | 'PUT';
  token?: string;
  body?: unknown;
  file?: { name: string; type: string; data: ArrayBuffer };
  headers?: Record<string, string>;
}

interface ApiResponse {
  status: number;
  body: string;
}

// 生产桌面端默认只访问统一 Gateway，避免绕过入口认证、限流和审计策略。
const gatewayUrl = process.env.FLOWMESH_GATEWAY_URL ?? 'http://127.0.0.1:8080';

/**
 * 创建安全的 Electron 主窗口。
 */
function createWindow(): void {
  const window = new BrowserWindow({
    width: 1440,
    height: 940,
    minWidth: 1120,
    minHeight: 720,
    backgroundColor: '#111820',
    title: 'FlowMesh',
    webPreferences: {
      preload: path.join(__dirname, 'preload.js'),
      contextIsolation: true,
      nodeIntegration: false,
      sandbox: true,
      webSecurity: true,
    },
  });

  window.webContents.setWindowOpenHandler(() => ({ action: 'deny' }));

  if (app.isPackaged) {
    void window.loadFile(path.join(__dirname, '../dist/index.html'));
  } else {
    void window.loadURL(DEV_SERVER_URL);
  }
}

function parseRequest(value: unknown): ApiRequest {
  if (!value || typeof value !== 'object') {
    throw new Error('非法 IPC 请求');
  }
  const request = value as Partial<ApiRequest>;
  if (!['iam', 'supplier', 'workflow', 'notification'].includes(request.service ?? '')) {
    throw new Error('未知 API 服务');
  }
  if (!request.path?.startsWith('/api/v1/')) {
    throw new Error('只允许访问 /api/v1 API');
  }
  if (request.method && !['GET', 'POST', 'PUT'].includes(request.method)) {
    throw new Error('不支持的 HTTP 方法');
  }
  if (request.file && request.body !== undefined) {
    throw new Error('文件请求不能同时携带 JSON 请求体');
  }
  if (request.file && (!request.file.name || request.file.data.byteLength > 21 * 1024 * 1024)) {
    throw new Error('文件请求不符合大小限制');
  }
  return request as ApiRequest;
}

async function requestApi(event: Electron.IpcMainInvokeEvent, value: unknown): Promise<ApiResponse> {
  const senderUrl = event.senderFrame?.url ?? '';
  if (!isTrustedSender(senderUrl, {
    packaged: app.isPackaged,
    devServerUrl: DEV_SERVER_URL,
    packagedEntryPath: PACKAGED_ENTRY_PATH,
  })) {
    throw new Error('未授权的 IPC 调用来源');
  }

  const request = parseRequest(value);
  const url = new URL(`/api/${request.service}${request.path}`, gatewayUrl);
  const headers: Record<string, string> = { Accept: 'application/json' };
  Object.assign(headers, request.headers);
  // IPC 调用方不能覆盖由主进程注入的访问令牌，避免渲染层请求头伪造。
  if (request.token) {
    headers.Authorization = `Bearer ${request.token}`;
  } else {
    delete headers.Authorization;
  }
  if (request.body !== undefined) {
    headers['Content-Type'] = 'application/json';
  }

  let body: BodyInit | undefined;
  if (request.file) {
    const formData = new FormData();
    formData.append('file', new Blob([request.file.data], { type: request.file.type }), request.file.name);
    body = formData;
  } else if (request.body !== undefined) {
    body = JSON.stringify(request.body);
  }

  try {
    const response = await fetch(url, {
      method: request.method ?? 'GET',
      headers,
      body,
    });
    return { status: response.status, body: await response.text() };
  } catch (error) {
    const message = error instanceof Error ? error.message : '未知网络错误';
    throw new Error(`无法连接 ${request.service}-service：${message}`);
  }
}

ipcMain.handle('flowmesh:request', requestApi);

app.whenReady().then(() => {
  session.defaultSession.setPermissionRequestHandler((_webContents, _permission, callback) => {
    callback(false);
  });
  createWindow();
  app.on('activate', () => {
    if (BrowserWindow.getAllWindows().length === 0) createWindow();
  });
});

app.on('web-contents-created', (_event, contents) => {
  contents.on('will-navigate', (navigationEvent, url) => {
    if (!isTrustedSender(url, {
      packaged: app.isPackaged,
      devServerUrl: DEV_SERVER_URL,
      packagedEntryPath: PACKAGED_ENTRY_PATH,
    })) navigationEvent.preventDefault();
  });
});

app.on('window-all-closed', () => {
  if (process.platform !== 'darwin') app.quit();
});
