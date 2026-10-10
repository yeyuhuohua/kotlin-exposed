<script setup lang="ts">
import { computed, onMounted, ref, watch } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { LogOut, Menu, Moon, Sun, Users } from '@lucide/vue'
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
/** 内嵌 iframe 类页面（meta.fillHeight）占满视口高度,主区不再产生页面滚动。 */
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
    notices.show('已退出本机;服务端会话撤销失败', true)
  } finally {
    signingOut.value = false
    await router.replace('/login')
  }
}
</script>

<template>
  <div class="app-shell">
    <header class="topbar">
      <div class="topbar-left">
        <el-button text class="mobile-menu" :icon="Menu" aria-label="打开导航" @click="mobileOpen = true" />
        <RouterLink :to="auth.home" class="brand">
          <span class="brand-icon"><Users :size="22" /></span>
          <span>
            人事工作台
            <small>HR WORKSPACE</small>
          </span>
        </RouterLink>
      </div>
      <TopNav class="topbar-center" />
      <div class="topbar-actions">
        <RouterLink
          v-if="auth.canPage('system') && systemTo"
          :to="systemTo"
          :class="['status-pill', { down: health?.status !== 'UP' }]"
        >
          <span class="status-dot"></span>
          {{ health?.status === 'UP' ? '服务已连接' : health ? '服务异常' : '状态未确认' }}
        </RouterLink>
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
          <el-avatar :size="32">{{ initials(auth.user?.username || 'U') }}</el-avatar>
          <span class="account-meta">
            {{ auth.user?.username }}
            <small>{{ auth.isAdmin ? '管理员' : '普通用户' }}</small>
          </span>
        </RouterLink>
        <el-tooltip content="退出登录">
          <el-button text :icon="LogOut" aria-label="退出登录" :loading="signingOut" @click="logout" />
        </el-tooltip>
      </div>
    </header>
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
    <main id="main-content" :class="['main-content', { 'fill-height': fillHeight }]">
      <RouterView v-slot="{ Component }"><component :is="Component" :key="route.path" /></RouterView>
    </main>
    <footer v-if="!fillHeight" class="workspace-footer">
      <span>HR Workspace</span>
      <span class="footer-dot"></span>
      <span>组织 · 人才 · 协作</span>
    </footer>
  </div>
</template>

<style scoped>
/* 本组件样式:颜色只用 styles.css 里的语义 token。 */
.app-shell {
  min-height: 100vh;
  background: var(--bg-page);
  display: flex;
  flex-direction: column;
}

/* 顶栏三栏网格:左右两栏按内容占位,中间栏占满剩余空间给导航 */
.topbar {
  height: 64px;
  background: var(--surface-translucent);
  border-bottom: 1px solid var(--border);
  display: grid;
  grid-template-columns: auto minmax(0, 1fr) auto;
  align-items: center;
  padding: 0 24px;
  gap: 16px;
  position: sticky;
  top: 0;
  z-index: 30;
  backdrop-filter: blur(14px);
}

.topbar-left {
  display: flex;
  align-items: center;
  gap: 10px;
  justify-self: start;
  min-width: 0;
}

.topbar-center {
  justify-self: center;
}

.brand {
  display: flex;
  align-items: center;
  gap: 12px;
  font-size: 17px;
  font-weight: 650;
  letter-spacing: -0.01em;
  white-space: nowrap;
}

.brand small {
  display: block;
  font-size: 10px;
  letter-spacing: 0.1em;
  color: var(--text-faint);
  margin-top: 3px;
  font-weight: 500;
}

.topbar-actions {
  display: flex;
  align-items: center;
  gap: 12px;
  justify-self: end;
}

/* 服务状态:描边胶囊,点状指示灯 */
.status-pill {
  display: inline-flex;
  align-items: center;
  gap: 8px;
  height: 36px;
  padding: 0 14px;
  border: 1px solid var(--border);
  border-radius: 999px;
  background: var(--surface);
  color: var(--text-medium);
  font-size: 12px;
  white-space: nowrap;
  transition:
    border-color 0.15s,
    color 0.15s;
}

.status-pill:hover {
  border-color: var(--border-strong);
  color: var(--text-strong);
}

.status-dot {
  width: 7px;
  height: 7px;
  border-radius: 50%;
  background: var(--status-up);
  box-shadow: 0 0 0 3px var(--status-up-soft);
}

.status-pill.down .status-dot {
  background: var(--status-down);
  box-shadow: none;
}

/* 账户入口:头像 + 双行文字的胶囊链接 */
.account-link {
  display: flex;
  align-items: center;
  gap: 10px;
  height: 44px;
  padding: 5px 14px 5px 5px;
  border: 1px solid var(--border);
  border-radius: 999px;
  background: var(--surface);
  font-size: 13px;
  font-weight: 600;
  color: var(--text);
  transition:
    border-color 0.15s,
    background 0.15s;
}

.account-link:hover {
  border-color: var(--border-strong);
  background: var(--surface-subtle);
}

.account-link small {
  display: block;
  color: var(--text-faint);
  font-size: 11px;
  font-weight: 400;
  margin-top: 1px;
}

.main-content {
  padding: 34px 36px 44px;
  max-width: 1440px;
  width: 100%;
  margin: 0 auto;
  flex: 1;
}

/* 填满视口模式（对话页等内嵌 iframe 页面）:主区高度锁死为视口剩余空间,
   无内边距、无页面滚动,内容自己在区域内滚动 */
.main-content.fill-height {
  display: flex;
  flex-direction: column;
  min-height: 0;
  padding: 0;
  max-width: none;
  overflow: hidden;
}

.main-content.fill-height > * {
  flex: 1;
  min-height: 0;
}

.workspace-footer {
  padding: 0 36px 22px;
  display: flex;
  justify-content: center;
  align-items: center;
  gap: 10px;
  color: var(--text-pale);
  font-size: 11px;
}

.footer-dot {
  width: 3px;
  height: 3px;
  border-radius: 50%;
  background: var(--text-pale);
}

.mobile-menu {
  display: none !important;
}

@media (min-width: 1500px) {
  .main-content {
    padding: 42px 48px;
  }
}

@media (max-width: 1200px) {
  .topbar {
    padding: 0 20px;
    gap: 12px;
  }
  .main-content {
    padding: 28px 26px;
  }
  .topbar-actions {
    gap: 10px;
  }
  .brand small {
    display: none;
  }
}

@media (max-width: 1100px) {
  .status-pill {
    display: none;
  }
}

@media (max-width: 900px) {
  .topbar {
    display: flex;
    justify-content: space-between;
    padding: 0 16px;
    gap: 9px;
    height: 58px;
  }
  .mobile-menu {
    display: inline-flex !important;
  }
  .topbar-actions {
    gap: 7px;
  }
  .main-content {
    padding: 24px 20px;
  }
}

@media (max-width: 680px) {
  .account-meta {
    display: none;
  }
  .account-link {
    padding-right: 5px;
    height: 40px;
  }
  .main-content {
    padding: 20px 16px;
  }
  .workspace-footer {
    padding: 0 16px 18px;
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
</style>
