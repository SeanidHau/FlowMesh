import { defineConfig, loadEnv } from 'vite';
import vue from '@vitejs/plugin-vue';

/**
 * 配置渲染进程的 Vite 开发与生产构建。
 */
export default defineConfig(({ mode }) => {
  const env = loadEnv(mode, process.cwd(), '');
  const gatewayUrl = env.VITE_API_GATEWAY_URL || 'http://127.0.0.1:8080';
  const apiProxy = {
    '/api/iam': { target: gatewayUrl },
    '/api/supplier': { target: gatewayUrl },
    '/api/workflow': { target: gatewayUrl },
    '/api/notification': { target: gatewayUrl },
  };

  return {
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
  };
});
