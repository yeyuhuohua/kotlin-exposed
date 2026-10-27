<script setup lang="ts">
import { computed, onMounted, ref, watch } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { LogOut, Menu, Moon, Sun } from '@lucide/vue'
import AppLogo from './AppLogo.vue'
import SidebarNav from './SidebarNav.vue'
import TopNav from './TopNav.vue'
import { useAuth } from '../stores/auth'
import { useNotices } from '../stores/notices'
import { useTheme } from '../stores/theme'
import { systemPaths } from '../api/paths'
import { api } from '../lib/api'
import { initials } from '../lib/format'
import { pagePath } from '../lib/pages'
import type { Health } from '../types'

/** 工作台布局只协调导航、服务状态和退出登录,不承担权限判定的最终职责。 */
const auth = useAuth()
const notices = useNotices()
const theme = useTheme()
const route = useRoute()
const router = useRouter()
const mobileOpen = ref(false)
const signingOut = ref(false)
const health = ref<Health>()
const fillHeight = computed(() => route.meta.fillHeight === true)
const systemTo = computed(() => pagePath(auth.pages, 'system'))
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
  <div :class="['app-shell', { 'fill-height': fillHeight }]">
    <header class="workspace-header">
      <div class="topbar">
        <div class="topbar-left">
          <el-button text class="mobile-menu" :icon="Menu" aria-label="打开导航" @click="mobileOpen = true" />
          <RouterLink :to="auth.home" class="brand-link" aria-label="返回工作区首页">
            <AppLogo />
          </RouterLink>
        </div>
        <div class="topbar-actions">
          <RouterLink
            v-if="auth.canPage('system') && systemTo"
            :to="systemTo"
            :class="['status-link', { down: health?.status !== 'UP' }]"
            :title="health?.status === 'UP' ? '服务运行正常' : '查看服务状态'"
          >
            <span class="status-dot" aria-hidden="true"></span>
            <span class="status-label">{{ health?.status === 'UP' ? '服务正常' : health ? '服务异常' : '状态未知' }}</span>
          </RouterLink>
          <span class="action-divider" aria-hidden="true"></span>
          <el-tooltip :content="theme.isDark ? '切换到浅色模式' : '切换到深色模式'">
            <el-button
              text
              class="icon-button outlined"
              :icon="theme.isDark ? Sun : Moon"
              :aria-label="theme.isDark ? '切换到浅色模式' : '切换到深色模式'"
              @click="theme.toggle()"
            />
          </el-tooltip>
          <RouterLink to="/account" class="account-trigger" aria-label="我的账号">
            <el-avatar :size="30">{{ initials(auth.user?.username || 'U') }}</el-avatar>
            <span class="account-meta">
              <strong>{{ auth.user?.username }}</strong>
              <small>{{ auth.isAdmin ? '管理员' : '普通用户' }}</small>
            </span>
          </RouterLink>
          <el-tooltip content="退出登录">
            <el-button
              text
              class="icon-button outlined logout-button"
              :icon="LogOut"
              :loading="signingOut"
              aria-label="退出登录"
              @click="logout"
            />
          </el-tooltip>
        </div>
      </div>
      <div class="navigation-row"><TopNav /></div>
    </header>
    <el-drawer
      v-model="mobileOpen"
      direction="ltr"
      size="280px"
      :with-header="false"
      class="navigation-drawer"
      title="工作空间导航"
    >
      <SidebarNav mobile @navigate="mobileOpen = false" />
    </el-drawer>
    <main id="main-content" :class="['main-content', { 'fill-height': fillHeight }]">
      <RouterView v-slot="{ Component }"><component :is="Component" :key="route.path" /></RouterView>
    </main>
  </div>
</template>

<style scoped>
.app-shell {
  min-height: 100dvh;
  display: flex;
  flex-direction: column;
  background: var(--bg-page);
}

.app-shell.fill-height {
  height: 100dvh;
  overflow: hidden;
}

