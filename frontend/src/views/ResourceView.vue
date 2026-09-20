<script setup lang="ts">
/** 复用业务目录页面，区分服务端分页与本地分页，并只暴露后端支持的管理操作。 */
import { computed, ref, watch } from 'vue'
import { ArrowUpRight, Pencil, Plus, RefreshCw, Search, ShieldCheck } from '@lucide/vue'
import { api, query } from '../lib/api'
import { money } from '../lib/format'
import { useResource } from '../composables/useResource'
import { useAuth } from '../stores/auth'
import Pagination from '../components/Pagination.vue'
import StateBlock from '../components/StateBlock.vue'
import RecordDialog from '../components/RecordDialog.vue'
import RolePermissionEditor from '../components/RolePermissionEditor.vue'
import type { Page, Resource, Row } from '../types'
const props = defineProps<{ resource: Resource }>()
const auth = useAuth()
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
    auth.canApi('PUT', `${props.resource.endpoint}/${props.resource.key === 'roles' ? '{code}' : '{id}'}`),
)
const canManagePermissions = computed(
  () =>
    props.resource.key === 'roles' &&
    auth.isAdmin &&
    auth.canApi('GET', '/auth/permissions') &&
    auth.canApi('GET', '/auth/roles/{code}/permissions') &&
    auth.canApi('PUT', '/auth/roles/{code}/permissions'),
)
const canLinkEmployees = computed(() => props.resource.key === 'departments' && auth.canPage('employees'))
const hasActions = computed(() => canEdit.value || canManagePermissions.value || canLinkEmployees.value)
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
        <el-button :icon="RefreshCw" :loading="loading" aria-label="刷新列表" @click="refresh" />
        <el-button v-if="canCreate" type="primary" :icon="Plus" @click="edit()">
          {{ resource.key === 'users' ? '新增用户' : resource.key === 'roles' ? '新增角色' : '新增记录' }}
        </el-button>
      </div>
    </header>
    <el-alert
      v-if="resource.key === 'roles'"
      title="同一角色的用户共享页面与接口权限；修改角色权限会要求该角色下所有账号重新登录。"
      type="info"
      :closable="false"
      show-icon
      class="role-notice"
    />
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
      <el-table-column v-if="hasActions" label="操作" width="125" align="right" fixed="right">
        <template #default="{ row }">
          <div class="row-actions">
            <RouterLink v-if="canLinkEmployees" :to="`/employees?departmentId=${row.departmentId}`">
              <el-button link :icon="ArrowUpRight" aria-label="查看部门员工" title="查看部门员工" />
            </RouterLink>
            <el-button
              v-if="canEdit"
              link
              :icon="Pencil"
              :disabled="resource.key === 'roles' && row.code === 'ADMIN'"
              :aria-label="`编辑 ${row[resource.id]}`"
              title="编辑记录"
              @click="edit(row)"
            />
            <el-tooltip
              v-if="canManagePermissions"
              :content="row.code === 'ADMIN' ? 'ADMIN 角色权限不可修改' : '配置角色的页面与接口权限'"
            >
              <span>
                <el-button
                  link
                  :icon="ShieldCheck"
                  :disabled="row.code === 'ADMIN'"
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
      :page="page"
      :page-size="pageSize"
      :total="total"
      :disabled="loading"
      @update:page="page = $event"
      @update:page-size="setPageSize"
    />
    <RecordDialog
      v-if="open && resource.fields"
      :title="`${editing ? '编辑' : '新增'}${resource.key === 'users' ? '用户' : resource.key === 'roles' ? '角色' : '记录'}`"
      :endpoint="resource.endpoint"
      :fields="resource.fields"
      :original="editing"
      :id-key="resource.id"
      @close="open = false"
      @saved="saved"
    />
    <RolePermissionEditor
      v-if="permissionTarget"
      :role="permissionTarget"
      @close="permissionTarget = undefined"
      @saved="permissionTarget = undefined"
    />
  </section>
</template>
