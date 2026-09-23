<script setup lang="ts">
/** 被页面权限拒绝时提供可访问的返回位置，避免反复跳转到无权访问的首页。 */
import { ShieldX, ArrowLeft } from '@lucide/vue'
import { useAuth } from '../stores/auth'
const auth = useAuth()
</script>
<template>
  <section class="forbidden-page">
    <div class="forbidden-card">
      <span class="forbidden-icon"><ShieldX :size="40" /></span>
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
/* 本组件样式：颜色只用 styles.css 里的语义 token。 */
.forbidden-page {
  min-height: 60vh;
  display: flex;
  align-items: center;
  justify-content: center;
  padding: 24px 0;
}

.forbidden-card {
  width: 100%;
  max-width: 430px;
  background: var(--surface);
  border: 1px solid var(--border);
  border-radius: var(--card-radius);
  padding: 46px 44px 42px;
  display: flex;
  flex-direction: column;
  align-items: center;
  text-align: center;
}

.forbidden-icon {
  width: 68px;
  height: 68px;
  display: grid;
  place-items: center;
  border-radius: 18px;
  background: var(--danger-tint);
  border: 1px solid var(--danger-soft);
  color: var(--danger);
  margin-bottom: 22px;
}

.forbidden-card h1 {
  font-size: 20px;
  justify-content: center;
}

.forbidden-card p {
  font-size: 13px;
  color: var(--text-muted);
  line-height: 1.7;
  margin: 10px 0 26px;
}

@media (max-width: 680px) {
  .forbidden-card {
    padding: 36px 24px 32px;
  }
  .forbidden-icon {
    width: 58px;
    height: 58px;
  }
}
</style>
