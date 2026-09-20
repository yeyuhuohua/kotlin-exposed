<script setup lang="ts">
/** 统一弹窗关闭策略：保存期间禁止关闭，焦点锁定和遮罩由 Element Plus 处理。 */
defineProps<{ title: string; wide?: boolean; busy?: boolean }>()
const emit = defineEmits<{ close: [] }>()
</script>
<template>
  <el-dialog
    :model-value="true"
    :title="title"
    :width="wide ? 'min(800px, calc(100vw - 24px))' : 'min(570px, calc(100vw - 24px))'"
    class="app-dialog"
    append-to-body
    destroy-on-close
    align-center
    :close-on-click-modal="!busy"
    :close-on-press-escape="!busy"
    :show-close="!busy"
    @close="emit('close')"
  >
    <slot />
  </el-dialog>
</template>
