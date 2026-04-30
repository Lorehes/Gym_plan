import { defineConfig, loadEnv } from 'vite';
import react from '@vitejs/plugin-react';
import path from 'node:path';

// https://vitejs.dev/config/
export default defineConfig(({ mode }) => {
  const env = loadEnv(mode, process.cwd(), '');
  // dev 프록시 타겟. 클라이언트가 상대경로(/api/v1)를 쓸 때 어디로 보낼지 결정.
  const proxyTarget = env.VITE_DEV_PROXY_TARGET ?? 'http://localhost:8080';

  return {
    plugins: [react()],
    resolve: {
      alias: {
        '@': path.resolve(__dirname, './src'),
      },
    },
    server: {
      port: 3000,
      host: true,
      // dev 환경에서 /api 를 게이트웨이로 프록시 — same-origin 이 되어
      // 브라우저 CORS 검사를 우회한다 (게이트웨이가 CORS 헤더를 중복 발급하는
      // 백엔드 구성에서도 동작).
      // 클라이언트는 baseURL 을 상대경로 '/api/v1' 로 사용한다 (.env.development).
      proxy: {
        '/api': {
          target: proxyTarget,
          changeOrigin: true,
          secure: false,
        },
      },
    },
  };
});
