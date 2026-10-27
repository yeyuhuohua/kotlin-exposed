<script setup lang="ts">
defineProps<{
  title: string
  description?: string
  category?: string
  count?: string | number
}>()
</script>

<template>
  <header class="page-header">
    <div class="page-header-copy">
      <div class="page-header-context">
        <span class="context-mark" aria-hidden="true"></span>
        <span>{{ category || '工作空间' }}</span>
      </div>
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
  min-height: 68px;
  margin-bottom: 24px;
}

.page-header-copy {
  min-width: 0;
}

.page-header-context {
  display: flex;
  align-items: center;
  gap: 8px;
  margin-bottom: 8px;
  color: var(--text-muted);
  font-size: 12px;
  font-weight: 550;
}

.context-mark {
  width: 6px;
  height: 6px;
  border-radius: 2px;
  background: var(--accent);
}

.page-header-title {
  display: flex;
  align-items: center;
  gap: 12px;
  min-width: 0;
}

.page-header-title h1 {
  font-size: 26px;
  font-weight: 650;
  letter-spacing: -0.035em;
  line-height: 1.25;
}

.count-badge {
  display: inline-flex;
  align-items: center;
  min-height: 24px;
  padding: 2px 8px;
  border: 1px solid var(--border);
  border-radius: 6px;
  background: var(--surface);
  color: var(--text-muted);
  font-size: 11px;
  font-weight: 550;
  font-variant-numeric: tabular-nums;
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
  gap: 10px;
  flex-shrink: 0;
}

@media (max-width: 680px) {
  .page-header {
    align-items: flex-start;
    gap: 14px;
    min-height: 60px;
    margin-bottom: 20px;
  }

  .page-header-title h1 {
    font-size: 22px;
  }

  .page-header-description {
    font-size: 12px;
  }

  .page-header-actions {
    gap: 8px;
  }
}
</style>
