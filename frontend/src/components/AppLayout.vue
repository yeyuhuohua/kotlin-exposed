<script setup lang="ts">
import { onMounted, ref, watch } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { ChevronRight, LogOut, Menu, PanelLeftClose } from '@lucide/vue'
import SidebarNav from './SidebarNav.vue'
import { useAuth } from '../stores/auth'
import { useNotices } from '../stores/notices'
import { api } from '../lib/api'
import { initials } from '../lib/format'
import type { Health } from '../types'

/** 工作台布局只协调导航、服务状态和退出登录，不承担权限判定的最终职责。 */
const auth = useAuth()
const notices = useNotices()
const route = useRoute()
const router = useRouter()
const mobileOpen = ref(false)
const signingOut = ref(false)
const health = ref<Health>()
watch(
  () => route.fullPath,
  () => {
    mobileOpen.value = false
  },
)
onMounted(async () => {
  try {
    health.value = await api<Health>('/health', { acceptUnavailable: true })
  } catch {
    health.value = undefined
  }
})
async function logout() {
  signingOut.value = true
  try {
    await auth.logout()
  } catch {
    notices.show('已退出本机；服务端会话撤销失败', true)
  } finally {
    signingOut.value = false
    await router.replace('/login')
  }
}
</script>
<template>
  <div class="app-shell">
    <aside class="sidebar desktop-sidebar"><SidebarNav /></aside>
    <el-drawer
      v-model="mobileOpen"
      direction="ltr"
      size="260px"
      :with-header="false"
      class="navigation-drawer"
      title="工作空间导航"
    >
      <SidebarNav mobile @navigate="mobileOpen = false" />
    </el-drawer>
    <div class="workspace">
      <header class="topbar">
        <el-button text class="mobile-menu" :icon="Menu" aria-label="打开导航" @click="mobileOpen = true" />
        <div class="breadcrumbs">
          <PanelLeftClose :size="17" />
          <span>工作空间</span>
          <ChevronRight :size="14" />
          <strong>{{ route.meta.title }}</strong>
        </div>
        <div class="topbar-actions">
          <RouterLink
            v-if="auth.canPage('system')"
            to="/system"
            :class="['connection-status', { down: health?.status !== 'UP' }]"
          >
            <span></span>
            {{ health?.status === 'UP' ? '服务已连接' : health ? '服务异常' : '状态未确认' }}
          </RouterLink>
          <span class="topbar-divider"></span>
          <RouterLink to="/account" class="account-link">
            <el-avatar :size="31">{{ initials(auth.user?.username || 'U') }}</el-avatar>
            <span>
              {{ auth.user?.username }}
              <small>{{ auth.isAdmin ? '管理员' : '普通用户' }}</small>
            </span>
          </RouterLink>
          <el-tooltip content="退出登录">
            <el-button text :icon="LogOut" aria-label="退出登录" :loading="signingOut" @click="logout" />
          </el-tooltip>
        </div>
      </header>
      <main id="main-content" class="main-content">
        <RouterView v-slot="{ Component }"><component :is="Component" :key="route.path" /></RouterView>
      </main>
      <footer class="workspace-footer">
        <span>HR Workspace</span>
        <span>组织 · 人才 · 协作</span>
      </footer>
    </div>
  </div>
</template>
