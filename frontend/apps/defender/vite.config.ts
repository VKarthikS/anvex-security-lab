import { fileURLToPath, URL } from 'node:url';
import react from '@vitejs/plugin-react';
import { defineConfig } from 'vite';

const sharedSrc = fileURLToPath(new URL('../../packages/shared/src', import.meta.url));

/**
 * The defender console is served by the Spring Boot app on port 8080, so the production build
 * lands in that app's static resources.
 *
 * Path note: this file lives in `frontend/apps/defender`, therefore the Spring Boot module that
 * lives beside `frontend/` is three levels up (`../../../defender/...`).
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
    port: 5173,
    strictPort: false,
    proxy: {
      // The Java service is reached over loopback, so analyst-only endpoints stay reachable
      // in development exactly as they are in production on the defender host.
      '/api': {
        target: 'http://127.0.0.1:8080',
        changeOrigin: false,
      },
    },
  },
  build: {
    outDir: '../../../defender/src/main/resources/static',
    emptyOutDir: true,
    assetsDir: 'assets',
    sourcemap: false,
  },
});
