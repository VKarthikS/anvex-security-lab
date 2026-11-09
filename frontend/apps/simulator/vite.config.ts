import { fileURLToPath, URL } from 'node:url';
import react from '@vitejs/plugin-react';
import { defineConfig } from 'vite';

const sharedSrc = fileURLToPath(new URL('../../packages/shared/src', import.meta.url));

/**
 * The simulator station is served by the Spring Boot app on port 8081, so the production build
 * lands in that app's static resources.
 *
 * Path note: this file lives in `frontend/apps/simulator`, therefore the Spring Boot module that
 * lives beside `frontend/` is three levels up (`../../../simulator/...`).
 */
export default defineConfig({
  plugins: [react()],
  resolve: {
    alias: [
      { find: '@anvex/shared/styles', replacement: `${sharedSrc}/styles` },
      { find: '@anvex/shared', replacement: `${sharedSrc}/index.ts` },
    ],
  },
  optimizeDeps: {
    exclude: ['@anvex/shared'],
  },
  server: {
    port: 5174,
    strictPort: false,
    proxy: {
      '/api': {
        target: 'http://127.0.0.1:8081',
        changeOrigin: false,
      },
    },
  },
  build: {
    outDir: '../../../simulator/src/main/resources/static',
    emptyOutDir: true,
    assetsDir: 'assets',
    sourcemap: false,
  },
});
