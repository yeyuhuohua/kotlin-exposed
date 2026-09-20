<script setup lang="ts">
import { onBeforeUnmount } from 'vue'
import zhCn from 'element-plus/es/locale/lang/zh-cn'
import { useRoute, useRouter } from 'vue-router'
import { useAuth } from './stores/auth'
import { useNotices } from './stores/notices'
// 认证状态在根组件统一响应；消息展示交给 Element Plus 的全局消息组件。
const auth = useAuth()
const notices = useNotices()
const router = useRouter()
const route = useRoute()
function expired() {
  const wasLoggedIn = Boolean(auth.user)
  auth.clear()
  if (wasLoggedIn) notices.show('登录已失效，请重新登录', true)
  if (route.name !== 'login') void router.replace({ name: 'login', query: { redirect: route.fullPath } })
}
async function forbidden() {
  // 被服务端拒绝说明权限可能刚变过，这里强制刷新而不是复用缓存
  await auth.refreshUser({ force: true })
  if (
    (route.meta.adminOnly && !auth.isAdmin) ||
    (typeof route.meta.page === 'string' && !auth.canPage(route.meta.page))
  )
    void router.replace('/forbidden')
}
window.addEventListener('hr:unauthorized', expired)
window.addEventListener('hr:forbidden', forbidden)
onBeforeUnmount(() => {
  window.removeEventListener('hr:unauthorized', expired)
  window.removeEventListener('hr:forbidden', forbidden)
})
</script>
<template>
  <el-config-provider :locale="zhCn"><RouterView /></el-config-provider>
</template>
