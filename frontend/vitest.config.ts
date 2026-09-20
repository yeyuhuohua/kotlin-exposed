import { defineConfig } from 'vitest/config'
import vue from '@vitejs/plugin-vue'

/** 组件测试使用与 Vite 相同的 Vue 转换，统一处理 Element Plus 校验器的 ESM 导入。 */
export default defineConfig({
  plugins: [vue()],
  test: {
    server: { deps: { inline: ['element-plus', 'async-validator'] } },
  },
})
