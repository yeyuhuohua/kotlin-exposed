<script setup lang="ts">
/** 审计日志页：登录记录与接口调用记录两个标签页，各自独立筛选与服务端分页。仅 ADMIN 可见。 */
import { ref, watch } from 'vue'
import { RefreshCw, Search } from '@lucide/vue'
import { auditPaths } from '../api/paths'
import { api, query } from '../lib/api'
import { useResource } from '../composables/useResource'
import DataPanel from '../components/DataPanel.vue'
import PageHeader from '../components/PageHeader.vue'
import Pagination from '../components/Pagination.vue'
import StateBlock from '../components/StateBlock.vue'
import type { ApiCallRecord, LoginRecord, Page } from '../types'

const tab = ref<'logins' | 'calls'>('logins')

// ── 登录记录 ────────────────────────────────────────────────
const loginPage = ref(1)
const loginPageSize = ref(20)
const loginUsername = ref('')
const loginSuccess = ref<'' | 'true' | 'false'>('')
const loginFilters = ref({ username: '', success: '' as '' | 'true' | 'false' })
const logins = useResource((signal) =>
  api<Page<LoginRecord>>(
    `${auditPaths.logins}${query({
      limit: loginPageSize.value,
      offset: (loginPage.value - 1) * loginPageSize.value,
      username: loginFilters.value.username || undefined,
      success: loginFilters.value.success || undefined,
    })}`,
    { signal },
  ),
)
function applyLoginFilters() {
  loginFilters.value = { username: loginUsername.value.trim(), success: loginSuccess.value }
  // 页码为 1 时 watch 不会触发，必须手动刷新
  if (loginPage.value === 1) void logins.refresh()
  else loginPage.value = 1
}

// ── 接口调用记录 ────────────────────────────────────────────
const callPage = ref(1)
const callPageSize = ref(20)
const callUsername = ref('')
const callMethod = ref('')
const callPath = ref('')
const callFilters = ref({ username: '', method: '', path: '' })
const calls = useResource((signal) =>
  api<Page<ApiCallRecord>>(
    `${auditPaths.apiCalls}${query({
      limit: callPageSize.value,
      offset: (callPage.value - 1) * callPageSize.value,
      username: callFilters.value.username || undefined,
      method: callFilters.value.method || undefined,
      path: callFilters.value.path || undefined,
    })}`,
    { signal },
  ),
)
function applyCallFilters() {
  callFilters.value = {
    username: callUsername.value.trim(),
    method: callMethod.value,
    path: callPath.value.trim(),
  }
  // 页码为 1 时 watch 不会触发，必须手动刷新
  if (callPage.value === 1) void calls.refresh()
  else callPage.value = 1
}
function setLoginPageSize(value: number) {
  loginPageSize.value = value
  loginPage.value = 1
}
function setCallPageSize(value: number) {
  callPageSize.value = value
  callPage.value = 1
}

const methodOptions = ['', 'GET', 'POST', 'PUT', 'PATCH', 'DELETE']
function statusClass(status: number): 'success' | 'warning' | 'error' {
  if (status < 300) return 'success'
  if (status < 500) return 'warning'
  return 'error'
}
function loginResultClass(success: boolean): 'success' | 'error' {
  return success ? 'success' : 'error'
}

