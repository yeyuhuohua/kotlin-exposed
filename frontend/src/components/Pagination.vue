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
  justify-content: space-between;
  align-items: center;
  gap: 16px;
  padding: 20px 0;
  font-size: 10px;
  color: var(--text-faint);
}

.pagination .icon-button {
  border: 1px solid var(--border-strong);
  background: var(--surface);
  width: 28px;
  height: 28px;
}

@media (max-width: 680px) {
  .pagination {
    font-size: 9px;
    gap: 5px;
  }
}

.pagination {
  justify-content: flex-end;
}

.pagination :deep(.el-pagination) {
  flex-wrap: wrap;
  justify-content: flex-end;
  gap: 8px;
}

/* 分页按钮和每页条数下拉统一成项目的描边小按钮，不再是 2px 圆角的灰色方块。 */
.pagination :deep(.el-pagination.is-background .btn-prev),
.pagination :deep(.el-pagination.is-background .btn-next),
.pagination :deep(.el-pagination.is-background .el-pager li) {
  min-width: 28px;
  height: 28px;
  line-height: 28px;
  border: 1px solid var(--border-strong);
  border-radius: 5px;
  background: var(--surface);
  color: var(--text-muted);
  font-size: 11px;
}

.pagination :deep(.el-pagination.is-background .el-pager li.is-active) {
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
  min-height: 28px;
  padding: 1px 8px;
}

.pagination :deep(.el-pagination__sizes .el-select__wrapper .el-select__selected-item) {
  font-size: 11px;
  line-height: 20px;
}

@media (max-width: 680px) {
  .pagination :deep(.el-pagination) {
    justify-content: center;
  }
}

@media (max-width: 680px) {
  .pagination :deep(.el-pagination__total) {
    display: none;
  }
}
</style>
