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

<style scoped>
/* 本组件样式：颜色只用 styles.css 里的语义 token。 */
.app-dialog.el-dialog {
  padding: 0;
  border-radius: 8px;
  box-shadow: 0 22px 90px var(--shadow-modal);
}

.app-dialog :deep(.el-dialog__header) {
  margin: 0;
  padding: 19px 48px 18px 23px;
  border-bottom: 1px solid var(--border);
}

.app-dialog :deep(.el-dialog__title) {
  font-size: 17px;
  font-weight: 600;
  overflow-wrap: anywhere;
}

.app-dialog :deep(.el-dialog__headerbtn) {
  top: 9px;
  right: 10px;
}

.app-dialog :deep(.el-dialog__body) {
  padding: 0;
  max-height: 78dvh;
  overflow-y: auto;
}

@media (max-width: 680px) {
  .app-dialog :deep(.el-dialog__body) {
    max-height: 77dvh;
  }
}
</style>
