<script setup lang="ts">
/** 编辑角色共享的页面与接口授权；版本号用于阻止覆盖其他管理员的新配置。 */
import { computed, onMounted, ref } from 'vue'
import { LoaderCircle, RefreshCw, Save, ShieldCheck } from '@lucide/vue'
import Modal from './Modal.vue'
import StateBlock from './StateBlock.vue'
import { fillTemplate, rolesPaths } from '../api/paths'
import { api } from '../lib/api'
import { useNotices } from '../stores/notices'
import type { PermissionDefinition, RolePermissions, Row } from '../types'

const props = defineProps<{ role: Row }>()
const emit = defineEmits<{ close: []; saved: [] }>()
const notices = useNotices()
const catalog = ref<PermissionDefinition[]>([])
const current = ref<RolePermissions>()
const selected = ref<string[]>([])
const tab = ref<'PAGE' | 'API'>('PAGE')
const loading = ref(false)
const busy = ref(false)
const error = ref('')
const saveError = ref('')
const locked = computed(() => current.value?.protectedRole === true)
const groups = computed(() => {
  if (tab.value === 'PAGE')
    return [{ label: '页面访问', items: catalog.value.filter((item) => item.kind === 'PAGE') }]
  const map = new Map<string, PermissionDefinition[]>()
  for (const permission of catalog.value.filter((item) => item.kind === tab.value)) {
    const group = map.get(permission.group) || []
    group.push(permission)
    map.set(permission.group, group)
  }
  return [...map].map(([label, items]) => ({ label, items }))
})
const allowed = (permission: PermissionDefinition) =>
  !permission.adminOnly || current.value?.role.code === 'ADMIN'
const pages = computed(() => selected.value.filter((code) => code.startsWith('page:')).length)
const apis = computed(() => selected.value.filter((code) => code.startsWith('api:')).length)
async function load() {
  loading.value = true
  error.value = ''
  saveError.value = ''
  try {
    const [definitions, profile] = await Promise.all([
      api<PermissionDefinition[]>(rolesPaths.catalog),
      api<RolePermissions>(fillTemplate(rolesPaths.permissions, String(props.role.code))),
    ])
    catalog.value = definitions
    current.value = profile
    selected.value = [...profile.permissions]
  } catch (cause) {
    error.value = cause instanceof Error ? cause.message : '权限加载失败'
  } finally {
    loading.value = false
  }
}
function selectAll() {
  const eligible = catalog.value
    .filter((item) => item.kind === tab.value && allowed(item))
    .map((item) => item.code)
  selected.value = [...new Set([...selected.value, ...eligible])]
}
function clearTab() {
  selected.value = selected.value.filter(
    (code) => !catalog.value.some((item) => item.kind === tab.value && item.code === code),
  )
}
async function save() {
  if (!current.value || locked.value) return
  busy.value = true
  saveError.value = ''
  try {
    await api(fillTemplate(rolesPaths.permissions, String(props.role.code)), {
      method: 'PUT',
      body: {
        revision: current.value.revision,
        permissions: selected.value,
      },
    })
    notices.show('角色权限已更新，该角色下的账号需重新登录')
    emit('saved')
  } catch (cause) {
    saveError.value = cause instanceof Error ? cause.message : '权限保存失败'
  } finally {
    busy.value = false
  }
}
onMounted(load)
</script>
<template>
  <Modal :title="`角色权限 · ${role.name}`" wide :busy="busy" @close="emit('close')">
    <div class="modal-body">
      <StateBlock :loading="loading" :error="error" @retry="load" />
      <template v-if="current && !loading && !error">
        <div class="permission-summary">
          <span class="badge admin role-chip">
            <ShieldCheck :size="13" />
            {{ current.role.code }}
          </span>
          <span class="summary-stats">{{ pages }} 个页面 · {{ apis }} 个接口</span>
          <span class="summary-revision">版本 {{ current.revision }}</span>
        </div>
        <p class="permission-impact">保存后将同时影响此角色下的所有账号，并要求它们重新登录。</p>
        <el-alert v-if="locked" title="ADMIN 角色保留全部权限，不允许修改。" type="info" :closable="false" />
        <div class="permission-toolbar">
          <el-tabs v-model="tab" aria-label="权限类型">
            <el-tab-pane label="页面权限" name="PAGE" />
            <el-tab-pane label="接口权限" name="API" />
          </el-tabs>
          <div class="push-right permission-actions">
            <el-button text class="text-button" :disabled="busy || locked" @click="selectAll">全选</el-button>
            <el-button text class="text-button" :disabled="busy || locked" @click="clearTab">清空</el-button>
          </div>
        </div>
        <div class="permission-list" role="tabpanel">
          <el-checkbox-group v-model="selected" :disabled="busy || locked">
            <fieldset v-for="group in groups" :key="group.label" class="permission-group">
              <legend>{{ group.label }}</legend>
              <el-checkbox
                v-for="permission in group.items"
                :key="permission.code"
                class="permission-item"
                :value="permission.code"
                :disabled="busy || locked || !allowed(permission)"
              >
                <span class="permission-text">
                  <strong>{{ permission.label }}</strong>
                  <code>{{ permission.method ? `${permission.method} ` : '' }}{{ permission.path }}</code>
                </span>
                <span v-if="permission.adminOnly" class="badge neutral">仅 ADMIN</span>
              </el-checkbox>
            </fieldset>
          </el-checkbox-group>
        </div>
        <p v-if="saveError" class="save-error" role="alert">
          {{ saveError }}
          <el-button text class="text-button" :disabled="busy" @click="load">
            <RefreshCw :size="13" />
            重新加载
          </el-button>
        </p>
      </template>
    </div>
    <footer class="modal-footer">
      <el-button :disabled="busy" @click="emit('close')">取消</el-button>
      <el-button type="primary" :disabled="busy || loading || !!error || !current || locked" @click="save">
        <LoaderCircle v-if="busy" :size="16" class="spin" />
        <Save v-else :size="16" />
        保存权限
      </el-button>
    </footer>
  </Modal>
