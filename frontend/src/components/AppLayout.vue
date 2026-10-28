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
        <TopNav class="topbar-nav" />
        <div class="topbar-actions">
          <RouterLink
            v-if="auth.canPage('system') && systemTo"
            :to="systemTo"
            :class="['status-link', { down: health?.status !== 'UP' }]"
            :title="health?.status === 'UP' ? '服务运行正常' : '查看服务状态'"
          >
            <span class="status-dot" aria-hidden="true"></span>
            <span class="status-label">
              {{ health?.status === 'UP' ? '服务正常' : health ? '服务异常' : '状态未知' }}
            </span>
          </RouterLink>
          <el-tooltip :content="theme.isDark ? '切换到浅色模式' : '切换到深色模式'">
            <el-button
              text
              class="icon-button"
              :icon="theme.isDark ? Sun : Moon"
              :aria-label="theme.isDark ? '切换到浅色模式' : '切换到深色模式'"
              @click="theme.toggle()"
            />
          </el-tooltip>
          <span class="action-divider" aria-hidden="true"></span>
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
              class="icon-button logout-button"
              :icon="LogOut"
              :loading="signingOut"
              aria-label="退出登录"
              @click="logout"
            />
          </el-tooltip>
        </div>
      </div>
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
  display: flex;
  flex-direction: column;
  min-height: 100dvh;
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
  backdrop-filter: blur(14px);
}

.topbar {
  display: flex;
  align-items: center;
  gap: 22px;
  height: var(--header-height);
  padding: 0 var(--page-gutter);
}

.topbar-left,
.topbar-actions {
  display: flex;
  align-items: center;
  min-width: 0;
}

.topbar-left {
  flex-shrink: 0;
  gap: 10px;
}

.topbar-nav {
  flex: 1;
  min-width: 0;
}

.topbar-actions {
  flex-shrink: 0;
  gap: 7px;
}

.brand-link {
  display: inline-flex;
  border-radius: 5px;
}

.status-link {
  display: inline-flex;
  align-items: center;
  gap: 7px;
  min-height: 32px;
  padding: 0 7px;
  border-radius: 5px;
  color: var(--text-muted);
  font-size: 12px;
  white-space: nowrap;
}

.status-link:hover {
  background: var(--surface-hover);
  color: var(--text-strong);
}

.status-dot {
  width: 6px;
  height: 6px;
  border-radius: 50%;
  background: var(--status-up);
}

.status-link.down .status-dot {
  background: var(--status-down);
}

.action-divider {
  width: 1px;
  height: 22px;
  margin: 0 4px;
  background: var(--border);
}

.account-trigger {
  display: flex;
  align-items: center;
  gap: 9px;
  min-height: 40px;
  padding: 4px 6px;
  border-radius: 6px;
  color: var(--text);
}

.account-trigger:hover,
.account-trigger:focus-visible {
  background: var(--surface-hover);
}

.account-meta {
  display: flex;
  flex-direction: column;
  min-width: 0;
  line-height: 1.25;
}

.account-meta strong {
  max-width: 112px;
  overflow: hidden;
  color: var(--text-strong);
  font-size: 12px;
  font-weight: 600;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.account-meta small {
  margin-top: 3px;
  color: var(--text-muted);
  font-size: 10px;
}

.logout-button:hover:not(:disabled) {
  background: var(--danger-hover);
  color: var(--danger);
}

.topbar :deep(.el-avatar) {
  border: 1px solid var(--border-accent);
  background: var(--accent-soft);
  color: var(--accent-text);
  font-size: 11px;
  font-weight: 650;
}

.main-content {
  width: 100%;
  max-width: var(--content-max);
  margin: 0 auto;
  padding: 30px var(--page-gutter) 48px;
  flex: 1;
}

.main-content.fill-height {
  display: flex;
  flex-direction: column;
  max-width: none;
  min-height: 0;
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

@media (max-width: 1320px) {
  .topbar {
    gap: 14px;
  }

  .status-label,
  .account-meta small {
    display: none;
  }
}

@media (max-width: 1180px) {
  .topbar {
    justify-content: space-between;
  }

  .topbar-nav {
    display: none;
  }

  .mobile-menu {
    display: inline-flex !important;
  }
}

@media (max-width: 680px) {
  .topbar {
    height: 60px;
    gap: 8px;
  }

  .topbar-actions {
    gap: 3px;
  }

  .account-meta,
  .action-divider,
  .status-link {
    display: none;
  }

  .account-trigger {
    padding: 4px;
  }

  .main-content {
    padding-top: 22px;
    padding-bottom: 32px;
  }
}

@media (max-width: 440px) {
  .brand-link :deep(.logo-copy small) {
    display: none;
  }
}
</style>
