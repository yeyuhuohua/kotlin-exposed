<script setup lang="ts">
/** 先用当前 HR 登录态向 DSH 代理换会话，未登录或非管理员不会打开对话。 */
import { onMounted, ref } from 'vue'
import { readSession } from '../lib/session'

/** DSH 地址走 VITE_DSH_URL（构建期注入），默认同机 3080；跨设备部署改配置或同源代理。 */
const dshUrl: string = import.meta.env.VITE_DSH_URL || 'http://127.0.0.1:3080'
const ready = ref(false)
const error = ref('')

onMounted(async () => {
  const token = readSession()?.token
  if (!token) {
    error.value = '请先登录'
    return
  }
  try {
    const response = await fetch(`${dshUrl}/hr-session`, {
      method: 'POST',
      credentials: 'include',
      headers: { Authorization: `Bearer ${token}` },
    })
    if (response.status === 401 || response.status === 403) {
      error.value = '需要管理员权限才能打开助手'
      return
    }
    if (!response.ok) {
      error.value = '助手暂时不可用，请稍后重试'
      return
    }
    ready.value = true
  } catch {
    error.value = '无法连接助手服务'
  }
})
</script>

<template>
  <p v-if="error" class="dsh-error">{{ error }}</p>
  <iframe v-else-if="ready" class="dsh-frame" :src="dshUrl" title="人事工作台助手"></iframe>
</template>

<style scoped>
.dsh-frame {
  display: block;
  width: 100%;
  height: 100%;
  border: 0;
  background: var(--surface);
}

.dsh-error {
  margin: 24px;
  color: var(--warn);
}
</style>
