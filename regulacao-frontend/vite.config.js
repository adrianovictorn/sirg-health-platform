import tailwindcss from '@tailwindcss/vite';
import { sveltekit } from '@sveltejs/kit/vite';
import { defineConfig, loadEnv } from 'vite';

export default defineConfig(({ mode }) => {
  const env = loadEnv(mode, process.cwd(), '');
  const BACKEND_URL = env.BACKEND_URL || `http://localhost:${env.BACKEND_PORT || 8080}`;

  return {
    plugins: [tailwindcss(), sveltekit()],
    server: {
      allowedHosts: true,
      proxy: {
        '/api': {
          target: BACKEND_URL,
          changeOrigin: true,
          secure: false
        },
        // Proxies extras para alternar rapidamente entre múltiplos backends no dev
        '/api-8080': {
          target: 'http://localhost:8080',
          changeOrigin: true,
          rewrite: (path) => path.replace(/^\/api-8080/, '/api')
        },
        '/api-8081': {
          target: 'http://localhost:8081',
          changeOrigin: true,
          rewrite: (path) => path.replace(/^\/api-8081/, '/api')
        },
        '/api-8083': {
          target: 'http://localhost:8083',
          changeOrigin: true,
          rewrite: (path) => path.replace(/^\/api-8083/, '/api')
        }
      }
    }
  };
});
