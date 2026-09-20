<script setup lang="ts">
import { computed } from 'vue'
import { useRoute } from 'vue-router'
import {
  Activity,
  ArrowUpRight,
  BookOpen,
  BriefcaseBusiness,
  Building2,
  Database,
  FolderClock,
  Globe2,
  LayoutDashboard,
  MapPin,
  ShieldCheck,
  Users,
  X,
} from '@lucide/vue'
import { docsPaths } from '../api/paths'
import { useAuth } from '../stores/auth'

/** 桌面侧栏与移动抽屉共用同一菜单，按服务端返回的角色页面权限过滤。 */
defineProps<{ mobile?: boolean }>()
const emit = defineEmits<{ navigate: [] }>()
const auth = useAuth()
const route = useRoute()
const groups = computed(() =>
  [
    {
      label: '工作空间',
      items: [
        { to: '/', label: '工作概览', icon: LayoutDashboard },
        { to: '/employees', label: '员工管理', icon: Users },
        { to: '/departments', label: '部门管理', icon: Building2 },
        { to: '/jobs', label: '岗位管理', icon: BriefcaseBusiness },
        { to: '/locations', label: '办公地点', icon: MapPin },
      ],
    },
    {
      label: '组织资料',
      items: [
        { to: '/job-history', label: '任职历史', icon: FolderClock },
        { to: '/job-grades', label: '薪资等级', icon: BookOpen },
        { to: '/countries', label: '国家与地区', icon: Globe2 },
        { to: '/regions', label: '区域目录', icon: Globe2 },
        { to: '/emp-details', label: '员工详情视图', icon: Users },
      ],
    },
    ...(auth.isAdmin
      ? [
          {
            label: '访问控制',
            items: [
              { to: '/users', label: '用户管理', icon: ShieldCheck },
              { to: '/roles', label: '角色权限', icon: ShieldCheck },
            ],
          },
        ]
      : []),
  ]
    .map((group) => ({ ...group, items: group.items.filter((item) => auth.canPage(item.to)) }))
    .filter((group) => group.items.length),
)
const demos = computed(() =>
  [
    { to: '/t-dept', label: '示例部门' },
    { to: '/t-emp', label: '示例人员' },
    { to: '/orders', label: '示例订单' },
  ].filter((item) => auth.canPage(item.to)),
)
</script>
<template>
  <div class="sidebar-content">
    <RouterLink :to="auth.home" class="brand" @click="emit('navigate')">
      <span class="brand-icon"><Users :size="23" /></span>
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
    <div class="workspace-label">
      <span class="workspace-symbol">H</span>
      <div>
        HR 组织空间
        <small>atguigudb</small>
      </div>
      <span class="workspace-dot"></span>
    </div>
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
        <el-sub-menu v-if="demos.length" index="demo">
          <template #title>
            <Database :size="18" />
            <span>演示数据</span>
          </template>
          <el-menu-item v-for="item in demos" :key="item.to" :index="item.to">{{ item.label }}</el-menu-item>
        </el-sub-menu>
      </el-menu>
    </nav>
    <div class="sidebar-bottom">
      <el-menu :default-active="route.path" router class="workspace-menu" @select="emit('navigate')">
        <el-menu-item v-if="auth.canPage('system')" index="/system">
          <Activity :size="18" />
          <span>系统状态</span>
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
.brand {
  display: flex;
  align-items: center;
  gap: 10px;
  padding: 27px 23px 25px;
  font-size: 18px;
  font-weight: 650;
  white-space: nowrap;
}

.brand small {
  display: block;
  font-size: 9px;
  color: var(--text-faint);
  margin-top: 3px;
  font-weight: 500;
}

.workspace-label {
  margin: 0 16px 18px;
  padding: 11px;
  border: 1px solid var(--border);
  border-radius: 6px;
  display: flex;
  gap: 9px;
  align-items: center;
  font-size: 12px;
  font-weight: 500;
}

.workspace-symbol {
  width: 29px;
  height: 29px;
  background: var(--green-soft-hover);
  display: grid;
  place-items: center;
  border-radius: 4px;
  color: var(--text-soft);
  font-size: 14px;
}

.workspace-label small {
  display: block;
  color: var(--text-dim);
  font-size: 10px;
  margin-top: 2px;
}

.workspace-dot {
  width: 6px;
  height: 6px;
  border-radius: 50%;
  background: var(--green-mid);
  margin-left: auto;
}

.main-nav {
  overflow-y: auto;
  flex: 1;
  padding: 0 12px 10px;
  scrollbar-width: thin;
}

.nav-link {
  display: flex;
  align-items: center;
  gap: 11px;
  padding: 10px 12px;
  margin: 3px 0;
  font-size: 12px;
  color: var(--text-nav);
  min-height: 37px;
  border-radius: 5px;
  transition:
    background 0.15s,
    color 0.15s;
}

.nav-link svg {
  color: var(--text-dim);
}

.nav-link:hover {
  background: var(--surface-hover);
  color: var(--text-green-dark);
}

.nav-link.active {
  color: var(--green);
  background: var(--green-soft);
  font-weight: 600;
}

.nav-link.active svg {
  color: var(--green);
}

.sidebar-bottom {
  padding: 10px 16px 0;
  border-top: 1px solid var(--border);
}

.sidebar-footnote {
  display: flex;
  align-items: center;
  gap: 6px;
  font-size: 9px;
  color: var(--text-pale);
  border-top: 1px solid var(--border);
  padding: 17px 0;
  margin-top: 8px;
}

.sidebar-footnote > span:last-child {
  margin-left: auto;
}

.tiny-square {
  height: 6px;
  width: 6px;
  background: var(--green-text-light);
  border-radius: 1px;
}

@media (max-width: 1200px) {
  .brand {
    padding-left: 19px;
    font-size: 16px;
    gap: 8px;
  }
}

@media (max-width: 900px) {
  .brand {
    padding: 24px 15px;
    font-size: 15px;
  }
}

@media (max-width: 900px) {
  .sidebar-footnote {
    font-size: 8px;
  }
}

.sidebar-content {
  height: 100%;
  display: flex;
  flex-direction: column;
  position: relative;
}

.workspace-menu.el-menu {
  border: 0;
  --el-menu-bg-color: transparent;
  --el-menu-hover-bg-color: var(--green-tint);
  --el-menu-active-color: var(--green);
}

.workspace-menu :deep(.el-menu-item),
.workspace-menu :deep(.el-sub-menu__title) {
  height: 39px;
  line-height: 39px;
  gap: 11px;
  border-radius: 5px;
  margin: 3px 0;
  font-size: 12px;
}

.workspace-menu :deep(.el-menu-item.is-active) {
  background: var(--green-soft);
  font-weight: 600;
}

.workspace-menu :deep(.el-menu-item-group__title) {
  font-size: 10px;
  color: var(--text-dim);
  padding-top: 15px;
}

.drawer-close.el-button {
  position: absolute;
  top: 27px;
  right: 6px;
}
</style>
