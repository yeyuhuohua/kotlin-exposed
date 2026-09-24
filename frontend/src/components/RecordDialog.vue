<script setup lang="ts">
import { computed, onMounted, reactive, ref } from 'vue'
import { Save } from '@lucide/vue'
import type { FormInstance, FormRules } from 'element-plus'
import Modal from './Modal.vue'
import { fillTemplate, itemUrl, rolesPaths, usersPaths } from '../api/paths'
import { api } from '../lib/api'
import { formPayload } from '../lib/format'
import { lookupSources } from '../config/resources'
import { useAuth } from '../stores/auth'
import { useNotices } from '../stores/notices'
import type { Field, Option, Row } from '../types'
const props = defineProps<{
  title: string
  endpoint: string
  /** 单条记录的路径模板（含 {id}/{code}），缺省时按 endpoint + 主键拼接。 */
  updateTemplate?: string
  fields: Field[]
  original?: Row
  idKey: string
  /** 后端该资源支持 PATCH（出现才改、null 即清空）时为 true。 */
  allowClear?: boolean
}>()
const emit = defineEmits<{ close: []; saved: [] }>()
/** 账号与角色资源的集合路径：这两类资源有额外的保护规则。 */
const accountCollections: string[] = [usersPaths.collection, rolesPaths.collection]
const auth = useAuth()
const notices = useNotices()
const values = reactive<Row>({})
const options = reactive<Record<string, Option[]>>({})
const busy = ref(false)
const loading = ref(false)
const error = ref('')
const lookupError = ref('')
const formRef = ref<FormInstance>()
const fields = computed(() =>
  props.fields
    .filter(
      (field) =>
        !(props.original && field.createOnly) &&
        !(accountCollections.includes(props.endpoint) && !props.original && field.key === 'enabled') &&
        !(
          props.endpoint === usersPaths.collection &&
          props.original?.username === 'admin' &&
          props.original.id !== auth.user?.id &&
          field.type === 'password'
        ),
    )
    .map((field) => {
      const source = field.lookup ? lookupSources[field.lookup] : undefined
      if (source && !auth.canApi('GET', source.endpoint)) {
        return {
          ...field,
          lookup: undefined,
          type: (['jobId', 'countryId', 'code'].includes(source.id) ? 'text' : 'number') as Field['type'],
        }
      }
      return field
    }),
)
for (const field of fields.value)
  values[field.key] = props.original?.[field.key] ?? field.default ?? (field.type === 'checkbox' ? false : '')
const self = computed(() => props.endpoint === usersPaths.collection && props.original?.id === auth.user?.id)
const disabledField = (field: Field) =>
  (self.value || (props.endpoint === usersPaths.collection && props.original?.username === 'admin')) &&
  ['enabled', 'roleCode'].includes(field.key)
