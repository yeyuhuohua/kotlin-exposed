<script setup lang="ts">
/** 概览的分布与薪资统计来自服务端聚合，不再拉取全量员工；缺少员工列表权限时隐藏该区块。 */
import { computed, onMounted } from 'vue'
import { pagePath } from '../lib/pages'
import { RouterLink } from 'vue-router'
import { useAuth } from '../stores/auth'
import {
  ArrowRight,
  ArrowUpRight,
  BriefcaseBusiness,
  Building2,
  MapPin,
  RefreshCw,
  Users,
} from '@lucide/vue'
import { dashboardPaths, employeesPaths } from '../api/paths'
import { api } from '../lib/api'
import { fullName, initials, integer, money } from '../lib/format'
import { useResource } from '../composables/useResource'
import DataPanel from '../components/DataPanel.vue'
import DepartmentChart from '../components/DepartmentChart.vue'
import PageHeader from '../components/PageHeader.vue'
import StateBlock from '../components/StateBlock.vue'
import type { Employee, Overview } from '../types'
const auth = useAuth()
const departmentsTo = computed(() => pagePath(auth.pages, 'departments'))
const employeesTo = computed(() => pagePath(auth.pages, 'employees'))
const canDetail = computed(
  () =>
    auth.canPage('employees') &&
    !!employeesTo.value &&
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
const greeting = computed(() => {
  const hour = new Date().getHours()
  if (hour < 6) return '夜深了'
  if (hour < 12) return '早上好'
  if (hour < 14) return '中午好'
  if (hour < 18) return '下午好'
  return '晚上好'
})
const metrics = computed(() =>
  data.value
    ? [
        {
          label: '员工总数',
          value: data.value.overview.employeeTotal,
          unit: '人',
          icon: Users,
          color: 'accent',
          key: 'employees',
          route: pagePath(auth.pages, 'employees'),
          foot: '员工档案',
        },
        {
          label: '组织部门',
          value: data.value.overview.departmentCount,
          unit: '个',
          icon: Building2,
          color: 'info',
          key: 'departments',
          route: departmentsTo.value,
          foot: '组织架构',
        },
        {
          label: '在册岗位',
          value: data.value.overview.jobCount,
          unit: '个',
          icon: BriefcaseBusiness,
          color: 'amber',
          key: 'jobs',
          route: pagePath(auth.pages, 'jobs'),
          foot: '岗位目录',
        },
        {
          label: '办公地点',
          value: data.value.overview.locationCount,
          unit: '处',
          icon: MapPin,
          color: 'violet',
          key: 'locations',
          route: pagePath(auth.pages, 'locations'),
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
  <section class="dashboard-page">
    <PageHeader
      :title="`${greeting}，${auth.user?.username}`"
      :description="today"
      category="组织概览"
    >
      <el-button :icon="RefreshCw" :loading="loading" aria-label="刷新概览" @click="refresh">
        刷新数据
      </el-button>
    </PageHeader>
    <StateBlock :loading="loading" :error="error" @retry="refresh" />
    <template v-if="data && !loading && !error">
      <div class="metrics-grid">
        <component
          :is="auth.canPage(metric.key) && metric.route ? RouterLink : 'div'"
          v-for="metric in metrics"
          :key="metric.label"
          :to="metric.route ?? undefined"
          class="metric-card"
        >
          <div class="metric-top">
            <span :class="['metric-icon', metric.color]"><component :is="metric.icon" :size="18" /></span>
            <ArrowUpRight v-if="auth.canPage(metric.key) && metric.route" :size="15" class="metric-link" />
          </div>
          <div class="metric-label">{{ metric.label }}</div>
          <div class="metric-value">
            {{ integer(metric.value) }}
            <span>{{ metric.unit }}</span>
          </div>
          <div class="metric-foot">{{ metric.foot }}</div>
        </component>
      </div>
      <div v-if="data.hasRoster" class="analytics-grid">
        <DataPanel title="部门人员分布" subtitle="按部门在岗人数排序">
          <template #actions>
            <RouterLink v-if="auth.canPage('departments') && departmentsTo" :to="departmentsTo" class="text-button">
              全部部门
              <ArrowRight :size="14" />
            </RouterLink>
          </template>
          <div class="panel-content">
            <div v-if="departments.length" class="bar-chart">
              <div
                v-for="(department, index) in departments.slice(0, 6)"
                :key="String(department.id)"
                class="bar-row"
              >
                <div class="bar-label">
                  <span class="rank">{{ String(index + 1).padStart(2, '0') }}</span>
                  <component
                    :is="auth.canPage('employees') && employeesTo ? RouterLink : 'span'"
                    :to="department.id === null ? `${employeesTo}?departmentId=none` : `${employeesTo}?departmentId=${department.id}`"
                  >
                    {{ department.label }}
                  </component>
                  <strong>{{ department.count }}<small>人</small></strong>
                </div>
                <div class="bar-track">
                  <div :style="{ width: `${(department.count / departments[0]!.count) * 100}%` }"></div>
                </div>
              </div>
            </div>
            <StateBlock v-else empty />
          </div>
        </DataPanel>
        <DataPanel
          title="组织构成"
          subtitle="部门人数占比"
          :meta="`${departments.filter((department) => department.id !== null).length} 个部门`"
        >
          <div class="panel-content composition-content">
            <DepartmentChart v-if="employeeTotal" :entries="distribution" :total="employeeTotal" />
            <StateBlock v-else empty />
            <div class="salary-summary">
              <span>平均月薪</span>
              <strong>{{ money(averageSalary) }}</strong>
            </div>
          </div>
        </DataPanel>
      </div>
      <DataPanel title="员工速览" subtitle="组织成员摘要" class="employee-panel">
        <template #actions>
          <RouterLink v-if="auth.canPage('employees') && employeesTo" :to="employeesTo" class="text-button">
            查看全部
            <ArrowRight :size="15" />
          </RouterLink>
        </template>
        <el-table :data="data.overview.sampleEmployees" row-key="employeeId" class="data-table">
          <el-table-column label="员工" min-width="240">
            <template #default="{ row }">
              <component
                :is="canDetail ? RouterLink : 'span'"
                :to="`${employeesTo}?employeeId=${row.employeeId}`"
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
              <RouterLink v-if="employeesTo" :to="{ path: employeesTo, query: { employeeId: row.employeeId } }">
                <el-button link :icon="ArrowRight" aria-label="查看员工" />
              </RouterLink>
            </template>
          </el-table-column>
        </el-table>
      </DataPanel>
    </template>
  </section>
</template>

<style scoped>
.metrics-grid {
  display: grid;
  grid-template-columns: repeat(4, minmax(0, 1fr));
  gap: 16px;
  margin-bottom: 24px;
}

.metric-card {
  min-width: 0;
  padding: 20px;
  border: 1px solid var(--border);
  border-radius: var(--card-radius);
  background: var(--surface);
  box-shadow: var(--panel-shadow);
  transition:
    border-color 0.15s,
    background 0.15s;
}

.metric-card:hover {
  border-color: var(--border-accent-strong);
  background: var(--surface-subtle);
}

.metric-top {
  display: flex;
  align-items: center;
  justify-content: space-between;
}

.metric-icon {
  width: 34px;
  height: 34px;
  display: grid;
  place-items: center;
  border-radius: 9px;
}

.metric-icon.accent {
  background: var(--accent-tint);
  color: var(--accent-text);
}

.metric-icon.info {
  background: var(--info-soft);
  color: var(--info);
}

.metric-icon.amber {
  background: var(--amber-soft);
  color: var(--warn-strong);
}

.metric-icon.violet {
  background: var(--violet-soft);
  color: var(--violet);
}

.metric-link {
  color: var(--text-faint);
}

.metric-label {
  margin-top: 18px;
  color: var(--text-muted);
  font-size: 12px;
  font-weight: 550;
}

.metric-value {
  margin: 5px 0 8px;
  color: var(--text-strong);
  font-size: 30px;
  font-weight: 650;
  letter-spacing: -0.035em;
  font-variant-numeric: tabular-nums;
}

.metric-value > span {
  margin-left: 7px;
  color: var(--text-faint);
  font-size: 12px;
  font-weight: 400;
  letter-spacing: 0;
}

.metric-foot {
  color: var(--text-faint);
  font-size: 11px;
}

.analytics-grid {
  display: grid;
  grid-template-columns: minmax(0, 1.6fr) minmax(340px, 1fr);
  gap: 24px;
  margin-bottom: 24px;
}

.panel-content {
  padding: 4px 22px 22px;
}

.composition-content {
  padding-top: 0;
}

.bar-row + .bar-row {
  margin-top: 18px;
}

.bar-label {
  display: flex;
  align-items: center;
  gap: 11px;
  margin-bottom: 8px;
  font-size: 13px;
}

.rank {
  color: var(--text-faint);
  font-size: 10px;
  font-weight: 600;
  font-variant-numeric: tabular-nums;
}

.bar-label a {
  overflow: hidden;
  color: var(--text-nav);
  text-overflow: ellipsis;
  white-space: nowrap;
}

.bar-label a:hover {
  color: var(--accent-text);
}

.bar-label strong {
  margin-left: auto;
  color: var(--text-soft);
  font-size: 13px;
  font-weight: 550;
  font-variant-numeric: tabular-nums;
}

.bar-label strong small {
  margin-left: 5px;
  color: var(--text-faint);
  font-size: 11px;
  font-weight: 400;
}

.bar-track {
  height: 6px;
  margin-left: 24px;
  overflow: hidden;
  border-radius: 99px;
  background: var(--chart-track);
}

.bar-track > div {
  height: 100%;
  border-radius: 99px;
  background: var(--chart-4);
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
  align-items: center;
  justify-content: space-between;
  margin-top: 18px;
  padding-top: 16px;
  border-top: 1px solid var(--border);
  color: var(--text-muted);
  font-size: 12px;
}

.salary-summary strong {
  color: var(--accent-text);
  font-size: 20px;
  font-weight: 650;
  letter-spacing: -0.02em;
  font-variant-numeric: tabular-nums;
}

@media (min-width: 1500px) {
  .metric-card {
    padding: 22px;
  }

  .metric-value {
    font-size: 34px;
  }
}

@media (max-width: 1200px) {
  .metrics-grid,
  .analytics-grid {
    gap: 16px;
  }

  .analytics-grid {
    grid-template-columns: minmax(0, 1.35fr) minmax(310px, 1fr);
  }
}

@media (max-width: 1000px) {
  .analytics-grid {
    grid-template-columns: 1fr;
  }
}

@media (max-width: 900px) {
  .metrics-grid {
    grid-template-columns: repeat(2, minmax(0, 1fr));
  }
}

@media (max-width: 680px) {
  .metrics-grid {
    gap: 12px;
    margin-bottom: 20px;
  }

  .metric-card {
    padding: 16px;
  }

  .metric-label {
    margin-top: 14px;
  }

  .metric-value {
    font-size: 26px;
  }

  .panel-content {
    padding: 4px 16px 18px;
  }
}
</style>
