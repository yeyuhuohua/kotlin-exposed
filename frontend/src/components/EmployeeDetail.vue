<script setup lang="ts">
/** 按已获接口权限组合基本档案与关联详情，不为缺失的授权发起额外查询。 */
import { computed, onMounted } from 'vue'
import { Building2, MapPin, Pencil } from '@lucide/vue'
import Modal from './Modal.vue'
import StateBlock from './StateBlock.vue'
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
    auth.canApi('GET', '/employees/{id}')
      ? api<Employee>(`/employees/${props.id}`, { signal })
      : Promise.resolve(null),
    auth.canApi('GET', '/employees/{id}/details')
      ? api<Employee>(`/employees/${props.id}/details`, { signal })
      : Promise.resolve(null),
  ])
  const record = employee || detail
  if (!record) throw new Error('没有员工档案接口的访问权限')
  return { employee: record, detail: (detail || {}) as Row, hasDetail: detail !== null }
})
onMounted(refresh)
const entries = computed(() =>
  data.value
    ? [
        ['员工编号', data.value.employee.employeeId],
        ['入职日期', data.value.employee.hireDate],
        ['邮箱账号', data.value.employee.email],
        ['联系电话', data.value.employee.phoneNumber],
        ['月薪', money(data.value.employee.salary)],
        [
          '提成比例',
          data.value.employee.commissionPct === null ? null : `${data.value.employee.commissionPct * 100}%`,
        ],
        ['直属经理编号', data.value.employee.managerId],
        ['国家', data.value.detail.countryName],
        ['区域', data.value.detail.regionName],
        ['州 / 省', data.value.detail.stateProvince],
      ]
    : [],
)
</script>
<template>
  <Modal title="员工档案" @close="$emit('close')">
    <div class="modal-body">
      <StateBlock :loading="loading" :error="error" @retry="refresh" />
      <template v-if="data && !loading">
        <div class="profile-heading">
          <span class="avatar large" :class="`tone-${id % 4}`">{{ initials(fullName(data.employee)) }}</span>
          <div>
            <h2>{{ fullName(data.employee) }}</h2>
            <p>{{ data.detail.jobTitle }}</p>
          </div>
        </div>
        <div v-if="data.hasDetail" class="profile-tags">
          <span>
            <Building2 :size="15" />
            {{ data.detail.departmentName || '未分配部门' }}
          </span>
          <span>
            <MapPin :size="15" />
            {{ data.detail.city || '未分配地点' }}
          </span>
        </div>
        <dl class="detail-grid">
          <div v-for="[label, value] in entries" :key="String(label)">
            <dt>{{ label }}</dt>
            <dd>{{ value ?? '—' }}</dd>
          </div>
        </dl>
      </template>
    </div>
    <footer class="modal-footer">
      <el-button class="button secondary" @click="$emit('close')">关闭</el-button>
      <el-button
        type="primary"
        v-if="auth.canApi('PUT', '/employees/{id}') && data"
        class="button primary"
        @click="$emit('edit', data.employee)"
      >
        <Pencil :size="15" />
        编辑资料
      </el-button>
    </footer>
  </Modal>
</template>
