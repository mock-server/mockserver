import { defineConfig } from 'vite';
import react from '@vitejs/plugin-react';

const mockserverTarget = process.env.MOCKSERVER_URL || 'http://localhost:1080';

export default defineConfig({
  plugins: [react()],
  base: '/mockserver/dashboard/',
  build: {
    outDir: 'build',
  },
  server: {
    port: 3000,
    proxy: {
      '/_mockserver_ui_websocket': {
        target: mockserverTarget,
        ws: true,
      },
      '/mockserver': {
        target: mockserverTarget,
        bypass(req) {
          if (req.url?.startsWith('/mockserver/dashboard')) {
            return req.url;
          }
        },
      },
    },
  },
});
