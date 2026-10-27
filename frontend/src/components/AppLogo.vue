<script setup lang="ts">
import { Users } from '@lucide/vue'

withDefaults(defineProps<{ compact?: boolean; inverse?: boolean; small?: boolean }>(), {
  compact: false,
  inverse: false,
  small: false,
})
</script>

<template>
  <span :class="['app-logo', { compact, inverse, small }]">
    <span class="logo-mark" aria-hidden="true"><Users :size="small ? 17 : 20" :stroke-width="1.8" /></span>
    <span v-if="!compact" class="logo-copy">
      <strong>人事工作台</strong>
      <small>HR WORKSPACE</small>
    </span>
  </span>
</template>

<style scoped>
.app-logo {
  display: inline-flex;
  align-items: center;
  gap: 11px;
  min-width: 0;
}

.logo-mark {
  width: 38px;
  height: 38px;
  display: grid;
  place-items: center;
  flex-shrink: 0;
  border-radius: 11px;
  background: var(--accent);
  color: var(--on-accent);
  box-shadow: 0 4px 12px var(--focus-ring);
}

.logo-copy {
  display: flex;
  flex-direction: column;
  min-width: 0;
  line-height: 1.2;
}

.logo-copy strong {
  color: var(--text-strong);
  font-size: 15px;
  font-weight: 650;
  letter-spacing: -0.01em;
  white-space: nowrap;
}

.logo-copy small {
  margin-top: 4px;
  color: var(--text-faint);
  font-size: 9px;
  font-weight: 500;
  letter-spacing: 0.12em;
}

.inverse .logo-mark {
  background: var(--brand-panel-accent);
  color: var(--brand-panel);
  box-shadow: none;
}

.inverse .logo-copy strong {
  color: var(--brand-panel-text);
}

.inverse .logo-copy small {
  color: var(--brand-panel-muted);
}

.small .logo-mark {
  width: 32px;
  height: 32px;
  border-radius: 9px;
}

.small .logo-copy strong {
  font-size: 14px;
}

.small .logo-copy small {
  font-size: 8px;
}
</style>
