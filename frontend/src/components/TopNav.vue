<script setup lang="ts">
import { computed } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { ArrowUpRight, BookOpen } from '@lucide/vue'
import { docsPaths } from '../api/paths'
import { menuIcon } from '../lib/menuIcons'
import { useAuth } from '../stores/auth'

/** 顶部胶囊导航:分组、图标、顺序全部来自后端下发的页面清单,与移动抽屉共用数据源。 */
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
    window.open(docsPaths.swagger, '_blank', 'noopener')
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
      :ellipsis="true"
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
/* 本组件样式:颜色只用 styles.css 里的语义 token。
   下拉浮层挂在 body 下,样式在 styles.css 的 Element Plus 覆盖区。 */
.top-nav {
  min-width: 0;
  width: 100%;
}

/* 菜单本体为占满中间栏的圆角胶囊,菜单项在胶囊内居中;
   宽度确定后 el-menu 的 ellipsis 才能正确测量,空间够就全部展示 */
.top-menu.el-menu {
  width: 100%;
  justify-content: center;
  padding: 4px;
  border: 1px solid var(--border);
  border-radius: 999px;
  background: var(--surface);
  box-shadow: 0 1px 2px var(--shadow-card);
  --el-menu-bg-color: transparent;
  --el-menu-border-color: var(--border);
  --el-menu-text-color: var(--text-medium);
  --el-menu-hover-text-color: var(--text-strong);
  --el-menu-hover-bg-color: var(--surface-hover);
  --el-menu-active-color: var(--green);
  --el-menu-item-height: 36px;
  --el-menu-sub-item-height: 38px;
  --el-menu-horizontal-height: 46px;
  --el-menu-horizontal-line-height: 0;
}

/* 药丸形菜单项:去掉 Element 默认的底部指示线,统一圆角 hover */
.top-menu :deep(.el-menu-item),
.top-menu :deep(.el-sub-menu__title) {
  height: 36px;
  line-height: 36px;
  padding: 0 15px;
  margin: 0 1px;
  gap: 6px;
  font-size: 13px;
  border: 0;
  border-radius: 999px;
  transition:
    background 0.15s,
    color 0.15s;
}

/* 子菜单箭头在横向模式里是绝对定位的,标题右侧要预留它的位置 */
.top-menu :deep(.el-sub-menu__title) {
  padding-right: 28px;
}

.top-menu :deep(.el-sub-menu__icon-arrow) {
  position: absolute;
  right: 10px;
  top: 50%;
  margin: -6px 0 0;
  width: 12px;
}

/* 选中态:药丸底色 + 翡翠文字,彻底清掉横向模式默认的底部指示线 */
.top-menu.el-menu--horizontal :deep(.el-menu-item.is-active) {
  background: var(--green-soft);
  color: var(--green);
  font-weight: 600;
  border-bottom: none;
}

.top-menu.el-menu--horizontal :deep(.el-sub-menu.is-active .el-sub-menu__title) {
  color: var(--green);
  border-bottom: none;
}

.external-mark {
  color: var(--text-dim);
  margin-left: 2px;
}

@media (max-width: 900px) {
  .top-nav {
    display: none;
  }
}
</style>
