import { defineConfig } from 'vitest/config'
import { resolve } from 'path'
import vue from '@vitejs/plugin-vue'

export default defineConfig({
  plugins: [vue()],
  resolve: {
    alias: {
      '@': resolve(__dirname, 'src'),
    },
  },
  test: {
    environment: 'jsdom',
    globals: true,
    // e2e/ 是 Playwright 用例(test:e2e 跑),vitest 误扫必挂 79 个 —— 显式排除
    exclude: ['e2e/**', 'node_modules/**'],
  },
})