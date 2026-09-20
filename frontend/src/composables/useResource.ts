import { onScopeDispose, ref, shallowRef } from 'vue'
/** 取消过时请求并核对请求归属，避免快速切换筛选条件时旧响应覆盖新数据。 */
export function useResource<T>(loader: (signal: AbortSignal) => Promise<T>) {
  const data = shallowRef<T>()
  const loading = ref(false)
  const error = ref('')
  let controller: AbortController | undefined
  async function refresh() {
    controller?.abort()
    const current = new AbortController()
    controller = current
    loading.value = true
    error.value = ''
    try {
      const result = await loader(current.signal)
      if (!current.signal.aborted) data.value = result
    } catch (cause) {
      if (!current.signal.aborted) {
        data.value = undefined
        error.value = cause instanceof Error ? cause.message : '数据加载失败'
      }
    } finally {
      if (controller === current) loading.value = false
    }
  }
  onScopeDispose(() => controller?.abort())
  return { data, loading, error, refresh }
}
