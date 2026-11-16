import { defineConfig, type Plugin } from 'vite';
import react from '@vitejs/plugin-react';
import tailwindcss from '@tailwindcss/vite';
import { createApiRouter } from './server/apiMiddleware';

function browserhubApiPlugin(): Plugin {
  const apiRouter = createApiRouter();
  return {
    name: 'browserhub-hybrid-api',
    configureServer(server) {
      server.middlewares.use('/api', apiRouter);
    },
    configurePreviewServer(server) {
      server.middlewares.use('/api', apiRouter);
    },
  };
}

export default defineConfig({
  base: './',
  plugins: [react(), tailwindcss(), browserhubApiPlugin()],
  server: {
    host: '0.0.0.0',
    port: 5173,
    allowedHosts: true,
  },
  preview: {
    host: '0.0.0.0',
    port: 5173,
    allowedHosts: true,
  },
});
