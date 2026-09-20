<script setup lang="ts">
/** 被页面权限拒绝时提供可访问的返回位置，避免反复跳转到无权访问的首页。 */
import { ShieldX, ArrowLeft } from '@lucide/vue'
import { useAuth } from '../stores/auth'
const auth = useAuth()
</script>
<template>
  <section class="state-block forbidden-page">
    <ShieldX :size="40" />
    <h1>无访问权限</h1>
    <p>当前账号未获此页面授权。</p>
    <RouterLink :to="auth.home" class="button secondary">
      <ArrowLeft :size="16" />
      返回工作台
    </RouterLink>
  </section>
</template>
