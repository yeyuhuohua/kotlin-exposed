<script setup lang="ts">
/** 菜单管理页：页面清单（侧栏分组、图标、路径、可见性）的运行时来源，仅 ADMIN 可见。 */
import { computed, onMounted, ref } from 'vue'
import { LoaderCircle, Pencil, Plus, RefreshCw, Save, Trash2 } from '@lucide/vue'
import Modal from '../components/Modal.vue'
import StateBlock from '../components/StateBlock.vue'
import { fillTemplate, menusPaths } from '../api/paths'
import { api, ApiError } from '../lib/api'
import { codePointLength } from '../lib/format'
import { menuIcon, menuIconNames } from '../lib/menuIcons'
import { useNotices } from '../stores/notices'
import { useAuth } from '../stores/auth'
import { useResource } from '../composables/useResource'
import type { MenuItem, MenuSavePayload } from '../types'

const auth = useAuth()
const notices = useNotices()
const menus = useResource((signal) => api<MenuItem[]>(menusPaths.collection, { signal }))
onMounted(menus.refresh)

// ── 新增 / 编辑弹窗 ─────────────────────────────────────────
interface MenuForm {
  key: string
  title: string
  path: string
  icon: string
  group: string
  sort: number
  adminOnly: boolean
  enabled: boolean
}
const blank: MenuForm = {
  key: '',
  title: '',
  path: '',
  icon: '',
  group: '工作空间',
  sort: 100,
  adminOnly: false,
  enabled: true,
}
const editing = ref<MenuItem>()
const dialogOpen = ref(false)
const form = ref<MenuForm>({ ...blank })
const formError = ref('')
const saving = ref(false)
const groupOptions = computed(() => {
  const names = [...new Set((menus.data.value ?? []).map((item) => item.group).filter(Boolean))].sort()
  return [...names, '侧栏底部']
})
function openCreate() {
  editing.value = undefined
  form.value = { ...blank }
  formError.value = ''
  dialogOpen.value = true
}
function openEdit(menu: MenuItem) {
  editing.value = menu
  form.value = {
    key: menu.key,
    title: menu.title,
    path: menu.path,
    icon: menu.icon ?? '',
    group: menu.group === '' ? '侧栏底部' : menu.group,
    sort: menu.sort,
    adminOnly: menu.adminOnly,
    enabled: menu.enabled,
  }
  formError.value = ''
  dialogOpen.value = true
}
/** 与后端 MenuModels 的校验规则保持一致；后端仍会兜底校验。 */
function validate(): string {
  const value = form.value
  if (!editing.value) {
    if (!/^[a-z][a-z0-9-]{1,39}$/.test(value.key))
      return '菜单 key 须为 2-40 位小写字母、数字或连字符，以字母开头'
    if (['login', 'app', 'account', 'forbidden'].includes(value.key)) return '该 key 是前端保留字，请换一个'
  }
  if (!value.title.trim() || codePointLength(value.title.trim()) > 30) return '菜单标题须为 1-30 个字符'
  if (!/^\/([a-z0-9_-]+(\/[a-z0-9_-]+)*)?$/.test(value.path) || codePointLength(value.path) > 100) {
    return '路径须以 / 开头，只能包含小写字母、数字、- _ /'
  }
  if (['/login', '/account', '/forbidden'].includes(value.path)) return '该路径是前端保留地址，请换一个'
  if (codePointLength(value.group) > 20) return '分组名最多 20 个字符'
  return ''
}
/** 界面上的「侧栏底部」对应后端空分组。 */
const storedGroup = (label: string) => (label === '侧栏底部' ? '' : label.trim())
async function save() {
  const message = validate()
  if (message) {
    formError.value = message
    return
  }
  saving.value = true
  formError.value = ''
  const value = form.value
  const payload: MenuSavePayload = {
    title: value.title.trim(),
    path: value.path,
    icon: value.icon || null,
    group: storedGroup(value.group),
    sort: value.sort,
    enabled: value.enabled,
  }
  try {
    if (editing.value) {
      await api(fillTemplate(menusPaths.item, editing.value.key), { method: 'PUT', body: payload })
      notices.show('菜单已更新，其他账号刷新或重新登录后生效')
    } else {
      await api(menusPaths.collection, {
        method: 'POST',
        body: { ...payload, key: value.key, adminOnly: value.adminOnly },
      })
      notices.show('菜单已创建，到角色权限里分配后对应账号才可见')
    }
    dialogOpen.value = false
    await Promise.all([menus.refresh(), syncPages()])
  } catch (cause) {
    formError.value =
      cause instanceof ApiError && cause.status === 409
        ? '菜单 key 或路径已被占用'
        : cause instanceof Error
          ? cause.message
          : '菜单保存失败'
  } finally {
    saving.value = false
  }
}

