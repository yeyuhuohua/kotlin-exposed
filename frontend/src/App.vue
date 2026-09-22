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
  if (route.name !== 'login')
    void router.replace({
      name: 'login',
      // 回到登录页后不要再带回 /forbidden，否则登录成功先落回无权限页。
      query: route.path === '/forbidden' ? {} : { redirect: route.fullPath },
    })
}
/** 权限刚被回收时一个页面可能并行多个 403，合并成一次强制刷新，避免 /auth/me 风暴。 */
let forbiddenTimer: ReturnType<typeof setTimeout> | undefined
function forbidden() {
  clearTimeout(forbiddenTimer)
  forbiddenTimer = setTimeout(checkForbidden, 300)
}
async function checkForbidden() {
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
  clearTimeout(forbiddenTimer)
  window.removeEventListener('hr:unauthorized', expired)
  window.removeEventListener('hr:forbidden', forbidden)
})
</script>
<template>
  <el-config-provider :locale="zhCn"><RouterView /></el-config-provider>
</template>
