<script setup lang="ts">
defineProps<{ title?: string; subtitle?: string; meta?: string | number }>()
</script>

<template>
  <section class="data-panel">
    <header v-if="title || $slots.header || $slots.actions" class="data-panel-header">
      <div class="data-panel-title">
        <slot name="header">
          <div class="data-panel-heading">
            <h2 v-if="title">{{ title }}</h2>
            <p v-if="subtitle">{{ subtitle }}</p>
          </div>
          <span v-if="meta !== undefined" class="badge neutral">{{ meta }}</span>
        </slot>
      </div>
      <slot name="actions" />
    </header>
    <div v-if="$slots.toolbar" class="data-panel-toolbar"><slot name="toolbar" /></div>
    <div class="data-panel-body"><slot /></div>
    <div v-if="$slots.footer" class="data-panel-footer"><slot name="footer" /></div>
  </section>
</template>
