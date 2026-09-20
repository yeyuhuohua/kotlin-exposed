import { defineConfig, loadEnv } from 'vite'
/** 开发服务器仅代理接口和文档路径；生产部署需要独立配置反向代理。 */
import vue from '@vitejs/plugin-vue'

export default defineConfig(({ mode }) => {
  const env = loadEnv(mode, process.cwd(), '')
  const target = env.API_PROXY_TARGET || 'http://127.0.0.1:8080'
  return {
    plugins: [vue()],
    server: {
      port: 5173,
      strictPort: false,
      proxy: Object.fromEntries(
        ['/api', '/swagger', '/v3', '/webjars', '/doc.html'].map((path) => [
          path,
          { target, changeOrigin: true },
        ]),
      ),
    },
  }
})
