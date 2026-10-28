<script setup lang="ts">
/** 将 Element Plus 分页事件转换为页面共用的受控分页接口。 */
defineProps<{ page: number; pageSize: number; total: number; disabled?: boolean }>()
defineEmits<{ 'update:page': [value: number]; 'update:pageSize': [value: number] }>()
</script>
<template>
  <footer class="pagination">
    <el-pagination
      :current-page="page"
      :page-size="pageSize"
      :total="total"
      :disabled="disabled"
      :page-sizes="[10, 20, 50]"
      :pager-count="5"
      layout="total, sizes, prev, pager, next"
      background
      @update:current-page="$emit('update:page', $event)"
      @update:page-size="$emit('update:pageSize', $event)"
    />
  </footer>
</template>

<style scoped>
.pagination {
  display: flex;
  align-items: center;
  justify-content: flex-end;
  gap: 12px;
  min-width: 0;
  color: var(--text-muted);
  font-size: 11px;
}

.pagination :deep(.el-pagination) {
  width: 100%;
  flex-wrap: wrap;
  justify-content: flex-end;
  gap: 7px;
}

.pagination :deep(.el-pagination.is-background .btn-prev),
.pagination :deep(.el-pagination.is-background .btn-next),
.pagination :deep(.el-pagination.is-background .el-pager li) {
  min-width: 30px;
  height: 30px;
  border: 1px solid transparent;
  border-radius: 4px;
  background: transparent;
  color: var(--text-muted);
  font-size: 12px;
  font-weight: 550;
  line-height: 28px;
}

.pagination :deep(.el-pagination.is-background .btn-prev:hover:not(:disabled)),
.pagination :deep(.el-pagination.is-background .btn-next:hover:not(:disabled)),
.pagination :deep(.el-pagination.is-background .el-pager li:hover) {
  background: var(--surface-hover);
  color: var(--text-strong);
}

.pagination :deep(.el-pagination.is-background .el-pager li.is-active),
.pagination :deep(.el-pagination.is-background .el-pager li.is-active:hover) {
  border-color: var(--accent);
  background: var(--accent);
  color: var(--on-accent);
}

.pagination :deep(.el-pagination__total) {
  margin-right: auto;
}

.pagination :deep(.el-pagination__total),
.pagination :deep(.el-pagination__jump) {
  color: var(--text-muted);
  font-size: 11px;
}

.pagination :deep(.el-pagination__sizes .el-select__wrapper) {
  min-height: 30px;
  padding: 1px 10px;
}

.pagination :deep(.el-pagination__sizes .el-select__wrapper .el-select__selected-item) {
  font-size: 12px;
  line-height: 22px;
}

@media (max-width: 680px) {
  .pagination {
    gap: 8px;
  }

  .pagination :deep(.el-pagination) {
    justify-content: center;
  }

  .pagination :deep(.el-pagination__total) {
    display: none;
  }
}
</style>
