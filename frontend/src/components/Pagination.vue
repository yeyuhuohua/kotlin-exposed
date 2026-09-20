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
