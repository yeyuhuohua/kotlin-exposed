<script setup lang="ts">
import { RefreshCw } from '@lucide/vue'
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
  <el-result v-else-if="error" icon="error" title="加载失败" :sub-title="error" role="alert">
    <template #extra><el-button :icon="RefreshCw" @click="$emit('retry')">重新加载</el-button></template>
  </el-result>
  <el-empty v-else-if="empty" description="暂无匹配记录" :image-size="90" />
</template>