</template>

<style scoped>
.permission-summary {
  display: flex;
  align-items: center;
  flex-wrap: wrap;
  gap: 12px 16px;
  padding-bottom: 15px;
  border-bottom: 1px solid var(--border);
  color: var(--text-muted);
  font-size: 13px;
}

.role-chip {
  flex-shrink: 0;
}

.summary-stats {
  font-variant-numeric: tabular-nums;
}

.summary-revision {
  margin-left: auto;
  color: var(--text-muted);
  font-size: 12px;
  font-variant-numeric: tabular-nums;
}

.permission-impact {
  margin: 15px 0;
  color: var(--text-muted);
  font-size: 12px;
  line-height: 1.7;
}

.permission-toolbar {
  display: flex;
  align-items: center;
  gap: 14px;
  margin-bottom: 14px;
}

.permission-toolbar :deep(.el-tabs) {
  flex: 1;
  min-width: 0;
}

.permission-toolbar :deep(.el-tabs__header) {
  margin-bottom: 0;
}

.permission-actions {
  display: flex;
  gap: 6px;
}

.permission-list {
  max-height: 46dvh;
  padding: 0 12px;
  overflow-y: auto;
  border: 1px solid var(--border);
  border-radius: 6px;
  background: var(--surface);
}

.permission-group {
  min-width: 0;
  margin: 0;
  padding: 8px 0 12px;
  border: 0;
}

.permission-group:not(:last-child) {
  border-bottom: 1px solid var(--border-soft);
}

.permission-group legend {
  padding: 12px 2px 6px;
  color: var(--text-muted);
  font-size: 11px;
  font-weight: 600;
}

.permission-item.el-checkbox {
  display: flex;
  align-items: center;
  width: 100%;
  height: auto;
  margin: 0;
  padding: 10px;
  border-radius: 5px;
  white-space: normal;
}

.permission-item.el-checkbox:hover {
  background: var(--surface-hover);
}

.permission-item.el-checkbox.is-checked {
  background: var(--accent-soft-hover);
}

.permission-item :deep(.el-checkbox__label) {
  display: flex;
  align-items: center;
  gap: 12px;
  flex: 1;
  min-width: 0;
  white-space: normal;
}

.permission-text {
  flex: 1;
  min-width: 0;
}

.permission-text strong {
  display: block;
  color: var(--text);
  font-size: 13px;
  font-weight: 550;
}

.permission-item.is-checked .permission-text strong {
  color: var(--accent-text);
}

.permission-text code {
  display: block;
  margin-top: 4px;
  color: var(--text-muted);
  font-size: 11px;
  white-space: normal;
  overflow-wrap: anywhere;
}

.permission-item.is-disabled .permission-text strong {
  color: var(--text-dim);
}

.permission-item.is-disabled .permission-text code {
  color: var(--text-pale);
}

.save-error {
  margin: 16px 0 0;
  padding: 12px 14px;
  border: 1px solid var(--danger-soft);
  border-radius: var(--control-radius);
  background: var(--danger-tint);
  color: var(--danger);
  font-size: 13px;
  line-height: 1.7;
  overflow-wrap: anywhere;
}

.save-error .text-button {
  margin-left: 8px;
}
</style>
