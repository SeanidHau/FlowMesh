import { defineConfig } from 'vite';
import vue from '@vitejs/plugin-vue';

/**
 * 配置渲染进程的 Vite 开发与生产构建。
 */
export default defineConfig({
  plugins: [vue()],
  server: {
    port: 5173,
    strictPort: true,
  },
  build: {
    outDir: 'dist',
    emptyOutDir: true,
  },
});
