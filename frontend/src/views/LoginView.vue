<script setup lang="ts">
import { reactive, ref } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { ArrowRight, LockKeyhole, Moon, Sun, Users } from '@lucide/vue'
import type { FormInstance, FormRules } from 'element-plus'
import { useAuth } from '../stores/auth'
import { useTheme } from '../stores/theme'

/** 登录页只接收账号凭据，Token 保存和失效处理统一交给认证状态模块。 */
const auth = useAuth()
const theme = useTheme()
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
    <el-tooltip :content="theme.isDark ? '切换到白天模式' : '切换到夜间模式'">
      <el-button
        text
        class="icon-button outlined login-theme-toggle"
        :icon="theme.isDark ? Sun : Moon"
        :aria-label="theme.isDark ? '切换到白天模式' : '切换到夜间模式'"
        @click="theme.toggle()"
      />
    </el-tooltip>
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

<style scoped>
/* 本组件样式：颜色只用 styles.css 里的语义 token。 */
.login-page {
  min-height: 100dvh;
  position: relative;
  display: flex;
  flex-direction: column;
  align-items: center;
  justify-content: center;
  overflow: hidden;
  padding: 100px 24px 80px;
  background: var(--login-bg);
}

.login-background {
  position: absolute;
  inset: 0;
  width: 100%;
  height: 100%;
  object-fit: cover;
  object-position: center;
  filter: saturate(0.6);
}

.login-shade {
  position: absolute;
  inset: 0;
  background: var(--overlay-login-shade);
}

/* 登录页与工作台的夜间开关 */
.login-theme-toggle {
  position: absolute;
  top: 30px;
  right: 32px;
  z-index: 2;
  background: var(--surface-translucent);
}

.login-brand {
  position: absolute;
  top: 33px;
  left: 40px;
  display: flex;
  align-items: center;
  gap: 12px;
  color: var(--text-green-dark);
}

.login-brand strong {
  font-size: 19px;
}

.login-brand > span:last-child {
  font-size: 10px;
  color: var(--text-medium);
  margin-left: 8px;
}

.login-panel {
  z-index: 1;
  width: 410px;
  max-width: 100%;
  padding: 37px 37px 23px;
  background: var(--surface-translucent);
  box-shadow: 0 15px 55px var(--shadow-login);
  border: 1px solid var(--surface-hairline);
  border-radius: 8px;
  backdrop-filter: blur(12px);
}

.login-eyebrow {
  font-size: 9px;
  color: var(--text-dim);
}

.login-panel h1 {
  font-size: 27px;
  margin: 10px 0 6px;
}

.login-subtitle {
  color: var(--text-faint);
  font-size: 12px;
}

.login-form {
  margin-top: 29px;
  display: flex;
  flex-direction: column;
  gap: 22px;
}

.login-submit {
  width: 100%;
  min-height: 44px;
  font-size: 13px;
  margin-top: 2px;
  justify-content: center;
}

.login-submit > svg:last-child {
  margin-left: auto;
}

.login-submit:has(svg:last-child) {
  padding-left: calc(50% - 16px);
}

.login-submit:disabled {
  padding-left: 14px;
}

.login-security {
  display: flex;
  align-items: center;
  justify-content: center;
  gap: 7px;
  color: var(--text-pale);
  font-size: 10px;
  margin-top: 27px;
  padding-top: 18px;
  border-top: 1px solid var(--border);
}

.login-footer {
  position: absolute;
  bottom: 25px;
  left: 40px;
  right: 40px;
  display: flex;
  justify-content: space-between;
  color: var(--green-deep);
  font-size: 10px;
  z-index: 1;
}

@media (max-width: 680px) {
  .login-brand {
    left: 22px;
    top: 25px;
  }
}

@media (max-width: 680px) {
  .login-brand strong {
    font-size: 16px;
  }
}

@media (max-width: 680px) {
  .login-brand > span:last-child {
    display: none;
  }
}

@media (max-width: 680px) {
  .login-panel {
    padding: 31px 25px 22px;
  }
}

@media (max-width: 680px) {
  .login-footer {
    left: 23px;
    right: 23px;
    font-size: 9px;
  }
}

@media (max-width: 680px) {
  .login-footer > span:last-child {
    display: none;
  }
}

.login-form {
  display: block;
}

.login-form :deep(.el-form-item) {
  margin-bottom: 22px;
}

.login-panel :deep(.el-input__wrapper) {
  min-height: 44px;
}

.login-panel :deep(.el-input__inner) {
  font-size: 13px;
}

.login-form :deep(input.el-input__inner) {
  height: 42px;
  background: transparent;
}

.login-panel .login-submit.el-button {
  padding: 0 16px;
  margin-top: 12px;
  height: 44px;
}

.login-submit.el-button > span {
  justify-content: center;
}
</style>
