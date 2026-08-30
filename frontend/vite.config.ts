import { defineConfig } from 'vite';
import vue from '@vitejs/plugin-vue';

const apiProxy = {
  '/api/iam': {
    target: 'http://127.0.0.1:8081',
    rewrite: (path: string) => path.replace(/^\/api\/iam/, ''),
  },
  '/api/supplier': {
    target: 'http://127.0.0.1:8082',
    rewrite: (path: string) => path.replace(/^\/api\/supplier/, ''),
  },
  '/api/workflow': {
    target: 'http://127.0.0.1:8083',
    rewrite: (path: string) => path.replace(/^\/api\/workflow/, ''),
  },
};

/**
 * 配置渲染进程的 Vite 开发与生产构建。
 */
export default defineConfig({
  plugins: [vue()],
  server: {
    port: 5173,
    strictPort: true,
    proxy: apiProxy,
  },
  preview: {
    port: 4173,
    strictPort: true,
    proxy: apiProxy,
  },
  build: {
    outDir: 'dist',
    emptyOutDir: true,
  },
});
