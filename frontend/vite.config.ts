import { defineConfig } from 'vite'
import react from '@vitejs/plugin-react'

// Spring Boot(8880)가 정적 리소스로 그대로 서빙할 수 있도록
// 빌드 산출물을 백엔드 static 디렉터리에 직접 출력한다.
export default defineConfig({
  plugins: [react()],
  base: '/',
  server: {
    port: 5173,
    proxy: {
      '/api': {
        target: 'http://localhost:8880',
        changeOrigin: true,
      },
    },
  },
  build: {
    outDir: '../src/main/resources/static',
    emptyOutDir: true,
  },
})
