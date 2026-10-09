<script setup lang="ts">
/** 布局内兜底：路径不存在或页面路由未注册时给出返回入口，替代裸的重定向。 */
import { ArrowLeft, Compass } from '@lucide/vue'
import { useAuth } from '../stores/auth'
const auth = useAuth()
</script>
<template>
  <section class="missing-page">
    <div class="missing-card">
      <span class="missing-icon"><Compass :size="40" /></span>
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
/* 本组件样式：颜色只用 styles.css 里的语义 token。 */
.missing-page {
  min-height: 60vh;
  display: flex;
  align-items: center;
  justify-content: center;
  padding: 24px 0;
}

.missing-card {
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

.missing-icon {
  width: 68px;
  height: 68px;
  display: grid;
  place-items: center;
  border-radius: 18px;
  background: var(--surface-hover);
  border: 1px solid var(--border);
  color: var(--text-medium);
  margin-bottom: 22px;
}

.missing-card h1 {
  font-size: 20px;
  justify-content: center;
}

.missing-card p {
  font-size: 13px;
  color: var(--text-muted);
  line-height: 1.7;
  margin: 10px 0 26px;
}

@media (max-width: 680px) {
  .missing-card {
    padding: 36px 24px 32px;
  }
  .missing-icon {
    width: 58px;
    height: 58px;
  }
}
</style>
