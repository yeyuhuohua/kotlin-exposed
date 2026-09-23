<script setup lang="ts">
/** 复用业务目录页面，区分服务端分页与本地分页，并只暴露后端支持的管理操作。 */
import { computed, ref, watch } from 'vue'
import { useRouter } from 'vue-router'
import { ArrowUpRight, Pencil, Plus, RefreshCw, Search, ShieldCheck, Trash2 } from '@lucide/vue'
import { api, query } from '../lib/api'
import { money } from '../lib/format'
import { useResource } from '../composables/useResource'
import { fillTemplate, rolesPaths } from '../api/paths'
import { isProtectedRole, isProtectedUser } from '../lib/permissions'
import { useAuth } from '../stores/auth'
import { useNotices } from '../stores/notices'
import Modal from '../components/Modal.vue'
import Pagination from '../components/Pagination.vue'
import StateBlock from '../components/StateBlock.vue'
import RecordDialog from '../components/RecordDialog.vue'
import RolePermissionEditor from '../components/RolePermissionEditor.vue'
import type { Page, Resource, Row } from '../types'
const props = defineProps<{ resource: Resource }>()
const auth = useAuth()
const router = useRouter()
const notices = useNotices()
const page = ref(1)
const pageSize = ref(20)
const search = ref('')
const open = ref(false)
const editing = ref<Row>()
const permissionTarget = ref<Row>()
const canCreate = computed(() => !!props.resource.fields && auth.canApi('POST', props.resource.endpoint))
const canEdit = computed(
  () =>
    !!props.resource.fields &&
    !!props.resource.updateTemplate &&
    auth.canApi('PUT', props.resource.updateTemplate),
)
const canManagePermissions = computed(
  () =>
    props.resource.key === 'roles' &&
    auth.isAdmin &&
    auth.canApi('GET', rolesPaths.catalog) &&
    auth.canApi('GET', rolesPaths.permissions) &&
    auth.canApi('PUT', rolesPaths.permissions),
)
const canLinkEmployees = computed(() => props.resource.key === 'departments' && auth.canPage('employees'))
const canDelete = computed(
  () => !!props.resource.updateTemplate && auth.canApi('DELETE', props.resource.updateTemplate),
)
const hasActions = computed(
  () => canEdit.value || canManagePermissions.value || canLinkEmployees.value || canDelete.value,
)
/** 删除确认里的名词：只有账号与角色开放删除，其余资源回落到"记录"。 */
const recordNoun = computed(() => ({ users: '用户', roles: '角色' })[props.resource.key] ?? '记录')
const deleting = ref<Row>()
const deleteBusy = ref(false)
const deleteError = ref('')
/** 内置 admin 账号、ADMIN 角色和当前登录账号都不允许删除，按钮直接禁用。 */
function protectedRow(row: Row): boolean {
  if (props.resource.key === 'roles') return isProtectedRole(row.code)
  if (props.resource.key === 'users') return isProtectedUser(row, auth.user?.id)
  return false
}
function protectedReason(row: Row): string {
  if (props.resource.key === 'roles') return 'ADMIN 角色不可删除'
  return row.id === auth.user?.id ? '不能删除当前登录账号' : 'admin 账号不可删除'
}
function recordLabel(row: Row): string {
  const column = props.resource.columns[1] ?? props.resource.columns[0]
  return String(row[column.key] ?? row[props.resource.id])
}
async function confirmDelete() {
  if (!deleting.value || !props.resource.updateTemplate) return
  deleteBusy.value = true
  deleteError.value = ''
  try {
    await api(fillTemplate(props.resource.updateTemplate, String(deleting.value[props.resource.id])), {
      method: 'DELETE',
    })
    notices.show(`${recordNoun.value}已删除`)
    deleting.value = undefined
    // 删掉的是当前页最后一条时先退页，由页码 watcher 触发刷新，避免 refresh 与页码钳制各发一次请求。
    if (props.resource.paginated && rows.value.length === 1 && page.value > 1) page.value -= 1
    else await refresh()
  } catch (cause) {
    deleteError.value = cause instanceof Error ? cause.message : '删除失败'
  } finally {
    deleteBusy.value = false
  }
}
const { data, loading, error, refresh } = useResource(async (signal) => {
  const result = await api<Row[] | Page<Row>>(
    `${props.resource.endpoint}${props.resource.paginated ? query({ limit: pageSize.value, offset: (page.value - 1) * pageSize.value }) : ''}`,
    { signal },
  )
  return Array.isArray(result) ? { items: result, total: result.length } : result
})
watch(
  () => props.resource.key,
  () => {
    page.value = 1
    search.value = ''
    void refresh()
  },
  { immediate: true },
)
watch([page, pageSize], () => {
  if (props.resource.paginated) void refresh()
})
watch(search, () => {
  page.value = 1
})
const filtered = computed(() => {
  const items = data.value?.items || []
  return search.value.trim()
    ? items.filter((row) =>
        props.resource.columns.some((column) =>
          String(row[column.key] ?? '')
            .toLowerCase()
            .includes(search.value.trim().toLowerCase()),
        ),
      )
    : items
})
const rows = computed(() =>
  props.resource.paginated
    ? filtered.value
    : filtered.value.slice((page.value - 1) * pageSize.value, page.value * pageSize.value),
)
const total = computed(() => (props.resource.paginated ? data.value?.total || 0 : filtered.value.length))
watch(total, (value) => {
  page.value = Math.min(page.value, Math.max(1, Math.ceil(value / pageSize.value)))
})
function setPageSize(value: number) {
  pageSize.value = value
  page.value = 1
}
function edit(row?: Row) {
  editing.value = row
  open.value = true
}
function saved() {
  open.value = false
  void refresh()
}
</script>
<template>
  <section>
    <header class="page-heading">
      <div>
        <p class="eyebrow">{{ resource.subtitle }}</p>
        <h1>
          {{ resource.title }}
          <el-tag v-if="data" type="info" effect="plain">{{ data.total }}</el-tag>
        </h1>
      </div>
      <div class="heading-actions">
        <el-button
          text
          class="icon-button outlined"
          aria-label="刷新列表"
          :disabled="loading"
          @click="refresh"
        >
          <RefreshCw :size="17" :class="{ spin: loading }" />
        </el-button>
        <el-button v-if="canCreate" type="primary" :icon="Plus" @click="edit()">
          {{ resource.key === 'users' ? '新增用户' : resource.key === 'roles' ? '新增角色' : '新增记录' }}
        </el-button>
      </div>
    </header>
    <el-tabs
      v-if="resource.key === 'emp-details'"
      model-value="details"
      @tab-change="(name) => name === 'directory' && router.push('/employees')"
    >
      <el-tab-pane v-if="auth.canPage('employees')" name="directory" label="员工名录" />
      <el-tab-pane name="details" label="详情视图" />
    </el-tabs>
    <el-alert
      v-if="resource.key === 'roles'"
      title="同一角色的用户共享页面与接口权限；修改角色权限会要求该角色下所有账号重新登录。"
      type="info"
      :closable="false"
      show-icon
      class="role-notice"
    />
    <section class="data-card">
      <div class="table-toolbar">
        <el-input
          v-if="!resource.paginated"
          v-model="search"
          class="search-control"
          aria-label="搜索当前列表"
          placeholder="搜索当前列表…"
          clearable
        >
          <template #prefix><Search :size="17" /></template>
        </el-input>
        <span v-else class="table-caption">{{ resource.key === 'users' ? '组织账号' : '员工关联档案' }}</span>
        <span class="table-caption">{{ canEdit || canCreate ? '全部记录' : '只读记录' }}</span>
      </div>
      <StateBlock
        :loading="loading"
        :error="error"
        :empty="!loading && !error && rows.length === 0"
        @retry="refresh"
      />
      <el-table v-if="!loading && !error && rows.length" :data="rows" class="data-table">
        <el-table-column
          v-for="column in resource.columns"
          :key="column.key"
          :prop="column.key"
          :label="column.label"
          :min-width="column.kind === 'id' ? 110 : 155"
          :align="column.kind === 'money' ? 'right' : 'left'"
        >
          <template #default="{ row }">
            <el-tag
              v-if="column.kind === 'status'"
              :type="row[column.key] ? 'success' : 'info'"
              effect="light"
              size="small"
            >
              {{ row[column.key] ? '已启用' : '已停用' }}
            </el-tag>
            <el-tag
              v-else-if="column.kind === 'role'"
              :type="row[column.key] === 'ADMIN' ? 'primary' : 'info'"
              effect="plain"
              size="small"
            >
              {{
                row[column.key] === 'ADMIN'
                  ? '管理员'
                  : row[column.key] === 'READER'
                    ? '普通用户'
                    : row[column.key]
              }}
            </el-tag>
            <span v-else-if="column.kind === 'money'">{{ money(row[column.key]) }}</span>
            <span v-else>{{ row[column.key] ?? '—' }}</span>
            <el-tag
              v-if="resource.key === 'users' && column.key === 'username' && row.id === auth.user?.id"
              type="success"
              size="small"
              class="self-label"
            >
              我
            </el-tag>
          </template>
        </el-table-column>
        <el-table-column
          v-if="hasActions"
          label="操作"
          :width="canDelete ? 150 : 125"
          align="right"
          fixed="right"
        >
          <template #default="{ row }">
            <div class="row-actions">
              <RouterLink v-if="canLinkEmployees" :to="`/employees?departmentId=${row.departmentId}`">
                <el-button link :icon="ArrowUpRight" aria-label="查看部门员工" title="查看部门员工" />
              </RouterLink>
              <el-button
                v-if="canEdit"
                link
                :icon="Pencil"
                :disabled="resource.key === 'roles' && isProtectedRole(row.code)"
                :aria-label="`编辑 ${row[resource.id]}`"
                title="编辑记录"
                @click="edit(row)"
              />
              <el-tooltip v-if="canDelete" :content="protectedRow(row) ? protectedReason(row) : '删除记录'">
                <span>
                  <el-button
                    link
                    type="danger"
                    :icon="Trash2"
                    :disabled="protectedRow(row)"
                    :aria-label="`删除 ${recordLabel(row)}`"
                    @click="deleting = row"
                  />
                </span>
              </el-tooltip>
              <el-tooltip
                v-if="canManagePermissions"
                :content="isProtectedRole(row.code) ? 'ADMIN 角色权限不可修改' : '配置角色的页面与接口权限'"
              >
                <span>
                  <el-button
                    link
                    :icon="ShieldCheck"
                    :disabled="isProtectedRole(row.code)"
                    :aria-label="`角色权限 ${row.code}`"
                    @click="permissionTarget = row"
                  />
                </span>
              </el-tooltip>
            </div>
          </template>
        </el-table-column>
      </el-table>
      <Pagination
        class="card-pagination"
        :page="page"
        :page-size="pageSize"
        :total="total"
        :disabled="loading"
        @update:page="page = $event"
        @update:page-size="setPageSize"
      />
    </section>
    <RecordDialog
      v-if="open && resource.fields"
      :title="`${editing ? '编辑' : '新增'}${resource.key === 'users' ? '用户' : resource.key === 'roles' ? '角色' : '记录'}`"
      :endpoint="resource.endpoint"
      :update-template="resource.updateTemplate"
      :fields="resource.fields"
      :original="editing"
      :id-key="resource.id"
      @close="open = false"
      @saved="saved"
    />
    <Modal v-if="deleting" :title="`删除${recordNoun}`" :busy="deleteBusy" @close="deleting = undefined">
      <p>
        确定删除「{{ recordLabel(deleting) }}」？该操作不可撤销。
        <template v-if="resource.key === 'roles'">仍在使用的角色需要先移除其下所有账号。</template>
      </p>
      <el-alert v-if="deleteError" :title="deleteError" type="error" :closable="false" show-icon />
      <template #footer>
        <el-button :disabled="deleteBusy" @click="deleting = undefined">取消</el-button>
        <el-button type="danger" :loading="deleteBusy" @click="confirmDelete">
          <Trash2 v-if="!deleteBusy" :size="16" />
          删除
        </el-button>
      </template>
    </Modal>
    <RolePermissionEditor
      v-if="permissionTarget"
      :role="permissionTarget"
      @close="permissionTarget = undefined"
      @saved="permissionTarget = undefined"
    />
  </section>
</template>

<style scoped>
/* 本组件样式：颜色只用 styles.css 里的语义 token。 */
.spin {
  animation: spin 0.8s linear infinite;
}

.data-card {
  background: var(--surface);
  border: 1px solid var(--border);
  border-radius: var(--card-radius);
  padding: 24px 26px;
  min-width: 0;
}

.data-card .table-toolbar {
  margin-bottom: 16px;
}

/* 分页与表格之间用一条分隔线收进同一张卡片。 */
.card-pagination {
  border-top: 1px solid var(--border);
}

.self-label {
  display: inline-flex;
  font-size: 10px;
  color: var(--green-text-light);
  background: var(--green-tint);
  border-radius: 3px;
  padding: 0 4px;
  margin-left: 7px;
}

.table-caption {
  font-size: 11px;
  color: var(--text-faint);
}

@media (max-width: 1200px) {
  .data-card {
    padding: 20px;
  }
}

@media (max-width: 900px) {
  .table-caption {
    font-size: 10px;
  }
}

@media (max-width: 680px) {
  .data-card {
    padding: 18px;
  }
}

.role-notice {
  margin-bottom: 22px;
}
</style>
