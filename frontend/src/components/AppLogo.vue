<script setup lang="ts">
withDefaults(defineProps<{ compact?: boolean; inverse?: boolean; small?: boolean }>(), {
  compact: false,
  inverse: false,
  small: false,
})
</script>

<template>
  <span :class="['app-logo', { compact, inverse, small }]">
    <span class="logo-mark" aria-hidden="true">
      HR
      <i></i>
    </span>
    <span v-if="!compact" class="logo-copy">
      <strong>人事工作台</strong>
      <small>组织与人员管理</small>
    </span>
  </span>
</template>

<style scoped>
.app-logo {
  display: inline-flex;
  align-items: center;
  gap: 12px;
  min-width: 0;
}

.logo-mark {
  position: relative;
  display: inline-flex;
  align-items: center;
  width: 35px;
  height: 36px;
  flex-shrink: 0;
  color: var(--text-strong);
  font-size: 22px;
  font-weight: 760;
  line-height: 1;
  letter-spacing: -0.09em;
}

.logo-mark i {
  position: absolute;
  right: 0;
  bottom: 5px;
  width: 5px;
  height: 5px;
  background: var(--accent);
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
  white-space: nowrap;
}

.logo-copy small {
  margin-top: 5px;
  color: var(--text-muted);
  font-size: 10px;
  letter-spacing: 0.04em;
}

.inverse .logo-mark,
.inverse .logo-copy strong {
  color: var(--brand-panel-text);
}

.inverse .logo-mark i {
  background: var(--brand-panel-accent);
}

.inverse .logo-copy small {
  color: var(--brand-panel-muted);
}

.small {
  gap: 9px;
}

.small .logo-mark {
  width: 30px;
  height: 32px;
  font-size: 19px;
}

.small .logo-copy strong {
  font-size: 14px;
}

.small .logo-copy small {
  font-size: 9px;
}
</style>
