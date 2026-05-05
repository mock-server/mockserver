import { defineConfig } from 'vite';
import react from '@vitejs/plugin-react';

export default defineConfig({
  plugins: [react()],
  base: '/mockserver/dashboard/',
  build: {
    outDir: 'build',
  },
  server: {
    port: 3000,
  },
});
