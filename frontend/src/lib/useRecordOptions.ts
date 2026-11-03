import { reactive, ref } from 'vue'
import type { Ref } from 'vue'
import { api } from './api'
import { lookupSources } from '../config/resources'
import type { Field, Option, Row } from '../types'

/** 关联查询未获授权时已由字段配置切换成编号输入，不为了填充下拉框越权请求接口。 */
export function useRecordOptions(fields: Ref<Field[]>, values: Row) {
  const options = reactive<Record<string, Option[]>>({})
  const loading = ref(false)
  const lookupError = ref('')

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
            // 当前值可能来自已停用角色等历史关联；保留选项，避免打开编辑框时误改。
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

  return { options, loading, lookupError, loadOptions }
}
