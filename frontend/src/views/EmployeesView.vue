<script setup lang="ts">
/** 员工列表使用服务端筛选和分页；关联下拉框与写操作分别检查接口权限。 */
import { computed, onMounted, ref, watch } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { Eye, Pencil, Plus, RefreshCw, Search, Trash2, X } from '@lucide/vue'
import { api, query } from '../lib/api'
import { fullName, initials, money } from '../lib/format'
import { employeeCreateFields, employeeUpdateFields } from '../config/resources'
import { useResource } from '../composables/useResource'
import { departmentsPaths, employeesPaths, fillTemplate, jobsPaths } from '../api/paths'
import { useAuth } from '../stores/auth'
import { useNotices } from '../stores/notices'
import StateBlock from '../components/StateBlock.vue'
import Pagination from '../components/Pagination.vue'
import RecordDialog from '../components/RecordDialog.vue'
import EmployeeDetail from '../components/EmployeeDetail.vue'
import Modal from '../components/Modal.vue'
import type { Department, Employee, Job, Page } from '../types'
const auth = useAuth()
const notices = useNotices()
const route = useRoute()
const router = useRouter()
const search = ref('')
const keyword = ref('')
const departmentId = ref(String(route.query.departmentId || ''))
const jobId = ref('')
const page = ref(1)
const pageSize = ref(10)
const departments = ref<Department[]>([])
const jobs = ref<Job[]>([])
const lookupError = ref('')
const formOpen = ref(false)
const editing = ref<Employee>()
const detailId = ref<number>()
const deleting = ref<Employee>()
const deleteError = ref('')
const deleteBusy = ref(false)
const { data, loading, error, refresh } = useResource((signal) =>
  api<Page<Employee>>(
    `${employeesPaths.collection}${query({ q: keyword.value, departmentId: departmentId.value, jobId: jobId.value, limit: pageSize.value, offset: (page.value - 1) * pageSize.value })}`,
    { signal },
  ),
)
const filtered = computed(() => Boolean(keyword.value || departmentId.value || jobId.value))
const canDetail = computed(
  () => auth.canApi('GET', employeesPaths.item) || auth.canApi('GET', employeesPaths.itemDetails),
)
// 清空可空字段走 PATCH，需要单独授权；没有该权限时编辑框仍可改值但不能清空。
const canClearFields = computed(() => auth.canApi('PATCH', employeesPaths.item))
watch([page, pageSize, keyword, departmentId, jobId], refresh, { immediate: true })
watch(
  () => data.value?.total,
  (total) => {
    if (total !== undefined) page.value = Math.min(page.value, Math.max(1, Math.ceil(total / pageSize.value)))
  },
)
watch(
  () => route.query.departmentId,
  (value) => {
    departmentId.value = String(value || '')
    page.value = 1
  },
)
watch(
  () => route.query.employeeId,
  (value) => {
    const id = Number(value)
    detailId.value = value && Number.isInteger(id) && id > 0 ? id : undefined
  },
  { immediate: true },
)
onMounted(async () => {
  try {
    ;[departments.value, jobs.value] = await Promise.all([
      auth.canApi('GET', departmentsPaths.collection)
        ? api<Department[]>(departmentsPaths.collection)
        : Promise.resolve([]),
      auth.canApi('GET', jobsPaths.collection) ? api<Job[]>(jobsPaths.collection) : Promise.resolve([]),
    ])
  } catch (cause) {
    lookupError.value = cause instanceof Error ? cause.message : '筛选选项加载失败'
  }
})
function applySearch() {
  const next = search.value.trim()
  const unchanged = keyword.value === next && page.value === 1
  keyword.value = next
  page.value = 1
  if (unchanged) void refresh()
}
function resetFilters() {
  search.value = ''
  keyword.value = ''
  departmentId.value = ''
  jobId.value = ''
  page.value = 1
  void router.replace({ query: {} })
}
function edit(employee?: Employee) {
  editing.value = employee
  formOpen.value = true
  detailId.value = undefined
}
function closeDetail() {
  detailId.value = undefined
  if (route.query.employeeId) void router.replace({ query: { ...route.query, employeeId: undefined } })
}
function saved() {
  formOpen.value = false
  void refresh()
}
function setPageSize(value: number) {
  pageSize.value = value
  page.value = 1
}
function confirmDelete(employee: Employee) {
  deleting.value = employee
  deleteError.value = ''
}
async function remove() {
  if (!deleting.value) return
  deleteBusy.value = true
  deleteError.value = ''
  try {
    await api(fillTemplate(employeesPaths.item, deleting.value.employeeId), { method: 'DELETE' })
    deleting.value = undefined
    notices.show('员工记录已删除')
    if (data.value?.items.length === 1 && page.value > 1) page.value--
    else await refresh()
  } catch (cause) {
    deleteError.value = cause instanceof Error ? cause.message : '删除失败'
  } finally {
    deleteBusy.value = false
  }
}
</script>
<template>
  <section>
    <header class="page-heading">
      <div>
        <p class="eyebrow">PEOPLE DIRECTORY</p>
        <h1>
          员工管理
          <el-tag v-if="data" type="info" effect="plain">{{ data.total }}</el-tag>
        </h1>
        <p class="page-subtitle">人事档案 · 组织成员</p>
      </div>
      <div class="heading-actions">
        <el-button :icon="RefreshCw" :loading="loading" aria-label="刷新员工" @click="refresh" />
        <el-button
          v-if="auth.canApi('POST', employeesPaths.collection)"
          type="primary"
          :icon="Plus"
          @click="edit()"
        >
          新增员工
        </el-button>
      </div>
    </header>
    <el-tabs
      model-value="directory"
      @tab-change="(name) => name === 'details' && router.push('/emp-details')"
    >
      <el-tab-pane name="directory" label="员工名录" />
      <el-tab-pane v-if="auth.canPage('emp-details')" name="details" label="详情视图" />
    </el-tabs>
    <section class="data-card">
      <div class="table-toolbar">
        <el-input
          v-model="search"
          class="search-control"
          aria-label="搜索员工"
          placeholder="搜索姓名或邮箱…"
          clearable
          @keyup.enter="applySearch"
          @clear="applySearch"
        >
          <template #prefix><Search :size="17" /></template>
          <template #append>
            <el-button :icon="Search" aria-label="执行搜索" @click="applySearch" />
          </template>
        </el-input>
        <div class="filters">
          <el-select
            v-model="departmentId"
            placeholder="全部部门"
            aria-label="部门筛选"
            clearable
            filterable
            @change="page = 1"
          >
            <el-option
              v-for="department in departments"
              :key="department.departmentId"
              :value="String(department.departmentId)"
              :label="department.departmentName"
            />
          </el-select>
          <el-select
            v-model="jobId"
            placeholder="全部岗位"
            aria-label="岗位筛选"
            clearable
            filterable
            @change="page = 1"
          >
            <el-option v-for="job in jobs" :key="job.jobId" :value="job.jobId" :label="job.jobTitle" />
          </el-select>
          <el-button v-if="filtered" :icon="X" aria-label="清除筛选" @click="resetFilters" />
        </div>
      </div>
      <el-alert
        v-if="lookupError"
        class="lookup-alert"
        :title="lookupError"
        type="error"
        :closable="false"
        show-icon
      />
      <StateBlock
        :loading="loading"
        :error="error"
        :empty="!loading && !error && data?.items.length === 0"
        @retry="refresh"
      />
      <el-table
        v-if="data?.items.length && !loading && !error"
        :data="data.items"
        row-key="employeeId"
        class="data-table"
      >
        <el-table-column label="员工" min-width="250">
          <template #default="{ row }">
            <el-button
              link
              class="person person-button"
              :disabled="!canDetail"
              @click="detailId = row.employeeId"
            >
              <span class="avatar" :class="`tone-${row.employeeId % 4}`">
                {{ initials(fullName(row as Employee)) }}
              </span>
              <span>
                <strong>{{ fullName(row as Employee) }}</strong>
                <small>{{ row.email }} · #{{ row.employeeId }}</small>
              </span>
            </el-button>
          </template>
        </el-table-column>
        <el-table-column label="部门" min-width="170">
          <template #default="{ row }">
            {{
              departments.find((item) => item.departmentId === row.departmentId)?.departmentName ||
              row.departmentId ||
              '未分配'
            }}
          </template>
        </el-table-column>
        <el-table-column label="岗位" min-width="190">
          <template #default="{ row }">
            <span class="job-name">
              {{ jobs.find((item) => item.jobId === row.jobId)?.jobTitle || row.jobId }}
            </span>
            <small class="cell-subtitle">{{ row.jobId }}</small>
          </template>
        </el-table-column>
        <el-table-column prop="hireDate" label="入职日期" width="125" />
        <el-table-column label="月薪" width="115" align="right">
          <template #default="{ row }">{{ money(row.salary) }}</template>
        </el-table-column>
        <el-table-column
          v-if="
            canDetail ||
            auth.canApi('PUT', employeesPaths.item) ||
            auth.canApi('PATCH', employeesPaths.item) ||
            auth.canApi('DELETE', employeesPaths.item)
          "
          label="操作"
          width="135"
          align="right"
          fixed="right"
        >
          <template #default="{ row }">
            <div class="row-actions">
              <el-tooltip v-if="canDetail" content="查看档案">
                <el-button
                  link
                  :icon="Eye"
                  :aria-label="`查看 ${fullName(row as Employee)}`"
                  @click="detailId = row.employeeId"
                />
              </el-tooltip>
              <el-tooltip
                v-if="auth.canApi('PUT', employeesPaths.item) || auth.canApi('PATCH', employeesPaths.item)"
                content="编辑员工"
              >
                <el-button
                  link
                  :icon="Pencil"
                  :aria-label="`编辑 ${fullName(row as Employee)}`"
                  @click="edit(row as Employee)"
                />
              </el-tooltip>
              <el-tooltip v-if="auth.canApi('DELETE', employeesPaths.item)" content="删除员工">
                <el-button
                  link
                  type="danger"
                  :icon="Trash2"
                  :aria-label="`删除 ${fullName(row as Employee)}`"
                  @click="confirmDelete(row as Employee)"
                />
              </el-tooltip>
            </div>
          </template>
        </el-table-column>
      </el-table>
      <Pagination
        :page="page"
        :page-size="pageSize"
        :total="data?.total || 0"
        :disabled="loading"
        @update:page="page = $event"
        @update:page-size="setPageSize"
      />
    </section>
    <RecordDialog
      v-if="formOpen"
      :title="editing ? '编辑员工' : '新增员工'"
      :endpoint="employeesPaths.collection"
      :update-template="employeesPaths.item"
      :fields="editing ? employeeUpdateFields : employeeCreateFields"
      :original="editing"
      :allow-clear="canClearFields"
      id-key="employeeId"
      @close="formOpen = false"
      @saved="saved"
    />
    <EmployeeDetail v-if="detailId && canDetail" :id="detailId" @close="closeDetail" @edit="edit" />
    <Modal v-if="deleting" title="删除员工" :busy="deleteBusy" @close="deleting = undefined">
      <div class="modal-body">
        <p>
          确定删除
          <strong>{{ fullName(deleting) }}</strong>
          （#{{ deleting.employeeId }}）？此操作无法撤销。
        </p>
        <el-alert v-if="deleteError" :title="deleteError" type="error" :closable="false" />
      </div>
      <footer class="modal-footer">
        <el-button :disabled="deleteBusy" @click="deleting = undefined">取消</el-button>
        <el-button type="danger" :loading="deleteBusy" :icon="Trash2" @click="remove">确认删除</el-button>
      </footer>
    </Modal>
  </section>