// ── 删除确认 ────────────────────────────────────────────────
const deleting = ref<MenuItem>()
const deleteError = ref('')
const deleteBusy = ref(false)
function openDelete(menu: MenuItem) {
  deleting.value = menu
  deleteError.value = ''
}
async function confirmDelete() {
  if (!deleting.value) return
  deleteBusy.value = true
  deleteError.value = ''
  try {
    await api(fillTemplate(menusPaths.item, deleting.value.key), { method: 'DELETE' })
    notices.show('菜单已删除，相关角色的页面授权已一并清理')
    deleting.value = undefined
    await Promise.all([menus.refresh(), syncPages()])
  } catch (cause) {
    deleteError.value = cause instanceof Error ? cause.message : '菜单删除失败'
  } finally {
    deleteBusy.value = false
  }
}

/** 菜单变化会影响当前会话的页面权限码与页面清单，强制刷新让侧栏与路由立即跟进。 */
async function syncPages() {
  await auth.refreshUser({ force: true })
  await auth.loadPages({ force: true })
}
</script>
<template>
  <section>
    <header class="page-heading">
      <div>
        <p class="eyebrow">MENU MANAGEMENT</p>
        <h1>菜单管理</h1>
        <p class="page-subtitle">侧栏页面清单的运行时来源 · 仅管理员可见</p>
      </div>
      <div class="heading-actions">
        <el-button class="button primary" :disabled="menus.loading.value" @click="openCreate">
          <Plus :size="16" />
          新增菜单
        </el-button>
        <el-button
          text
          class="icon-button outlined"
          aria-label="刷新菜单列表"
          title="刷新"
          :disabled="menus.loading.value"
          @click="menus.refresh"
        >
          <RefreshCw :size="17" :class="{ spin: menus.loading.value }" />
        </el-button>
      </div>
    </header>

    <section class="data-card">
      <StateBlock :loading="menus.loading.value" :error="menus.error.value" @retry="menus.refresh" />
      <el-table
        v-if="!menus.loading.value && !menus.error.value && menus.data.value"
        :data="menus.data.value"
        class="data-table"
      >
        <el-table-column label="菜单" min-width="170">
          <template #default="{ row }">
            <span class="menu-title">
              <component :is="menuIcon(row.icon)" :size="16" class="menu-title-icon" />
              {{ row.title }}
            </span>
          </template>
        </el-table-column>
        <el-table-column prop="key" label="key" min-width="110">
          <template #default="{ row }">
            <code class="menu-key">{{ row.key }}</code>
          </template>
        </el-table-column>
        <el-table-column prop="path" label="路径" min-width="120">
          <template #default="{ row }">
            <code class="menu-key">{{ row.path }}</code>
          </template>
        </el-table-column>
        <el-table-column label="分组" width="110">
          <template #default="{ row }">{{ row.group || '侧栏底部' }}</template>
        </el-table-column>
        <el-table-column prop="sort" label="排序" width="80" align="center" />
        <el-table-column label="可见性" width="110" align="center">
          <template #default="{ row }">
            <el-tag :type="row.adminOnly ? 'warning' : 'info'" size="small" effect="light">
              {{ row.adminOnly ? '仅 ADMIN' : '可授权' }}
            </el-tag>
          </template>
        </el-table-column>
        <el-table-column label="状态" width="90" align="center">
          <template #default="{ row }">
            <el-tag :type="row.enabled ? 'success' : 'info'" size="small" effect="light">
              {{ row.enabled ? '启用' : '停用' }}
            </el-tag>
          </template>
        </el-table-column>
        <el-table-column label="操作" width="150" align="center">
          <template #default="{ row }">
            <el-button text class="text-button" aria-label="编辑菜单" @click="openEdit(row as MenuItem)">
              <Pencil :size="15" />
            </el-button>
            <el-button
              text
              class="text-button danger"
              :aria-label="row.builtin ? '内置菜单不可删除' : '删除菜单'"
              :disabled="row.builtin"
              @click="openDelete(row as MenuItem)"
            >
              <Trash2 :size="15" />
            </el-button>
          </template>
        </el-table-column>
      </el-table>
      <p class="card-footnote">
        内置菜单随后端启动播种，不可删除；菜单的标题、路径、图标与状态修改后，其他账号刷新页面或重新登录后生效。
      </p>
    </section>

    <Modal
      v-if="dialogOpen"
      :title="editing ? `编辑菜单 · ${editing.key}` : '新增菜单'"
      :busy="saving"
      @close="dialogOpen = false"
    >
      <div class="modal-body">
        <form class="menu-form" @submit.prevent="save">
          <label v-if="!editing" class="form-field">
            <span>菜单 key</span>
            <el-input v-model="form.key" placeholder="如 handbook，创建后不可改" maxlength="40" />
          </label>
          <label class="form-field">
            <span>标题</span>
            <el-input v-model="form.title" placeholder="侧栏显示的名称" maxlength="30" />
          </label>
          <label class="form-field">
            <span>路径</span>
            <el-input v-model="form.path" placeholder="如 /handbook" maxlength="100" />
          </label>
          <label class="form-field">
            <span>图标</span>
            <el-select v-model="form.icon" placeholder="默认图标" clearable aria-label="选择图标">
              <el-option v-for="name in menuIconNames" :key="name" :label="name" :value="name">
                <span class="icon-option">
                  <component :is="menuIcon(name)" :size="15" />
                  {{ name }}
                </span>
              </el-option>
            </el-select>
          </label>
          <label class="form-field">
            <span>分组</span>
            <el-select
              v-model="form.group"
              placeholder="选择或输入新分组"
              filterable
              allow-create
              aria-label="选择分组"
            >
              <el-option v-for="name in groupOptions" :key="name" :label="name" :value="name" />
            </el-select>
          </label>
          <label class="form-field">
            <span>排序</span>
            <el-input-number v-model="form.sort" :min="0" :max="9999" :step="10" class="sort-input" />
          </label>
          <label v-if="!editing" class="form-check">
            <el-checkbox v-model="form.adminOnly">仅 ADMIN 可见（创建后不可改）</el-checkbox>
          </label>
          <label class="form-check">
            <el-checkbox v-model="form.enabled">启用（停用后从侧栏与路由中移除）</el-checkbox>
          </label>
        </form>
        <p v-if="formError" class="save-error" role="alert">{{ formError }}</p>
      </div>
      <template #footer>
        <footer class="modal-footer">
          <el-button :disabled="saving" @click="dialogOpen = false">取消</el-button>
          <el-button type="primary" :disabled="saving" @click="save">
            <LoaderCircle v-if="saving" :size="16" class="spin" />
            <Save v-else :size="16" />
            保存菜单
          </el-button>
        </footer>
      </template>
    </Modal>

    <Modal
      v-if="deleting"
      :title="`删除菜单 · ${deleting.title}`"
      :busy="deleteBusy"
      @close="deleting = undefined"
    >
      <div class="modal-body">
        <p class="delete-copy">
          删除后，各角色持有的
          <code>page:{{ deleting.key }}</code>
          授权会一并清理，对应账号的侧栏与路由随即移除该页面。
        </p>
        <p v-if="deleteError" class="save-error" role="alert">{{ deleteError }}</p>
      </div>
      <template #footer>
        <footer class="modal-footer">
          <el-button :disabled="deleteBusy" @click="deleting = undefined">取消</el-button>
          <el-button type="danger" :disabled="deleteBusy" @click="confirmDelete">
            <LoaderCircle v-if="deleteBusy" :size="16" class="spin" />
            <Trash2 v-else :size="16" />
            确认删除
          </el-button>
        </footer>
      </template>
    </Modal>
  </section>
