<script setup lang="ts">
/** 布局内兜底：路径不存在或页面路由未注册时给出返回入口，替代裸的重定向。 */
import { ArrowLeft, Compass } from '@lucide/vue'
import { useAuth } from '../stores/auth'
const auth = useAuth()
</script>
<template>
  <section class="missing-page">
    <div class="missing-card">
      <span class="missing-icon"><Compass :size="32" :stroke-width="1.5" /></span>
      <h1>页面不存在</h1>
      <p>地址有误，或该页面对当前账号不可用。</p>
      <RouterLink :to="auth.home" class="button secondary">
        <ArrowLeft :size="16" />
        返回工作台
      </RouterLink>
    </div>
  </section>
</template>

<style scoped>
.missing-page {
  display: flex;
  align-items: center;
  justify-content: center;
  min-height: 62vh;
  padding: 32px 0;
}

.missing-card {
  display: flex;
  align-items: center;
  flex-direction: column;
  width: 100%;
  max-width: 420px;
  padding: 32px 24px;
  text-align: center;
}

.missing-icon {
  margin-bottom: 22px;
  color: var(--text-muted);
}

.missing-card h1 {
  justify-content: center;
  font-size: 22px;
}

.missing-card p {
  margin: 10px 0 26px;
  color: var(--text-muted);
  font-size: 13px;
  line-height: 1.7;
}
</style>
