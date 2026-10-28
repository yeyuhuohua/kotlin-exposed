<script setup lang="ts">
/** 被页面权限拒绝时提供可访问的返回位置，避免反复跳转到无权访问的首页。 */
import { ShieldX, ArrowLeft } from '@lucide/vue'
import { useAuth } from '../stores/auth'
const auth = useAuth()
</script>
<template>
  <section class="forbidden-page">
    <div class="forbidden-card">
      <span class="forbidden-icon"><ShieldX :size="32" :stroke-width="1.5" /></span>
      <h1>无访问权限</h1>
      <p>当前账号未获此页面授权。</p>
      <RouterLink :to="auth.home" class="button secondary">
        <ArrowLeft :size="16" />
        返回工作台
      </RouterLink>
    </div>
  </section>
</template>

<style scoped>
.forbidden-page {
  display: flex;
  align-items: center;
  justify-content: center;
  min-height: 62vh;
  padding: 32px 0;
}

.forbidden-card {
  display: flex;
  align-items: center;
  flex-direction: column;
  width: 100%;
  max-width: 420px;
  padding: 32px 24px;
  text-align: center;
}

.forbidden-icon {
  margin-bottom: 22px;
  color: var(--danger);
}

.forbidden-card h1 {
  justify-content: center;
  font-size: 22px;
}

.forbidden-card p {
  margin: 10px 0 26px;
  color: var(--text-muted);
  font-size: 13px;
  line-height: 1.7;
}
</style>
