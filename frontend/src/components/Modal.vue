<script setup lang="ts">
/** 统一弹窗关闭策略：保存期间禁止关闭，焦点锁定和遮罩由 Element Plus 处理。 */
import { useSlots } from 'vue'

defineProps<{ title: string; wide?: boolean; busy?: boolean }>()
const emit = defineEmits<{ close: [] }>()
const slots = useSlots()
</script>
<template>
  <el-dialog
    :model-value="true"
    :title="title"
    :width="wide ? 'min(820px, calc(100vw - 24px))' : 'min(600px, calc(100vw - 24px))'"
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
    <!-- 调用方传入 footer 插槽时透传给 el-dialog，确认类按钮才挂得上 -->
    <template v-if="slots.footer" #footer>
      <slot name="footer" />
    </template>
  </el-dialog>
</template>

<style scoped>
/* 本组件样式：颜色只用 styles.css 里的语义 token。 */
.app-dialog.el-dialog {
  padding: 0;
  border-radius: var(--card-radius);
  box-shadow: 0 22px 90px var(--shadow-modal);
}

.app-dialog :deep(.el-dialog__header) {
  margin: 0;
  padding: 20px 56px 19px 26px;
  border-bottom: 1px solid var(--border);
}

.app-dialog :deep(.el-dialog__title) {
  font-size: 16px;
  font-weight: 600;
  letter-spacing: -0.01em;
  color: var(--text-strong);
  overflow-wrap: anywhere;
}

.app-dialog :deep(.el-dialog__headerbtn) {
  top: 13px;
  right: 13px;
  width: 34px;
  height: 34px;
  border-radius: 9px;
  color: var(--text-muted);
}

.app-dialog :deep(.el-dialog__headerbtn:hover) {
  background: var(--surface-hover);
  color: var(--text-strong);
}

.app-dialog :deep(.el-dialog__close) {
  color: inherit;
}

.app-dialog :deep(.el-dialog__body) {
  padding: 0;
  max-height: 78dvh;
  overflow-y: auto;
}

@media (max-width: 680px) {
  .app-dialog :deep(.el-dialog__header) {
    padding: 18px 52px 17px 22px;
  }
  .app-dialog :deep(.el-dialog__body) {
    max-height: 77dvh;
  }
}
</style>
