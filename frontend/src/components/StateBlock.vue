<script setup lang="ts">
import { CircleAlert, Inbox, RefreshCw } from '@lucide/vue'
/** 列表加载、错误与空数据状态；重试操作仍由调用页面决定。 */
defineProps<{ loading?: boolean; error?: string; empty?: boolean }>()
defineEmits<{ retry: [] }>()
</script>
<template>
  <div
    v-if="loading"
    v-loading="true"
    element-loading-text="正在加载"
    class="state-block"
    role="status"
    aria-label="正在加载"
  ></div>
  <div v-else-if="error" class="state-block" role="alert">
    <CircleAlert :size="36" :stroke-width="1.5" />
    <p>
      <strong>加载失败</strong>
      {{ error }}
    </p>
    <button type="button" class="button secondary" @click="$emit('retry')">
      <RefreshCw :size="15" />
      重新加载
    </button>
  </div>
  <div v-else-if="empty" class="state-block">
    <Inbox :size="36" :stroke-width="1.5" class="empty-icon" />
    <p>暂无匹配记录</p>
  </div>
</template>

<style scoped>
/* 本组件样式：颜色只用 styles.css 里的语义 token。 */
.state-block strong {
  display: block;
  font-size: 14px;
  font-weight: 600;
  letter-spacing: -0.01em;
  margin-bottom: 5px;
}

.state-block[role='alert'] > svg {
  color: var(--danger);
}

.empty-icon {
  color: var(--text-pale);
}

.state-block .button {
  margin-top: 4px;
}
</style>