// 切换标签页时按需首次加载；分页与刷新各自独立。
const loaded = { logins: false, calls: false }
function ensureLoad(target: 'logins' | 'calls') {
  if (loaded[target]) return
  loaded[target] = true
  void (target === 'logins' ? logins.refresh() : calls.refresh())
}
watch(tab, (target) => ensureLoad(target), { immediate: true })
watch([loginPage, loginPageSize], () => void logins.refresh())
watch([callPage, callPageSize], () => void calls.refresh())
</script>
<template>
  <section>
    <PageHeader title="审计日志" description="登录尝试与接口调用记录 · 仅管理员可见">
      <el-button
        :icon="RefreshCw"
        :loading="tab === 'logins' ? logins.loading.value : calls.loading.value"
        aria-label="刷新审计日志"
        @click="tab === 'logins' ? logins.refresh() : calls.refresh()"
      />
    </PageHeader>
    <el-tabs v-model="tab" class="audit-tabs">
      <el-tab-pane label="登录记录" name="logins" />
      <el-tab-pane label="接口调用记录" name="calls" />
    </el-tabs>

    <DataPanel v-show="tab === 'logins'">
      <template #toolbar>
        <div class="table-toolbar">
          <div class="filters">
            <el-input
              v-model="loginUsername"
              class="filter-input"
              placeholder="用户名模糊搜索"
              aria-label="按用户名过滤"
              clearable
              @keyup.enter="applyLoginFilters"
            >
              <template #prefix><Search :size="16" /></template>
            </el-input>
            <el-select
              v-model="loginSuccess"
              class="filter-select"
              placeholder="全部结果"
              aria-label="按登录结果过滤"
              @change="applyLoginFilters"
            >
              <el-option label="全部结果" value="" />
              <el-option label="仅成功" value="true" />
              <el-option label="仅失败" value="false" />
            </el-select>
            <el-button class="button secondary" @click="applyLoginFilters">查询</el-button>
          </div>
        </div>
      </template>
      <StateBlock :loading="logins.loading.value" :error="logins.error.value" @retry="logins.refresh" />
      <el-table
        v-if="!logins.loading.value && !logins.error.value && logins.data.value"
        :data="logins.data.value.items"
        class="data-table"
      >
        <el-table-column prop="createdAt" label="时间" min-width="170" />
        <el-table-column prop="username" label="用户名" min-width="120" />
        <el-table-column prop="ip" label="IP" min-width="120" />
        <el-table-column label="结果" width="90" align="center">
          <template #default="{ row }">
            <span :class="['badge', loginResultClass(row.success)]">
              {{ row.success ? '成功' : '失败' }}
            </span>
          </template>
        </el-table-column>
        <el-table-column label="失败原因" min-width="130">
          <template #default="{ row }">{{ row.errorCode ?? '—' }}</template>
        </el-table-column>
        <el-table-column label="User-Agent" min-width="220" show-overflow-tooltip>
          <template #default="{ row }">{{ row.userAgent ?? '—' }}</template>
        </el-table-column>
      </el-table>
      <template #footer>
        <Pagination
          v-if="logins.data.value"
          :page="loginPage"
          :page-size="loginPageSize"
          :total="logins.data.value.total"
          :disabled="logins.loading.value"
          @update:page="loginPage = $event"
          @update:page-size="setLoginPageSize"
        />
      </template>
    </DataPanel>

    <DataPanel v-show="tab === 'calls'">
      <template #toolbar>
        <div class="table-toolbar">
          <div class="filters">
            <el-input
              v-model="callUsername"
              class="filter-input"
              placeholder="用户名模糊搜索"
              aria-label="按用户名过滤"
              clearable
              @keyup.enter="applyCallFilters"
            >
              <template #prefix><Search :size="16" /></template>
            </el-input>
            <el-select
              v-model="callMethod"
              class="filter-select narrow"
              placeholder="全部方法"
              aria-label="按 HTTP 方法过滤"
              @change="applyCallFilters"
            >
              <el-option
                v-for="option in methodOptions"
                :key="option"
                :label="option || '全部方法'"
                :value="option"
              />
            </el-select>
            <el-input
              v-model="callPath"
              class="filter-input"
              placeholder="按路径前缀过滤，如 /api/employees"
              aria-label="按路径前缀过滤"
              clearable
              @keyup.enter="applyCallFilters"
            />
            <el-button class="button secondary" @click="applyCallFilters">查询</el-button>
          </div>
        </div>
      </template>
      <StateBlock :loading="calls.loading.value" :error="calls.error.value" @retry="calls.refresh" />
      <el-table
        v-if="!calls.loading.value && !calls.error.value && calls.data.value"
        :data="calls.data.value.items"
        class="data-table"
      >
        <el-table-column prop="createdAt" label="时间" min-width="170" />
        <el-table-column label="用户" min-width="110">
          <template #default="{ row }">{{ row.username ?? '—' }}</template>
        </el-table-column>
        <el-table-column label="方法" width="90" align="center">
          <template #default="{ row }">
            <span class="badge neutral">{{ row.method }}</span>
          </template>
        </el-table-column>
        <el-table-column label="路径" min-width="220" show-overflow-tooltip>
          <template #default="{ row }">
            {{ row.path }}{{ row.queryString ? `?${row.queryString}` : '' }}
          </template>
        </el-table-column>
        <el-table-column label="状态" width="90" align="center">
          <template #default="{ row }">
            <span :class="['badge', statusClass(row.statusCode)]">
              {{ row.statusCode }}
            </span>
          </template>
        </el-table-column>
        <el-table-column label="耗时" width="100" align="right">
          <template #default="{ row }">{{ row.durationMs }} ms</template>
        </el-table-column>
        <el-table-column prop="ip" label="IP" min-width="120" />
      </el-table>
      <template #footer>
        <Pagination
          v-if="calls.data.value"
          :page="callPage"
          :page-size="callPageSize"
          :total="calls.data.value.total"
          :disabled="calls.loading.value"
          @update:page="callPage = $event"
          @update:page-size="setCallPageSize"
        />
      </template>
    </DataPanel>
  </section>
</template>

<style scoped>
/* 本组件样式：颜色只用 styles.css 里的语义 token。 */
.audit-tabs {
  margin-bottom: 0;
}

.filters {
  display: flex;
  align-items: center;
  gap: 10px;
  flex-wrap: wrap;
}

.filter-input {
  width: 240px;
  max-width: 100%;
}

.filter-select {
  width: 140px;
}

.filter-select.narrow {
  width: 120px;
}

@media (max-width: 900px) {
  .filter-input {
    flex: 1;
    min-width: 180px;
  }
}

@media (max-width: 680px) {
  .filter-input,
  .filter-select,
  .filter-select.narrow {
    width: 100%;
  }
}
</style>
