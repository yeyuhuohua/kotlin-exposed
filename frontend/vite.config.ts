import { defineConfig, loadEnv } from 'vite'
/** 开发服务器仅代理接口和文档路径；生产部署需要独立配置反向代理。 */
import vue from '@vitejs/plugin-vue'

export default defineConfig(({ mode }) => {
  const env = loadEnv(mode, process.cwd(), '')
  const target = env.API_PROXY_TARGET || 'http://127.0.0.1:8080'
  return {
    plugins: [vue()],
    build: {
      rollupOptions: {
        output: {
          // 框架与组件库拆成独立 vendor 块：单块不再超 500 kB，
          // 且库代码与业务代码分离，业务发布时浏览器可复用库缓存。
          // element-plus 按组件目录再细分（el-table、el-form 等），
          // 重组件并行加载，未使用的组件不进包。
          manualChunks(id: string) {
            if (!id.includes('node_modules')) return undefined
            const component = id.match(/element-plus\/es\/components\/([\w-]+)/)
            if (component) return `el-${component[1]}`
            if (id.includes('element-plus') || id.includes('@element-plus') || id.includes('@popperjs')) {
              return 'vendor-element'
            }
            if (id.includes('@lucide') || id.includes('lucide')) return 'vendor-icons'
            return 'vendor-vue'
          },
        },
      },
    },
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
