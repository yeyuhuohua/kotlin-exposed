import { beforeEach, describe, expect, it, vi } from 'vitest'
import { ref } from 'vue'
import { useRecordOptions } from '../src/lib/useRecordOptions'
import { api } from '../src/lib/api'
import type { Field, Row } from '../src/types'

vi.mock('../src/lib/api', () => ({ api: vi.fn() }))

const mockedApi = vi.mocked(api)

beforeEach(() => {
  mockedApi.mockReset()
})

describe('useRecordOptions', () => {
  it('filters disabled roles and preserves the current historical value', async () => {
    mockedApi.mockResolvedValue([
      { code: 'READER', name: '普通角色', enabled: true },
      { code: 'DISABLED', name: '停用角色', enabled: false },
    ])
    const fields: Field[] = [{ key: 'roleCode', label: '角色', type: 'select', lookup: 'roles' }]
    const values: Row = { roleCode: 'DISABLED' }
    const state = useRecordOptions(ref(fields), values)

    await state.loadOptions()

    expect(state.options.roleCode?.map((option) => option.value)).toEqual(['READER', 'DISABLED'])
    expect(state.loading.value).toBe(false)
    expect(state.lookupError.value).toBe('')
  })

  it('exposes lookup errors without leaving loading enabled', async () => {
    mockedApi.mockRejectedValue(new Error('关联数据加载失败'))
    const state = useRecordOptions(ref([{ key: 'roleCode', label: '角色', type: 'select', lookup: 'roles' }]), {})

    await state.loadOptions()

    expect(state.lookupError.value).toBe('关联数据加载失败')
    expect(state.loading.value).toBe(false)
  })
})
