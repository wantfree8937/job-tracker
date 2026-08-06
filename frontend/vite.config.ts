/// <reference types="vitest/config" />
import { defineConfig } from 'vite'
import react from '@vitejs/plugin-react'

// https://vite.dev/config/
export default defineConfig({
  plugins: [react()],
  test: {
    // 브라우저 환경(jsdom)에서 컴포넌트 테스트 실행
    environment: 'jsdom',
    // 테스트 코드에서 import 없이 describe/it/expect 사용 가능
    globals: true,
    // 테스트마다 공통 초기화 (jest-dom 매처 등)
    setupFiles: './src/test/setup.ts',
    // 커버리지 측정 (v8 엔진 — 백엔드 JaCoCo에 대응하는 프론트 커버리지)
    coverage: {
      provider: 'v8',
      reporter: ['text', 'html', 'lcov'],
      // 순수 UI/설정 파일은 측정 제외 (커버리지 의미 없음)
      exclude: [
        'src/main.tsx',
        'src/vite-env.d.ts',
        'src/test/**',
        '**/*.d.ts',
      ],
    },
  },
})
