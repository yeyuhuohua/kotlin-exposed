<script setup lang="ts">
import { onMounted, ref, watch } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { ChevronRight, LogOut, Menu, Moon, PanelLeftClose, Sun } from '@lucide/vue'
import SidebarNav from './SidebarNav.vue'
import { useAuth } from '../stores/auth'
import { useNotices } from '../stores/notices'
import { useTheme } from '../stores/theme'
import { systemPaths } from '../api/paths'
import { api } from '../lib/api'
import { initials } from '../lib/format'
import type { Health } from '../types'

/** 工作台布局只协调导航、服务状态和退出登录，不承担权限判定的最终职责。 */
const auth = useAuth()
const notices = useNotices()
const theme = useTheme()
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
    health.value = await api<Health>(systemPaths.health, { acceptUnavailable: true })
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
          <el-tooltip :content="theme.isDark ? '切换到白天模式' : '切换到夜间模式'">
            <el-button
              text
              class="icon-button outlined"
              :icon="theme.isDark ? Sun : Moon"
              :aria-label="theme.isDark ? '切换到白天模式' : '切换到夜间模式'"
              @click="theme.toggle()"
            />
          </el-tooltip>
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

<style scoped>
/* 本组件样式：颜色只用 styles.css 里的语义 token。 */
.app-shell {
  min-height: 100vh;
}

.sidebar {
  width: 226px;
  position: fixed;
  inset: 0 auto 0 0;
  background: var(--surface);
  border-right: 1px solid var(--border);
  display: flex;
  flex-direction: column;
  z-index: 30;
}

.workspace {
  margin-left: 226px;
  min-height: 100vh;
  display: flex;
  flex-direction: column;
}

.topbar {
  height: 73px;
  background: var(--surface-translucent);
  border-bottom: 1px solid var(--border);
  display: flex;
  align-items: center;
  justify-content: space-between;
  padding: 0 33px;
  gap: 18px;
  position: sticky;
  top: 0;
  z-index: 20;
  backdrop-filter: blur(12px);
}

.breadcrumbs {
  display: flex;
  gap: 14px;
  align-items: center;
  font-size: 11px;
  color: var(--text-faint);
  white-space: nowrap;
}

.breadcrumbs > svg:first-child {
  margin-right: 8px;
  color: var(--text-dim);
}

.breadcrumbs strong {
  font-weight: 500;
  color: var(--text-body);
}

.topbar-actions {
  display: flex;
  align-items: center;
  gap: 16px;
}

.topbar-divider {
  height: 25px;
  width: 1px;
  background: var(--border);
  margin: 0 1px;
}

.account-link {
  display: flex;
  gap: 9px;
  align-items: center;
  font-size: 11px;
  font-weight: 600;
}

.account-link small {
  display: block;
  color: var(--text-faint);
  font-size: 9px;
  font-weight: 400;
  margin-top: 3px;
}

.main-content {
  padding: 32px 34px 38px;
  max-width: 1700px;
  width: 100%;
  margin: 0 auto;
  flex: 1;
}

.workspace-footer {
  padding: 0 34px 19px;
  display: flex;
  justify-content: space-between;
  color: var(--text-pale);
  font-size: 10px;
}

.mobile-menu,
.mobile-close {
  display: none !important;
}

@media (min-width: 1500px) {
  .main-content {
    padding: 40px 50px;
  }
}

@media (max-width: 1200px) {
  .sidebar {
    width: 206px;
  }
}

@media (max-width: 1200px) {
  .workspace {
    margin-left: 206px;
  }
}

@media (max-width: 1200px) {
  .main-content {
    padding: 28px 25px;
  }
}

@media (max-width: 1200px) {
  .topbar {
    padding: 0 25px;
  }
}

@media (max-width: 900px) {
  .sidebar {
    width: 190px;
  }
}

@media (max-width: 900px) {
  .workspace {
    margin-left: 190px;
  }
}

@media (max-width: 900px) {
  .main-content {
    padding: 25px 21px;
  }
}

@media (max-width: 900px) {
  .topbar {
    padding: 0 20px;
  }
}

@media (max-width: 900px) {
  .breadcrumbs > span,
  .breadcrumbs > svg {
    display: none;
  }
}

@media (max-width: 900px) {
  .topbar-actions {
    gap: 10px;
  }
}

@media (max-width: 680px) {
  .sidebar {
    transform: translateX(-100%);
    transition: transform 0.2s;
    width: 236px;
  }
}

@media (max-width: 680px) {
  .sidebar.open {
    transform: translateX(0);
  }
}

@media (max-width: 680px) {
  .mobile-menu {
    display: inline-flex !important;
  }
}

@media (max-width: 680px) {
  .workspace {
    margin-left: 0;
  }
}

@media (max-width: 680px) {
  .topbar {
    height: 62px;
    padding: 0 15px;
    gap: 9px;
  }
}

@media (max-width: 680px) {
  .topbar-actions {
    gap: 7px;
    margin-left: auto;
  }
}

@media (max-width: 680px) {
  .breadcrumbs {
    font-size: 11px;
  }
}

@media (max-width: 680px) {
  .topbar-divider {
    display: none;
  }
}

@media (max-width: 680px) {
  .account-link > span:last-child {
    display: none;
  }
}

@media (max-width: 680px) {
  .main-content {
    padding: 23px 17px;
  }
}

@media (max-width: 680px) {
  .workspace-footer {
    padding: 0 17px 19px;
    font-size: 9px;
  }
}

.navigation-drawer :deep(.el-drawer__body) {
  padding: 0;
}

.topbar :deep(.el-avatar) {
  background: var(--green-soft);
  color: var(--green-deep);
  font-size: 11px;
}

@media (max-width: 680px) {
  .desktop-sidebar {
    display: none;
  }
}
.connection-status {
  display: flex;
  gap: 7px;
  align-items: center;
  color: var(--text-medium);
  font-size: 10px;
  white-space: nowrap;
}
.connection-status > span {
  width: 6px;
  height: 6px;
  border-radius: 50%;
  background: var(--status-up);
  box-shadow: 0 0 0 3px var(--status-up-soft);
}
.connection-status.down > span {
  background: var(--status-down);
  box-shadow: none;
}
@media (max-width: 900px) {
  .connection-status {
    font-size: 9px;
  }
}
@media (max-width: 680px) {
  .connection-status {
    display: none;
  }
}
</style>
