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
          <el-tag type="primary" effect="plain" class="role-chip">
            <ShieldCheck :size="13" />
            {{ current.role.code }}
          </el-tag>
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
                <el-tag v-if="permission.adminOnly" type="info" size="small">仅 ADMIN</el-tag>
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
/* 本组件样式：颜色只用 styles.css 里的语义 token。 */
.permission-summary {
  display: flex;
  align-items: center;
  gap: 14px;
  flex-wrap: wrap;
  padding: 13px 16px;
  background: var(--surface-subtle);
  border: 1px solid var(--border-soft);
  border-radius: var(--control-radius);
  font-size: 13px;
  color: var(--text-muted);
}

.role-chip {
  flex-shrink: 0;
}

.summary-stats {
  font-variant-numeric: tabular-nums;
}

.summary-revision {
  margin-left: auto;
  font-size: 12px;
  color: var(--text-dim);
  font-variant-numeric: tabular-nums;
}

.permission-impact {
  margin: 16px 2px 18px;
  font-size: 12px;
  line-height: 1.7;
  color: var(--text-dim);
}

.permission-toolbar {
  display: flex;
  align-items: center;
  gap: 14px;
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
  overflow-y: auto;
  padding-right: 6px;
}

.permission-group {
  border: 0;
  margin: 0;
  padding: 8px 0 12px;
  min-width: 0;
}

.permission-group:not(:last-child) {
  border-bottom: 1px solid var(--border-soft);
}

.permission-group legend {
  padding: 12px 2px 6px;
  font-size: 11px;
  font-weight: 600;
  letter-spacing: 0.08em;
  text-transform: uppercase;
  color: var(--text-faint);
}

.permission-item.el-checkbox {
  display: flex;
  align-items: center;
  width: 100%;
  height: auto;
  margin: 0;
  padding: 9px 10px;
  border-radius: var(--control-radius);
  white-space: normal;
}

.permission-item.el-checkbox:hover {
  background: var(--surface-subtle);
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
  font-size: 13px;
  font-weight: 550;
  color: var(--text);
}

.permission-text code {
  display: block;
  margin-top: 4px;
  font-size: 11px;
  color: var(--text-faint);
  overflow-wrap: anywhere;
  white-space: normal;
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
  background: var(--danger-tint);
  color: var(--danger);
  border-radius: var(--control-radius);
  font-size: 13px;
  line-height: 1.7;
  overflow-wrap: anywhere;
}

.save-error .text-button {
  margin-left: 8px;
}

.spin {
  animation: spin 0.8s linear infinite;
}
</style>
