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
/* 本组件样式：颜色只用 styles.css 里的语义 token。 */
.pagination {
  display: flex;
  justify-content: flex-end;
  align-items: center;
  gap: 16px;
  margin-top: 10px;
  padding-top: 18px;
  border-top: 1px solid var(--border);
  font-size: 11px;
  color: var(--text-faint);
}

.pagination :deep(.el-pagination) {
  flex-wrap: wrap;
  justify-content: flex-end;
  gap: 8px;
}

/* 分页按钮与每页条数下拉统一成项目的描边小按钮语言，翡翠只落在选中态上。 */
.pagination :deep(.el-pagination.is-background .btn-prev),
.pagination :deep(.el-pagination.is-background .btn-next),
.pagination :deep(.el-pagination.is-background .el-pager li) {
  min-width: 30px;
  height: 30px;
  line-height: 28px;
  border: 1px solid var(--border-strong);
  border-radius: 8px;
  background: var(--surface);
  color: var(--text-muted);
  font-size: 12px;
  font-weight: 550;
  transition:
    color 0.15s,
    border-color 0.15s,
    background 0.15s;
}

.pagination :deep(.el-pagination.is-background .btn-prev:hover:not(:disabled)),
.pagination :deep(.el-pagination.is-background .btn-next:hover:not(:disabled)),
.pagination :deep(.el-pagination.is-background .el-pager li:hover) {
  color: var(--green);
  border-color: var(--border-green-strong);
  background: var(--green-soft-hover);
}

.pagination :deep(.el-pagination.is-background .el-pager li.is-active),
.pagination :deep(.el-pagination.is-background .el-pager li.is-active:hover) {
  border-color: var(--green);
  background: var(--green);
  color: var(--on-accent);
}

.pagination :deep(.el-pagination__total),
.pagination :deep(.el-pagination__jump) {
  font-size: 11px;
  color: var(--text-faint);
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
