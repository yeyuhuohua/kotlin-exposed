<script setup lang="ts">
/** 概览与员工分布均来源于实际接口；缺少员工列表权限时不计算该列表的统计。 */
import { computed, onMounted } from 'vue'
import { RouterLink } from 'vue-router'
import { useAuth } from '../stores/auth'
import {
  ArrowDownRight,
  ArrowRight,
  BriefcaseBusiness,
  Building2,
  CalendarDays,
  MapPin,
  RefreshCw,
  Users,
} from '@lucide/vue'
import { api, query } from '../lib/api'
import { fullName, initials, integer, money } from '../lib/format'
import { useResource } from '../composables/useResource'
import DepartmentChart from '../components/DepartmentChart.vue'
import StateBlock from '../components/StateBlock.vue'
import type { Employee, Overview, Page } from '../types'
const auth = useAuth()
const canDetail = computed(
  () =>
    auth.canPage('employees') &&
    (auth.canApi('GET', '/employees/{id}') || auth.canApi('GET', '/employees/{id}/details')),
)
const { data, loading, error, refresh } = useResource(async (signal) => {
  const hasRoster = auth.canApi('GET', '/employees')
  const [overview, first] = await Promise.all([
    api<Overview>('/overview', { signal }),
    hasRoster
      ? api<Page<Employee>>('/employees?limit=200', { signal })
      : Promise.resolve({ total: 0, items: [] as Employee[] }),
  ])
  const employees = [...first.items]
  for (let offset = 200; offset < first.total; offset += 200) {
    const next = await api<Page<Employee>>(`/employees${query({ limit: 200, offset })}`, { signal })
    employees.push(...next.items)
  }
  return { overview, employees, hasRoster }
})
onMounted(refresh)
const today = new Intl.DateTimeFormat('zh-CN', {
  year: 'numeric',
  month: 'long',
  day: 'numeric',
  weekday: 'long',
}).format(new Date())
const metrics = computed(() =>
  data.value
    ? [
        {
          label: '员工总数',
          value: data.value.overview.employeeTotal,
          unit: '人',
          icon: Users,
          color: 'green',
          route: '/employees',
          foot: '员工档案',
        },
        {
          label: '组织部门',
          value: data.value.overview.departmentCount,
          unit: '个',
          icon: Building2,
          color: 'blue',
          route: '/departments',
          foot: '组织架构',
        },
        {
          label: '在册岗位',
          value: data.value.overview.jobCount,
          unit: '个',
          icon: BriefcaseBusiness,
          color: 'amber',
          route: '/jobs',
          foot: '岗位目录',
        },
        {
          label: '办公地点',
          value: data.value.overview.locationCount,
          unit: '处',
          icon: MapPin,
          color: 'violet',
          route: '/locations',
          foot: `${data.value.overview.regionCount} 个区域`,
        },
      ]
    : [],
)
const departments = computed(() => {
  if (!data.value) return []
  const counts = new Map<number | null, number>()
  data.value.employees.forEach((employee) =>
    counts.set(employee.departmentId, (counts.get(employee.departmentId) || 0) + 1),
  )
  return [...counts]
    .map(([id, count]) => ({
      id,
      count,
      label:
        data.value!.overview.departments.find((department) => department.departmentId === id)
          ?.departmentName || '未分配部门',
    }))
    .sort((a, b) => b.count - a.count)
})
const distribution = computed(() => [
  ...departments.value.slice(0, 5),
  ...(departments.value.length > 5
    ? [{ label: '其他部门', count: departments.value.slice(5).reduce((sum, item) => sum + item.count, 0) }]
    : []),
])
const averageSalary = computed(() => {
  const salaries =
    data.value?.employees
      .map((employee) => employee.salary)
      .filter((salary): salary is number => salary !== null) || []
  return salaries.length ? salaries.reduce((sum, salary) => sum + salary, 0) / salaries.length : null
})
function departmentName(id: number | null) {
  return (
    data.value?.overview.departments.find((department) => department.departmentId === id)?.departmentName ||
    '未分配'
  )
}
</script>
<template>
  <section>
    <header class="page-heading">
      <div>
        <p class="eyebrow">WORKSPACE OVERVIEW</p>
        <h1>
          工作概览
          <span class="heading-dot"></span>
        </h1>
        <p class="page-subtitle">人力资源 · 组织运营</p>
      </div>
      <div class="heading-actions">
        <span class="date-label">
          <CalendarDays :size="16" />
          {{ today }}
        </span>
        <el-button
          text
          class="icon-button outlined"
          title="刷新概览"
          aria-label="刷新概览"
          :disabled="loading"
          @click="refresh"
        >
          <RefreshCw :size="17" :class="{ spin: loading }" />
        </el-button>
      </div>
    </header>
    <StateBlock :loading="loading" :error="error" @retry="refresh" />
    <template v-if="data && !loading && !error">
      <div class="metrics-grid">
        <component
          :is="auth.canPage(metric.route) ? RouterLink : 'div'"
          v-for="metric in metrics"
          :key="metric.label"
          :to="metric.route"
          class="metric"
        >
          <div class="metric-label">
            {{ metric.label }}
            <span :class="['metric-icon', metric.color]"><component :is="metric.icon" :size="18" /></span>
          </div>
          <div class="metric-value">
            {{ integer(metric.value) }}
            <span>{{ metric.unit }}</span>
          </div>
          <div class="metric-foot">
            {{ metric.foot }}
            <ArrowDownRight :size="16" />
          </div>
        </component>
      </div>
      <div v-if="data.hasRoster" class="analytics-grid">
        <section class="analytics-section">
          <header class="section-heading">
            <div>
              <h2>部门人员分布</h2>
              <span>EMPLOYEES BY DEPARTMENT</span>
            </div>
            <RouterLink v-if="auth.canPage('departments')" to="/departments" class="text-button">
              全部部门
              <ArrowRight :size="14" />
            </RouterLink>
          </header>
          <div v-if="departments.length" class="bar-chart">
            <div
              v-for="(department, index) in departments.slice(0, 6)"
              :key="String(department.id)"
              class="bar-row"
            >
              <div>
                <span class="rank">{{ String(index + 1).padStart(2, '0') }}</span>
                <component
                  :is="auth.canPage('employees') ? RouterLink : 'span'"
                  :to="department.id === null ? '/employees' : `/employees?departmentId=${department.id}`"
                >
                  {{ department.label }}
                </component>
                <strong>
                  {{ department.count }}
                  <small>人</small>
                </strong>
              </div>
              <div class="bar-track">
                <div :style="{ width: `${(department.count / departments[0]!.count) * 100}%` }"></div>
              </div>
            </div>
          </div>
          <StateBlock v-else empty />
        </section>
        <section class="analytics-section composition">
          <header class="section-heading">
            <div>
              <h2>组织构成</h2>
              <span>TEAM COMPOSITION</span>
            </div>
            <span class="badge neutral">
              {{ departments.filter((department) => department.id !== null).length }} 个在岗部门
            </span>
          </header>
          <DepartmentChart
            v-if="data.employees.length"
            :entries="distribution"
            :total="data.employees.length"
          />
          <StateBlock v-else empty />
          <div class="salary-summary">
            <span>平均月薪</span>
            <strong>{{ money(averageSalary) }}</strong>
          </div>
        </section>
      </div>
      <section class="data-section">
        <header class="section-heading">
          <div>
            <h2>员工速览</h2>
            <span>EMPLOYEE DIRECTORY</span>
          </div>
          <RouterLink v-if="auth.canPage('employees')" to="/employees" class="text-button">
            查看全部
            <ArrowRight :size="15" />
          </RouterLink>
        </header>
        <el-table :data="data.overview.sampleEmployees" row-key="employeeId" class="data-table">
          <el-table-column label="员工" min-width="240">
            <template #default="{ row }">
              <component
                :is="canDetail ? RouterLink : 'span'"
                :to="`/employees?employeeId=${row.employeeId}`"
                class="person"
              >
                <span class="avatar" :class="`tone-${row.employeeId % 4}`">
                  {{ initials(fullName(row as Employee)) }}
                </span>
                <span>
                  <strong>{{ fullName(row as Employee) }}</strong>
                  <small>#{{ row.employeeId }}</small>
                </span>
              </component>
            </template>
          </el-table-column>
          <el-table-column label="部门" min-width="160">
            <template #default="{ row }">{{ departmentName(row.departmentId) }}</template>
          </el-table-column>
          <el-table-column label="岗位编码" min-width="130">
            <template #default="{ row }">
              <el-tag type="info" size="small" effect="plain">{{ row.jobId }}</el-tag>
            </template>
          </el-table-column>
          <el-table-column prop="hireDate" label="入职日期" width="130" />
          <el-table-column label="月薪" width="120" align="right">
            <template #default="{ row }">{{ money(row.salary) }}</template>
          </el-table-column>
          <el-table-column v-if="canDetail" width="65" fixed="right" align="right">
            <template #default="{ row }">
              <RouterLink :to="`/employees?employeeId=${row.employeeId}`">
                <el-button link :icon="ArrowRight" aria-label="查看员工" />
              </RouterLink>
            </template>
          </el-table-column>
        </el-table>
      </section>
    </template>
  </section>
</template>
