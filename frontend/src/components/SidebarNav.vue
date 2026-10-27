<script setup lang="ts">
import { computed } from 'vue'
import { useRoute } from 'vue-router'
import { ArrowUpRight, BookOpen, X } from '@lucide/vue'
import AppLogo from './AppLogo.vue'
import { docsHref, docsPaths } from '../api/paths'
import { menuIcon } from '../lib/menuIcons'
import { useAuth } from '../stores/auth'

/** 移动抽屉菜单；分组、图标、顺序全部来自后端下发的页面清单。 */
defineProps<{ mobile?: boolean }>()
const emit = defineEmits<{ navigate: [] }>()
const auth = useAuth()
const route = useRoute()

const visiblePages = computed(() => auth.pages.filter((page) => auth.canPage(page.key)))
const groups = computed(() => {
  const byGroup = new Map<string, { to: string; label: string; icon: ReturnType<typeof menuIcon> }[]>()
  for (const page of visiblePages.value) {
    if (page.group === '') continue
    const items = byGroup.get(page.group) ?? []
    items.push({ to: page.path, label: page.title, icon: menuIcon(page.icon) })
    byGroup.set(page.group, items)
  }
  return [...byGroup].map(([label, items]) => ({ label, items }))
})
const bottomPages = computed(() =>
  visiblePages.value
    .filter((page) => page.group === '')
    .map((page) => ({ to: page.path, label: page.title, icon: menuIcon(page.icon) })),
)
</script>

<template>
  <div class="sidebar-content">
    <div class="drawer-header">
      <RouterLink :to="auth.home" class="brand-link" @click="emit('navigate')">
        <AppLogo />
      </RouterLink>
      <el-button
        v-if="mobile"
        text
        class="drawer-close"
        :icon="X"
        aria-label="关闭导航"
        @click="emit('navigate')"
      />
    </div>
    <nav class="main-nav" aria-label="主导航">
      <el-menu :default-active="route.path" router class="workspace-menu" @select="emit('navigate')">
        <el-menu-item-group v-for="group in groups" :key="group.label" :title="group.label">
          <el-menu-item v-for="item in group.items" :key="item.to" :index="item.to">
            <component :is="item.icon" :size="17" />
            <span>{{ item.label }}</span>
          </el-menu-item>
        </el-menu-item-group>
      </el-menu>
    </nav>
    <div class="sidebar-bottom">
      <el-menu :default-active="route.path" router class="workspace-menu" @select="emit('navigate')">
        <el-menu-item v-for="item in bottomPages" :key="item.to" :index="item.to">
          <component :is="item.icon" :size="17" />
          <span>{{ item.label }}</span>
        </el-menu-item>
      </el-menu>
      <a class="nav-link" :href="docsHref(docsPaths.swagger)" target="_blank" rel="noopener">
        <BookOpen :size="17" />
        <span>接口文档</span>
        <ArrowUpRight :size="14" class="push-right" />
      </a>
      <div class="sidebar-footnote">
        <span class="footnote-dot"></span>
        <span>HR Workspace</span>
        <span>v1.0</span>
      </div>
    </div>
  </div>
</template>

<style scoped>
.sidebar-content {
  height: 100%;
  display: flex;
  flex-direction: column;
  background: var(--surface);
}

.drawer-header {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 12px;
  padding: 18px;
  border-bottom: 1px solid var(--border);
}

.brand-link {
  display: inline-flex;
  border-radius: 11px;
}

.drawer-close.el-button {
  width: 32px;
  height: 32px;
  padding: 0;
  color: var(--text-muted);
}

.main-nav {
  flex: 1;
  overflow-y: auto;
  padding: 12px;
  scrollbar-width: thin;
}

.workspace-menu.el-menu {
  border: 0;
  --el-menu-bg-color: transparent;
  --el-menu-hover-bg-color: var(--surface-hover);
  --el-menu-active-color: var(--accent-text);
}

.workspace-menu :deep(.el-menu-item),
.workspace-menu :deep(.el-sub-menu__title) {
  height: 42px;
  line-height: 42px;
  gap: 11px;
  margin: 2px 0;
  border-radius: 8px;
  color: var(--text-nav);
  font-size: 13px;
}

.workspace-menu :deep(.el-menu-item:hover),
.workspace-menu :deep(.el-sub-menu__title:hover) {
  color: var(--text-strong);
}

.workspace-menu :deep(.el-menu-item.is-active) {
  background: var(--accent-tint);
  box-shadow: inset 2px 0 0 var(--accent);
  color: var(--accent-text);
  font-weight: 600;
}

.workspace-menu :deep(.el-menu-item-group__title) {
  padding: 18px 12px 7px;
  color: var(--text-faint);
  font-size: 10px;
  font-weight: 600;
  letter-spacing: 0.08em;
}

.workspace-menu :deep(.el-sub-menu .el-menu-item) {
  min-width: 0;
}

.sidebar-bottom {
  padding: 12px;
  border-top: 1px solid var(--border);
}

.nav-link {
  display: flex;
  align-items: center;
  gap: 11px;
  min-height: 42px;
  margin-top: 4px;
  padding: 10px 12px;
  border-radius: 8px;
  color: var(--text-nav);
  font-size: 13px;
}

.nav-link:hover {
  background: var(--surface-hover);
  color: var(--text-strong);
}

.nav-link svg {
  color: var(--text-faint);
}

.sidebar-footnote {
  display: flex;
  align-items: center;
  gap: 7px;
  margin-top: 12px;
  padding: 16px 4px 4px;
  border-top: 1px solid var(--border-soft);
  color: var(--text-faint);
  font-size: 10px;
}

.sidebar-footnote > span:last-child {
  margin-left: auto;
}

.footnote-dot {
  width: 6px;
  height: 6px;
  border-radius: 2px;
  background: var(--accent);
}
</style>