</template>

<style scoped>
/* 本组件样式：颜色只用 styles.css 里的语义 token。 */
.menu-title {
  display: inline-flex;
  align-items: center;
  gap: 8px;
  font-weight: 550;
}

.menu-title-icon {
  color: var(--text-dim);
}

.menu-key {
  font-size: 12px;
  color: var(--text-muted);
  background: var(--surface-subtle);
  border-radius: 6px;
  padding: 2px 6px;
}

.card-footnote {
  margin: 14px 4px 2px;
  font-size: 12px;
  line-height: 1.7;
  color: var(--text-faint);
}

.menu-form {
  display: grid;
  grid-template-columns: 1fr 1fr;
  gap: 14px 16px;
}

.form-field {
  display: flex;
  flex-direction: column;
  gap: 6px;
  font-size: 13px;
  color: var(--text-muted);
}

.form-check {
  grid-column: span 2;
  font-size: 13px;
  color: var(--text-muted);
}

.sort-input {
  width: 100%;
}

.icon-option {
  display: inline-flex;
  align-items: center;
  gap: 8px;
}

.delete-copy {
  font-size: 13px;
  line-height: 1.8;
  color: var(--text-muted);
}

.delete-copy code {
  background: var(--surface-subtle);
  border-radius: 6px;
  padding: 1px 6px;
}

.save-error {
  margin: 16px 0 0;
  padding: 12px 14px;
  border: 1px solid var(--danger-soft);
  background: var(--danger-tint);
  color: var(--danger);
  border-radius: var(--control-radius);
  font-size: 13px;
  line-height: 1.7;
}

.spin {
  animation: spin 0.8s linear infinite;
}

@media (max-width: 680px) {
  .menu-form {
    grid-template-columns: 1fr;
  }

  .form-check {
    grid-column: span 1;
  }
}
</style>
