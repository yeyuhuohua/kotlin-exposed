import { createApp } from 'vue'
import { createPinia } from 'pinia'
import { installElementPlus } from './plugins/element'
import 'element-plus/theme-chalk/dark/css-vars.css'
import App from './App.vue'
import { router } from './router'
import './styles.css'

// 统一注册中文 Element Plus，业务状态与路由仍由 Pinia 和 Vue Router 管理。
const app = createApp(App).use(createPinia()).use(router)
installElementPlus(app)
app.mount('#app')
