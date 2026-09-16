import { defineConfig } from 'vite';
import react from '@vitejs/plugin-react';

// Dev server on 5173 - the backend's app.cors.allowed-origins already trusts that port.
// /api is proxied to Spring Boot on 8080, so the browser only ever talks to one origin and
// there is no CORS preflight to fight with during development.
export default defineConfig({
  plugins: [react()],
  server: {
    port: 5173,
    proxy: {
      '/api': { target: 'http://localhost:8080', changeOrigin: true },
    },
  },
});
