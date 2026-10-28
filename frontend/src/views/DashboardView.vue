<script setup lang="ts">
/** 概览的分布与薪资统计来自服务端聚合，不再拉取全量员工；缺少员工列表权限时隐藏该区块。 */
import { computed, onMounted } from 'vue'
import { pagePath } from '../lib/pages'
import { RouterLink } from 'vue-router'
import { useAuth } from '../stores/auth'
import { ArrowRight, ArrowUpRight, BriefcaseBusiness, Building2, MapPin, RefreshCw, Users } from '@lucide/vue'
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
          key: 'employees',
          route: pagePath(auth.pages, 'employees'),
          foot: '员工档案',
        },
        {
          label: '组织部门',
          value: data.value.overview.departmentCount,
          unit: '个',
          icon: Building2,
          key: 'departments',
          route: departmentsTo.value,
          foot: '组织架构',
        },
        {
          label: '在册岗位',
          value: data.value.overview.jobCount,
          unit: '个',
          icon: BriefcaseBusiness,
          key: 'jobs',
          route: pagePath(auth.pages, 'jobs'),
          foot: '岗位目录',
        },
        {
          label: '办公地点',
          value: data.value.overview.locationCount,
          unit: '处',
          icon: MapPin,
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
    <PageHeader title="工作概览" :description="`${greeting}，${auth.user?.username}。${today}`">
      <el-button :icon="RefreshCw" :loading="loading" aria-label="刷新概览" @click="refresh">
        刷新数据
      </el-button>
    </PageHeader>
    <StateBlock :loading="loading" :error="error" @retry="refresh" />
    <template v-if="data && !loading && !error">
      <div class="metrics-strip">
        <component
          :is="auth.canPage(metric.key) && metric.route ? RouterLink : 'div'"
          v-for="metric in metrics"
          :key="metric.label"
          :to="metric.route ?? undefined"
          class="metric-item"
        >
          <div class="metric-heading">
            <span>{{ metric.label }}</span>
            <component :is="metric.icon" :size="17" :stroke-width="1.6" />
          </div>
          <div class="metric-value">
            {{ integer(metric.value) }}
            <span>{{ metric.unit }}</span>
          </div>
          <div class="metric-foot">
            <span>{{ metric.foot }}</span>
            <ArrowUpRight v-if="auth.canPage(metric.key) && metric.route" :size="14" />
          </div>
        </component>
      </div>
      <div v-if="data.hasRoster" class="analytics-grid">
        <DataPanel title="部门人员分布" subtitle="在岗人数最多的部门">
          <template #actions>
            <RouterLink
              v-if="auth.canPage('departments') && departmentsTo"
              :to="departmentsTo"
              class="text-button"
            >
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
                    :to="
                      department.id === null
                        ? `${employeesTo}?departmentId=none`
                        : `${employeesTo}?departmentId=${department.id}`
                    "
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
            <ArrowRight :size="14" />
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
              <span class="job-code">{{ row.jobId }}</span>
            </template>
          </el-table-column>
          <el-table-column prop="hireDate" label="入职日期" width="130" />
          <el-table-column label="月薪" width="120" align="right">
            <template #default="{ row }">{{ money(row.salary) }}</template>
          </el-table-column>
          <el-table-column v-if="canDetail" width="65" fixed="right" align="right">
            <template #default="{ row }">
              <RouterLink
                v-if="employeesTo"
                :to="{ path: employeesTo, query: { employeeId: row.employeeId } }"
              >
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
.metrics-strip {
  display: grid;
  grid-template-columns: repeat(4, minmax(0, 1fr));
  margin-bottom: 24px;
  overflow: hidden;
  border: 1px solid var(--border);
  border-radius: var(--card-radius);
  background: var(--surface);
}

.metric-item {
  min-width: 0;
  padding: 22px 24px 20px;
  transition: background 0.15s;
}

.metric-item + .metric-item {
  border-left: 1px solid var(--border);
}

.metric-item:hover {
  background: var(--surface-subtle);
}

.metric-heading {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 12px;
  color: var(--text-muted);
  font-size: 12px;
}

.metric-heading svg {
  color: var(--text-dim);
}

.metric-value {
  margin: 15px 0 12px;
  color: var(--text-strong);
  font-size: 32px;
  font-weight: 600;
  line-height: 1.15;
  letter-spacing: -0.035em;
  font-variant-numeric: tabular-nums;
}

.metric-value > span {
  margin-left: 7px;
  color: var(--text-muted);
  font-size: 12px;
  font-weight: 400;
  letter-spacing: 0;
}

.metric-foot {
  display: flex;
  align-items: center;
  justify-content: space-between;
  color: var(--text-muted);
  font-size: 11px;
}

.metric-foot svg {
  color: var(--accent-text);
}

.analytics-grid {
  display: grid;
  grid-template-columns: minmax(0, 3fr) minmax(320px, 2fr);
  gap: 20px;
  margin-bottom: 24px;
}

.panel-content {
  padding: 8px 22px 22px;
}

.composition-content {
  padding-top: 0;
}

.bar-row + .bar-row {
  margin-top: 17px;
}

.bar-label {
  display: flex;
  align-items: center;
  gap: 11px;
  margin-bottom: 7px;
  font-size: 13px;
}

.rank {
  color: var(--text-muted);
  font-size: 10px;
  font-variant-numeric: tabular-nums;
}

.bar-label a {
  overflow: hidden;
  color: var(--text-body);
  text-overflow: ellipsis;
  white-space: nowrap;
}

.bar-label a:hover {
  color: var(--accent-text);
}

.bar-label strong {
  margin-left: auto;
  color: var(--text-body);
  font-size: 13px;
  font-weight: 550;
  font-variant-numeric: tabular-nums;
}

.bar-label strong small {
  margin-left: 5px;
  color: var(--text-muted);
  font-size: 11px;
  font-weight: 400;
}

.bar-track {
  height: 5px;
  margin-left: 26px;
  overflow: hidden;
  background: var(--chart-track);
}

.bar-track > div {
  height: 100%;
  background: var(--chart-2);
}

.bar-row:first-child .bar-track > div {
  background: var(--chart-1);
}

.bar-row:nth-child(3) .bar-track > div {
  background: var(--chart-3);
}

.salary-summary {
  display: flex;
  align-items: center;
  justify-content: space-between;
  margin-top: 16px;
  padding-top: 17px;
  border-top: 1px solid var(--border);
  color: var(--text-muted);
  font-size: 12px;
}

.salary-summary strong {
  color: var(--text-strong);
  font-size: 20px;
  font-weight: 600;
  letter-spacing: -0.02em;
  font-variant-numeric: tabular-nums;
}

.job-code {
  color: var(--text-muted);
  font-size: 12px;
  font-variant-numeric: tabular-nums;
}

@media (max-width: 1100px) {
  .metric-item {
    padding: 20px;
  }

  .analytics-grid {
    grid-template-columns: minmax(0, 1fr);
  }
}

@media (max-width: 760px) {
  .metrics-strip {
    grid-template-columns: repeat(2, minmax(0, 1fr));
  }

  .metric-item:nth-child(3) {
    border-left: 0;
  }

  .metric-item:nth-child(n + 3) {
    border-top: 1px solid var(--border);
  }
}

@media (max-width: 680px) {
  .metrics-strip {
    margin-bottom: 20px;
  }

  .metric-item {
    padding: 17px 16px;
  }

  .metric-value {
    margin: 12px 0 9px;
    font-size: 27px;
  }

  .analytics-grid {
    gap: 16px;
    margin-bottom: 20px;
  }

  .panel-content {
    padding: 6px 16px 18px;
  }
}
</style>
