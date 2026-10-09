<script setup lang="ts">
import { computed } from 'vue'
import { useRoute } from 'vue-router'
import { ArrowUpRight, BookOpen, Users, X } from '@lucide/vue'
import { docsPaths } from '../api/paths'
import { menuIcon } from '../lib/menuIcons'
import { useAuth } from '../stores/auth'

/** 桌面侧栏与移动抽屉共用同一菜单；分组、图标、顺序全部来自后端下发的页面清单。 */
defineProps<{ mobile?: boolean }>()
const emit = defineEmits<{ navigate: [] }>()
const auth = useAuth()
const route = useRoute()

// 清单与顺序数据源是后端 /api/auth/routes（菜单表按 sort 排序下发）；
// group 为空的页面固定在侧栏底部，不参与分组。
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
    <RouterLink :to="auth.home" class="brand" @click="emit('navigate')">
      <span class="brand-icon"><Users :size="22" /></span>
      <span>
        人事工作台
        <small>HR WORKSPACE</small>
      </span>
    </RouterLink>
    <el-button
      v-if="mobile"
      text
      class="drawer-close"
      :icon="X"
      aria-label="关闭导航"
      @click="emit('navigate')"
    />
    <nav class="main-nav" aria-label="主导航">
      <el-menu
        :default-active="route.path"
        router
        unique-opened
        class="workspace-menu"
        @select="emit('navigate')"
      >
        <el-menu-item-group v-for="group in groups" :key="group.label" :title="group.label">
          <el-menu-item v-for="item in group.items" :key="item.to" :index="item.to">
            <component :is="item.icon" :size="18" />
            <span>{{ item.label }}</span>
          </el-menu-item>
        </el-menu-item-group>
      </el-menu>
    </nav>
    <div class="sidebar-bottom">
      <el-menu :default-active="route.path" router class="workspace-menu" @select="emit('navigate')">
        <el-menu-item v-for="item in bottomPages" :key="item.to" :index="item.to">
          <component :is="item.icon" :size="18" />
          <span>{{ item.label }}</span>
        </el-menu-item>
      </el-menu>
      <a class="nav-link" :href="docsPaths.swagger" target="_blank" rel="noopener">
        <BookOpen :size="18" />
        接口文档
        <ArrowUpRight :size="14" class="push-right" />
      </a>
      <div class="sidebar-footnote">
        <span class="tiny-square"></span>
        HR / 组织管理
        <span>v1.0</span>
      </div>
    </div>
  </div>
</template>

<style scoped>
/* 本组件样式：颜色只用 styles.css 里的语义 token。 */
.sidebar-content {
  height: 100%;
  display: flex;
  flex-direction: column;
  position: relative;
}

.brand {
  display: flex;
  align-items: center;
  gap: 12px;
  padding: 24px 20px 26px;
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

.main-nav {
  overflow-y: auto;
  flex: 1;
  padding: 0 14px 12px;
  scrollbar-width: thin;
}

.nav-link {
  display: flex;
  align-items: center;
  gap: 11px;
  padding: 10px 12px;
  margin: 3px 0;
  font-size: 13px;
  color: var(--text-nav);
  min-height: 40px;
  border-radius: 10px;
  transition:
    background 0.15s,
    color 0.15s;
}

.nav-link svg {
  color: var(--text-dim);
}

.nav-link:hover {
  background: var(--surface-hover);
  color: var(--text-strong);
}

.sidebar-bottom {
  padding: 10px 14px 0;
  border-top: 1px solid var(--border);
}

.sidebar-footnote {
  display: flex;
  align-items: center;
  gap: 7px;
  font-size: 10px;
  letter-spacing: 0.06em;
  text-transform: uppercase;
  color: var(--text-pale);
  border-top: 1px solid var(--border);
  padding: 16px 2px;
  margin-top: 8px;
}

.sidebar-footnote > span:last-child {
  margin-left: auto;
}

.tiny-square {
  height: 6px;
  width: 6px;
  background: var(--green-text-light);
  border-radius: 2px;
}

.workspace-menu.el-menu {
  border: 0;
  --el-menu-bg-color: transparent;
  --el-menu-hover-bg-color: var(--surface-hover);
  --el-menu-active-color: var(--green);
}

.workspace-menu :deep(.el-menu-item),
.workspace-menu :deep(.el-sub-menu__title) {
  height: 40px;
  line-height: 40px;
  gap: 11px;
  border-radius: 10px;
  margin: 2px 0;
  font-size: 13px;
  color: var(--text-nav);
}

.workspace-menu :deep(.el-menu-item:hover),
.workspace-menu :deep(.el-sub-menu__title:hover) {
  color: var(--text-strong);
}

.workspace-menu :deep(.el-menu-item.is-active) {
  background: var(--green-soft);
  color: var(--green);
  font-weight: 600;
}

.workspace-menu :deep(.el-menu-item-group__title) {
  font-size: 10px;
  font-weight: 600;
  letter-spacing: 0.1em;
  text-transform: uppercase;
  color: var(--text-faint);
  padding: 18px 12px 6px;
}

.workspace-menu :deep(.el-sub-menu .el-menu-item) {
  min-width: 0;
}

.drawer-close.el-button {
  position: absolute;
  top: 26px;
  right: 8px;
}

@media (max-width: 1200px) {
  .brand {
    padding-left: 16px;
    font-size: 16px;
    gap: 10px;
  }
}
</style>
