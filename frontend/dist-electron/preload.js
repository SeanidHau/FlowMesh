import { contextBridge, ipcRenderer } from 'electron';
/**
 * 只向本地渲染进程暴露受限的 API 请求能力，不暴露 Node 或 Electron 原始对象。
 */
contextBridge.exposeInMainWorld('flowmesh', {
    request: (request) => ipcRenderer.invoke('flowmesh:request', request),
});
