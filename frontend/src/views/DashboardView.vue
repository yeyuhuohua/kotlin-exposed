<script setup lang="ts">
/** 概览的分布与薪资统计来自服务端聚合，不再拉取全量员工；缺少员工列表权限时隐藏该区块。 */
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
import { dashboardPaths, employeesPaths } from '../api/paths'
import { api } from '../lib/api'
import { fullName, initials, integer, money } from '../lib/format'
import { useResource } from '../composables/useResource'
import DepartmentChart from '../components/DepartmentChart.vue'
import StateBlock from '../components/StateBlock.vue'
import type { Employee, Overview } from '../types'
const auth = useAuth()
const canDetail = computed(
  () =>
    auth.canPage('employees') &&
    (auth.canApi('GET', employeesPaths.item) || auth.canApi('GET', employeesPaths.itemDetails)),
)
const { data, loading, error, refresh } = useResource(async (signal) => {
  const hasRoster = auth.canApi('GET', employeesPaths.collection)
  const overview = await api<Overview>(dashboardPaths.overview, { signal })
  return { overview, hasRoster }
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
// 部门人数分布由 /overview 在数据库里聚合好，顺序已按人数倒序。
const departments = computed(
  () =>
    data.value?.overview.departmentHeadcount.map((item) => ({
      id: item.departmentId,
      count: item.count,
      label: item.departmentName,
    })) || [],
)
const distribution = computed(() => [
  ...departments.value.slice(0, 5),
  ...(departments.value.length > 5
    ? [{ label: '其他部门', count: departments.value.slice(5).reduce((sum, item) => sum + item.count, 0) }]
    : []),
])
const averageSalary = computed(() => data.value?.overview.salarySummary.averageSalary ?? null)
const employeeTotal = computed(() => data.value?.overview.employeeTotal ?? 0)
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
          <DepartmentChart v-if="employeeTotal" :entries="distribution" :total="employeeTotal" />
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

<style scoped>
/* 本组件样式：颜色只用 styles.css 里的语义 token。 */
.heading-dot {
  width: 7px;
  height: 7px;
  border-radius: 50%;
  background: var(--green-light);
  margin-top: 3px;
}

.date-label {
  display: flex;
  align-items: center;
  gap: 8px;
  color: var(--text-muted);
  font-size: 10px;
}

.badge {
  display: inline-flex;
  align-items: center;
  gap: 5px;
  max-width: 100%;
  padding: 4px 7px;
  font-size: 10px;
  font-weight: 500;
  border-radius: 4px;
  line-height: 1.35;
  white-space: nowrap;
}

.badge.neutral {
  background: var(--surface-neutral);
  color: var(--text-muted);
}

.badge.success {
  background: var(--green-soft);
  color: var(--green-text);
}

.badge.admin {
  background: var(--info-soft);
  color: var(--info-deep);
}

.badge.error {
  background: var(--error-soft);
  color: var(--danger);
}

.metrics-grid {
  display: grid;
  grid-template-columns: repeat(4, minmax(0, 1fr));
  gap: 16px;
  margin-bottom: 30px;
}

.metric {
  background: var(--surface);
  border: 1px solid var(--border);
  border-radius: 7px;
  padding: 17px 19px 12px;
  min-width: 0;
  transition:
    border-color 0.15s,
    transform 0.15s;
}

.metric:hover {
  border-color: var(--border-green-strong);
  transform: translateY(-2px);
}

.metric-label {
  display: flex;
  justify-content: space-between;
  align-items: center;
  color: var(--text-muted);
  font-size: 11px;
}

.metric-icon {
  width: 30px;
  height: 30px;
  display: grid;
  place-items: center;
  border-radius: 5px;
}

.metric-icon.green {
  color: var(--green-text);
  background: var(--green-tint);
}

.metric-icon.blue {
  color: var(--info);
  background: var(--info-soft);
}

.metric-icon.amber {
  color: var(--warn-strong);
  background: var(--amber-soft);
}

.metric-icon.violet {
  color: var(--violet);
  background: var(--violet-soft);
}

.metric-value {
  font-size: 32px;
  font-weight: 600;
  margin: 9px 0 16px;
  font-variant-numeric: tabular-nums;
}

.metric-value > span {
  font-size: 11px;
  color: var(--text-faint);
  margin-left: 8px;
  font-weight: 400;
}

.metric-foot {
  border-top: 1px solid var(--border-soft);
  padding-top: 11px;
  color: var(--text-faint);
  font-size: 10px;
  display: flex;
  justify-content: space-between;
  align-items: center;
}

.metric-foot svg {
  color: var(--text-dim);
}

.analytics-grid {
  display: grid;
  grid-template-columns: minmax(0, 1.35fr) minmax(0, 1fr);
  gap: 33px;
  padding: 0 0 28px;
  border-bottom: 1px solid var(--border);
  margin-bottom: 25px;
}

.analytics-section {
  min-width: 0;
}

.analytics-section.composition {
  border-left: 1px solid var(--border);
  padding-left: 31px;
}

.section-heading {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 12px;
  margin-bottom: 22px;
}

.section-heading h2 {
  font-size: 14px;
}

.section-heading > div > span {
  display: block;
  color: var(--text-pale);
  font-size: 8px;
  margin-top: 4px;
}

.bar-chart {
  padding-top: 1px;
}

.bar-row {
  margin-bottom: 17px;
}

.bar-row > div:first-child {
  display: flex;
  align-items: center;
  font-size: 11px;
  margin-bottom: 8px;
  gap: 11px;
}

.rank {
  font-size: 9px;
  color: var(--text-faint);
}

.bar-row a {
  color: var(--text-nav);
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.bar-row strong {
  margin-left: auto;
  color: var(--text-soft);
  font-size: 11px;
  font-weight: 500;
}

.bar-row strong small {
  color: var(--text-faint);
  font-size: 9px;
  margin-left: 5px;
}

.bar-track {
  height: 7px;
  background: var(--chart-track);
  border-radius: 2px;
  overflow: hidden;
  margin-left: 23px;
}

.bar-track > div {
  height: 100%;
  background: var(--chart-4);
  border-radius: 2px;
}

.bar-row:first-child .bar-track > div {
  background: var(--chart-1);
}

.bar-row:nth-child(2) .bar-track > div {
  background: var(--chart-2);
}

.bar-row:nth-child(3) .bar-track > div {
  background: var(--chart-3);
}

.salary-summary {
  display: flex;
  justify-content: space-between;
  align-items: center;
  border-top: 1px solid var(--border);
  padding-top: 14px;
  margin-top: 17px;
  font-size: 11px;
  color: var(--text-dim);
}

.salary-summary small {
  font-size: 8px;
  color: var(--text-faint);
  margin-left: 5px;
}

.salary-summary strong {
  font-size: 19px;
  font-weight: 500;
  color: var(--green-deep);
}

.data-section {
  background: none;
}

@media (min-width: 1500px) {
  .metrics-grid {
    gap: 23px;
  }
}

@media (min-width: 1500px) {
  .metric {
    padding: 22px 25px 15px;
  }
}

@media (min-width: 1500px) {
  .analytics-grid {
    gap: 45px;
    padding-top: 7px;
  }
}

@media (min-width: 1500px) {
  .analytics-section.composition {
    padding-left: 45px;
  }
}

@media (min-width: 1500px) {
  .bar-row {
    margin-bottom: 21px;
  }
}

@media (min-width: 1500px) {
  .metric-value {
    font-size: 38px;
  }
}

@media (max-width: 1200px) {
  .salary-summary {
    margin-top: 12px;
  }
}

@media (max-width: 1200px) {
  .date-label {
    font-size: 9px;
  }
}

@media (max-width: 1200px) {
  .analytics-grid {
    gap: 23px;
  }
}

@media (max-width: 1200px) {
  .analytics-section.composition {
    padding-left: 23px;
  }
}

@media (max-width: 1200px) {
  .metric {
    padding: 14px;
  }
}

@media (max-width: 1200px) {
  .metrics-grid {
    gap: 12px;
  }
}

@media (max-width: 900px) {
  .date-label {
    display: none;
  }
}

@media (max-width: 900px) {
  .metrics-grid {
    grid-template-columns: repeat(2, minmax(0, 1fr));
  }
}

@media (max-width: 900px) {
  .analytics-grid {
    grid-template-columns: 1fr;
  }
}

@media (max-width: 900px) {
  .analytics-section.composition {
    border-left: 0;
    padding-left: 0;
    border-top: 1px solid var(--border);
    padding-top: 23px;
  }
}

@media (max-width: 680px) {
  .metric-value {
    font-size: 30px;
  }
}

@media (max-width: 680px) {
  .metric {
    padding: 14px;
  }
}

@media (max-width: 680px) {
  .metric-label {
    font-size: 10px;
  }
}

@media (max-width: 680px) {
  .metrics-grid {
    gap: 11px;
    margin-bottom: 26px;
  }
}

@media (max-width: 680px) {
  .metric-icon {
    width: 26px;
    height: 26px;
  }
}

@media (max-width: 680px) {
  .metric-foot {
    font-size: 9px;
  }
}

@media (max-width: 680px) {
  .section-heading h2 {
    font-size: 14px;
  }
}

@media (max-width: 680px) {
  .section-heading .text-button {
    font-size: 10px;
  }
}
</style>