/** 通用字段规则保持与后端约束一致；编辑时空密码表示不重置密码。 */
const rules = computed<FormRules>(() =>
  Object.fromEntries(
    fields.value.map((field) => [
      field.key,
      [
        {
          trigger: ['blur', 'change'],
          validator: (_rule: unknown, value: unknown, done: (error?: Error) => void) => {
            const empty =
              value === '' ||
              value === null ||
              value === undefined ||
              (typeof value === 'string' && !value.trim())
            if (empty)
              return done(
                field.required || (field.type === 'password' && !props.original)
                  ? new Error(`请填写${field.label}`)
                  : undefined,
              )
            if (field.type === 'number') {
              const number = Number(value)
              if (!Number.isFinite(number)) return done(new Error(`${field.label}必须是有效数字`))
              if (field.min !== undefined && number < field.min)
                return done(new Error(`${field.label}不能小于 ${field.min}`))
              if (field.max !== undefined && number > field.max)
                return done(new Error(`${field.label}不能大于 ${field.max}`))
              if ((!field.step || field.step === '1') && !Number.isInteger(number))
                return done(new Error(`${field.label}必须是整数`))
            } else if (typeof value === 'string') {
              if (field.minLength !== undefined && value.length < field.minLength)
                return done(new Error(`${field.label}至少 ${field.minLength} 个字符`))
              if (field.maxLength !== undefined && value.length > field.maxLength)
                return done(new Error(`${field.label}最多 ${field.maxLength} 个字符`))
              if (field.pattern && !field.pattern.test(value))
                return done(new Error(field.patternMessage || `${field.label}格式不正确`))
            }
            done()
          },
        },
      ],
    ]),
  ),
)
function numericValue(key: string): number | undefined {
  const value = values[key]
  return value === '' || value === null || value === undefined ? undefined : Number(value)
}
/** 关联查询未获授权时已切换成编号输入，不会为了填充下拉框越权请求接口。 */
async function loadOptions() {
  loading.value = true
  lookupError.value = ''
  try {
    await Promise.all(
      fields.value
        .filter((field) => field.lookup)
        .map(async (field) => {
          const source = lookupSources[field.lookup!]!
          const rows = await api<Row[]>(source.endpoint)
          options[field.key] = rows
            .filter((row) => field.lookup !== 'roles' || row.enabled)
            .map((row) => ({
              value: row[source.id] as string | number,
              label: `${row[source.label] ?? row[source.id]} · ${row[source.id]}`,
            }))
          const current = values[field.key]
          if (
            current !== '' &&
            current != null &&
            !options[field.key]!.some((option) => option.value === current)
          )
            options[field.key]!.push({ value: current as string | number, label: String(current) })
        }),
    )
  } catch (cause) {
    lookupError.value = cause instanceof Error ? cause.message : '关联数据加载失败'
  } finally {
    loading.value = false
  }
}
onMounted(loadOptions)
async function save() {
  if (busy.value || loading.value || lookupError.value) return
  if (!(await formRef.value?.validate().catch(() => false))) return
  error.value = ''
  busy.value = true
  try {
    const payload = formPayload(fields.value, values, props.original, { allowClear: props.allowClear })
    // 与原值合并后再校验：只改最低或最高一端时，另一端要取数据库里的现值。
    const merged = { ...props.original, ...payload }
    if (
      merged.minSalary != null &&
      merged.maxSalary != null &&
      Number(merged.minSalary) > Number(merged.maxSalary)
    )
      throw new Error('最低月薪不能大于最高月薪')
    const path = !props.original
      ? props.endpoint
      : props.updateTemplate
        ? fillTemplate(props.updateTemplate, String(props.original[props.idKey]))
        : itemUrl(props.endpoint, String(props.original[props.idKey]))
    // 只有真的要清空字段时才用 PATCH，其余情况保持原来的 PUT 语义，权限也不用扩大；
    // 没有 PUT 权限但被单独授予 PATCH 时，整单（只含变更字段）走 PATCH。
    const clearing = Object.values(payload).some((value) => value === null)
    const canPut = !props.updateTemplate || auth.canApi('PUT', props.updateTemplate)
    const method = props.original ? (canPut && !clearing ? 'PUT' : 'PATCH') : 'POST'
    await api(path, { method, body: payload })
    notices.show(props.original ? '修改已保存' : '记录已创建')
    if (self.value) {
      // 先给出原因再清会话：App 的 expired 处理在 user 为空时不会再弹提示。
      notices.show('账号信息已修改，请重新登录', true)
      auth.clear()
      window.dispatchEvent(new Event('hr:unauthorized'))
    }
    emit('saved')
  } catch (cause) {
    error.value = cause instanceof Error ? cause.message : '保存失败'
  } finally {
    busy.value = false
  }
}
</script>
<template>
  <Modal :title="title" :busy="busy" @close="emit('close')">
    <el-form ref="formRef" :model="values" :rules="rules" label-position="top" @submit.prevent="save">
      <div class="modal-body">
        <el-alert v-if="lookupError" :title="lookupError" type="error" :closable="false" show-icon>
          <el-button link type="primary" @click="loadOptions">重试</el-button>
        </el-alert>
        <div class="form-grid">
          <el-form-item
            v-for="field in fields"
            :key="field.key"
            :prop="field.key"
            :label="field.type === 'password' && original ? '重置密码' : field.label"
            :required="field.required || (field.type === 'password' && !original)"
          >
            <el-select
              v-if="field.type === 'select'"
              :model-value="values[field.key] as string | number"
              @update:model-value="values[field.key] = $event ?? ''"
              :aria-label="field.label"
              filterable
              clearable
              :disabled="loading || busy || disabledField(field)"
            >
              <el-option
                v-for="option in options[field.key] || field.options || []"
                :key="option.value"
                :value="option.value"
                :label="option.label"
              />
            </el-select>
            <el-switch
              v-else-if="field.type === 'checkbox'"
              :model-value="Boolean(values[field.key])"
              @update:model-value="values[field.key] = Boolean($event)"
              :aria-label="field.label"
              :disabled="busy || disabledField(field)"
            />
            <el-input-number
              v-else-if="field.type === 'number'"
              :model-value="numericValue(field.key)"
              @update:model-value="values[field.key] = $event ?? ''"
              :min="field.min"
              :max="field.max"
              :step="Number(field.step || 1)"
              :precision="field.step === '0.01' ? 2 : 0"
              :disabled="busy"
              :aria-label="field.label"
              controls-position="right"
            />
            <el-date-picker
              v-else-if="field.type === 'date'"
              :model-value="String(values[field.key] || '')"
              @update:model-value="values[field.key] = $event || ''"
              type="date"
              value-format="YYYY-MM-DD"
              format="YYYY-MM-DD"
              :disabled="busy"
              :aria-label="field.label"
            />
            <el-input
              v-else
              :model-value="String(values[field.key] ?? '')"
              @update:model-value="values[field.key] = $event"
              :type="field.type || 'text'"
              :maxlength="field.maxLength"
              :show-password="field.type === 'password'"
              :aria-label="field.label"
              :disabled="busy"
              :autocomplete="field.type === 'password' ? 'new-password' : 'off'"
              :placeholder="field.type === 'password' && original ? '保持不变' : undefined"
            />
          </el-form-item>
        </div>
        <el-alert v-if="error" :title="error" type="error" :closable="false" show-icon />
      </div>
      <footer class="modal-footer">
        <el-button :disabled="busy" @click="emit('close')">取消</el-button>
        <el-button
          native-type="submit"
          type="primary"
          :loading="busy"
          :disabled="loading || Boolean(lookupError)"
        >
          <Save v-if="!busy" :size="16" />
          保存
        </el-button>
      </footer>
    </el-form>
  </Modal>
</template>

<style scoped>
/* 本组件样式：颜色只用 styles.css 里的语义 token。 */
.form-grid {
  display: grid;
  grid-template-columns: repeat(2, minmax(0, 1fr));
  gap: 20px 18px;
  align-items: start;
}

.form-grid :deep(.el-form-item) {
  margin-bottom: 0;
  min-width: 0;
}

/* 字段数为奇数时最后一个占满整行，避免右侧空出一块。 */
.form-grid > :deep(.el-form-item:last-child:nth-child(odd)) {
  grid-column: 1 / -1;
}

/* 开关字段做成整行状态条：沉底面板衬底，标签在左、开关在右。 */
.form-grid > :deep(.el-form-item:has(.el-switch)) {
  grid-column: 1 / -1;
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 16px;
  padding: 13px 16px;
  background: var(--surface-subtle);
  border: 1px solid var(--border-soft);
  border-radius: var(--control-radius);
}

.form-grid > :deep(.el-form-item:has(.el-switch) .el-form-item__label) {
  margin-bottom: 0;
}

@media (max-width: 680px) {
  .form-grid {
    grid-template-columns: 1fr;
  }
}
</style>
