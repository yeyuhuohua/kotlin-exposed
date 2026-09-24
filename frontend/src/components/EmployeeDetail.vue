<script setup lang="ts">
/** 按已获接口权限组合基本档案与关联详情，不为缺失的授权发起额外查询。 */
import { computed, watch } from 'vue'
import { Building2, MapPin, Pencil } from '@lucide/vue'
import Modal from './Modal.vue'
import StateBlock from './StateBlock.vue'
import { employeesPaths, fillTemplate } from '../api/paths'
import { api } from '../lib/api'
import { useResource } from '../composables/useResource'
import { fullName, initials, money } from '../lib/format'
import { useAuth } from '../stores/auth'
import type { Employee, Row } from '../types'
const props = defineProps<{ id: number }>()
defineEmits<{ close: []; edit: [employee: Employee] }>()
const auth = useAuth()
const { data, loading, error, refresh } = useResource(async (signal) => {
  const [employee, detail] = await Promise.all([
    auth.canApi('GET', employeesPaths.item)
      ? api<Employee>(fillTemplate(employeesPaths.item, props.id), { signal })
      : Promise.resolve(null),
    auth.canApi('GET', employeesPaths.itemDetails)
      ? api<Employee>(fillTemplate(employeesPaths.itemDetails, props.id), { signal })
      : Promise.resolve(null),
  ])
  const record = employee || detail
  if (!record) throw new Error('没有员工档案接口的访问权限')
  return { employee: record, detail: (detail || {}) as Row, hasDetail: detail !== null }
})
// 弹窗复用组件实例，ID 变化必须重新加载；useResource 会取消过时的旧请求。
watch(() => props.id, refresh, { immediate: true })
/** 新数据未就位（或返回的不是当前 ID）时为 undefined，加载期间不暴露上一位员工。 */
const current = computed(() => (data.value?.employee.employeeId === props.id ? data.value : undefined))
const entries = computed(() =>
  current.value
    ? [
        ['员工编号', current.value.employee.employeeId],
        ['入职日期', current.value.employee.hireDate],
        ['邮箱账号', current.value.employee.email],
        ['联系电话', current.value.employee.phoneNumber],
        ['月薪', money(current.value.employee.salary)],
        [
          '提成比例',
          current.value.employee.commissionPct === null
            ? null
            : `${current.value.employee.commissionPct * 100}%`,
        ],
        ['直属经理编号', current.value.employee.managerId],
        ['国家', current.value.detail.countryName],
        ['区域', current.value.detail.regionName],
        ['州 / 省', current.value.detail.stateProvince],
      ]
    : [],
)
// 档案分组只改变展示结构，条目与顺序与上面的 entries 完全一致。
const groups = computed(() => [
  { title: '基本档案', items: entries.value.slice(0, 4) },
  { title: '薪酬与汇报', items: entries.value.slice(4, 7) },
  { title: '地域归属', items: entries.value.slice(7) },
])
</script>
<template>
  <Modal title="员工档案" @close="$emit('close')">
    <div class="modal-body">
      <StateBlock :loading="loading" :error="error" @retry="refresh" />
      <template v-if="current && !loading">
        <div class="profile-heading">
          <span class="avatar large" :class="`tone-${id % 4}`">
            {{ initials(fullName(current.employee)) }}
          </span>
          <div class="profile-title">
            <h2>{{ fullName(current.employee) }}</h2>
            <el-tag v-if="current.detail.jobTitle" class="job-chip" type="info" effect="plain">
              {{ current.detail.jobTitle }}
            </el-tag>
          </div>
        </div>
        <div v-if="current.hasDetail" class="profile-tags">
          <span>
            <Building2 :size="15" />
            {{ current.detail.departmentName || '未分配部门' }}
          </span>
          <span>
            <MapPin :size="15" />
            {{ current.detail.city || '未分配地点' }}
          </span>
        </div>
        <section v-for="group in groups" :key="group.title" class="profile-group">
          <h3 class="group-title">{{ group.title }}</h3>
          <dl class="detail-grid">
            <div v-for="[label, value] in group.items" :key="String(label)">
              <dt>{{ label }}</dt>
              <dd>{{ value ?? '—' }}</dd>
            </div>
          </dl>
        </section>
      </template>
    </div>
    <footer class="modal-footer">
      <el-button class="button secondary" @click="$emit('close')">关闭</el-button>
      <el-button
        type="primary"
        v-if="
          (auth.canApi('PUT', employeesPaths.item) || auth.canApi('PATCH', employeesPaths.item)) && current
        "
        class="button primary"
        @click="$emit('edit', current!.employee)"
      >
        <Pencil :size="15" />
        编辑资料
      </el-button>
    </footer>
  </Modal>
</template>

<style scoped>
/* 本组件样式：颜色只用 styles.css 里的语义 token。 */
.profile-title {
  min-width: 0;
}

.job-chip {
  margin-top: 4px;
}

.profile-tags {
  display: flex;
  gap: 10px;
  flex-wrap: wrap;
  margin-bottom: 22px;
}

.profile-tags > span {
  display: flex;
  align-items: center;
  gap: 7px;
  padding: 6px 12px;
  border-radius: 8px;
  background: var(--surface-sunken);
  font-size: 12px;
  color: var(--text-soft);
}

.profile-tags svg {
  color: var(--text-faint);
}

.profile-group {
  border-top: 1px solid var(--border);
  padding-top: 16px;
}

.profile-group + .profile-group {
  margin-top: 22px;
}

.group-title {
  font-size: 11px;
  font-weight: 600;
  letter-spacing: 0.08em;
  text-transform: uppercase;
  color: var(--text-faint);
}

.profile-group .detail-grid {
  border-top: 0;
  padding: 14px 0 4px;
}
</style>
