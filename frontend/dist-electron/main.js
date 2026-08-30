import { app, BrowserWindow, ipcMain, session } from 'electron';
import path from 'node:path';
import { fileURLToPath } from 'node:url';
const __dirname = path.dirname(fileURLToPath(import.meta.url));
const DEV_SERVER_URL = 'http://127.0.0.1:5173';
const serviceUrls = {
    iam: process.env.FLOWMESH_IAM_URL ?? 'http://127.0.0.1:8081',
    supplier: process.env.FLOWMESH_SUPPLIER_URL ?? 'http://127.0.0.1:8082',
    workflow: process.env.FLOWMESH_WORKFLOW_URL ?? 'http://127.0.0.1:8083',
};
/**
 * 创建安全的 Electron 主窗口。
 */
function createWindow() {
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
    }
    else {
        void window.loadURL(DEV_SERVER_URL);
    }
}
function isTrustedSender(url) {
    return app.isPackaged
        ? url.startsWith('file://')
        : url.startsWith(DEV_SERVER_URL);
}
function parseRequest(value) {
    if (!value || typeof value !== 'object') {
        throw new Error('非法 IPC 请求');
    }
    const request = value;
    if (!['iam', 'supplier', 'workflow'].includes(request.service ?? '')) {
        throw new Error('未知 API 服务');
    }
    if (!request.path?.startsWith('/api/v1/')) {
        throw new Error('只允许访问 /api/v1 API');
    }
    if (request.method && !['GET', 'POST'].includes(request.method)) {
        throw new Error('不支持的 HTTP 方法');
    }
    return request;
}
async function requestApi(event, value) {
    const senderUrl = event.senderFrame?.url ?? '';
    if (!isTrustedSender(senderUrl)) {
        throw new Error('未授权的 IPC 调用来源');
    }
    const request = parseRequest(value);
    const url = new URL(request.path, serviceUrls[request.service]);
    const headers = { Accept: 'application/json' };
    if (request.token) {
        headers.Authorization = `Bearer ${request.token}`;
    }
    Object.assign(headers, request.headers);
    if (request.body !== undefined) {
        headers['Content-Type'] = 'application/json';
    }
    try {
        const response = await fetch(url, {
            method: request.method ?? 'GET',
            headers,
            body: request.body === undefined ? undefined : JSON.stringify(request.body),
        });
        return { status: response.status, body: await response.text() };
    }
    catch (error) {
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
        if (BrowserWindow.getAllWindows().length === 0)
            createWindow();
    });
});
app.on('web-contents-created', (_event, contents) => {
    contents.on('will-navigate', (navigationEvent, url) => {
        if (!isTrustedSender(url))
            navigationEvent.preventDefault();
    });
});
app.on('window-all-closed', () => {
    if (process.platform !== 'darwin')
        app.quit();
});
