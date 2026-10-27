<script setup lang="ts">
import { computed } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { ArrowUpRight, BookOpen } from '@lucide/vue'
import { docsHref, docsPaths } from '../api/paths'
import { menuIcon } from '../lib/menuIcons'
import { useAuth } from '../stores/auth'

/** 顶部导航:分组、图标、顺序全部来自后端下发的页面清单,与移动抽屉共用数据源。 */
const emit = defineEmits<{ navigate: [] }>()
const auth = useAuth()
const route = useRoute()
const router = useRouter()

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
const tailPages = computed(() =>
  visiblePages.value
    .filter((page) => page.group === '')
    .map((page) => ({ to: page.path, label: page.title, icon: menuIcon(page.icon) })),
)

const DOCS_INDEX = '__docs__'

function onSelect(index: string) {
  if (index === DOCS_INDEX) {
    window.open(docsHref(docsPaths.swagger), '_blank', 'noopener')
    return
  }
  if (index.startsWith('/')) void router.push(index)
  emit('navigate')
}
</script>

<template>
  <nav class="top-nav" aria-label="主导航">
    <el-menu
      mode="horizontal"
      :default-active="route.path"
      :ellipsis="false"
      class="top-menu"
      @select="onSelect"
    >
      <el-sub-menu v-for="group in groups" :key="group.label" :index="group.label">
        <template #title>{{ group.label }}</template>
        <el-menu-item v-for="item in group.items" :key="item.to" :index="item.to">
          <component :is="item.icon" :size="16" />
          <span>{{ item.label }}</span>
        </el-menu-item>
      </el-sub-menu>
      <el-menu-item v-for="item in tailPages" :key="item.to" :index="item.to">
        <component :is="item.icon" :size="16" />
        <span>{{ item.label }}</span>
      </el-menu-item>
      <el-menu-item :index="DOCS_INDEX">
        <BookOpen :size="16" />
        <span>接口文档</span>
        <ArrowUpRight :size="13" class="external-mark" />
      </el-menu-item>
    </el-menu>
  </nav>
</template>

<style scoped>
.top-nav {
  width: 100%;
  min-width: 0;
  overflow-x: auto;
  scrollbar-width: none;
}

.top-nav::-webkit-scrollbar {
  display: none;
}

.top-menu.el-menu {
  width: max-content;
  min-width: 100%;
  justify-content: flex-start;
  padding: 0;
  border: 0;
  border-radius: 0;
  background: transparent;
  box-shadow: none;
  --el-menu-bg-color: transparent;
  --el-menu-border-color: var(--border);
  --el-menu-text-color: var(--text-nav);
  --el-menu-hover-text-color: var(--text-strong);
  --el-menu-hover-bg-color: var(--surface-hover);
  --el-menu-active-color: var(--accent-text);
  --el-menu-item-height: 47px;
  --el-menu-sub-item-height: 47px;
  --el-menu-horizontal-height: 47px;
  --el-menu-horizontal-line-height: 0;
}

.top-menu :deep(.el-menu-item),
.top-menu :deep(.el-sub-menu__title) {
  position: relative;
  height: 47px;
  line-height: 47px;
  margin: 0;
  padding: 0 15px;
  gap: 7px;
  border: 0;
  border-radius: 8px 8px 0 0;
  font-size: 13px;
  font-weight: 500;
  transition:
    background 0.15s,
    color 0.15s;
}

.top-menu :deep(.el-sub-menu__icon-arrow) {
  position: static;
  width: 12px;
  margin: 0 -2px 0 2px;
  color: var(--text-faint);
}

.top-menu.el-menu--horizontal :deep(.el-menu-item.is-active),
.top-menu.el-menu--horizontal :deep(.el-sub-menu.is-active .el-sub-menu__title) {
  border-bottom: none;
  background: transparent;
  color: var(--accent-text);
  font-weight: 600;
}

.top-menu.el-menu--horizontal :deep(.el-menu-item.is-active)::after,
.top-menu.el-menu--horizontal :deep(.el-sub-menu.is-active .el-sub-menu__title)::after {
  content: '';
  position: absolute;
  right: 15px;
  bottom: 0;
  left: 15px;
  height: 2px;
  border-radius: 2px 2px 0 0;
  background: var(--accent);
}

.external-mark {
  margin-left: 1px;
  color: var(--text-faint);
}

@media (max-width: 900px) {
  .top-nav {
    display: none;
  }
}
</style>
