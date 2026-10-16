import { defineConfig } from 'vite'
/** 开发服务器不提供代理：浏览器直接跨域访问后端，本机来源由后端 CORS 放行。 */
import vue from '@vitejs/plugin-vue'

export default defineConfig({
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
  },
})