.workspace-header {
  position: sticky;
  top: 0;
  z-index: 30;
  flex-shrink: 0;
  border-bottom: 1px solid var(--border);
  background: var(--surface-translucent);
  backdrop-filter: blur(18px);
}

.topbar {
  height: var(--header-height);
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 24px;
  padding: 0 var(--page-gutter);
}

.topbar-left,
.topbar-actions {
  display: flex;
  align-items: center;
  min-width: 0;
}

.topbar-left {
  gap: 10px;
}

.topbar-actions {
  gap: 10px;
  flex-shrink: 0;
}

.brand-link {
  display: inline-flex;
  border-radius: 11px;
}

.navigation-row {
  height: var(--nav-height);
  padding: 0 var(--page-gutter);
  border-top: 1px solid var(--border-soft);
}

.status-link {
  display: inline-flex;
  align-items: center;
  gap: 7px;
  min-height: 32px;
  padding: 0 6px;
  border-radius: 8px;
  color: var(--text-muted);
  font-size: 12px;
  white-space: nowrap;
}

.status-link:hover {
  background: var(--surface-hover);
  color: var(--text-strong);
}

.status-dot {
  width: 7px;
  height: 7px;
  border-radius: 50%;
  background: var(--status-up);
  box-shadow: 0 0 0 3px var(--status-up-soft);
}

.status-link.down .status-dot {
  background: var(--status-down);
  box-shadow: none;
}

.action-divider {
  width: 1px;
  height: 20px;
  margin: 0 2px;
  background: var(--border);
}

.account-trigger {
  display: flex;
  align-items: center;
  gap: 9px;
  height: 42px;
  padding: 4px 8px 4px 5px;
  border: 1px solid transparent;
  border-radius: 10px;
  background: transparent;
  color: var(--text);
  text-align: left;
}

.account-trigger:hover,
.account-trigger:focus-visible {
  border-color: var(--border);
  background: var(--surface-hover);
}

.account-meta {
  display: flex;
  flex-direction: column;
  min-width: 0;
  line-height: 1.3;
}

.account-meta strong {
  max-width: 130px;
  overflow: hidden;
  color: var(--text);
  font-size: 12px;
  font-weight: 600;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.account-meta small {
  margin-top: 2px;
  color: var(--text-faint);
  font-size: 10px;
}

.logout-button:hover:not(:disabled) {
  background: var(--danger-hover);
  color: var(--danger);
}

.topbar :deep(.el-avatar) {
  background: var(--accent-soft);
  color: var(--accent-text);
  font-size: 11px;
  font-weight: 650;
}

.main-content {
  width: 100%;
  max-width: var(--content-max);
  margin: 0 auto;
  padding: 32px var(--page-gutter) 48px;
  flex: 1;
}

.main-content.fill-height {
  display: flex;
  flex-direction: column;
  min-height: 0;
  max-width: none;
  padding: 0;
  overflow: hidden;
}

.main-content.fill-height > * {
  flex: 1;
  min-height: 0;
}

.mobile-menu {
  display: none !important;
}

.navigation-drawer :deep(.el-drawer__body) {
  padding: 0;
  background: var(--surface);
}

@media (min-width: 1500px) {
  .main-content {
    padding-top: 40px;
  }
}

@media (max-width: 1100px) {
  .status-label {
    display: none;
  }

  .status-link {
    width: 32px;
    justify-content: center;
    padding: 0;
  }
}

@media (max-width: 900px) {
  .topbar {
    height: 60px;
    gap: 10px;
  }

  .navigation-row {
    display: none;
  }

  .mobile-menu {
    display: inline-flex !important;
  }

  .main-content {
    padding-top: 24px;
  }
}

@media (max-width: 680px) {
  .topbar-actions {
    gap: 6px;
  }

  .account-meta,
  .action-divider {
    display: none;
  }

  .account-trigger {
    padding-right: 5px;
  }

  .main-content {
    padding-top: 20px;
    padding-bottom: 32px;
  }
}

@media (max-width: 480px) {
  .brand-link :deep(.logo-copy small) {
    display: none;
  }
}
</style>
