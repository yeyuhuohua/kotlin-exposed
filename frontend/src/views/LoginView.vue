<script setup lang="ts">
import { reactive, ref } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { ArrowRight, LockKeyhole, Users } from '@lucide/vue'
import type { FormInstance, FormRules } from 'element-plus'
import { useAuth } from '../stores/auth'

/** 登录页只接收账号凭据，Token 保存和失效处理统一交给认证状态模块。 */
const auth = useAuth()
const router = useRouter()
const route = useRoute()
const form = reactive({ username: '', password: '' })
const formRef = ref<FormInstance>()
const busy = ref(false)
const error = ref('')
const rules: FormRules = {
  username: [{ required: true, whitespace: true, message: '请输入用户名', trigger: 'blur' }],
  password: [{ required: true, message: '请输入密码', trigger: 'blur' }],
}
async function submit() {
  if (busy.value || !(await formRef.value?.validate().catch(() => false))) return
  busy.value = true
  error.value = ''
  try {
    await auth.login(form.username.trim(), form.password)
    form.password = ''
    const target = typeof route.query.redirect === 'string' ? route.query.redirect : auth.home
    // 不跳转到外部地址，也不返回已失去页面权限的旧地址。
    await router.replace(
      target.startsWith('/') &&
        !target.startsWith('//') &&
        !target.startsWith('/login') &&
        auth.canPage(router.resolve(target).path)
        ? target
        : auth.home,
    )
  } catch (cause) {
    error.value = cause instanceof Error ? cause.message : '登录失败'
  } finally {
    busy.value = false
  }
}
</script>
<template>
  <div class="login-page">
    <img
      class="login-background"
      src="https://images.unsplash.com/photo-1497366216548-37526070297c?auto=format&amp;fit=crop&amp;w=2400&amp;q=85"
      alt="明亮的开放式办公空间"
    />
    <div class="login-shade"></div>
    <div class="login-brand">
      <span class="brand-icon"><Users :size="25" /></span>
      <strong>人事工作台</strong>
      <span>HR WORKSPACE</span>
    </div>
    <section class="login-panel">
      <span class="login-eyebrow">HR WORKSPACE</span>
      <h1>登录工作台</h1>
      <p class="login-subtitle">欢迎回来</p>
      <el-form
        ref="formRef"
        :model="form"
        :rules="rules"
        label-position="top"
        class="login-form"
        @submit.prevent="submit"
      >
        <el-form-item label="用户名" prop="username">
          <el-input
            v-model="form.username"
            name="username"
            aria-label="用户名"
            autocomplete="username"
            placeholder="输入用户名"
            maxlength="64"
            :disabled="busy"
          />
        </el-form-item>
        <el-form-item label="密码" prop="password">
          <el-input
            v-model="form.password"
            name="password"
            aria-label="密码"
            type="password"
            show-password
            autocomplete="current-password"
            placeholder="输入密码"
            maxlength="128"
            :disabled="busy"
          />
        </el-form-item>
        <el-alert v-if="error" :title="error" type="error" :closable="false" show-icon />
        <el-button native-type="submit" type="primary" class="login-submit" :loading="busy">
          登录
          <ArrowRight v-if="!busy" :size="18" />
        </el-button>
      </el-form>
      <div class="login-security">
        <LockKeyhole :size="14" />
        <span>组织账号 · 安全访问</span>
      </div>
    </section>
    <footer class="login-footer">
      <span>HR / 人事与组织管理</span>
      <span>HR WORKSPACE</span>
    </footer>
  </div>
</template>
