import { contextBridge, ipcRenderer } from 'electron';

interface ApiRequest {
  service: 'iam' | 'supplier' | 'workflow';
  path: string;
  method?: 'GET' | 'POST';
  token?: string;
  body?: unknown;
  headers?: Record<string, string>;
}

interface ApiResponse {
  status: number;
  body: string;
}

/**
 * 只向本地渲染进程暴露受限的 API 请求能力，不暴露 Node 或 Electron 原始对象。
 */
contextBridge.exposeInMainWorld('flowmesh', {
  request: (request: ApiRequest): Promise<ApiResponse> =>
    ipcRenderer.invoke('flowmesh:request', request),
});
