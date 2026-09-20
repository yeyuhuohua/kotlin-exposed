import { defineStore } from 'pinia'
import { ElMessage } from 'element-plus'
/** 统一业务通知入口，避免各页面自行管理消息计时器和销毁逻辑。 */
export const useNotices = defineStore('notices', () => {
  function show(message: string, error = false) {
    ElMessage({ message, type: error ? 'error' : 'success', duration: 5500, showClose: true, grouping: true })
  }
  return { show }
})
