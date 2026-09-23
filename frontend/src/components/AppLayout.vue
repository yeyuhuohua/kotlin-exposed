<script setup lang="ts">
import { onMounted, ref, watch } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { LogOut, Menu, Moon, PanelLeftClose, PanelLeftOpen, Sun } from '@lucide/vue'
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
const collapsed = ref(false)
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
    <aside class="sidebar desktop-sidebar" :class="{ collapsed }"><SidebarNav /></aside>
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
    <div class="workspace" :class="{ collapsed }">
      <header class="topbar">
        <div class="topbar-left">
          <el-button text class="mobile-menu" :icon="Menu" aria-label="打开导航" @click="mobileOpen = true" />
          <el-tooltip :content="collapsed ? '展开导航' : '收起导航'">
            <el-button
              text
              class="icon-button sidebar-toggle"
              :icon="collapsed ? PanelLeftOpen : PanelLeftClose"
              :aria-label="collapsed ? '展开导航' : '收起导航'"
              @click="collapsed = !collapsed"
            />
          </el-tooltip>
          <h1 class="page-title">{{ route.meta.title }}</h1>
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
  background: var(--bg-page);
}

.sidebar {
  width: 240px;
  position: fixed;
  inset: 0 auto 0 0;
  background: var(--surface);
  border-right: 1px solid var(--border);
  display: flex;
  flex-direction: column;
  z-index: 30;
  transition: transform 0.25s ease;
}

.sidebar.collapsed {
  transform: translateX(-100%);
}

.workspace {
  margin-left: 240px;
  min-height: 100vh;
  display: flex;
  flex-direction: column;
  transition: margin-left 0.25s ease;
}

.workspace.collapsed {
  margin-left: 0;
}

.topbar {
  height: 64px;
  background: var(--surface-translucent);
  border-bottom: 1px solid var(--border);
  display: flex;
  align-items: center;
  justify-content: space-between;
  padding: 0 32px;
  gap: 18px;
  position: sticky;
  top: 0;
  z-index: 20;
  backdrop-filter: blur(14px);
}

.topbar-left {
  display: flex;
  align-items: center;
  gap: 14px;
  min-width: 0;
}

.page-title {
  font-size: 15px;
  font-weight: 600;
  letter-spacing: -0.01em;
  color: var(--text-strong);
  white-space: nowrap;
  display: block;
}

.topbar-actions {
  display: flex;
  align-items: center;
  gap: 16px;
}

.topbar-divider {
  height: 24px;
  width: 1px;
  background: var(--border);
  margin: 0 1px;
}

.account-link {
  display: flex;
  gap: 10px;
  align-items: center;
  font-size: 13px;
  font-weight: 600;
  color: var(--text);
  padding: 5px 8px 5px 5px;
  border-radius: 10px;
  transition: background 0.15s;
}

.account-link:hover {
  background: var(--surface-hover);
}

.account-link small {
  display: block;
  color: var(--text-faint);
  font-size: 11px;
  font-weight: 400;
  margin-top: 2px;
}

.main-content {
  padding: 36px 40px 44px;
  max-width: 1500px;
  width: 100%;
  margin: 0 auto;
  flex: 1;
}

.workspace-footer {
  padding: 0 40px 22px;
  display: flex;
  justify-content: space-between;
  color: var(--text-pale);
  font-size: 11px;
}

.mobile-menu,
.mobile-close {
  display: none !important;
}

@media (max-width: 680px) {
  .sidebar-toggle {
    display: none !important;
  }
}

@media (min-width: 1500px) {
  .main-content {
    padding: 44px 56px;
  }
}

@media (max-width: 1200px) {
  .sidebar {
    width: 220px;
  }
  .workspace {
    margin-left: 220px;
  }
  .main-content {
    padding: 30px 28px;
  }
  .topbar {
    padding: 0 26px;
  }
}

@media (max-width: 900px) {
  .sidebar {
    width: 200px;
  }
  .workspace {
    margin-left: 200px;
  }
  .main-content {
    padding: 26px 22px;
  }
  .topbar {
    padding: 0 20px;
  }
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
  .sidebar.open {
    transform: translateX(0);
  }
  .mobile-menu {
    display: inline-flex !important;
  }
  .workspace {
    margin-left: 0;
  }
  .topbar {
    height: 58px;
    padding: 0 16px;
    gap: 9px;
  }
  .topbar-actions {
    gap: 7px;
    margin-left: auto;
  }
  .topbar-divider {
    display: none;
  }
  .account-link > span:last-child {
    display: none;
  }
  .main-content {
    padding: 22px 18px;
  }
  .workspace-footer {
    padding: 0 18px 20px;
    font-size: 10px;
  }
}

.navigation-drawer :deep(.el-drawer__body) {
  padding: 0;
}

.topbar :deep(.el-avatar) {
  background: var(--green-soft);
  color: var(--green-deep);
  font-size: 11px;
  font-weight: 600;
}

@media (max-width: 680px) {
  .desktop-sidebar {
    display: none;
  }
}

.connection-status {
  display: flex;
  gap: 8px;
  align-items: center;
  color: var(--text-medium);
  font-size: 12px;
  white-space: nowrap;
}

.connection-status > span {
  width: 7px;
  height: 7px;
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
    font-size: 11px;
  }
}

@media (max-width: 680px) {
  .connection-status {
    display: none;
  }
}
</style>