</template>

<style scoped>
/* 本组件样式：颜色只用 styles.css 里的语义 token。 */
.data-card {
  background: var(--surface);
  border: 1px solid var(--border);
  border-radius: var(--card-radius);
  padding: 24px 26px;
  min-width: 0;
}

.data-card > .table-toolbar {
  margin-bottom: 16px;
}

.lookup-alert {
  margin-bottom: 16px;
}

/* 分页收进卡片，与表格之间用一条分隔线断开。 */
.data-card :deep(.pagination) {
  margin-top: 18px;
  border-top: 1px solid var(--border);
  padding: 18px 2px 2px;
}

.person-button {
  border: 0;
  background: transparent;
  padding: 0;
  cursor: pointer;
}

.person-button:hover strong {
  color: var(--green);
}

.cell-subtitle {
  display: block;
  color: var(--text-faint);
  font-size: 11px;
  margin-top: 2px;
}

.job-name {
  white-space: nowrap;
}

.filters {
  display: flex;
  align-items: center;
  gap: 9px;
  min-width: 0;
}

.filters :deep(.el-select) {
  width: 180px;
}

.el-button.person-button {
  height: auto;
  padding: 0;
  margin: 0;
  text-align: left;
  /* 组件库按钮默认居中内容，会把头像推到列中间、和表头对不齐。 */
  justify-content: flex-start;
  width: 100%;
}

.el-button.person-button > span {
  display: flex;
  align-items: center;
  justify-content: flex-start;
  gap: 11px;
  flex: 1;
  min-width: 0;
}

@media (max-width: 1200px) {
  .data-card {
    padding: 20px;
  }
}

@media (max-width: 900px) {
  .filters {
    width: 100%;
  }
}

@media (max-width: 680px) {
  .data-card {
    padding: 16px;
  }
  .filters :deep(.el-select) {
    flex: 1;
    min-width: 0;
    width: 130px;
  }
}
</style>
