<script setup lang="ts">
defineProps<{
  title: string
  description?: string
  count?: string | number
}>()
</script>

<template>
  <header class="page-header">
    <div class="page-header-copy">
      <div class="page-header-title">
        <h1>{{ title }}</h1>
        <span v-if="count !== undefined" class="count-badge">{{ count }}</span>
      </div>
      <p v-if="description" class="page-header-description">{{ description }}</p>
    </div>
    <div v-if="$slots.default" class="page-header-actions"><slot /></div>
  </header>
</template>

<style scoped>
.page-header {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 24px;
  min-height: 72px;
  margin-bottom: 24px;
  padding-bottom: 22px;
  border-bottom: 1px solid var(--border);
}

.page-header-copy {
  min-width: 0;
}

.page-header-title {
  display: flex;
  align-items: baseline;
  gap: 10px;
  min-width: 0;
}

.page-header-title h1 {
  color: var(--text-strong);
}

.count-badge {
  color: var(--text-muted);
  font-size: 13px;
  font-variant-numeric: tabular-nums;
}

.count-badge::before {
  content: '(';
}

.count-badge::after {
  content: ')';
}

.page-header-description {
  margin-top: 7px;
  color: var(--text-muted);
  font-size: 13px;
  line-height: 1.6;
}

.page-header-actions {
  display: flex;
  align-items: center;
  justify-content: flex-end;
  gap: 9px;
  flex-shrink: 0;
}

@media (max-width: 680px) {
  .page-header {
    align-items: flex-start;
    gap: 14px;
    min-height: 64px;
    margin-bottom: 20px;
    padding-bottom: 18px;
  }

  .page-header-title {
    align-items: center;
  }

  .page-header-description {
    font-size: 12px;
  }

  .page-header-actions {
    gap: 7px;
  }
}
</style>
